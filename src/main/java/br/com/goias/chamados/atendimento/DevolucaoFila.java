package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.CalculadoraPrazo;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracao;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracaoRepository;
import br.com.goias.chamados.dominio.chamado.ChamadoRepository;
import br.com.goias.chamados.dominio.chamado.PausaSla;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.chamado.TipoInteracao;
import br.com.goias.chamados.dominio.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Regra ÚNICA de "devolver o chamado para a fila" (Etapa 3), usada:
 * - pelo botão "Devolver" do próprio responsável ({@link AtendimentoService#devolver});
 * - pelo admin, ao mudar a área de alguém com chamados em andamento (ajuste pós-MVP);
 * - pelo admin, ao desativar alguém, se marcar "Devolver os chamados dele para a fila".
 *
 * O que acontece com cada chamado:
 * 1. fica sem responsável e grava uma ATRIBUICAO (interna) com o texto informado;
 * 2. se ainda NÃO houve resposta ao solicitante e estava EM_ATENDIMENTO ou AGUARDANDO,
 *    volta para ABERTO (e uma pausa de SLA em andamento é encerrada, empurrando os prazos);
 * 3. senão, continua no mesmo status, só que sem responsável.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevolucaoFila {

    /** Chamados que contam como "em andamento" de um responsável. */
    static final EnumSet<StatusChamado> FINALIZADOS = EnumSet.of(StatusChamado.RESOLVIDO, StatusChamado.FECHADO);

    public static final String TEXTO_MUDANCA_AREA = "Devolvido à fila por mudança de área do responsável.";
    public static final String TEXTO_DESATIVACAO = "Devolvido à fila porque o responsável foi desativado.";

    private final ChamadoRepository chamadoRepository;
    private final ChamadoInteracaoRepository interacaoRepository;
    private final CalculadoraPrazo calculadoraPrazo;

    /**
     * Para qual status o chamado vai ao ser devolvido (regra pura, testável).
     *
     * @param jaRespondeu já existe alguma RESPOSTA_SOLICITANTE no chamado
     */
    public static StatusChamado statusDepoisDeDevolver(StatusChamado atual, boolean jaRespondeu) {
        if (!jaRespondeu && (atual == StatusChamado.EM_ATENDIMENTO || atual == StatusChamado.AGUARDANDO)) {
            return StatusChamado.ABERTO;
        }
        return atual;
    }

    /**
     * Devolve UM chamado (já carregado e conferido por quem chamou).
     * Precisa rodar dentro de uma transação existente.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void devolver(Chamado chamado, Usuario autor, String texto, LocalDateTime agora) {
        chamado.setAtendente(null);
        gravar(chamado, autor, TipoInteracao.ATRIBUICAO, texto, null, null, agora);

        boolean jaRespondeu = interacaoRepository.existsByChamadoIdAndTipo(chamado.getId(), TipoInteracao.RESPOSTA_SOLICITANTE);
        StatusChamado atual = chamado.getStatus();
        StatusChamado novo = statusDepoisDeDevolver(atual, jaRespondeu);
        if (novo != atual) {
            if (atual == StatusChamado.AGUARDANDO) {
                PausaSla.encerrar(chamado, agora, calculadoraPrazo);
            }
            chamado.setStatus(novo);
            gravar(chamado, autor, TipoInteracao.MUDANCA_STATUS, null, atual, novo, agora);
        }
    }

    /**
     * Devolve para a fila TODOS os chamados em andamento (não resolvidos/fechados) de um responsável.
     * Precisa rodar dentro da transação de quem chamou (ex.: salvar o usuário): ou tudo, ou nada.
     *
     * @param autor quem fez a mudança (o admin), gravado como autor das interações
     * @return quantos chamados foram devolvidos
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int devolverTodosDe(Long responsavelId, Usuario autor, String texto) {
        List<Chamado> chamados = chamadoRepository.findByAtendenteIdAndStatusNotIn(responsavelId, FINALIZADOS);
        LocalDateTime agora = LocalDateTime.now();
        for (Chamado chamado : chamados) {
            devolver(chamado, autor, texto, agora);
        }
        if (!chamados.isEmpty()) {
            log.info("{} chamado(s) do usuário {} devolvido(s) para a fila: {}", chamados.size(), responsavelId, texto);
        }
        return chamados.size();
    }

    /** Grava uma interação; "interno" segue a regra oficial do tipo (TipoInteracao.isInterno). */
    private void gravar(Chamado chamado, Usuario autor, TipoInteracao tipo, String texto,
                        StatusChamado anterior, StatusChamado novo, LocalDateTime agora) {
        ChamadoInteracao interacao = new ChamadoInteracao();
        interacao.setChamado(chamado);
        interacao.setAutor(autor);
        interacao.setTipo(tipo);
        interacao.setTexto(texto);
        interacao.setInterno(tipo.isInterno());
        interacao.setStatusAnterior(anterior);
        interacao.setStatusNovo(novo);
        interacao.setData(agora);
        interacaoRepository.save(interacao);
    }
}
