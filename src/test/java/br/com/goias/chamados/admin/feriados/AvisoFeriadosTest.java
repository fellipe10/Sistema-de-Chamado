package br.com.goias.chamados.admin.feriados;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvisoFeriadosTest {

    @Test
    @DisplayName("Próximo ano sem feriados: avisa com o ano")
    void avisa() {
        assertThat(AvisoFeriados.anoSemFeriado(2027, List.of(2026, 2027))).isEqualTo(2028);
        assertThat(AvisoFeriados.mensagem(2028)).contains("2028").contains("feriados");
    }

    @Test
    @DisplayName("Próximo ano com pelo menos um feriado: sem aviso")
    void naoAvisa() {
        assertThat(AvisoFeriados.anoSemFeriado(2026, List.of(2026, 2027))).isNull();
        assertThat(AvisoFeriados.anoSemFeriado(2027, List.of(2026, 2027, 2028))).isNull();
    }

    @Test
    @DisplayName("Só o próximo ano importa (o ano atual vazio ou anos distantes não mudam o aviso)")
    void soProximoAno() {
        assertThat(AvisoFeriados.anoSemFeriado(2026, List.of(2027))).isNull();
        assertThat(AvisoFeriados.anoSemFeriado(2026, List.of(2026, 2030))).isEqualTo(2027);
        assertThat(AvisoFeriados.anoSemFeriado(2026, List.of())).isEqualTo(2027);
    }

    @Test
    @DisplayName("Ano de referência para teste: vazio usa o ano de hoje; número fixo é respeitado; lixo é ignorado")
    void anoReferencia() {
        assertThat(new AnoReferencia("").anoAtual()).isEqualTo(java.time.LocalDate.now().getYear());
        assertThat(new AnoReferencia(" 2027 ").anoAtual()).isEqualTo(2027);
        assertThat(new AnoReferencia("abc").anoAtual()).isEqualTo(java.time.LocalDate.now().getYear());
    }
}
