package br.com.goias.chamados.consulta;

import br.com.goias.chamados.consulta.ConsultaResultado.ItemLinhaTempo;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracao;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracaoRepository;
import br.com.goias.chamados.dominio.chamado.ChamadoRepository;
import br.com.goias.chamados.dominio.chamado.MotivoAguardando;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.chamado.TipoInteracao;
import br.com.goias.chamados.dominio.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Consulta pública pelo código do chamado.
 *
 * Linha do tempo: só interações NÃO internas dos tipos MUDANCA_STATUS e RESPOSTA_SOLICITANTE.
 * Comentários internos nunca aparecem (filtro duplo: interno = false E tipo permitido).
 */
@Service
@RequiredArgsConstructor
public class ConsultaService {

    private static final Set<TipoInteracao> TIPOS_VISIVEIS =
            Set.of(TipoInteracao.MUDANCA_STATUS, TipoInteracao.RESPOSTA_SOLICITANTE);

    private final ChamadoRepository chamadoRepository;
    private final ChamadoInteracaoRepository interacaoRepository;

    /** @param protocolo código já normalizado (ex.: "TI-2026-7K3F") */
    @Transactional(readOnly = true)
    public Optional<ConsultaResultado> buscar(String protocolo) {
        return chamadoRepository.findByProtocolo(protocolo).map(this::montar);
    }

    private ConsultaResultado montar(Chamado chamado) {
        List<ItemLinhaTempo> linhaTempo = interacaoRepository
                .findByChamadoIdAndInternoFalseOrderByDataAscIdAsc(chamado.getId())
                .stream()
                .filter(i -> !i.isInterno() && TIPOS_VISIVEIS.contains(i.getTipo()))
                .map(ConsultaService::paraItem)
                .sorted(Comparator.comparing(ItemLinhaTempo::data).reversed()) // mais recente primeiro
                .toList();

        return new ConsultaResultado(
                chamado.getProtocolo(),
                chamado.getStatus(),
                chamado.getArea().getNome(),
                chamado.getCategoria().getNome(),
                chamado.getLocal().getNome(),
                chamado.getTitulo(),
                chamado.getDataAbertura(),
                primeiroNome(chamado.getAtendente()),
                chamado.getAguardandoMotivo() == null ? null : chamado.getAguardandoMotivo().getTextoPublico(),
                chamado.getAguardandoMotivo() == MotivoAguardando.SOLICITANTE,
                linhaTempo);
    }

    private static ItemLinhaTempo paraItem(ChamadoInteracao interacao) {
        if (interacao.getTipo() == TipoInteracao.MUDANCA_STATUS) {
            String texto = textoLimpo(interacao.getTexto());
            boolean abertura = interacao.getStatusAnterior() == null
                    && interacao.getStatusNovo() == StatusChamado.ABERTO;
            String titulo;
            if (abertura) {
                titulo = "Chamado aberto";
                if ("Chamado aberto".equalsIgnoreCase(texto)) {
                    texto = null; // repetiria o título
                }
            } else if (interacao.getStatusNovo() == null) {
                titulo = "Status atualizado";
            } else {
                titulo = "Status: " + interacao.getStatusNovo().getDescricao();
            }
            return new ItemLinhaTempo(interacao.getData(), titulo, texto, false);
        }
        String autor = primeiroNome(interacao.getAutor());
        String titulo = autor == null ? "Mensagem" : "Resposta de " + autor;
        return new ItemLinhaTempo(interacao.getData(), titulo, textoLimpo(interacao.getTexto()), true);
    }

    /** "Maria da Silva" → "Maria". Null quando não há atendente. */
    static String primeiroNome(Usuario usuario) {
        if (usuario == null || usuario.getNome() == null || usuario.getNome().isBlank()) {
            return null;
        }
        return usuario.getNome().trim().split("\\s+")[0];
    }

    private static String textoLimpo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
