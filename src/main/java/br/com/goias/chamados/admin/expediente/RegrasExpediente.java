package br.com.goias.chamados.admin.expediente;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regras do expediente de uma área (Etapa 5) — sem banco, fáceis de testar.
 *
 * Para cada dia da semana, zero ou mais períodos (início/fim):
 * - período com só um dos horários preenchido é recusado;
 * - fim precisa ser depois do início (CHECK ck_expediente_horas);
 * - períodos do mesmo dia não podem se sobrepor (encostar pode: 08–12 e 12–13);
 * - a semana precisa ter pelo menos um período (sem expediente, o prazo não teria como contar).
 */
public final class RegrasExpediente {

    static final String INCOMPLETO = "Preencha o início e o fim do período (ou deixe os dois vazios).";
    static final String HORA_INVALIDA = "Horário inválido. Use o formato 08:00.";
    static final String FIM_ANTES = "O fim precisa ser depois do início.";
    static final String SOBREPOSTOS = "Os períodos deste dia estão sobrepostos.";
    public static final String SEMANA_VAZIA = "Informe pelo menos um período de trabalho na semana.";

    private RegrasExpediente() {
    }

    public record Periodo(LocalTime inicio, LocalTime fim) {
        public long minutos() {
            return java.time.Duration.between(inicio, fim).toMinutes();
        }
    }

    /** O que veio da tela (texto "HH:mm" ou vazio). */
    public record Digitado(String inicio, String fim) {
    }

    /**
     * @param periodos  dia → períodos válidos, em ordem (só quando não há erros)
     * @param erros     dia → mensagem; a chave especial "semana" para a semana vazia
     */
    public record Resultado(Map<DayOfWeek, List<Periodo>> periodos, Map<String, String> erros) {
        public boolean sucesso() {
            return erros.isEmpty();
        }
    }

    public static Resultado validar(Map<DayOfWeek, List<Digitado>> semana) {
        Map<DayOfWeek, List<Periodo>> resultado = new EnumMap<>(DayOfWeek.class);
        Map<String, String> erros = new LinkedHashMap<>();
        int total = 0;
        for (DayOfWeek dia : DayOfWeek.values()) {
            List<Periodo> doDia = new ArrayList<>();
            String erro = null;
            for (Digitado d : semana.getOrDefault(dia, List.of())) {
                boolean temInicio = d.inicio() != null && !d.inicio().isBlank();
                boolean temFim = d.fim() != null && !d.fim().isBlank();
                if (!temInicio && !temFim) {
                    continue;
                }
                if (temInicio != temFim) {
                    erro = INCOMPLETO;
                    break;
                }
                LocalTime inicio = hora(d.inicio());
                LocalTime fim = hora(d.fim());
                if (inicio == null || fim == null) {
                    erro = HORA_INVALIDA;
                    break;
                }
                if (!fim.isAfter(inicio)) {
                    erro = FIM_ANTES;
                    break;
                }
                doDia.add(new Periodo(inicio, fim));
            }
            if (erro == null && sobrepostos(doDia)) {
                erro = SOBREPOSTOS;
            }
            if (erro != null) {
                erros.put(dia.name(), erro);
            } else {
                doDia.sort(Comparator.comparing(Periodo::inicio));
                resultado.put(dia, doDia);
                total += doDia.size();
            }
        }
        if (erros.isEmpty() && total == 0) {
            erros.put("semana", SEMANA_VAZIA);
        }
        return new Resultado(erros.isEmpty() ? resultado : Map.of(), erros);
    }

    /** Algum período começa antes do anterior terminar? (encostar não conta). */
    public static boolean sobrepostos(List<Periodo> periodos) {
        List<Periodo> ordenados = new ArrayList<>(periodos);
        ordenados.sort(Comparator.comparing(Periodo::inicio));
        for (int i = 1; i < ordenados.size(); i++) {
            if (ordenados.get(i).inicio().isBefore(ordenados.get(i - 1).fim())) {
                return true;
            }
        }
        return false;
    }

    /** "8:00", "08:00" ou "08:00:00" → LocalTime; inválido → null. */
    static LocalTime hora(String texto) {
        String t = texto.strip();
        if (t.matches("\\d:\\d{2}")) {
            t = "0" + t;
        }
        try {
            return LocalTime.parse(t.length() > 5 ? t.substring(0, 5) : t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
