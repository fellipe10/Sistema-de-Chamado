package br.com.goias.chamados.dominio.chamado;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Ciclo de vida do chamado:
 * ABERTO → EM_ATENDIMENTO → AGUARDANDO → RESOLVIDO → FECHADO
 * (REABERTO quando o problema não foi resolvido).
 */
@Getter
@RequiredArgsConstructor
public enum StatusChamado {

    ABERTO("Aberto"),
    EM_ATENDIMENTO("Em atendimento"),
    AGUARDANDO("Aguardando"),
    RESOLVIDO("Resolvido"),
    FECHADO("Fechado"),
    REABERTO("Reaberto");

    private final String descricao;

    /** Chamado ainda conta para fila e SLA de resolução. */
    public boolean isEmAndamento() {
        return this != RESOLVIDO && this != FECHADO;
    }
}
