package br.com.goias.chamados.dominio.cadastro;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Calendário de trabalho de UMA área: períodos de expediente por dia da semana + feriados.
 * Classe pura (sem banco), usada pelo cálculo de SLA, pelo fechamento automático, pelo painel
 * e pelo recálculo único dos prazos antigos. Quem monta a partir do banco é {@link CalendariosExpediente}.
 *
 * Responde duas perguntas:
 * - {@link #somarMinutosUteis}: "somando N minutos úteis a partir daqui, onde eu paro?"
 * - {@link #minutosUteisEntre}: "quantos minutos úteis existem entre estes dois momentos?"
 *
 * Regras:
 * - Só conta dentro dos períodos do dia (pula almoço, noite, dia sem expediente e feriado).
 * - Começando fora do expediente, a contagem começa no início do próximo período útil.
 * - Se a soma termina exatamente no fim de um período, o resultado é esse fim (ex.: 17:00),
 *   e não o começo do próximo dia.
 * - Área SEM nenhum período cadastrado: conta em tempo corrido (24 h por dia), para nunca
 *   travar o sistema; quem monta o calendário avisa no log.
 */
public final class CalendarioExpediente {

    /** Um período de trabalho dentro do dia (ex.: 08:00–12:00). */
    public record Periodo(LocalTime inicio, LocalTime fim) {
        public Periodo {
            Objects.requireNonNull(inicio, "inicio");
            Objects.requireNonNull(fim, "fim");
            if (!fim.isAfter(inicio)) {
                throw new IllegalArgumentException("Período inválido: " + inicio + "–" + fim);
            }
        }

        Duration duracao() {
            return Duration.between(inicio, fim);
        }
    }

    /** Proteção contra configuração impossível (ex.: todos os dias do ano são feriado). */
    private static final int LIMITE_DIAS = 3660;

    private final Map<DayOfWeek, List<Periodo>> periodosPorDia;
    private final Set<LocalDate> feriados;
    private final boolean temExpediente;

    public CalendarioExpediente(Map<DayOfWeek, List<Periodo>> periodosPorDia, Collection<LocalDate> feriados) {
        Map<DayOfWeek, List<Periodo>> copia = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dia : DayOfWeek.values()) {
            List<Periodo> lista = new ArrayList<>(periodosPorDia == null
                    ? List.of() : periodosPorDia.getOrDefault(dia, List.of()));
            lista.sort(Comparator.comparing(Periodo::inicio));
            for (int i = 1; i < lista.size(); i++) {
                if (lista.get(i).inicio().isBefore(lista.get(i - 1).fim())) {
                    throw new IllegalArgumentException("Períodos sobrepostos em " + dia + ": "
                            + lista.get(i - 1) + " e " + lista.get(i));
                }
            }
            copia.put(dia, List.copyOf(lista));
        }
        this.periodosPorDia = copia;
        this.feriados = feriados == null ? Set.of() : Set.copyOf(feriados);
        this.temExpediente = copia.values().stream().anyMatch(l -> !l.isEmpty());
    }

    /**
     * Atalho para os testes e para o padrão do clube: os mesmos períodos em vários dias da semana.
     * Ex.: {@code padrao(List.of(MONDAY..FRIDAY), List.of(08–12, 13–17), feriados)}.
     */
    public static CalendarioExpediente padrao(Collection<DayOfWeek> dias, List<Periodo> periodos,
                                              Collection<LocalDate> feriados) {
        Map<DayOfWeek, List<Periodo>> mapa = new EnumMap<>(DayOfWeek.class);
        dias.forEach(d -> mapa.put(d, periodos));
        return new CalendarioExpediente(mapa, feriados);
    }

    /** false = nenhum período cadastrado para a área (cálculo cai em tempo corrido). */
    public boolean temExpediente() {
        return temExpediente;
    }

    public boolean ehFeriado(LocalDate data) {
        return feriados.contains(data);
    }

    /** Períodos de trabalho daquela data (vazio em feriado ou dia sem expediente). */
    public List<Periodo> periodosDoDia(LocalDate data) {
        if (feriados.contains(data)) {
            return List.of();
        }
        return periodosPorDia.get(data.getDayOfWeek());
    }

    /** Períodos cadastrados para o dia da semana (ignora feriados). */
    public List<Periodo> periodosDaSemana(DayOfWeek dia) {
        return periodosPorDia.get(dia);
    }

    /** Minutos úteis num dia comum da semana (ex.: 480 de segunda a sexta). */
    public long minutosDoDia(DayOfWeek dia) {
        return periodosPorDia.get(dia).stream().mapToLong(p -> p.duracao().toMinutes()).sum();
    }

    /** Hora do primeiro início e do último fim entre todos os dias (ex.: 08:00 e 17:00). Nulo sem expediente. */
    public LocalTime primeiraHoraDaSemana() {
        return periodosPorDia.values().stream().flatMap(List::stream).map(Periodo::inicio)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    public LocalTime ultimaHoraDaSemana() {
        return periodosPorDia.values().stream().flatMap(List::stream).map(Periodo::fim)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    // =================================================================
    // Somar tempo útil
    // =================================================================

    /** Soma minutos úteis a partir de "inicio". Com 0 minutos, devolve o próximo momento útil. */
    public LocalDateTime somarMinutosUteis(LocalDateTime inicio, long minutos) {
        if (minutos < 0) {
            throw new IllegalArgumentException("Minutos negativos: " + minutos);
        }
        return somar(inicio, Duration.ofMinutes(minutos));
    }

    /** Primeiro momento útil a partir de "momento" (ele mesmo, se já estiver dentro do expediente). */
    public LocalDateTime proximoMomentoUtil(LocalDateTime momento) {
        return somar(momento, Duration.ZERO);
    }

    private LocalDateTime somar(LocalDateTime inicio, Duration quanto) {
        Objects.requireNonNull(inicio, "inicio");
        if (!temExpediente) {
            return inicio.plus(quanto);
        }
        Duration resto = quanto;
        LocalDate dia = inicio.toLocalDate();
        for (int i = 0; i < LIMITE_DIAS; i++, dia = dia.plusDays(1)) {
            for (Periodo p : periodosDoDia(dia)) {
                LocalDateTime abre = dia.atTime(p.inicio());
                LocalDateTime fecha = dia.atTime(p.fim());
                if (!fecha.isAfter(inicio)) {
                    continue; // período já passou
                }
                LocalDateTime de = abre.isAfter(inicio) ? abre : inicio;
                if (resto.isZero()) {
                    return de;
                }
                Duration disponivel = Duration.between(de, fecha);
                if (disponivel.compareTo(resto) >= 0) {
                    return de.plus(resto);
                }
                resto = resto.minus(disponivel);
            }
        }
        throw new IllegalStateException("Sem expediente nos próximos " + LIMITE_DIAS
                + " dias a partir de " + inicio + ". Confira o cadastro de expediente e feriados.");
    }

    // =================================================================
    // Medir tempo útil
    // =================================================================

    /** Minutos úteis entre dois momentos (0 se "ate" não for depois de "de"). Arredonda para baixo. */
    public long minutosUteisEntre(LocalDateTime de, LocalDateTime ate) {
        return duracaoUtilEntre(de, ate).toMinutes();
    }

    /** Tempo útil entre dois momentos (com segundos). */
    public Duration duracaoUtilEntre(LocalDateTime de, LocalDateTime ate) {
        Objects.requireNonNull(de, "de");
        Objects.requireNonNull(ate, "ate");
        if (!ate.isAfter(de)) {
            return Duration.ZERO;
        }
        if (!temExpediente) {
            return Duration.between(de, ate);
        }
        Duration total = Duration.ZERO;
        for (LocalDate dia = de.toLocalDate(); !dia.isAfter(ate.toLocalDate()); dia = dia.plusDays(1)) {
            for (Periodo p : periodosDoDia(dia)) {
                LocalDateTime abre = dia.atTime(p.inicio());
                LocalDateTime fecha = dia.atTime(p.fim());
                LocalDateTime inicioTrecho = abre.isAfter(de) ? abre : de;
                LocalDateTime fimTrecho = fecha.isBefore(ate) ? fecha : ate;
                if (fimTrecho.isAfter(inicioTrecho)) {
                    total = total.plus(Duration.between(inicioTrecho, fimTrecho));
                }
            }
        }
        return total;
    }
}
