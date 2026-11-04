package br.com.goias.chamados.painel;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente.Periodo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Dados do gráfico "Abertos x resolvidos" (Chart.js), já agrupados e com ZERO nos intervalos sem chamados.
 *
 * Regras (planejamento, seção "Painel do gestor — gráfico"):
 * - por hora → barras, rótulos "8h", "9h"... (período de 2 dias: "Ter 8h");
 *   mostra as horas do expediente da área naquele dia (do 1º início ao último fim, incluindo o almoço),
 *   e acrescenta qualquer hora fora do expediente que tenha chamado (nada some do gráfico);
 * - por dia → até 14 dias: barras com "Qua 28"; mais que isso: linha com "05/10" e rótulo a cada 5 dias;
 * - por mês → barras com "Jun", "Jul"... (período que atravessa o ano: "Dez/26", "Jan/27").
 * Nunca usa "semana 40/S40".
 *
 * @param tipo           "bar" ou "line" (Chart.js)
 * @param rotulos        rótulo de cada intervalo
 * @param mostrarRotulo  se o rótulo aparece no eixo (na linha de 30 dias, um a cada 5)
 * @param dicas          texto completo do intervalo no tooltip (ex.: "Qua, 28/10")
 */
public record GraficoPainel(String tipo, List<String> rotulos, List<Boolean> mostrarRotulo, List<String> dicas,
                            List<Long> abertos, List<Long> resolvidos, String descricao) {

    static final int LIMITE_BARRAS_POR_DIA = 14;
    private static final String[] DIAS_SEMANA = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
    private static final String[] MESES = {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"};

    public long totalAbertos() {
        return abertos.stream().mapToLong(Long::longValue).sum();
    }

    public long totalResolvidos() {
        return resolvidos.stream().mapToLong(Long::longValue).sum();
    }

    public static GraficoPainel montar(PeriodoPainel periodo, Collection<LocalDateTime> aberturas,
                                       Collection<LocalDateTime> resolucoes, CalendarioExpediente calendario) {
        return switch (periodo.agrupamento()) {
            case HORA -> porHora(periodo, aberturas, resolucoes, calendario);
            case DIA -> porDia(periodo, aberturas, resolucoes);
            case MES -> porMes(periodo, aberturas, resolucoes);
        };
    }

    // ---------------------------------------------------------------- hora

    private static GraficoPainel porHora(PeriodoPainel periodo, Collection<LocalDateTime> aberturas,
                                         Collection<LocalDateTime> resolucoes, CalendarioExpediente calendario) {
        boolean variosDias = periodo.dias() > 1;
        Map<LocalDateTime, long[]> baldes = new LinkedHashMap<>();
        for (LocalDate dia = periodo.de(); !dia.isAfter(periodo.ate()); dia = dia.plusDays(1)) {
            TreeSet<Integer> horas = horasDoExpediente(dia, calendario);
            for (LocalDateTime t : aberturas) {
                if (t.toLocalDate().equals(dia)) {
                    horas.add(t.getHour());
                }
            }
            for (LocalDateTime t : resolucoes) {
                if (t.toLocalDate().equals(dia)) {
                    horas.add(t.getHour());
                }
            }
            if (!horas.isEmpty()) {
                for (int h = horas.first(); h <= horas.last(); h++) { // sem buracos (inclui o almoço)
                    baldes.put(dia.atTime(h, 0), new long[2]);
                }
            }
        }
        contar(baldes, aberturas, 0, t -> t.withMinute(0).withSecond(0).withNano(0));
        contar(baldes, resolucoes, 1, t -> t.withMinute(0).withSecond(0).withNano(0));

        List<String> rotulos = new ArrayList<>();
        List<String> dicas = new ArrayList<>();
        for (LocalDateTime t : baldes.keySet()) {
            String hora = t.getHour() + "h";
            rotulos.add(variosDias ? diaSemana(t.toLocalDate()) + " " + hora : hora);
            dicas.add(diaSemana(t.toLocalDate()) + ", " + ddMM(t.toLocalDate()) + " · "
                    + String.format("%02d:00–%02d:59", t.getHour(), t.getHour()));
        }
        return criar("bar", rotulos, todos(rotulos.size()), dicas, baldes,
                "Por hora · " + periodo.descricao());
    }

    /** Horas cheias do expediente daquele dia; em feriado/fim de semana usa a faixa geral da semana. */
    static TreeSet<Integer> horasDoExpediente(LocalDate dia, CalendarioExpediente calendario) {
        TreeSet<Integer> horas = new TreeSet<>();
        if (calendario == null || !calendario.temExpediente()) {
            return horas;
        }
        List<Periodo> periodos = calendario.periodosDoDia(dia);
        LocalTime primeira;
        LocalTime ultima;
        if (periodos.isEmpty()) {
            primeira = calendario.primeiraHoraDaSemana();
            ultima = calendario.ultimaHoraDaSemana();
        } else {
            primeira = periodos.get(0).inicio();
            ultima = periodos.get(periodos.size() - 1).fim();
        }
        int ultimaHora = ultima.getMinute() == 0 && ultima.getSecond() == 0 ? ultima.getHour() - 1 : ultima.getHour();
        for (int h = primeira.getHour(); h <= ultimaHora; h++) {
            horas.add(h);
        }
        return horas;
    }

    // ---------------------------------------------------------------- dia

    private static GraficoPainel porDia(PeriodoPainel periodo, Collection<LocalDateTime> aberturas,
                                        Collection<LocalDateTime> resolucoes) {
        Map<LocalDate, long[]> baldes = new LinkedHashMap<>();
        for (LocalDate dia = periodo.de(); !dia.isAfter(periodo.ate()); dia = dia.plusDays(1)) {
            baldes.put(dia, new long[2]);
        }
        contar(baldes, aberturas, 0, LocalDateTime::toLocalDate);
        contar(baldes, resolucoes, 1, LocalDateTime::toLocalDate);

        boolean barras = baldes.size() <= LIMITE_BARRAS_POR_DIA;
        List<String> rotulos = new ArrayList<>();
        List<Boolean> mostrar = new ArrayList<>();
        List<String> dicas = new ArrayList<>();
        int i = 0;
        int ultimo = baldes.size() - 1;
        for (LocalDate dia : baldes.keySet()) {
            rotulos.add(barras ? diaSemana(dia) + " " + String.format("%02d", dia.getDayOfMonth()) : ddMM(dia));
            mostrar.add(barras || i % 5 == 0 || i == ultimo);
            dicas.add(diaSemana(dia) + ", " + ddMM(dia));
            i++;
        }
        return criar(barras ? "bar" : "line", rotulos, mostrar, dicas, baldes, "Por dia · " + periodo.descricao());
    }

    // ---------------------------------------------------------------- mês

    private static GraficoPainel porMes(PeriodoPainel periodo, Collection<LocalDateTime> aberturas,
                                        Collection<LocalDateTime> resolucoes) {
        Map<YearMonth, long[]> baldes = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(periodo.de()); !m.isAfter(YearMonth.from(periodo.ate())); m = m.plusMonths(1)) {
            baldes.put(m, new long[2]);
        }
        contar(baldes, aberturas, 0, YearMonth::from);
        contar(baldes, resolucoes, 1, YearMonth::from);

        boolean variosAnos = periodo.de().getYear() != periodo.ate().getYear();
        List<String> rotulos = new ArrayList<>();
        List<String> dicas = new ArrayList<>();
        for (YearMonth m : baldes.keySet()) {
            String mes = MESES[m.getMonthValue() - 1];
            rotulos.add(variosAnos ? mes + "/" + String.format("%02d", m.getYear() % 100) : mes);
            dicas.add(mes + " de " + m.getYear());
        }
        return criar("bar", rotulos, todos(rotulos.size()), dicas, baldes, "Por mês · " + periodo.descricao());
    }

    // ---------------------------------------------------------------- apoio

    private static <K> void contar(Map<K, long[]> baldes, Collection<LocalDateTime> momentos, int serie,
                                   java.util.function.Function<LocalDateTime, K> chave) {
        for (LocalDateTime t : momentos) {
            long[] balde = baldes.get(chave.apply(t));
            if (balde != null) {
                balde[serie]++;
            }
        }
    }

    private static <K> GraficoPainel criar(String tipo, List<String> rotulos, List<Boolean> mostrar, List<String> dicas,
                                           Map<K, long[]> baldes, String descricao) {
        List<Long> a = new ArrayList<>();
        List<Long> r = new ArrayList<>();
        baldes.values().forEach(v -> {
            a.add(v[0]);
            r.add(v[1]);
        });
        return new GraficoPainel(tipo, List.copyOf(rotulos), List.copyOf(mostrar), List.copyOf(dicas),
                List.copyOf(a), List.copyOf(r), descricao);
    }

    private static List<Boolean> todos(int n) {
        List<Boolean> lista = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            lista.add(true);
        }
        return lista;
    }

    static String diaSemana(LocalDate dia) {
        return DIAS_SEMANA[dia.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue()];
    }

    private static String ddMM(LocalDate dia) {
        return String.format("%02d/%02d", dia.getDayOfMonth(), dia.getMonthValue());
    }
}
