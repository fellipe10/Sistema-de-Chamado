package br.com.goias.chamados.dominio.cadastro;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente.Periodo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Monta o {@link CalendarioExpediente} de uma área a partir das tabelas expediente e feriado.
 * Lê do banco a cada chamada (são tabelas pequenas); quem precisa de várias áreas na mesma
 * tela guarda o resultado num Map durante a requisição.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendariosExpediente {

    private final ExpedienteRepository expedienteRepository;
    private final FeriadoRepository feriadoRepository;

    public CalendarioExpediente daArea(Long areaId) {
        Map<DayOfWeek, List<Periodo>> periodos = new EnumMap<>(DayOfWeek.class);
        for (Expediente e : expedienteRepository.findByAreaIdOrderByDiaSemanaAscHoraInicioAsc(areaId)) {
            periodos.computeIfAbsent(e.getDiaDaSemana(), d -> new ArrayList<>())
                    .add(new Periodo(e.getHoraInicio(), e.getHoraFim()));
        }
        CalendarioExpediente calendario = new CalendarioExpediente(periodos, feriadoRepository.datasDaArea(areaId));
        if (!calendario.temExpediente()) {
            log.warn("Área {} sem expediente cadastrado: prazos e tempos serão contados em tempo corrido.", areaId);
        }
        return calendario;
    }
}
