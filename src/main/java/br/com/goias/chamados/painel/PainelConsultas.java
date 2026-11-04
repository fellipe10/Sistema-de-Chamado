package br.com.goias.chamados.painel;

import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.painel.Indicadores.Resolvido;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Consultas agregadas próprias do painel e dos relatórios (sempre de UMA área).
 * "Período" = [inicio, fim) — fim exclusivo (meia-noite do dia seguinte).
 */
@Repository
@RequiredArgsConstructor
class PainelConsultas {

    static final Set<StatusChamado> FINALIZADOS = EnumSet.of(StatusChamado.RESOLVIDO, StatusChamado.FECHADO);

    private final EntityManager em;

    // =================================================================
    // Situação atual (não depende do período)
    // =================================================================

    long contarPorStatus(Long areaId, Set<StatusChamado> status) {
        return em.createQuery("""
                        select count(c) from Chamado c
                         where c.area.id = :area and c.status in :status""", Long.class)
                .setParameter("area", areaId).setParameter("status", status)
                .getSingleResult();
    }

    long contarAbertosDesde(Long areaId, LocalDateTime desde) {
        return em.createQuery("""
                        select count(c) from Chamado c
                         where c.area.id = :area and c.dataAbertura >= :desde""", Long.class)
                .setParameter("area", areaId).setParameter("desde", desde)
                .getSingleResult();
    }

    /** Quantas pessoas diferentes estão com chamado EM_ATENDIMENTO. */
    long contarAtendentesEmAtendimento(Long areaId) {
        return em.createQuery("""
                        select count(distinct c.atendente.id) from Chamado c
                         where c.area.id = :area and c.status = :status and c.atendente is not null""", Long.class)
                .setParameter("area", areaId).setParameter("status", StatusChamado.EM_ATENDIMENTO)
                .getSingleResult();
    }

    /** Não resolvidos, com prazo de resolução vencido e NÃO pausados. */
    long contarAtrasados(Long areaId, LocalDateTime agora) {
        return em.createQuery("""
                        select count(c) from Chamado c
                         where c.area.id = :area and c.status not in :finalizados
                           and c.prazoResolucao < :agora and c.pausaInicio is null""", Long.class)
                .setParameter("area", areaId).setParameter("finalizados", FINALIZADOS).setParameter("agora", agora)
                .getSingleResult();
    }

    /**
     * "Precisam de atenção": não resolvidos; primeiro os que estão correndo (pausados vão para o fim),
     * pelo prazo de resolução mais antigo (atrasados primeiro, depois os que vencem logo); sem prazo no fim.
     */
    List<Chamado> precisamDeAtencao(Long areaId, int limite) {
        return em.createQuery("""
                        select c from Chamado c
                          join fetch c.area join fetch c.categoria join fetch c.setor
                          left join fetch c.atendente
                         where c.area.id = :area and c.status not in :finalizados
                         order by case when c.pausaInicio is null then 0 else 1 end,
                                  c.prazoResolucao asc nulls last, c.dataAbertura asc""", Chamado.class)
                .setParameter("area", areaId).setParameter("finalizados", FINALIZADOS)
                .setMaxResults(limite)
                .getResultList();
    }

    // =================================================================
    // Do período
    // =================================================================

    List<LocalDateTime> aberturas(Long areaId, LocalDateTime inicio, LocalDateTime fim) {
        return em.createQuery("""
                        select c.dataAbertura from Chamado c
                         where c.area.id = :area and c.dataAbertura >= :inicio and c.dataAbertura < :fim""",
                        LocalDateTime.class)
                .setParameter("area", areaId).setParameter("inicio", inicio).setParameter("fim", fim)
                .getResultList();
    }

