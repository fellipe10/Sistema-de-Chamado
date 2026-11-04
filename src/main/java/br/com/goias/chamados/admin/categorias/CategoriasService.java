package br.com.goias.chamados.admin.categorias;

import br.com.goias.chamados.admin.RegistroNaoEncontradoException;
import br.com.goias.chamados.admin.comum.FiltroSituacao;
import br.com.goias.chamados.admin.comum.Parametros;
import br.com.goias.chamados.admin.comum.RegrasNome;
import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.area.Categoria;
import br.com.goias.chamados.dominio.area.CategoriaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cadastro de categorias (/admin/categorias) — Etapa 5.
 *
 * - Cada categoria é de uma área; nome único dentro da área (sem diferenciar maiúsculas/acentos).
 * - Depois de usada em chamado, a área da categoria não muda (o banco exige que a categoria
 *   do chamado seja da mesma área — FK composta da V1).
 * - Ativar/desativar (nunca apagar). Área sem nenhuma categoria ativa sai do formulário público.
 */
@Service
@RequiredArgsConstructor
public class CategoriasService {

    public static final int NOME_MAXIMO = 100;

    private final CategoriaRepository categoriaRepository;
    private final AreaRepository areaRepository;
    private final EntityManager em;

    public record Linha(Long id, String nome, Long areaId, String areaNome, boolean areaAtiva,
                        boolean ativo, long chamados) {
    }

    public record OpcaoArea(Long id, String nome, boolean ativo) {
        public String rotulo() {
            return ativo ? nome : nome + " (inativa)";
        }
    }

    public record Edicao(Long id, String nome, Long areaId, long chamados) {
        public boolean areaTravada() {
            return chamados > 0;
        }
    }

    public record Resultado(Long id, Map<String, String> erros) {
        public boolean sucesso() {
            return erros.isEmpty();
        }
    }

    @Transactional(readOnly = true)
    public List<Linha> listar(Long areaId, String busca, FiltroSituacao situacao) {
        Map<Long, Long> uso = new HashMap<>();
        em.createQuery("select c.categoria.id, count(c) from Chamado c group by c.categoria.id", Object[].class)
                .getResultList().forEach(l -> uso.put((Long) l[0], (Long) l[1]));
        return em.createQuery("select c from Categoria c join fetch c.area", Categoria.class).getResultList().stream()
                .filter(c -> areaId == null || c.getArea().getId().equals(areaId))
                .filter(c -> situacao.aceita(c.isAtivo()))
                .filter(c -> Parametros.contem(c.getNome(), busca))
                .sorted(Comparator.comparing((Categoria c) -> RegrasNome.chave(c.getArea().getNome()))
                        .thenComparing(c -> !c.isAtivo())
                        .thenComparing(c -> RegrasNome.chave(c.getNome())))
                .map(c -> new Linha(c.getId(), c.getNome(), c.getArea().getId(), c.getArea().getNome(),
                        c.getArea().isAtivo(), c.isAtivo(), uso.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpcaoArea> areas() {
        return areaRepository.findAll().stream()
                .sorted(Comparator.comparing((Area a) -> !a.isAtivo()).thenComparing(a -> RegrasNome.chave(a.getNome())))
                .map(a -> new OpcaoArea(a.getId(), a.getNome(), a.isAtivo()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Edicao edicao(Long id) {
        Categoria c = buscar(id);
        return new Edicao(c.getId(), c.getNome(), c.getArea().getId(), contarChamados(id));
    }

    @Transactional
    public Resultado salvar(Long id, Long areaId, String nome) {
        Categoria categoria = id == null ? null : buscar(id);
        Map<String, String> erros = new LinkedHashMap<>();

        Area area = areaId == null ? null : areaRepository.findById(areaId).orElse(null);
        boolean mudouArea = categoria != null && area != null && !categoria.getArea().getId().equals(area.getId());
        if (area == null) {
            erros.put("areaId", "Escolha a área.");
        } else if ((categoria == null || mudouArea) && !area.isAtivo()) {
            erros.put("areaId", "Escolha uma área ativa.");
        } else if (mudouArea && contarChamados(categoria.getId()) > 0) {
            erros.put("areaId", "A área não pode mudar: a categoria já foi usada em chamados desta área.");
        }

        if (area != null) {
            List<String> outros = categoriaRepository.findByAreaIdOrderByNomeAsc(area.getId()).stream()
                    .filter(c -> categoria == null || !c.getId().equals(categoria.getId()))
                    .map(Categoria::getNome)
                    .toList();
            String erroNome = RegrasNome.validar(nome, NOME_MAXIMO, outros, "uma categoria nesta área");
            if (erroNome != null) {
                erros.put("nome", erroNome);
            }
        } else if (RegrasNome.normalizar(nome) == null || RegrasNome.normalizar(nome).isEmpty()) {
            erros.put("nome", "Informe o nome.");
        }
        if (!erros.isEmpty()) {
            return new Resultado(null, erros);
        }

        Categoria gravar = categoria != null ? categoria : new Categoria();
        gravar.setNome(RegrasNome.normalizar(nome));
        gravar.setArea(area);
        if (categoria == null) {
            gravar.setAtivo(true);
        }
        categoriaRepository.saveAndFlush(gravar);
        return new Resultado(gravar.getId(), Map.of());
    }

    /**
     * Ativa ou desativa.
     *
     * @return aviso extra quando a área ficou sem categoria ativa (sai do formulário público), ou null
     */
    @Transactional
    public String alterarSituacao(Long id, boolean ativar) {
        Categoria c = buscar(id);
        c.setAtivo(ativar);
        categoriaRepository.saveAndFlush(c);
        if (!ativar && c.getArea().isAtivo()
                && categoriaRepository.findByAreaIdAndAtivoTrueOrderByNomeAsc(c.getArea().getId()).isEmpty()) {
            return "A área " + c.getArea().getNome()
                    + " ficou sem categoria ativa e saiu do formulário de abertura até ter uma de novo.";
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Categoria buscar(Long id) {
        Categoria c = em.createQuery("select c from Categoria c join fetch c.area where c.id = :id", Categoria.class)
                .setParameter("id", id).getResultStream().findFirst().orElse(null);
        if (c == null) {
            throw new RegistroNaoEncontradoException("Categoria", id);
        }
        return c;
    }

    private long contarChamados(Long categoriaId) {
        return em.createQuery("select count(c) from Chamado c where c.categoria.id = :id", Long.class)
                .setParameter("id", categoriaId).getSingleResult();
    }
}
