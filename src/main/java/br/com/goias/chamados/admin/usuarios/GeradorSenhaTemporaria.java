package br.com.goias.chamados.admin.usuarios;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Gera a senha temporária de um usuário novo ou de "Redefinir senha" (Etapa 5).
 *
 * - 12 caracteres, sorteados com SecureRandom;
 * - sempre com pelo menos 1 maiúscula, 1 minúscula, 1 número e exatamente 1 símbolo
 *   (um símbolo só, para ficar fácil de ditar por telefone);
 * - sem caracteres que se confundem ao ditar ou digitar (0/O/o, 1/l/I, 5/S...);
 * - passa nas regras da senha nova (ValidadorNovaSenha: 8+ caracteres, letra e número).
 *
 * A senha é mostrada UMA vez ao admin e a pessoa é obrigada a trocá-la no primeiro acesso.
 */
public final class GeradorSenhaTemporaria {

    public static final int TAMANHO = 12;

    static final String MAIUSCULAS = "ABCDEFGHJKMNPQRTUVWXYZ";
    static final String MINUSCULAS = "abcdefghjkmnpqrtuvwxyz";
    static final String NUMEROS = "2346789";
    static final String SIMBOLOS = "@#$%&*+=?";
    /** Completa a senha (sem símbolos: o único símbolo já foi sorteado). */
    static final String LETRAS_E_NUMEROS = MAIUSCULAS + MINUSCULAS + NUMEROS;

    private static final SecureRandom SORTEIO = new SecureRandom();

    private GeradorSenhaTemporaria() {
    }

    public static String gerar() {
        return gerar(SORTEIO);
    }

    /** Versão com o sorteio informado (usada nos testes). */
    static String gerar(Random sorteio) {
        List<Character> letras = new ArrayList<>(TAMANHO);
        letras.add(sortear(MAIUSCULAS, sorteio));
        letras.add(sortear(MINUSCULAS, sorteio));
        letras.add(sortear(NUMEROS, sorteio));
        letras.add(sortear(SIMBOLOS, sorteio));
        while (letras.size() < TAMANHO) {
            letras.add(sortear(LETRAS_E_NUMEROS, sorteio));
        }
        Collections.shuffle(letras, sorteio);
        StringBuilder senha = new StringBuilder(TAMANHO);
        letras.forEach(senha::append);
        return senha.toString();
    }

    private static char sortear(String alfabeto, Random sorteio) {
        return alfabeto.charAt(sorteio.nextInt(alfabeto.length()));
    }
}
