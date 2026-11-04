package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.StatusChamado;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Mudanças de status permitidas pela tela de atendimento (qualquer outra é recusada no servidor).
 *
 * EM_ATENDIMENTO ↔ AGUARDANDO
 * EM_ATENDIMENTO ou AGUARDANDO → RESOLVIDO  (exige mensagem de solução → RESPOSTA_SOLICITANTE)
 * RESOLVIDO → FECHADO                      (FECHADO é final)
 * RESOLVIDO → REABERTO                     (exige motivo → COMENTARIO interno)
 * REABERTO → EM_ATENDIMENTO
 *
 * ABERTO → EM_ATENDIMENTO não está aqui de propósito: acontece só ao ASSUMIR o chamado
 * (ou quando o gestor transfere — ver AtendimentoService).
 */
public final class TransicoesStatus {

    private static final Map<StatusChamado, Set<StatusChamado>> PERMITIDAS = new EnumMap<>(StatusChamado.class);

    static {
        PERMITIDAS.put(StatusChamado.ABERTO, EnumSet.noneOf(StatusChamado.class));
        PERMITIDAS.put(StatusChamado.EM_ATENDIMENTO, EnumSet.of(StatusChamado.AGUARDANDO, StatusChamado.RESOLVIDO));
        PERMITIDAS.put(StatusChamado.AGUARDANDO, EnumSet.of(StatusChamado.EM_ATENDIMENTO, StatusChamado.RESOLVIDO));
        PERMITIDAS.put(StatusChamado.RESOLVIDO, EnumSet.of(StatusChamado.FECHADO, StatusChamado.REABERTO));
        PERMITIDAS.put(StatusChamado.REABERTO, EnumSet.of(StatusChamado.EM_ATENDIMENTO));
        PERMITIDAS.put(StatusChamado.FECHADO, EnumSet.noneOf(StatusChamado.class));
    }

    private TransicoesStatus() {
    }

    /** Status para os quais dá para ir a partir de "atual" (em ordem do enum). */
    public static Set<StatusChamado> permitidas(StatusChamado atual) {
        Set<StatusChamado> destinos = atual == null ? null : PERMITIDAS.get(atual);
        return destinos == null ? EnumSet.noneOf(StatusChamado.class) : EnumSet.copyOf(destinos);
    }

    public static boolean permitida(StatusChamado de, StatusChamado para) {
        return de != null && para != null && permitidas(de).contains(para);
    }

    /** RESOLVIDO exige mensagem de solução; REABERTO exige motivo. */
    public static boolean exigeMensagem(StatusChamado para) {
        return para == StatusChamado.RESOLVIDO || para == StatusChamado.REABERTO;
    }
}
