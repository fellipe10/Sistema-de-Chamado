package br.com.goias.chamados.atendimento;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * Abas da fila (parâmetro ?filtro=...).
 *
 * NOVOS       → ABERTO sem atendente
 * MEUS        → atendente = usuário logado, não fechados (inclui RESOLVIDO)
 * ANDAMENTO   → todos os não resolvidos/fechados
 * AGUARDANDO  → status AGUARDANDO
 * HISTORICO   → RESOLVIDO e FECHADO
 */
@Getter
@RequiredArgsConstructor
public enum AbaFila {

    NOVOS("novos", "Novos"),
    MEUS("meus", "Meus"),
    ANDAMENTO("andamento", "Em andamento"),
    AGUARDANDO("aguardando", "Aguardando"),
    HISTORICO("historico", "Resolvidos e fechados");

    /** Aba usada quando o link não informa nenhuma (o contador do menu também é de "Novos"). */
    public static final AbaFila PADRAO = NOVOS;

    private final String parametro;
    private final String rotulo;

    public static AbaFila doParametro(String valor) {
        if (valor == null) {
            return PADRAO;
        }
        return Arrays.stream(values())
                .filter(a -> a.parametro.equalsIgnoreCase(valor.strip()))
                .findFirst()
                .orElse(PADRAO);
    }
}
