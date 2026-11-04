package br.com.goias.chamados.admin.areas;

import br.com.goias.chamados.admin.RegistroNaoEncontradoException;
import br.com.goias.chamados.admin.comum.FiltroSituacao;
import br.com.goias.chamados.admin.comum.Parametros;
import br.com.goias.chamados.admin.comum.RegrasNome;
import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.cadastro.Expediente;
import br.com.goias.chamados.dominio.cadastro.ExpedienteRepository;
import br.com.goias.chamados.dominio.cadastro.SlaConfig;
import br.com.goias.chamados.dominio.cadastro.SlaConfigRepository;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cadastro de áreas (/admin/areas) — Etapa 5.
 *
 * - Criar: nome + sigla; já recebe o expediente padrão e o SLA padrão ({@link PadroesAreaNova}).
 * - Editar: nome sempre; sigla só enquanto a área não tiver chamados ({@link RegrasArea}).
 * - Ativar/desativar (nunca apagar). Não deixa desativar a última área ativa.
 * - Área sem categoria ativa não aparece no formulário público (ver abertura/OpcoesFormulario).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AreasService {

    private static final EnumSet<StatusChamado> FINALIZADOS = EnumSet.of(StatusChamado.RESOLVIDO, StatusChamado.FECHADO);

    private final AreaRepository areaRepository;
    private final ExpedienteRepository expedienteRepository;
    private final SlaConfigRepository slaConfigRepository;
    private final EntityManager em;

    /** Linha da lista. */
    public record Linha(Long id, String nome, String sigla, boolean ativo, long categoriasAtivas,
                        long usuariosAtivos, long chamados, long emAndamento) {

        /** Ativa mas sem categoria: não aparece no formulário público. */
        public boolean foraDoFormulario() {
            return ativo && categoriasAtivas == 0;
        }
    }

    /** Dados para o formulário de edição. */
    public record Edicao(Long id, String nome, String sigla, long chamados) {
        public boolean siglaTravada() {
            return chamados > 0;
        }
    }

    public record Resultado(Long id, Map<String, String> erros) {
        public boolean sucesso() {
            return erros.isEmpty();
        }
    }

    @Transactional(readOnly = true)
    public List<Linha> listar(String busca, FiltroSituacao situacao) {
        Map<Long, Long> categorias = contarPorArea(
                "select c.area.id, count(c) from Categoria c where c.ativo = true group by c.area.id");
        Map<Long, Long> usuarios = contarPorArea(
                "select u.area.id, count(u) from Usuario u where u.ativo = true and u.area is not null group by u.area.id");
        Map<Long, Long> chamados = contarPorArea("select c.area.id, count(c) from Chamado c group by c.area.id");
        Map<Long, Long> emAndamento = new HashMap<>();
        em.createQuery("select c.area.id, count(c) from Chamado c where c.status not in :fim group by c.area.id",
                        Object[].class)
                .setParameter("fim", FINALIZADOS).getResultList()
                .forEach(l -> emAndamento.put((Long) l[0], (Long) l[1]));

        return areaRepository.findAll().stream()
                .filter(a -> situacao.aceita(a.isAtivo()))
                .filter(a -> Parametros.contem(a.getNome() + " " + a.getSigla(), busca))
                .sorted(Comparator.comparing((Area a) -> !a.isAtivo()).thenComparing(a -> RegrasNome.chave(a.getNome())))
                .map(a -> new Linha(a.getId(), a.getNome(), a.getSigla(), a.isAtivo(),
                        categorias.getOrDefault(a.getId(), 0L), usuarios.getOrDefault(a.getId(), 0L),
                        chamados.getOrDefault(a.getId(), 0L), emAndamento.getOrDefault(a.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Edicao edicao(Long id) {
        Area area = buscar(id);
        return new Edicao(area.getId(), area.getNome(), area.getSigla(), contarChamados(id));
    }

    @Transactional
    public Resultado salvar(Long id, String nome, String sigla) {
        Area area = id == null ? null : buscar(id);
        List<Area> outras = areaRepository.findAll().stream()
                .filter(a -> area == null || !a.getId().equals(area.getId()))
                .toList();

        Map<String, String> erros = new LinkedHashMap<>();
        String erroNome = RegrasNome.validar(nome, RegrasArea.NOME_MAXIMO,
                outras.stream().map(Area::getNome).toList(), "uma área");
        if (erroNome != null) {
            erros.put("nome", erroNome);
        }
        String siglaNova = RegrasArea.normalizarSigla(sigla);
        if (area == null || (siglaNova != null && !siglaNova.equals(area.getSigla()))) {
            String erroSigla = RegrasArea.validarSigla(sigla, outras.stream().map(Area::getSigla).toList());
            if (erroSigla == null && area != null) {
                erroSigla = RegrasArea.podeMudarSigla(area.getSigla(), sigla, contarChamados(area.getId()));
            }
            if (erroSigla != null) {
                erros.put("sigla", erroSigla);
            }
        }
        if (!erros.isEmpty()) {
            return new Resultado(null, erros);
        }

        if (area == null) {
            Area nova = new Area();
            nova.setNome(RegrasNome.normalizar(nome));
            nova.setSigla(siglaNova);
            nova.setAtivo(true);
            areaRepository.saveAndFlush(nova);
            copiarPadroes(nova);
            log.info("Área criada: {} ({}) com expediente e SLA padrão.", nova.getNome(), nova.getSigla());
            return new Resultado(nova.getId(), Map.of());
        }
        area.setNome(RegrasNome.normalizar(nome));
        if (siglaNova != null && !siglaNova.isEmpty()) {
            area.setSigla(siglaNova);
        }
        areaRepository.saveAndFlush(area);
        return new Resultado(area.getId(), Map.of());
    }

    /** @return mensagem de erro, ou null se deu certo */
    @Transactional
    public String alterarSituacao(Long id, boolean ativar) {
        Area area = buscar(id);
        if (area.isAtivo() == ativar) {
            return null;
        }
        if (!ativar && areaRepository.findByAtivoTrueOrderByNomeAsc().size() <= 1) {
            return "Precisa haver pelo menos uma área ativa para o formulário de abertura funcionar.";
        }
        area.setAtivo(ativar);
        areaRepository.saveAndFlush(area);
        return null;
    }

    @Transactional(readOnly = true)
    public Area buscar(Long id) {
        return areaRepository.findById(id).orElseThrow(() -> new RegistroNaoEncontradoException("Área", id));
    }

    /** Expediente seg–sex 08–12/13–17 e SLA padrão (só se a área ainda não tiver nenhum). */
    private void copiarPadroes(Area area) {
        if (expedienteRepository.findByAreaIdOrderByDiaSemanaAscHoraInicioAsc(area.getId()).isEmpty()) {
            for (PadroesAreaNova.Periodo p : PadroesAreaNova.EXPEDIENTE) {
                Expediente e = new Expediente();
                e.setArea(area);
                e.setDiaSemana(p.diaSemana());
                e.setHoraInicio(p.inicio());
                e.setHoraFim(p.fim());
                expedienteRepository.save(e);
            }
        }
        if (slaConfigRepository.findByAreaId(area.getId()).isEmpty()) {
            PadroesAreaNova.SLA.forEach((prioridade, minutos) -> {
                SlaConfig sla = new SlaConfig();
                sla.setArea(area);
                sla.setPrioridade(prioridade);
                sla.setMinutosPrimeiraResposta(minutos[0]);
                sla.setMinutosResolucao(minutos[1]);
                slaConfigRepository.save(sla);
            });
        }
    }

    private long contarChamados(Long areaId) {
        return em.createQuery("select count(c) from Chamado c where c.area.id = :a", Long.class)
                .setParameter("a", areaId).getSingleResult();
    }

    private Map<Long, Long> contarPorArea(String jpql) {
        Map<Long, Long> mapa = new HashMap<>();
        em.createQuery(jpql, Object[].class).getResultList().forEach(l -> mapa.put((Long) l[0], (Long) l[1]));
        return mapa;
    }
}
