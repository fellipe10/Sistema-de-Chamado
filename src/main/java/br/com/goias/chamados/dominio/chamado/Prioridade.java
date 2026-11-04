package br.com.goias.chamados.dominio.chamado;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Prioridade definida pela equipe (base do SLA).
 * "peso" serve para ordenar a fila (maior = mais urgente).
 */
@Getter
@RequiredArgsConstructor
public enum Prioridade {

    CRITICA("Crítica", 4),
    ALTA("Alta", 3),
    MEDIA("Média", 2),
    BAIXA("Baixa", 1);

    private final String descricao;
    private final int peso;
}
