package br.com.goias.chamados.admin.recalculo;

import br.com.goias.chamados.config.RecalculoPrazosV6;
import br.com.goias.chamados.config.RecalculoPrazosV6.LinhaChamado;
import br.com.goias.chamados.config.RecalculoPrazosV6.Mudanca;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosExpediente;
import br.com.goias.chamados.dominio.cadastro.SlaConfigRepository;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracao;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracaoRepository;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.chamado.TipoInteracao;
import br.com.goias.chamados.dominio.usuario.Usuario;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import br.com.goias.chamados.login.UsuarioLogado;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Recalcular prazos dos chamados em andamento" (Etapa 5) — botão das telas de SLA, expediente e feriados.
 *
 * REAPROVEITA A REGRA DA MIGRATION V6 ({@link RecalculoPrazosV6#calcularMudancas}): o prazo de cada chamado
 * não resolvido/fechado é refeito do zero (abertura + SLA da prioridade + minutos já pausados, em tempo útil
 * com o expediente e os feriados de AGORA), e só muda quem ficou com prazo diferente.
 * - Rodar duas vezes seguidas não muda nada na segunda (idempotente).
 * - Cada chamado alterado ganha um COMENTARIO interno:
 *   "Prazos recalculados após mudança de expediente/feriado/SLA por {admin}."
 * - Tudo numa transação: ou recalcula todos ou nenhum. Se alguém alterar um dos chamados no mesmo instante,
 *   o @Version do chamado faz a transação falhar e o admin vê a mensagem para tentar de novo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecalculoPrazosService {

    private static final EnumSet<StatusChamado> FINALIZADOS = EnumSet.of(StatusChamado.RESOLVIDO, StatusChamado.FECHADO);

    private final EntityManager em;
    private final SlaConfigRepository slaConfigRepository;
    private final CalendariosExpediente calendarios;
    private final ChamadoInteracaoRepository interacaoRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * @param emAndamento quantos chamados não resolvidos/fechados existem no alcance
     * @param afetados    quantos vão (ou foram) ter o prazo alterado
     */
    public record Previa(long emAndamento, int afetados) {
    }

    /** Quantos chamados seriam alterados (não grava nada). */
    @Transactional(readOnly = true)
    public Previa previa(Long areaId) {
        List<Chamado> chamados = emAndamento(areaId);
        return new Previa(chamados.size(), calcular(chamados).size());
    }

    /**
     * Recalcula e grava.
     *
     * @param areaId null = todas as áreas
     * @return quantos chamados foram recalculados
     */
    @Transactional
    public int executar(Long areaId, OrigemRecalculo origem, UsuarioLogado admin) {
        List<Chamado> chamados = emAndamento(areaId);
        List<Mudanca> mudancas = calcular(chamados);
        if (mudancas.isEmpty()) {
            log.info("Recálculo de prazos ({}, área {}) por {}: nada mudou.", origem.getNome(),
                    areaId == null ? "todas" : areaId, admin.getLogin());
            return 0;
        }
        Map<Long, Chamado> porId = chamados.stream().collect(Collectors.toMap(Chamado::getId, Function.identity()));
        Usuario autor = usuarioRepository.getReferenceById(admin.getId());
        LocalDateTime agora = LocalDateTime.now();
        String texto = origem.comentario(admin.getNome());
        for (Mudanca m : mudancas) {
            Chamado c = porId.get(m.id());
            c.setPrazoPrimeiraResposta(m.primeiraResposta());
            c.setPrazoResolucao(m.resolucao());

            ChamadoInteracao comentario = new ChamadoInteracao();
            comentario.setChamado(c);
            comentario.setAutor(autor);
            comentario.setTipo(TipoInteracao.COMENTARIO);
            comentario.setInterno(TipoInteracao.COMENTARIO.isInterno());
            comentario.setTexto(texto);
            comentario.setData(agora);
            interacaoRepository.save(comentario);
        }
        em.flush();
        log.info("Recálculo de prazos ({}, área {}) por {}: {} de {} chamados em andamento recalculados.",
                origem.getNome(), areaId == null ? "todas" : areaId, admin.getLogin(), mudancas.size(), chamados.size());
        return mudancas.size();
    }

    // =================================================================

    private List<Chamado> emAndamento(Long areaId) {
        String jpql = "select c from Chamado c join fetch c.area where c.status not in :fim"
                + (areaId == null ? "" : " and c.area.id = :area") + " order by c.id";
        var query = em.createQuery(jpql, Chamado.class).setParameter("fim", FINALIZADOS);
        if (areaId != null) {
            query.setParameter("area", areaId);
        }
        return query.getResultList();
    }

    /** Mesma regra pura da V6, com os dados lidos pelo JPA. */
    private List<Mudanca> calcular(List<Chamado> chamados) {
        Map<String, int[]> slas = new HashMap<>();
        slaConfigRepository.findAll().forEach(s -> slas.put(
                RecalculoPrazosV6.chave(s.getArea().getId(), s.getPrioridade().name()),
                new int[]{s.getMinutosPrimeiraResposta(), s.getMinutosResolucao()}));
        Map<Long, CalendarioExpediente> porArea = new HashMap<>();
        List<LinhaChamado> linhas = chamados.stream().map(c -> {
            porArea.computeIfAbsent(c.getArea().getId(), calendarios::daArea);
            return new LinhaChamado(c.getId(), c.getArea().getId(), c.getPrioridade().name(), c.getDataAbertura(),
                    c.getDataPrimeiraResposta(), c.getMinutosPausados() == null ? 0 : c.getMinutosPausados(),
                    c.getPrazoPrimeiraResposta(), c.getPrazoResolucao());
        }).toList();
        return RecalculoPrazosV6.calcularMudancas(linhas, porArea, slas);
    }
}
