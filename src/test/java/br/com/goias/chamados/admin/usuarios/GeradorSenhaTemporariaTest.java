package br.com.goias.chamados.admin.usuarios;

import br.com.goias.chamados.conta.ValidadorNovaSenha;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeradorSenhaTemporariaTest {

    @RepeatedTest(200)
    @DisplayName("Senha forte: 12 caracteres com maiúscula, minúscula, número e exatamente 1 símbolo")
    void forte() {
        String s = GeradorSenhaTemporaria.gerar();
        assertThat(s).hasSize(12);
        assertThat(s.chars().anyMatch(Character::isUpperCase)).isTrue();
        assertThat(s.chars().anyMatch(Character::isLowerCase)).isTrue();
        assertThat(s.chars().anyMatch(Character::isDigit)).isTrue();
        assertThat(s.chars().filter(c -> GeradorSenhaTemporaria.SIMBOLOS.indexOf(c) >= 0).count()).isEqualTo(1);
    }

    @RepeatedTest(200)
    @DisplayName("Sem caracteres que se confundem (0 O o 1 l I 5 S)")
    void semAmbiguos() {
        assertThat(GeradorSenhaTemporaria.gerar()).doesNotContainPattern("[0Oo1lI5S]");
    }

    @Test
    @DisplayName("Passa nas regras da senha nova (a pessoa poderia até mantê-la)")
    void passaNoValidador() {
        for (int i = 0; i < 200; i++) {
            String s = GeradorSenhaTemporaria.gerar();
            assertThat(ValidadorNovaSenha.validar(s, s, x -> false)).isEmpty();
        }
    }

    @Test
    @DisplayName("Não repete: 5.000 senhas diferentes")
    void naoRepete() {
        Set<String> senhas = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            senhas.add(GeradorSenhaTemporaria.gerar());
        }
        assertThat(senhas).hasSize(5000);
    }

    @Test
    @DisplayName("Mesmo sorteio = mesma senha (o embaralhamento usa o sorteio informado)")
    void deterministicaComSemente() {
        assertThat(GeradorSenhaTemporaria.gerar(new Random(42))).isEqualTo(GeradorSenhaTemporaria.gerar(new Random(42)));
    }
}
