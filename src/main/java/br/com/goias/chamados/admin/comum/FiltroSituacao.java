package br.com.goias.chamados.admin.comum;

/**
 * Filtro "Situação" das listas de cadastro: ativos (padrão), inativos ou todos.
 */
public enum FiltroSituacao {

    ATIVOS("ativos", "Ativos"),
    INATIVOS("inativos", "Inativos"),
    TODOS("todos", "Todos");

    private final String parametro;
    private final String rotulo;

    FiltroSituacao(String parametro, String rotulo) {
        this.parametro = parametro;
        this.rotulo = rotulo;
    }

    public String getParametro() {
        return parametro;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Valor da URL; qualquer coisa diferente cai em "ativos". */
    public static FiltroSituacao de(String valor) {
        if (valor != null) {
            for (FiltroSituacao f : values()) {
                if (f.parametro.equalsIgnoreCase(valor.strip())) {
                    return f;
                }
            }
        }
        return ATIVOS;
    }

    public boolean aceita(boolean ativo) {
        return this == TODOS || (this == ATIVOS) == ativo;
    }
}
