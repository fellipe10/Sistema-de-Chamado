package br.com.goias.chamados.admin.sla;

import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.cadastro.SlaConfig;
import br.com.goias.chamados.dominio.cadastro.SlaConfigRepository;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Prazos de SLA por área (/admin/sla) — Etapa 5.
 * Grava em minutos na tabela sla_config (uma linha por área + prioridade; cria a que faltar).
 * Não mexe em prazos já calculados: para isso existe o botão Recalcular.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaService {

    private final SlaConfigRepository slaConfigRepository;
    private final AreaRepository areaRepository;

    /** Uma linha da grade (valores em horas e minutos, como texto, para voltar à tela com erro). */
    public record LinhaGrade(Prioridade prioridade, String primeiraHoras, String primeiraMinutos,
                             String resolucaoHoras, String resolucaoMinutos, Integer minutosPrimeira,
                             Integer minutosResolucao) {

        public String dicaPrimeira() {
            return minutosPrimeira == null ? "" : "= " + RegrasSla.descrever(minutosPrimeira);
        }

        public String dicaResolucao() {
            return minutosResolucao == null ? "" : "= " + RegrasSla.descrever(minutosResolucao);
        }
    }

    /** Grade como está gravada (linhas que faltam aparecem vazias). */
    @Transactional(readOnly = true)
    public List<LinhaGrade> grade(Long areaId) {
        Map<Prioridade, SlaConfig> gravado = new EnumMap<>(Prioridade.class);
        slaConfigRepository.findByAreaId(areaId).forEach(s -> gravado.put(s.getPrioridade(), s));
        List<LinhaGrade> linhas = new ArrayList<>();
        for (Prioridade p : ordem()) {
            SlaConfig s = gravado.get(p);
            if (s == null) {
                linhas.add(new LinhaGrade(p, "", "", "", "", null, null));
            } else {
                int a = s.getMinutosPrimeiraResposta();
                int b = s.getMinutosResolucao();
                linhas.add(new LinhaGrade(p, String.valueOf(a / 60), String.valueOf(a % 60),
                        String.valueOf(b / 60), String.valueOf(b % 60), a, b));
            }
        }
        return linhas;
    }

    /** Grade com o que a pessoa digitou (para mostrar de novo quando há erro). */
    public static List<LinhaGrade> gradeDigitada(Map<Prioridade, RegrasSla.Digitado> digitado) {
        List<LinhaGrade> linhas = new ArrayList<>();
        for (Prioridade p : ordem()) {
            RegrasSla.Digitado d = digitado.get(p);
            linhas.add(new LinhaGrade(p, d.primeiraHoras(), d.primeiraMinutos(), d.resolucaoHoras(),
                    d.resolucaoMinutos(), null, null));
        }
        return linhas;
    }

    @Transactional
    public RegrasSla.Resultado salvar(Long areaId, Map<Prioridade, RegrasSla.Digitado> digitado) {
        RegrasSla.Resultado r = RegrasSla.validar(digitado);
        if (!r.sucesso()) {
            return r;
        }
        var area = areaRepository.getReferenceById(areaId);
        r.minutos().forEach((prioridade, minutos) -> {
            SlaConfig sla = slaConfigRepository.findByAreaIdAndPrioridade(areaId, prioridade).orElseGet(() -> {
                SlaConfig nova = new SlaConfig();
                nova.setArea(area);
                nova.setPrioridade(prioridade);
                return nova;
            });
            sla.setMinutosPrimeiraResposta(minutos.primeiraResposta());
            sla.setMinutosResolucao(minutos.resolucao());
            slaConfigRepository.save(sla);
        });
        slaConfigRepository.flush();
        log.info("SLA da área {} atualizado.", areaId);
        return r;
    }

    /** Crítica → Baixa (mesma ordem da tela). */
    static List<Prioridade> ordem() {
        return List.of(Prioridade.CRITICA, Prioridade.ALTA, Prioridade.MEDIA, Prioridade.BAIXA);
    }
}
