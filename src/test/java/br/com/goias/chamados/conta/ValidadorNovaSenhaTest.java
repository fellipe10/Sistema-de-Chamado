package br.com.goias.chamados.conta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorNovaSenhaTest {

    private static final String ATUAL = "Goias@Chamados2026";
    private static final Predicate<String> IGUAL_ATUAL = ATUAL::equals;

    private static Map<String, String> validar(String nova, String confirmacao) {
        return ValidadorNovaSenha.validar(nova, confirmacao, IGUAL_ATUAL);
    }

    @Test
    @DisplayName("Senha válida: 8+ caracteres, letra, número, diferente da atual, confirmação igual")
    void valida() {
        assertThat(validar("Verdao2026", "Verdao2026")).isEmpty();
        assertThat(validar("abcdefg1", "abcdefg1")).isEmpty();
        assertThat(validar("çãoõéàü9", "çãoõéàü9")).isEmpty(); // acentos contam como letra
    }

    @Test
    @DisplayName("Menos de 8 caracteres")
    void curta() {
        assertThat(validar("abc1234", "abc1234")).containsEntry("novaSenha", ValidadorNovaSenha.CURTA);
    }

    @Test
    @DisplayName("Mais de 64 caracteres")
    void longa() {
        String s = "a1".repeat(33);
        assertThat(validar(s, s)).containsEntry("novaSenha", ValidadorNovaSenha.LONGA);
    }

    @Test
    @DisplayName("Sem número ou sem letra")
    void semLetraOuNumero() {
        assertThat(validar("somenteletras", "somenteletras")).containsEntry("novaSenha", ValidadorNovaSenha.SEM_LETRA_OU_NUMERO);
        assertThat(validar("12345678", "12345678")).containsEntry("novaSenha", ValidadorNovaSenha.SEM_LETRA_OU_NUMERO);
        assertThat(validar("@#$%&*!?", "@#$%&*!?")).containsEntry("novaSenha", ValidadorNovaSenha.SEM_LETRA_OU_NUMERO);
    }

    @Test
    @DisplayName("Igual à senha atual")
    void igualAtual() {
        assertThat(validar(ATUAL, ATUAL)).containsEntry("novaSenha", ValidadorNovaSenha.IGUAL_ATUAL);
    }

    @Test
    @DisplayName("Confirmação vazia ou diferente")
    void confirmacao() {
        assertThat(validar("Verdao2026", "")).containsEntry("confirmacao", ValidadorNovaSenha.CONFIRMACAO_OBRIGATORIA);
        assertThat(validar("Verdao2026", "verdao2026")).containsEntry("confirmacao", ValidadorNovaSenha.CONFIRMACAO_DIFERENTE);
    }

    @Test
    @DisplayName("Senha vazia: erro nos dois campos")
    void vazia() {
        Map<String, String> erros = validar(null, null);
        assertThat(erros).containsEntry("novaSenha", ValidadorNovaSenha.OBRIGATORIA)
                .containsEntry("confirmacao", ValidadorNovaSenha.CONFIRMACAO_OBRIGATORIA);
    }
}
