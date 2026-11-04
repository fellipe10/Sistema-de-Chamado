package br.com.goias.chamados.admin.comum;

import java.util.Locale;

/**
 * Leitura tolerante de parâmetros da URL nas telas de administração:
 * texto inválido é ignorado em vez de dar erro 400 (mesmo padrão da fila e do painel).
 */
public final class Parametros {

    private Parametros() {
    }

    public static Long numero(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(valor.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer inteiro(String valor) {
        Long n = numero(valor);
        return n == null || n > Integer.MAX_VALUE || n < Integer.MIN_VALUE ? null : n.intValue();
    }

    /** Busca digitada: sem espaços sobrando, no máximo 100 caracteres; vazia = null. */
    public static String busca(String valor) {
        if (valor == null) {
            return null;
        }
        String limpa = valor.strip().replaceAll("\\s+", " ");
        if (limpa.isEmpty()) {
            return null;
        }
        return limpa.length() > 100 ? limpa.substring(0, 100) : limpa;
    }

    /** O texto contém a busca (sem diferenciar maiúsculas nem acentos)? */
    public static boolean contem(String texto, String busca) {
        if (busca == null) {
            return true;
        }
        return RegrasNome.chave(texto).contains(RegrasNome.chave(busca.toLowerCase(Locale.ROOT)));
    }
}
