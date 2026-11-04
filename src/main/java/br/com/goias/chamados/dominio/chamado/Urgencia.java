package br.com.goias.chamados.dominio.chamado;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Urgência informada pelo solicitante no formulário público.
 * Vira a prioridade inicial do chamado; a equipe pode ajustar depois
 * (inclusive para CRITICA, que o solicitante não escolhe).
 */
@Getter
@RequiredArgsConstructor
public enum Urgencia {

    BAIXA("Baixa", "Posso aguardar, não atrapalha meu trabalho", Prioridade.BAIXA),
    MEDIA("Média", "Atrapalha, mas consigo continuar trabalhando", Prioridade.MEDIA),
    ALTA("Alta", "Estou com o trabalho parado ou muito prejudicado", Prioridade.ALTA);

    private final String descricao;
    private final String explicacao;
    private final Prioridade prioridadeSugerida;
}
