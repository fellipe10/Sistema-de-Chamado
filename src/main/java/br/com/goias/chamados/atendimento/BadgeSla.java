package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.StatusChamado;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Badge de prazo (SLA) da fila, da tela do chamado e do painel, calculado na hora com base em prazo_resolucao.
 *
 * Chamado em andamento:
 * - "Pausado" (cinza-azulado) quando está "Aguardando o solicitante" — não conta atraso;
 * - "Atrasado X" (vermelho) quando o prazo já passou;
 * - "Vence em X" (âmbar) quando falta 1 h ou menos OU 20% ou menos do prazo total
 *   (prazo total = prazo_resolucao − data_abertura);
 * - "No prazo" (verde) no resto.
 * Resolvido/fechado: "Resolvido no prazo" (verde) ou "Resolvido com atraso" (vermelho),
 * comparando data_resolucao com o prazo.
 * Sem prazo configurado: "Sem prazo" (cinza).
 *
 * Etapa 4: com o calendário da área, "falta", "total" e "atraso" são medidos em HORAS ÚTEIS
 * (ex.: sexta 16:30 com prazo segunda 09:00 → "Vence em 1h 30min"). Sem calendário (testes antigos),
 * conta em tempo corrido.
 *
 * @param texto  o que aparece no badge
 * @param classe classe CSS (sla-ok, sla-vence, sla-atrasado, sla-pausado, sla-sem)
 * @param dica   texto do title (passar o mouse), com a data do prazo
 */
public record BadgeSla(String texto, String classe, String dica) {

    static final String OK = "sla-ok";
    static final String VENCE = "sla-vence";
    static final String ATRASADO = "sla-atrasado";
    static final String PAUSADO = "sla-pausado";
    static final String SEM = "sla-sem";

    private static final Duration UMA_HORA = Duration.ofHours(1);
    private static final DateTimeFormatter FORMATO_DICA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    /** Badge do chamado, medindo em horas úteis da área. */
    public static BadgeSla doChamado(Chamado c, CalendarioExpediente calendario, LocalDateTime agora) {
        return calcular(c.getStatus(), c.getDataAbertura(), c.getPrazoResolucao(), c.getDataResolucao(),
                c.getDataFechamento(), c.getPausaInicio(), calendario, agora);
    }

    /** Versão em tempo corrido e sem pausa (mantida para os testes da Etapa 3). */
    public static BadgeSla calcular(StatusChamado status,
                                    LocalDateTime dataAbertura,
                                    LocalDateTime prazoResolucao,
                                    LocalDateTime dataResolucao,
                                    LocalDateTime dataFechamento,
                                    LocalDateTime agora) {
        return calcular(status, dataAbertura, prazoResolucao, dataResolucao, dataFechamento, null, null, agora);
    }

    /**
     * @param pausaInicio início da pausa atual (null = prazo correndo)
     * @param calendario  expediente da área (null = tempo corrido)
     */
    public static BadgeSla calcular(StatusChamado status,
                                    LocalDateTime dataAbertura,
                                    LocalDateTime prazoResolucao,
                                    LocalDateTime dataResolucao,
                                    LocalDateTime dataFechamento,
                                    LocalDateTime pausaInicio,
                                    CalendarioExpediente calendario,
                                    LocalDateTime agora) {
        if (prazoResolucao == null) {
            return new BadgeSla("Sem prazo", SEM, "Sem prazo de SLA configurado para esta área e prioridade.");
        }
        String dicaPrazo = "Prazo de resolução: " + FORMATO_DICA.format(prazoResolucao);

        if (status == StatusChamado.RESOLVIDO || status == StatusChamado.FECHADO) {
            LocalDateTime resolvidoEm = dataResolucao != null ? dataResolucao : dataFechamento;
            if (resolvidoEm == null || !resolvidoEm.isAfter(prazoResolucao)) {
                return new BadgeSla("Resolvido no prazo", OK, dicaPrazo);
            }
            return new BadgeSla("Resolvido com atraso", ATRASADO, dicaPrazo);
        }

        if (pausaInicio != null) {
            return new BadgeSla("Pausado", PAUSADO, "Prazo pausado desde " + FORMATO_DICA.format(pausaInicio)
                    + " (aguardando o solicitante). " + dicaPrazo + " — será estendido ao retomar.");
        }

        if (!agora.isBefore(prazoResolucao)) {
            Duration atraso = medir(calendario, prazoResolucao, agora);
            String texto = atraso.toMinutes() < 1 && calendario != null
                    ? "Atrasado" // venceu no fim do expediente e ainda não houve tempo útil depois
                    : "Atrasado " + formatarAtraso(atraso);
            return new BadgeSla(texto, ATRASADO, dicaPrazo);
        }
        Duration restante = medir(calendario, agora, prazoResolucao);
        Duration total = dataAbertura == null ? null : medir(calendario, dataAbertura, prazoResolucao);
        if (venceLogo(total, restante)) {
            return new BadgeSla("Vence em " + formatarRestante(restante), VENCE, dicaPrazo);
        }
        return new BadgeSla("No prazo", OK, dicaPrazo);
    }

    private static Duration medir(CalendarioExpediente calendario, LocalDateTime de, LocalDateTime ate) {
        return calendario == null ? Duration.between(de, ate) : calendario.duracaoUtilEntre(de, ate);
    }

    /** Usado pelos testes da Etapa 3 (tempo corrido). */
    static boolean venceLogo(LocalDateTime dataAbertura, LocalDateTime prazoResolucao, Duration restante) {
        return venceLogo(dataAbertura == null ? null : Duration.between(dataAbertura, prazoResolucao), restante);
    }

    /** Falta 1 h ou menos, OU falta 20% ou menos do prazo total. */
    static boolean venceLogo(Duration total, Duration restante) {
        if (restante.compareTo(UMA_HORA) <= 0) {
            return true;
        }
        if (total == null) {
            return false;
        }
        long totalSegundos = total.getSeconds();
        if (totalSegundos <= 0) {
            return true;
        }
        // restante <= 20% do total  ⇔  restante × 5 <= total
        return restante.getSeconds() * 5 <= totalSegundos;
    }

    /** "25 min", "5h", "3d" (atraso arredondado para baixo, mínimo 1 min). */
    static String formatarAtraso(Duration atraso) {
        long minutos = Math.max(1, atraso.toMinutes());
        if (minutos < 60) {
            return minutos + " min";
        }
        long horas = minutos / 60;
        if (horas < 48) {
            return horas + "h";
        }
        return (horas / 24) + "d";
    }

    /** "menos de 1 min", "25 min", "1h", "2h 30min", "1d 4h". */
    static String formatarRestante(Duration restante) {
        long minutos = restante.toMinutes();
        if (minutos < 1) {
            return "menos de 1 min";
        }
        if (minutos < 60) {
            return minutos + " min";
        }
        long horas = minutos / 60;
        long resto = minutos % 60;
        if (horas < 24) {
            return resto == 0 ? horas + "h" : horas + "h " + String.format("%02d", resto) + "min";
        }
        long dias = horas / 24;
        long horasResto = horas % 24;
        return horasResto == 0 ? dias + "d" : dias + "d " + horasResto + "h";
    }
}
