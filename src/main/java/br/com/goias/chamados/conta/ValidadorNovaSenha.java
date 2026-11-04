package br.com.goias.chamados.conta;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Regras da senha nova (sem banco, fácil de testar):
 * - mínimo de 8 caracteres (máximo 64, por causa do limite do BCrypt);
 * - pelo menos uma letra e pelo menos um número;
 * - diferente da senha atual;
 * - confirmação igual à senha nova.
 *
 * Devolve os erros por campo ("novaSenha", "confirmacao"); vazio = senha aceita.
 */
public final class ValidadorNovaSenha {

    public static final int TAMANHO_MINIMO = 8;
    public static final int TAMANHO_MAXIMO = 64;

    static final String OBRIGATORIA = "Informe a senha nova.";
    static final String CURTA = "A senha precisa ter pelo menos " + TAMANHO_MINIMO + " caracteres.";
    static final String LONGA = "A senha pode ter no máximo " + TAMANHO_MAXIMO + " caracteres.";
    static final String SEM_LETRA_OU_NUMERO = "Use pelo menos uma letra e um número.";
    static final String IGUAL_ATUAL = "A senha nova precisa ser diferente da atual.";
    static final String CONFIRMACAO_OBRIGATORIA = "Repita a senha nova.";
    static final String CONFIRMACAO_DIFERENTE = "As duas senhas não são iguais.";

    private ValidadorNovaSenha() {
    }

    /**
     * @param novaSenha         senha nova digitada
     * @param confirmacao       repetição da senha nova
     * @param igualASenhaAtual  diz se um texto é a senha atual do usuário (confere o hash)
     */
    public static Map<String, String> validar(String novaSenha, String confirmacao,
                                              Predicate<String> igualASenhaAtual) {
        Map<String, String> erros = new LinkedHashMap<>();

        if (novaSenha == null || novaSenha.isEmpty()) {
            erros.put("novaSenha", OBRIGATORIA);
        } else if (novaSenha.length() < TAMANHO_MINIMO) {
            erros.put("novaSenha", CURTA);
        } else if (novaSenha.length() > TAMANHO_MAXIMO) {
            erros.put("novaSenha", LONGA);
        } else if (!temLetra(novaSenha) || !temNumero(novaSenha)) {
            erros.put("novaSenha", SEM_LETRA_OU_NUMERO);
        } else if (igualASenhaAtual.test(novaSenha)) {
            erros.put("novaSenha", IGUAL_ATUAL);
        }

        if (confirmacao == null || confirmacao.isEmpty()) {
            erros.put("confirmacao", CONFIRMACAO_OBRIGATORIA);
        } else if (!confirmacao.equals(novaSenha)) {
            erros.put("confirmacao", CONFIRMACAO_DIFERENTE);
        }
        return erros;
    }

    static boolean temLetra(String texto) {
        return texto.codePoints().anyMatch(Character::isLetter);
    }

    static boolean temNumero(String texto) {
        return texto.codePoints().anyMatch(Character::isDigit);
    }
}
