package br.com.goias.chamados.admin.areas;

import br.com.goias.chamados.dominio.chamado.Prioridade;

import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * O que uma área nova recebe automaticamente ao ser criada (Etapa 5).
 * Os mesmos valores que TI e Manutenção receberam nas migrations V2 e V4.
 */
public final class PadroesAreaNova {

    /** Um período de trabalho (dia ISO 1 = segunda ... 7 = domingo). */
    public record Periodo(int diaSemana, LocalTime inicio, LocalTime fim) {
    }

    /** Segunda a sexta, 08:00–12:00 e 13:00–17:00 (8 h úteis por dia). */
    public static final List<Periodo> EXPEDIENTE;

    /** Prazos padrão em minutos: {1ª resposta, resolução}. Dia útil = 480 min. */
    public static final Map<Prioridade, int[]> SLA;

    static {
        java.util.ArrayList<Periodo> periodos = new java.util.ArrayList<>();
        for (int dia = 1; dia <= 5; dia++) {
            periodos.add(new Periodo(dia, LocalTime.of(8, 0), LocalTime.of(12, 0)));
            periodos.add(new Periodo(dia, LocalTime.of(13, 0), LocalTime.of(17, 0)));
        }
        EXPEDIENTE = List.copyOf(periodos);

        Map<Prioridade, int[]> sla = new EnumMap<>(Prioridade.class);
        sla.put(Prioridade.CRITICA, new int[]{30, 240});
        sla.put(Prioridade.ALTA, new int[]{120, 480});
        sla.put(Prioridade.MEDIA, new int[]{240, 1440});
        sla.put(Prioridade.BAIXA, new int[]{480, 2400});
        SLA = java.util.Collections.unmodifiableMap(sla);
    }

    private PadroesAreaNova() {
    }
}
