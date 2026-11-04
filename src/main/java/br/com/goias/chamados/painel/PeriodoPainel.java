package br.com.goias.chamados.painel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Período escolhido no topo do painel e dos relatórios.
 *
 * | Filtro      | Intervalo                                 | Agrupamento do gráfico                    |
 * |-------------|-------------------------------------------|-------------------------------------------|
 * | Hoje        | hoje                                      | por hora (horas do expediente da área)    |
 * | 7 dias      | hoje e os 6 dias anteriores               | por dia                                   |
 * | 30 dias     | hoje e os 29 dias anteriores (PADRÃO)     | por dia                                   |
 * | 6 meses     | do dia 1º de 5 meses atrás até hoje       | por mês                                   |
 * | Período     | de/até (datas inclusivas)                 | até 2 dias → hora; até 45 → dia; acima → mês |
 *
 * URL: ?periodo=hoje|7d|30d|6m ou ?de=AAAA-MM-DD&ate=AAAA-MM-DD (planejamento).
 * Período personalizado inválido (datas vazias, de > até, até no futuro, mais de 3 anos)
 * volta para 30 dias com uma mensagem.
 *
 * @param inicio primeiro instante (inclusive)
 * @param fim    instante final (exclusive) = meia-noite do dia seguinte a "ate"
 */
public record PeriodoPainel(Tipo tipo, LocalDate de, LocalDate ate, LocalDateTime inicio, LocalDateTime fim,
                            Agrupamento agrupamento, String descricao, String erro) {

    public enum Tipo {
        HOJE("hoje", "Hoje"),
        SETE_DIAS("7d", "7 dias"),
        TRINTA_DIAS("30d", "30 dias"),
        SEIS_MESES("6m", "6 meses"),
        PERSONALIZADO("personalizado", "Período");

        private final String parametro;
        private final String rotulo;

        Tipo(String parametro, String rotulo) {
            this.parametro = parametro;
            this.rotulo = rotulo;
        }

        public String getParametro() {
            return parametro;
        }

        public String getRotulo() {
            return rotulo;
        }
    }

    public enum Agrupamento { HORA, DIA, MES }

    static final int MAXIMO_DIAS_PERSONALIZADO = 1096; // ~3 anos
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * @param periodo parâmetro "periodo" da URL (pode ser nulo)
     * @param de      parâmetro "de" (texto AAAA-MM-DD, pode ser nulo)
     * @param ate     parâmetro "ate"
     * @param hoje    data de hoje
     */
    public static PeriodoPainel interpretar(String periodo, String de, String ate, LocalDate hoje) {
        boolean pediuPersonalizado = (de != null && !de.isBlank()) || (ate != null && !ate.isBlank())
                || "personalizado".equalsIgnoreCase(periodo == null ? "" : periodo.strip());
        if (pediuPersonalizado) {
            return personalizado(lerData(de), lerData(ate), hoje);
        }
        Tipo tipo = Arrays.stream(Tipo.values())
                .filter(t -> t != Tipo.PERSONALIZADO && t.parametro.equalsIgnoreCase(periodo == null ? "" : periodo.strip()))
                .findFirst().orElse(Tipo.TRINTA_DIAS);
        return fixo(tipo, hoje, null);
    }

    static PeriodoPainel fixo(Tipo tipo, LocalDate hoje, String erro) {
        return switch (tipo) {
            case HOJE -> criar(tipo, hoje, hoje, Agrupamento.HORA, "hoje", erro);
            case SETE_DIAS -> criar(tipo, hoje.minusDays(6), hoje, Agrupamento.DIA, "últimos 7 dias", erro);
            case SEIS_MESES -> criar(tipo, hoje.withDayOfMonth(1).minusMonths(5), hoje, Agrupamento.MES,
                    "últimos 6 meses", erro);
            default -> criar(Tipo.TRINTA_DIAS, hoje.minusDays(29), hoje, Agrupamento.DIA, "últimos 30 dias", erro);
        };
    }

    static PeriodoPainel personalizado(LocalDate de, LocalDate ate, LocalDate hoje) {
        if (de == null || ate == null) {
            return fixo(Tipo.TRINTA_DIAS, hoje, "Informe as duas datas do período (de e até).");
        }
        if (de.isAfter(ate)) {
            return fixo(Tipo.TRINTA_DIAS, hoje, "A data inicial precisa ser antes da data final.");
        }
        if (ate.isAfter(hoje)) {
            return fixo(Tipo.TRINTA_DIAS, hoje, "A data final não pode ser depois de hoje.");
        }
        long dias = ChronoUnit.DAYS.between(de, ate) + 1;
        if (dias > MAXIMO_DIAS_PERSONALIZADO) {
            return fixo(Tipo.TRINTA_DIAS, hoje, "Escolha um período de até 3 anos.");
        }
        Agrupamento agrupamento = dias <= 2 ? Agrupamento.HORA : dias <= 45 ? Agrupamento.DIA : Agrupamento.MES;
        String descricao = de.equals(ate) ? DATA.format(de) : "de " + DATA.format(de) + " a " + DATA.format(ate);
        return criar(Tipo.PERSONALIZADO, de, ate, agrupamento, descricao, null);
    }

    private static PeriodoPainel criar(Tipo tipo, LocalDate de, LocalDate ate, Agrupamento agrupamento,
                                       String descricao, String erro) {
        return new PeriodoPainel(tipo, de, ate, de.atStartOfDay(), ate.plusDays(1).atStartOfDay(),
                agrupamento, descricao, erro);
    }

    private static LocalDate lerData(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.strip());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public long dias() {
        return ChronoUnit.DAYS.between(de, ate) + 1;
    }

    /** Parâmetros da URL que reproduzem este período (para links de exportar, paginação, área). */
    public Map<String, String> parametros() {
        Map<String, String> p = new LinkedHashMap<>();
        if (tipo == Tipo.PERSONALIZADO) {
            p.put("de", de.toString());
            p.put("ate", ate.toString());
        } else {
            p.put("periodo", tipo.parametro);
        }
        return p;
    }

    /** Rótulo do botão "Período" quando ativo (ex.: "01/10 – 15/10"). */
    public String rotuloPersonalizado() {
        DateTimeFormatter curto = DateTimeFormatter.ofPattern(de.getYear() == ate.getYear() ? "dd/MM" : "dd/MM/yy");
        return de.equals(ate) ? curto.format(de) : curto.format(de) + " – " + curto.format(ate);
    }

    /** Pedaço do nome do arquivo exportado (ex.: "2026-10-05-a-2026-11-03"). */
    public String sufixoArquivo() {
        return de.equals(ate) ? de.toString() : de + "-a-" + ate;
    }
}
