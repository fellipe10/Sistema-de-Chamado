package br.com.goias.chamados.admin.recalculo;

import java.util.Arrays;

/**
 * De qual tela veio o pedido de recálculo (vai no comentário interno e no link de volta).
 */
public enum OrigemRecalculo {

    SLA("sla", "SLA", "/admin/sla", true),
    EXPEDIENTE("expediente", "expediente", "/admin/expediente", true),
    FERIADO("feriado", "feriado", "/admin/feriados", false);

    private final String parametro;
    private final String nome;
    private final String telaDeVolta;
    /** true = recalcula só a área da tela; false = todas as áreas (feriado pode valer para todas). */
    private final boolean porArea;

    OrigemRecalculo(String parametro, String nome, String telaDeVolta, boolean porArea) {
        this.parametro = parametro;
        this.nome = nome;
        this.telaDeVolta = telaDeVolta;
        this.porArea = porArea;
    }

    public String getParametro() {
        return parametro;
    }

    public String getNome() {
        return nome;
    }

    public String getTelaDeVolta() {
        return telaDeVolta;
    }

    public boolean isPorArea() {
        return porArea;
    }

    /** Texto do COMENTARIO interno gravado em cada chamado recalculado. */
    public String comentario(String nomeAdmin) {
        return "Prazos recalculados após mudança de " + nome + " por " + nomeAdmin + ".";
    }

    public static OrigemRecalculo de(String valor) {
        return Arrays.stream(values())
                .filter(o -> o.parametro.equalsIgnoreCase(valor == null ? "" : valor.strip()))
                .findFirst()
                .orElse(null);
    }
}
