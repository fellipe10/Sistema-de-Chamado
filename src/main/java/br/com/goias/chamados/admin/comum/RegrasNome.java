package br.com.goias.chamados.admin.comum;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

/**
 * Regras do campo "nome" dos cadastros simples (setor, local, categoria, área) — Etapa 5.
 *
 * - obrigatório, com espaços sobrando removidos;
 * - tamanho máximo do banco;
 * - único sem diferenciar maiúsculas/minúsculas nem acentos ("Elétrica" = "eletrica").
 */
public final class RegrasNome {

    private RegrasNome() {
    }

    /** "  Sala   de  TI " → "Sala de TI" (null continua null). */
    public static String normalizar(String nome) {
        return nome == null ? null : nome.strip().replaceAll("\\s+", " ");
    }

    /** Chave de comparação: sem acento, minúsculas, espaços normalizados. */
    public static String chave(String nome) {
        String n = normalizar(nome);
        if (n == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    /**
     * @param outrosNomes nomes já cadastrados (sem o próprio item, quando é edição)
     * @param oQue        "setor", "local", "categoria"... para a mensagem
     * @return mensagem de erro, ou null se o nome serve
     */
    public static String validar(String nome, int tamanhoMaximo, Collection<String> outrosNomes, String oQue) {
        String n = normalizar(nome);
        if (n == null || n.isEmpty()) {
            return "Informe o nome.";
        }
        if (n.length() > tamanhoMaximo) {
            return "O nome pode ter no máximo " + tamanhoMaximo + " caracteres.";
        }
        String chave = chave(n);
        if (outrosNomes.stream().anyMatch(outro -> chave(outro).equals(chave))) {
            return "Já existe " + oQue + " com este nome.";
        }
        return null;
    }
}
