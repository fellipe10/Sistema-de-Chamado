package br.com.goias.chamados.admin.expediente;

import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.cadastro.Expediente;
import br.com.goias.chamados.dominio.cadastro.ExpedienteRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Expediente de cada área (/admin/expediente) — Etapa 5.
 *
 * Salvar troca todos os períodos da área de uma vez (apaga e grava de novo, na mesma transação).
 * "Copiar de outra área" faz o mesmo com os períodos da área de origem.
 * Não mexe em prazos já calculados: para isso existe o botão Recalcular.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpedienteService {

    /** Campos de período mostrados por dia (vazios ficam para a pessoa preencher). */
    public static final int PERIODOS_POR_DIA = 3;

    private static final Locale PT_BR = new Locale("pt", "BR");

    private final ExpedienteRepository expedienteRepository;
    private final AreaRepository areaRepository;
    private final EntityManager em;

    /** Um dia na tela: períodos como texto "HH:mm" (vazios completam até 3). */
    public record DiaTela(DayOfWeek dia, List<RegrasExpediente.Digitado> periodos) {

        public String nome() {
            String n = dia.getDisplayName(TextStyle.FULL, PT_BR);
            return Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }

        public String chave() {
            return dia.name();
        }

        /** Total de horas do dia ("8h", "4h 30min", "—"), considerando só períodos completos e válidos. */
        public String total() {
            long minutos = 0;
            for (RegrasExpediente.Digitado d : periodos) {
                if (d.inicio() == null || d.inicio().isBlank() || d.fim() == null || d.fim().isBlank()) {
                    continue;
                }
                LocalTime i = RegrasExpediente.hora(d.inicio());
                LocalTime f = RegrasExpediente.hora(d.fim());
                if (i != null && f != null && f.isAfter(i)) {
                    minutos += java.time.Duration.between(i, f).toMinutes();
                }
            }
            return formatar(minutos);
        }

        public boolean vazio() {
            return periodos.stream().allMatch(p -> (p.inicio() == null || p.inicio().isBlank())
                    && (p.fim() == null || p.fim().isBlank()));
        }
    }

    @Transactional(readOnly = true)
    public List<DiaTela> semana(Long areaId) {
        Map<DayOfWeek, List<RegrasExpediente.Digitado>> porDia = new EnumMap<>(DayOfWeek.class);
        for (Expediente e : expedienteRepository.findByAreaIdOrderByDiaSemanaAscHoraInicioAsc(areaId)) {
            porDia.computeIfAbsent(e.getDiaDaSemana(), d -> new ArrayList<>())
                    .add(new RegrasExpediente.Digitado(e.getHoraInicio().toString(), e.getHoraFim().toString()));
        }
        return montar(porDia);
    }

    /** Tela com o que a pessoa digitou (quando há erro). */
    public static List<DiaTela> montar(Map<DayOfWeek, List<RegrasExpediente.Digitado>> porDia) {
        List<DiaTela> dias = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            List<RegrasExpediente.Digitado> periodos = new ArrayList<>(porDia.getOrDefault(dia, List.of()));
            while (periodos.size() < PERIODOS_POR_DIA) {
                periodos.add(new RegrasExpediente.Digitado("", ""));
            }
            dias.add(new DiaTela(dia, periodos));
        }
        return dias;
    }

    /** Total da semana em horas ("40h"). */
    public static String totalSemana(List<DiaTela> dias) {
        long minutos = 0;
        for (DiaTela d : dias) {
            for (RegrasExpediente.Digitado p : d.periodos()) {
                if (p.inicio() == null || p.inicio().isBlank() || p.fim() == null || p.fim().isBlank()) {
                    continue;
                }
                LocalTime i = RegrasExpediente.hora(p.inicio());
                LocalTime f = RegrasExpediente.hora(p.fim());
                if (i != null && f != null && f.isAfter(i)) {
                    minutos += java.time.Duration.between(i, f).toMinutes();
                }
            }
        }
        return formatar(minutos);
    }

    @Transactional
    public RegrasExpediente.Resultado salvar(Long areaId, Map<DayOfWeek, List<RegrasExpediente.Digitado>> digitado) {
        RegrasExpediente.Resultado r = RegrasExpediente.validar(digitado);
        if (!r.sucesso()) {
            return r;
        }
        substituir(areaId, r.periodos());
        log.info("Expediente da área {} atualizado.", areaId);
        return r;
    }

    /** Copia os períodos da área de origem para a de destino (substitui tudo). */
    @Transactional
    public String copiar(Long origemId, Long destinoId) {
        if (origemId == null || origemId.equals(destinoId) || areaRepository.findById(origemId).isEmpty()) {
            return "Escolha outra área para copiar.";
        }
        Map<DayOfWeek, List<RegrasExpediente.Periodo>> periodos = new EnumMap<>(DayOfWeek.class);
        for (Expediente e : expedienteRepository.findByAreaIdOrderByDiaSemanaAscHoraInicioAsc(origemId)) {
            periodos.computeIfAbsent(e.getDiaDaSemana(), d -> new ArrayList<>())
                    .add(new RegrasExpediente.Periodo(e.getHoraInicio(), e.getHoraFim()));
        }
        if (periodos.isEmpty()) {
            return "A área escolhida não tem expediente cadastrado.";
        }
        substituir(destinoId, periodos);
        log.info("Expediente da área {} copiado para a área {}.", origemId, destinoId);
        return null;
    }

    private void substituir(Long areaId, Map<DayOfWeek, List<RegrasExpediente.Periodo>> periodos) {
        em.createQuery("delete from Expediente e where e.area.id = :area").setParameter("area", areaId).executeUpdate();
        em.flush();
        var area = areaRepository.getReferenceById(areaId);
        periodos.forEach((dia, lista) -> lista.forEach(p -> {
            Expediente e = new Expediente();
            e.setArea(area);
            e.setDiaSemana(dia.getValue());
            e.setHoraInicio(p.inicio());
            e.setHoraFim(p.fim());
            em.persist(e);
        }));
        em.flush();
    }

    static String formatar(long minutos) {
        if (minutos == 0) {
            return "—";
        }
        long h = minutos / 60;
        long m = minutos % 60;
        return m == 0 ? h + "h" : h + "h " + m + "min";
    }
}
