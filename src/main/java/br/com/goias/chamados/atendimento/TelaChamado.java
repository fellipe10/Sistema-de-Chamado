package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.MotivoAguardando;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.chamado.TipoInteracao;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tudo o que a tela do chamado (GET /atendimento/{id}) mostra.
 * O chamado vem com área, categoria, setor, local e atendente já carregados.
 */
public record TelaChamado(
        Chamado chamado,
        PermissoesAtendimento permissoes,
        BadgeSla badgeSla,
        List<ItemHistorico> historico,
        List<AnexoTela> anexos,
        List<OpcaoStatus> opcoesStatus,
        List<Prioridade> prioridades,
        List<Pessoa> paraTransferir,
        boolean podeResponder,
        boolean podeMudarPrioridade,
        List<MotivoAguardando> motivosAguardando) {

    public boolean fechado() {
        return chamado.getStatus() == StatusChamado.FECHADO;
    }

    public String classeStatus() {
        return "status-" + chamado.getStatus().name().toLowerCase();
    }

    public String classePrioridade() {
        return "prioridade-" + chamado.getPrioridade().name().toLowerCase();
    }

    /** 1ª resposta dentro do prazo? null = ainda sem resposta ou sem prazo. */
    public Boolean primeiraRespostaNoPrazo() {
        if (chamado.getDataPrimeiraResposta() == null || chamado.getPrazoPrimeiraResposta() == null) {
            return null;
        }
        return !chamado.getDataPrimeiraResposta().isAfter(chamado.getPrazoPrimeiraResposta());
    }

    /** Um registro da linha do tempo. */
    public record ItemHistorico(
            LocalDateTime data,
            TipoInteracao tipo,
            String titulo,
            String texto,
            String autor,
            boolean interno,
            String icone,
            String classe) {
    }

    /** Anexo com o tamanho já formatado. */
    public record AnexoTela(Long id, String nome, String tipoMime, String tamanho, String icone) {
    }

    /** Tempo já pausado, formatado ("1h 20min"); null se nunca pausou. */
    public String tempoPausado() {
        Long m = chamado.getMinutosPausados();
        return m == null || m <= 0 ? null : AtendimentoService.formatarMinutos(m);
    }

    /**
     * Botão de mudança de status.
     * exigeMensagem → abre campo de texto (resolver, reabrir); exigeMotivo → abre a escolha do motivo (aguardando).
     */
    public record OpcaoStatus(
            StatusChamado status,
            String rotulo,
            String icone,
            String classeBotao,
            boolean exigeMensagem,
            boolean exigeMotivo,
            String rotuloMensagem,
            String ajuda) {
    }

    /** Atendente/gestor da área (lista de transferência). */
    public record Pessoa(Long id, String nome, String perfil) {
    }
}
