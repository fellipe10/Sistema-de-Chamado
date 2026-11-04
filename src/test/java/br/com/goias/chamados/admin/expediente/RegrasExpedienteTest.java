package br.com.goias.chamados.admin.expediente;

import br.com.goias.chamados.admin.expediente.RegrasExpediente.Digitado;
import br.com.goias.chamados.admin.expediente.RegrasExpediente.Periodo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasExpedienteTest {

    private static Map<DayOfWeek, List<Digitado>> semana(DayOfWeek dia, Digitado... periodos) {
        Map<DayOfWeek, List<Digitado>> m = new EnumMap<>(DayOfWeek.class);
        m.put(dia, List.of(periodos));
        return m;
    }

    private static Periodo p(String i, String f) {
        return new Periodo(LocalTime.parse(i), LocalTime.parse(f));
    }

    @Test
    @DisplayName("Padrão 08–12 e 13–17: aceito")
    void padrao() {
        var r = RegrasExpediente.validar(semana(DayOfWeek.MONDAY, new Digitado("08:00", "12:00"), new Digitado("13:00", "17:00")));
        assertThat(r.sucesso()).isTrue();
        assertThat(r.periodos().get(DayOfWeek.MONDAY)).containsExactly(p("08:00", "12:00"), p("13:00", "17:00"));
    }

    @Test
    @DisplayName("Períodos sobrepostos no mesmo dia: recusado")
    void sobrepostos() {
        var r = RegrasExpediente.validar(semana(DayOfWeek.MONDAY,
                new Digitado("08:00", "12:00"), new Digitado("13:00", "17:00"), new Digitado("11:00", "14:00")));
        assertThat(r.erros()).containsEntry("MONDAY", RegrasExpediente.SOBREPOSTOS);
        assertThat(RegrasExpediente.sobrepostos(List.of(p("08:00", "12:00"), p("11:59", "13:00")))).isTrue();
        assertThat(RegrasExpediente.sobrepostos(List.of(p("13:00", "17:00"), p("08:00", "18:00")))).isTrue(); // um dentro do outro
    }

    @Test
    @DisplayName("Períodos encostados (12:00 e 12:00) não são sobreposição")
    void encostados() {
        assertThat(RegrasExpediente.sobrepostos(List.of(p("08:00", "12:00"), p("12:00", "13:00")))).isFalse();
        assertThat(RegrasExpediente.validar(semana(DayOfWeek.FRIDAY,
                new Digitado("08:00", "12:00"), new Digitado("12:00", "13:00"))).sucesso()).isTrue();
    }

    @Test
    @DisplayName("Fim igual ou antes do início: recusado")
    void fimAntes() {
        assertThat(RegrasExpediente.validar(semana(DayOfWeek.TUESDAY, new Digitado("18:00", "17:00"))).erros())
                .containsEntry("TUESDAY", RegrasExpediente.FIM_ANTES);
        assertThat(RegrasExpediente.validar(semana(DayOfWeek.TUESDAY, new Digitado("08:00", "08:00"))).erros())
                .containsEntry("TUESDAY", RegrasExpediente.FIM_ANTES);
    }

    @Test
    @DisplayName("Período pela metade e horário inválido: recusados")
    void incompletoEInvalido() {
        assertThat(RegrasExpediente.validar(semana(DayOfWeek.WEDNESDAY, new Digitado("18:00", ""))).erros())
                .containsEntry("WEDNESDAY", RegrasExpediente.INCOMPLETO);
        assertThat(RegrasExpediente.validar(semana(DayOfWeek.WEDNESDAY, new Digitado("25:00", "26:00"))).erros())
                .containsEntry("WEDNESDAY", RegrasExpediente.HORA_INVALIDA);
    }

    @Test
    @DisplayName("Semana inteira vazia: recusado; vazios são ignorados")
    void semanaVazia() {
        var r = RegrasExpediente.validar(semana(DayOfWeek.MONDAY, new Digitado("", ""), new Digitado(null, null)));
        assertThat(r.erros()).containsEntry("semana", RegrasExpediente.SEMANA_VAZIA);
        assertThat(RegrasExpediente.validar(new EnumMap<>(DayOfWeek.class)).erros()).containsKey("semana");
    }

    @Test
    @DisplayName("Fora de ordem é aceito e gravado em ordem; '8:00' vira 08:00")
    void ordena() {
        var r = RegrasExpediente.validar(semana(DayOfWeek.SATURDAY, new Digitado("13:00", "15:00"), new Digitado("8:00", "12:00")));
        assertThat(r.periodos().get(DayOfWeek.SATURDAY)).containsExactly(p("08:00", "12:00"), p("13:00", "15:00"));
    }

    @Test
    @DisplayName("Erro num dia não esconde os outros dias")
    void variosDias() {
        Map<DayOfWeek, List<Digitado>> m = new EnumMap<>(DayOfWeek.class);
        m.put(DayOfWeek.MONDAY, List.of(new Digitado("08:00", "12:00"), new Digitado("11:00", "13:00")));
        m.put(DayOfWeek.TUESDAY, List.of(new Digitado("10:00", "09:00")));
        m.put(DayOfWeek.WEDNESDAY, List.of(new Digitado("08:00", "17:00")));
        var r = RegrasExpediente.validar(m);
        assertThat(r.erros()).containsOnlyKeys("MONDAY", "TUESDAY");
        assertThat(r.periodos()).isEmpty();
    }
}
