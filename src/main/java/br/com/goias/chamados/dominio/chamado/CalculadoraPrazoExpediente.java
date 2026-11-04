package br.com.goias.chamados.dominio.chamado;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosExpediente;
import br.com.goias.chamados.dominio.cadastro.SlaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Prazos de SLA em HORÁRIO DE EXPEDIENTE (Etapa 4).
 *
 * Exemplo (TI, segunda a sexta 08–12 e 13–17, prioridade Alta = 120/480 min):
 * chamado aberto sexta 16:50 → 10 min na sexta + o resto na segunda:
 * 1ª resposta segunda 09:50; resolução segunda 16:50 (se segunda não for feriado).
 *
 * Chamado aberto fora do expediente (noite, sábado, feriado) começa a contar
 * no início do próximo período útil.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalculadoraPrazoExpediente implements CalculadoraPrazo {

    private final SlaConfigRepository slaConfigRepository;
    private final CalendariosExpediente calendarios;

    @Override
    public Prazos recalcular(Long areaId, Prioridade prioridade, LocalDateTime abertura,
                             long minutosPausados, boolean jaTevePrimeiraResposta) {
        return slaConfigRepository.findByAreaIdAndPrioridade(areaId, prioridade)
                .map(sla -> prazos(calendarios.daArea(areaId), abertura,
                        sla.getMinutosPrimeiraResposta(), sla.getMinutosResolucao(),
                        minutosPausados, jaTevePrimeiraResposta))
                .orElseGet(() -> {
                    log.warn("Sem SLA configurado para área {} e prioridade {}; chamado ficará sem prazo.",
                            areaId, prioridade);
                    return Prazos.semPrazo();
                });
    }

    @Override
    public LocalDateTime somarMinutosUteis(Long areaId, LocalDateTime inicio, long minutos) {
        return calendarios.daArea(areaId).somarMinutosUteis(inicio, minutos);
    }

    @Override
    public long minutosUteisEntre(Long areaId, LocalDateTime de, LocalDateTime ate) {
        return calendarios.daArea(areaId).minutosUteisEntre(de, ate);
    }

    /**
     * Regra pura (sem banco), também usada pelo recálculo único da Etapa 4:
     * prazo = abertura + minutos do SLA + minutos pausados, tudo em tempo útil.
     * A pausa só entra no prazo da 1ª resposta se ela ainda não aconteceu.
     */
    public static Prazos prazos(CalendarioExpediente calendario, LocalDateTime abertura,
                                Integer minutosPrimeiraResposta, Integer minutosResolucao,
                                long minutosPausados, boolean jaTevePrimeiraResposta) {
        long pausados = Math.max(0, minutosPausados);
        LocalDateTime primeira = minutosPrimeiraResposta == null ? null
                : calendario.somarMinutosUteis(abertura,
                minutosPrimeiraResposta + (jaTevePrimeiraResposta ? 0 : pausados));
        LocalDateTime resolucao = minutosResolucao == null ? null
                : calendario.somarMinutosUteis(abertura, minutosResolucao + pausados);
        return new Prazos(primeira, resolucao);
    }
}
