package br.com.goias.chamados.dominio.cadastro;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente.Periodo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Calendário usado nos testes = padrão do clube (V4):
 * segunda a sexta, 08:00–12:00 e 13:00–17:00, com Finados (seg 02/11/2026) e
 * Consciência Negra (sex 20/11/2026) como feriados.
 */
public final class CalendariosDeTeste {

    public static final List<Periodo> PERIODOS = List.of(
            new Periodo(LocalTime.of(8, 0), LocalTime.of(12, 0)),
            new Periodo(LocalTime.of(13, 0), LocalTime.of(17, 0)));
    public static final EnumSet<DayOfWeek> SEG_A_SEX = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    public static final List<LocalDate> FERIADOS = List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 20));

    private CalendariosDeTeste() {
    }

    public static CalendarioExpediente padrao() {
        return CalendarioExpediente.padrao(SEG_A_SEX, PERIODOS, FERIADOS);
    }
}
