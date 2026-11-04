package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.area.Categoria;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Consultas próprias da fila (Criteria API, porque os filtros são opcionais).
 *
 * Ordenação padrão: prazo de resolução mais próximo primeiro (atrasados ficam no topo,
 * porque o prazo deles é o mais antigo), depois prioridade (Crítica → Baixa), depois abertura.
 * Chamados sem prazo vão para o fim.
 * Na aba "Resolvidos e fechados" (histórico) a ordem é a resolução mais recente primeiro.
 */
@Repository
@RequiredArgsConstructor
class FilaConsultas {

    private static final EnumSet<StatusChamado> FINALIZADOS = EnumSet.of(StatusChamado.RESOLVIDO, StatusChamado.FECHADO);

    private final EntityManager em;

    /**
     * @param areaId     área visível (null = todas, só para ADMIN)
     * @param usuarioId  usuário logado (aba "Meus")
     * @param busca      código, título ou nome do solicitante (opcional)
     * @param atendenteId responsável pelo chamado (opcional; atalho do admin ao desativar alguém)
     */
    record Criterios(Long areaId, Long usuarioId, AbaFila aba, String busca,
                     Prioridade prioridade, Long categoriaId, Long atendenteId) {

        Criterios naAba(AbaFila outra) {
            return new Criterios(areaId, usuarioId, outra, busca, prioridade, categoriaId, atendenteId);
        }
    }

    List<Chamado> listar(Criterios criterios, int primeiro, int quantidade) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Chamado> query = cb.createQuery(Chamado.class);
        Root<Chamado> chamado = query.from(Chamado.class);
        chamado.fetch("area");
        chamado.fetch("categoria");
        chamado.fetch("setor");
        chamado.fetch("atendente", JoinType.LEFT);

        query.select(chamado)
                .where(predicados(cb, chamado, criterios))
                .orderBy(ordem(cb, chamado, criterios.aba()));
        return em.createQuery(query)
                .setFirstResult(primeiro)
                .setMaxResults(quantidade)
                .getResultList();
    }

    long contar(Criterios criterios) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Chamado> chamado = query.from(Chamado.class);
        query.select(cb.count(chamado)).where(predicados(cb, chamado, criterios));
        return em.createQuery(query).getSingleResult();
    }

    /** Contador do menu: chamados Novos (ABERTO sem atendente) da área (null = todas). */
    long contarNovos(Long areaId) {
        return contar(new Criterios(areaId, null, AbaFila.NOVOS, null, null, null, null));
    }

    /** Categorias para o filtro (inclui inativas, para achar chamados antigos). */
    List<Categoria> categorias(Long areaId) {
        if (areaId == null) {
            return em.createQuery("select c from Categoria c join fetch c.area a order by a.nome, c.nome",
                    Categoria.class).getResultList();
        }
        return em.createQuery("select c from Categoria c join fetch c.area a where a.id = :area order by c.nome",
                Categoria.class).setParameter("area", areaId).getResultList();
    }

    private Predicate[] predicados(CriteriaBuilder cb, Root<Chamado> chamado, Criterios c) {
        List<Predicate> lista = new ArrayList<>();
        Expression<StatusChamado> status = chamado.get("status");

        if (c.areaId() != null) {
            lista.add(cb.equal(chamado.get("area").get("id"), c.areaId()));
        }

        switch (c.aba()) {
            case NOVOS -> {
                lista.add(cb.equal(status, StatusChamado.ABERTO));
                lista.add(cb.isNull(chamado.get("atendente")));
            }
            case MEUS -> {
                lista.add(c.usuarioId() == null
                        ? cb.disjunction()
                        : cb.equal(chamado.get("atendente").get("id"), c.usuarioId()));
                lista.add(cb.notEqual(status, StatusChamado.FECHADO));
            }
            case ANDAMENTO -> lista.add(cb.not(status.in(FINALIZADOS)));
            case AGUARDANDO -> lista.add(cb.equal(status, StatusChamado.AGUARDANDO));
            case HISTORICO -> lista.add(status.in(FINALIZADOS));
        }

        if (c.busca() != null && !c.busca().isBlank()) {
            String termo = c.busca().strip().toLowerCase(Locale.ROOT);
            String termoCodigo = termo.replace("-", "").replace("–", "").replaceAll("\\s+", "");
            Expression<String> codigoSemHifen = cb.lower(cb.function("replace", String.class,
                    chamado.get("protocolo"), cb.literal("-"), cb.literal("")));
            List<Predicate> ou = new ArrayList<>();
            if (!termoCodigo.isEmpty()) {
                ou.add(cb.like(codigoSemHifen, contem(termoCodigo), '\\'));
            }
            ou.add(cb.like(cb.lower(chamado.get("titulo")), contem(termo), '\\'));
            ou.add(cb.like(cb.lower(chamado.get("solicitanteNome")), contem(termo), '\\'));
            lista.add(cb.or(ou.toArray(Predicate[]::new)));
        }
        if (c.prioridade() != null) {
            lista.add(cb.equal(chamado.get("prioridade"), c.prioridade()));
        }
        if (c.categoriaId() != null) {
            lista.add(cb.equal(chamado.get("categoria").get("id"), c.categoriaId()));
        }
        if (c.atendenteId() != null) {
            lista.add(cb.equal(chamado.get("atendente").get("id"), c.atendenteId()));
        }
        return lista.toArray(Predicate[]::new);
    }

    private List<Order> ordem(CriteriaBuilder cb, Root<Chamado> chamado, AbaFila aba) {
        if (aba == AbaFila.HISTORICO) {
            Expression<Integer> semResolucao = cb.<Integer>selectCase()
                    .when(cb.isNull(chamado.get("dataResolucao")), 1).otherwise(0);
            return List.of(cb.asc(semResolucao), cb.desc(chamado.get("dataResolucao")), cb.desc(chamado.get("id")));
        }
        Expression<Integer> semPrazo = cb.<Integer>selectCase()
                .when(cb.isNull(chamado.get("prazoResolucao")), 1).otherwise(0);
        Expression<Integer> pesoPrioridade = cb.<Integer>selectCase()
                .when(cb.equal(chamado.get("prioridade"), Prioridade.CRITICA), Prioridade.CRITICA.getPeso())
                .when(cb.equal(chamado.get("prioridade"), Prioridade.ALTA), Prioridade.ALTA.getPeso())
                .when(cb.equal(chamado.get("prioridade"), Prioridade.MEDIA), Prioridade.MEDIA.getPeso())
                .otherwise(Prioridade.BAIXA.getPeso());
        return List.of(
                cb.asc(semPrazo),
                cb.asc(chamado.get("prazoResolucao")),
                cb.desc(pesoPrioridade),
                cb.asc(chamado.get("dataAbertura")),
                cb.asc(chamado.get("id")));
    }

    /** "%termo%" com os curingas do LIKE escapados (% e _ digitados viram texto normal). */
    static String contem(String termo) {
        String escapado = termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }
}
