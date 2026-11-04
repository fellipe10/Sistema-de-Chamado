package br.com.goias.chamados.abertura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeradorProtocoloTest {

    /** Cópia literal do CHECK ck_chamado_protocolo da V1 (não reaproveita a constante de propósito). */
    private static final Pattern REGEX_DO_BANCO = Pattern.compile("^[A-Z]{2,10}-[0-9]{4}-[2-9A-HJKMNP-Z]{4}$");

    private final GeradorProtocolo gerador = new GeradorProtocolo();

    @Test
    @DisplayName("Formato ÁREA-ANO-XXXX com a sigla e o ano informados")
    void formato() {
        String codigo = gerador.gerar("TI", 2026);

        assertThat(codigo).startsWith("TI-2026-").hasSize("TI-2026-".length() + 4);
        assertThat(gerador.gerar("MAN", 2027)).startsWith("MAN-2027-").hasSize(13);
    }

    @RepeatedTest(20)
    @DisplayName("Sempre bate com a regex do banco")
    void bateComRegexDoBanco() {
        for (int i = 0; i < 500; i++) {
            assertThat(gerador.gerar("TI", 2026)).matches(REGEX_DO_BANCO);
            assertThat(gerador.gerar("MAN", 2026)).matches(REGEX_DO_BANCO);
        }
    }

    @Test
    @DisplayName("Nunca usa caracteres ambíguos (0, O, 1, I, L)")
    void semCaracteresAmbiguos() {
        for (int i = 0; i < 20_000; i++) {
            String parteAleatoria = gerador.gerar("TI", 2026).substring("TI-2026-".length());
            assertThat(parteAleatoria).doesNotContain("0", "O", "1", "I", "L");
        }
        assertThat(GeradorProtocolo.ALFABETO).doesNotContain("0", "O", "1", "I", "L").hasSize(31);
    }

    @Test
    @DisplayName("Todos os caracteres do alfabeto são aceitos pelo banco e chegam a ser sorteados")
    void alfabetoCompletoEAceitoPeloBanco() {
        Set<Character> sorteados = new HashSet<>();
        GeradorProtocolo comSemente = new GeradorProtocolo(new Random(42));
        for (int i = 0; i < 5_000; i++) {
            for (char c : comSemente.gerar("TI", 2026).substring(8).toCharArray()) {
                sorteados.add(c);
            }
        }
        for (char c : GeradorProtocolo.ALFABETO.toCharArray()) {
            assertThat(sorteados).contains(c);
            assertThat("TI-2026-" + c + c + c + c).matches(REGEX_DO_BANCO);
        }
    }

    @Test
    @DisplayName("Constante FORMATO do código é igual à regex do banco")
    void constanteIgualAoBanco() {
        assertThat(GeradorProtocolo.FORMATO.pattern()).isEqualTo(REGEX_DO_BANCO.pattern());
    }

    @Test
    @DisplayName("Recusa sigla ou ano inválidos")
    void recusaEntradaInvalida() {
        assertThatThrownBy(() -> gerador.gerar("ti", 2026)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gerador.gerar("T", 2026)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gerador.gerar(null, 2026)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gerador.gerar("TI", 26)).isInstanceOf(IllegalArgumentException.class);
    }
}
