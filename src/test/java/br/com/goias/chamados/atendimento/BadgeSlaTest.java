package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.StatusChamado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeSlaTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 3, 10, 0);

    private static BadgeSla emAndamento(LocalDateTime abertura, LocalDateTime prazo) {
        return BadgeSla.calcular(StatusChamado.EM_ATENDIMENTO, abertura, prazo, null, null, AGORA);
    }

    @Test
    @DisplayName("No prazo: falta mais de 1 h e mais de 20% do prazo total")
    void noPrazo() {
        // prazo total 24 h, faltam 10 h (41%)
        BadgeSla b = emAndamento(AGORA.minusHours(14), AGORA.plusHours(10));
        assertThat(b.texto()).isEqualTo("No prazo");
        assertThat(b.classe()).isEqualTo(BadgeSla.OK);
    }

    @Test
    @DisplayName("Vence logo pelo critério de 1 hora (mesmo com mais de 20% restante)")
    void venceLogoPorUmaHora() {
        // prazo total 2 h, faltam 50 min (41%) → âmbar por faltar ≤ 1 h
        BadgeSla b = emAndamento(AGORA.minusMinutes(70), AGORA.plusMinutes(50));
        assertThat(b.texto()).isEqualTo("Vence em 50 min");
        assertThat(b.classe()).isEqualTo(BadgeSla.VENCE);
    }

    @Test
    @DisplayName("Vence logo pelo critério de 20% (mesmo faltando mais de 1 hora)")
    void venceLogoPorVintePorCento() {
        // prazo total 40 h, faltam 7 h 30 (18,75%) → âmbar
        BadgeSla b = emAndamento(AGORA.minusMinutes(32 * 60 + 30), AGORA.plusMinutes(7 * 60 + 30));
        assertThat(b.texto()).isEqualTo("Vence em 7h 30min");
        assertThat(b.classe()).isEqualTo(BadgeSla.VENCE);
    }

    @Test
    @DisplayName("Exatamente 20% restante conta como vence logo; 21% não")
    void limiteDosVintePorCento() {
        assertThat(emAndamento(AGORA.minusHours(32), AGORA.plusHours(8)).classe()).isEqualTo(BadgeSla.VENCE);
        assertThat(emAndamento(AGORA.minusMinutes(1896), AGORA.plusMinutes(504)).classe()).isEqualTo(BadgeSla.OK);
    }

    @Test
    @DisplayName("Atrasado: mostra quanto passou (minutos, horas ou dias)")
    void atrasado() {
        assertThat(emAndamento(AGORA.minusHours(5), AGORA.minusMinutes(25)).texto()).isEqualTo("Atrasado 25 min");
        BadgeSla b = emAndamento(AGORA.minusHours(10), AGORA.minusHours(2).minusMinutes(10));
        assertThat(b.texto()).isEqualTo("Atrasado 2h");
        assertThat(b.classe()).isEqualTo(BadgeSla.ATRASADO);
        assertThat(emAndamento(AGORA.minusDays(5), AGORA.minusDays(3)).texto()).isEqualTo("Atrasado 3d");
        assertThat(emAndamento(AGORA.minusHours(2), AGORA).texto()).isEqualTo("Atrasado 1 min");
    }

    @Test
    @DisplayName("Resolvido no prazo e com atraso (vale também para fechado)")
    void resolvido() {
        LocalDateTime abertura = AGORA.minusDays(2);
        LocalDateTime prazo = abertura.plusHours(8);
        BadgeSla noPrazo = BadgeSla.calcular(StatusChamado.RESOLVIDO, abertura, prazo, prazo.minusMinutes(1), null, AGORA);
        assertThat(noPrazo.texto()).isEqualTo("Resolvido no prazo");
        assertThat(noPrazo.classe()).isEqualTo(BadgeSla.OK);

        BadgeSla comAtraso = BadgeSla.calcular(StatusChamado.FECHADO, abertura, prazo, prazo.plusMinutes(1), prazo.plusHours(1), AGORA);
        assertThat(comAtraso.texto()).isEqualTo("Resolvido com atraso");
        assertThat(comAtraso.classe()).isEqualTo(BadgeSla.ATRASADO);

        BadgeSla noLimite = BadgeSla.calcular(StatusChamado.RESOLVIDO, abertura, prazo, prazo, null, AGORA);
        assertThat(noLimite.texto()).isEqualTo("Resolvido no prazo");
    }

    @Test
    @DisplayName("Sem prazo configurado")
    void semPrazo() {
        BadgeSla b = BadgeSla.calcular(StatusChamado.ABERTO, AGORA, null, null, null, AGORA);
        assertThat(b.texto()).isEqualTo("Sem prazo");
        assertThat(b.classe()).isEqualTo(BadgeSla.SEM);
    }

    @Test
    @DisplayName("Formatação do tempo restante")
    void formatacao() {
        assertThat(BadgeSla.formatarRestante(Duration.ofSeconds(30))).isEqualTo("menos de 1 min");
        assertThat(BadgeSla.formatarRestante(Duration.ofMinutes(60))).isEqualTo("1h");
        assertThat(BadgeSla.formatarRestante(Duration.ofMinutes(125))).isEqualTo("2h 05min");
        assertThat(BadgeSla.formatarRestante(Duration.ofHours(28))).isEqualTo("1d 4h");
        assertThat(BadgeSla.formatarRestante(Duration.ofHours(48))).isEqualTo("2d");
    }
}