    /** Resolvidos no período (data_resolucao no período; status RESOLVIDO ou FECHADO). */
    List<Resolvido> resolvidos(Long areaId, LocalDateTime inicio, LocalDateTime fim) {
        List<Object[]> linhas = em.createQuery("""
                        select a.id, a.nome, c.dataAbertura, c.dataResolucao, c.prazoResolucao, c.minutosPausados
                          from Chamado c left join c.atendente a
                         where c.area.id = :area and c.status in :finalizados
                           and c.dataResolucao >= :inicio and c.dataResolucao < :fim""", Object[].class)
                .setParameter("area", areaId).setParameter("finalizados", FINALIZADOS)
                .setParameter("inicio", inicio).setParameter("fim", fim)
                .getResultList();
        List<Resolvido> resolvidos = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            resolvidos.add(new Resolvido((Long) l[0], (String) l[1], (LocalDateTime) l[2], (LocalDateTime) l[3],
                    (LocalDateTime) l[4], l[5] == null ? 0 : (Long) l[5]));
        }
        return resolvidos;
    }

    /** Pares (abertura, 1ª resposta) dos chamados cuja 1ª resposta aconteceu no período. */
    List<LocalDateTime[]> primeirasRespostas(Long areaId, LocalDateTime inicio, LocalDateTime fim) {
        List<Object[]> linhas = em.createQuery("""
                        select c.dataAbertura, c.dataPrimeiraResposta from Chamado c
                         where c.area.id = :area
                           and c.dataPrimeiraResposta >= :inicio and c.dataPrimeiraResposta < :fim""", Object[].class)
                .setParameter("area", areaId).setParameter("inicio", inicio).setParameter("fim", fim)
                .getResultList();
        return linhas.stream().map(l -> new LocalDateTime[]{(LocalDateTime) l[0], (LocalDateTime) l[1]}).toList();
    }

    /** Chamados abertos no período por categoria, maiores primeiro. */
    List<Object[]> porCategoria(Long areaId, LocalDateTime inicio, LocalDateTime fim) {
        return em.createQuery("""
                        select c.categoria.nome, count(c) from Chamado c
                         where c.area.id = :area and c.dataAbertura >= :inicio and c.dataAbertura < :fim
                         group by c.categoria.id, c.categoria.nome
                         order by count(c) desc, c.categoria.nome asc""", Object[].class)
                .setParameter("area", areaId).setParameter("inicio", inicio).setParameter("fim", fim)
                .getResultList();
    }

    // =================================================================
    // Lista de chamados do período (relatórios e exportação CSV)
    // =================================================================

    /** Filtros dos relatórios (todos opcionais). atendenteId = 0 → "sem atendente". */
    record FiltroLista(Long areaId, LocalDateTime inicio, LocalDateTime fim, StatusChamado status,
                       Prioridade prioridade, Long categoriaId, Long atendenteId) {
    }

    /** Chamados ABERTOS no período, mais recentes primeiro. maximo = null → todos (exportação). */
    List<Chamado> listar(FiltroLista f, Integer primeiro, Integer maximo) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Chamado> q = cb.createQuery(Chamado.class);
        Root<Chamado> c = q.from(Chamado.class);
        c.fetch("area");
        c.fetch("categoria");
        c.fetch("setor");
        c.fetch("local");
        c.fetch("atendente", JoinType.LEFT);
        q.select(c).where(predicados(cb, c, f))
                .orderBy(cb.desc(c.get("dataAbertura")), cb.desc(c.get("id")));
        var query = em.createQuery(q);
        if (primeiro != null) {
            query.setFirstResult(primeiro);
        }
        if (maximo != null) {
            query.setMaxResults(maximo);
        }
        return query.getResultList();
    }

    long contar(FiltroLista f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Chamado> c = q.from(Chamado.class);
        q.select(cb.count(c)).where(predicados(cb, c, f));
        return em.createQuery(q).getSingleResult();
    }

    private static Predicate[] predicados(CriteriaBuilder cb, Root<Chamado> c, FiltroLista f) {
        List<Predicate> p = new ArrayList<>();
        p.add(cb.equal(c.get("area").get("id"), f.areaId()));
        p.add(cb.greaterThanOrEqualTo(c.get("dataAbertura"), f.inicio()));
        p.add(cb.lessThan(c.get("dataAbertura"), f.fim()));
        if (f.status() != null) {
            p.add(cb.equal(c.get("status"), f.status()));
        }
        if (f.prioridade() != null) {
            p.add(cb.equal(c.get("prioridade"), f.prioridade()));
        }
        if (f.categoriaId() != null) {
            p.add(cb.equal(c.get("categoria").get("id"), f.categoriaId()));
        }
        if (f.atendenteId() != null) {
            p.add(f.atendenteId() == 0
                    ? cb.isNull(c.get("atendente"))
                    : cb.equal(c.get("atendente").get("id"), f.atendenteId()));
        }
        return p.toArray(Predicate[]::new);
    }
}
