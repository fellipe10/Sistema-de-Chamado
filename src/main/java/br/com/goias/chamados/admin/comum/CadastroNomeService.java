package br.com.goias.chamados.admin.comum;

import br.com.goias.chamados.admin.RegistroNaoEncontradoException;
import br.com.goias.chamados.dominio.cadastro.ItemNomeado;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Regras comuns dos cadastros "só nome + ativo" (setores e locais) — Etapa 5.
 *
 * - nome obrigatório, até 100 caracteres, único (sem diferenciar maiúsculas/acentos);
 * - nunca apaga: só ativa/desativa (o item pode estar em chamados antigos);
 * - não deixa desativar o último ativo (o formulário público exige setor e local).
 *
 * SetoresService e LocaisService só informam a entidade e os textos.
 */
@Slf4j
public abstract class CadastroNomeService<T extends ItemNomeado> {

    public static final int NOME_MAXIMO = 100;

    private final EntityManager em;
    private final Class<T> tipo;
    private final Supplier<T> criar;
    /** Campo do Chamado que aponta para este cadastro ("setor" ou "local"). */
    private final String campoNoChamado;
    /** "um setor", "um local" (para mensagens). */
    private final String umItem;

    protected CadastroNomeService(EntityManager em, Class<T> tipo, Supplier<T> criar,
                                  String campoNoChamado, String umItem) {
        this.em = em;
        this.tipo = tipo;
        this.criar = criar;
        this.campoNoChamado = campoNoChamado;
        this.umItem = umItem;
    }

    /** Linha da lista. */
    public record Linha(Long id, String nome, boolean ativo, long chamados) {
    }

    /** Resultado de salvar: id gravado ou erro no campo nome. */
    public record Resultado(Long id, Map<String, String> erros) {
        public boolean sucesso() {
            return erros.isEmpty();
        }
    }

    @Transactional(readOnly = true)
    public List<Linha> listar(String busca, FiltroSituacao situacao) {
        Map<Long, Long> uso = usoEmChamados();
        return todos().stream()
                .filter(i -> situacao.aceita(i.isAtivo()))
                .filter(i -> Parametros.contem(i.getNome(), busca))
                .sorted(Comparator.comparing((T i) -> !i.isAtivo()).thenComparing(i -> RegrasNome.chave(i.getNome())))
                .map(i -> new Linha(i.getId(), i.getNome(), i.isAtivo(), uso.getOrDefault(i.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public T buscar(Long id) {
        T item = em.find(tipo, id);
        if (item == null) {
            throw new RegistroNaoEncontradoException(tipo.getSimpleName(), id);
        }
        return item;
    }

    @Transactional
    public Resultado salvar(Long id, String nome) {
        T item = id == null ? null : buscar(id);
        List<String> outros = todos().stream()
                .filter(i -> item == null || !i.getId().equals(item.getId()))
                .map(ItemNomeado::getNome)
                .toList();
        String erro = RegrasNome.validar(nome, NOME_MAXIMO, outros, umItem);
        if (erro != null) {
            Map<String, String> erros = new LinkedHashMap<>();
            erros.put("nome", erro);
            return new Resultado(null, erros);
        }
        T gravar = item != null ? item : criar.get();
        gravar.setNome(RegrasNome.normalizar(nome));
        if (item == null) {
            gravar.setAtivo(true);
            em.persist(gravar);
        }
        em.flush();
        log.info("{} {} gravado: {}", tipo.getSimpleName(), gravar.getId(), gravar.getNome());
        return new Resultado(gravar.getId(), Map.of());
    }

    /** @return mensagem de erro, ou null se deu certo */
    @Transactional
    public String alterarSituacao(Long id, boolean ativar) {
        T item = buscar(id);
        if (item.isAtivo() == ativar) {
            return null;
        }
        if (!ativar) {
            long ativos = todos().stream().filter(ItemNomeado::isAtivo).count();
            if (ativos <= 1) {
                return "Precisa haver pelo menos " + umItem + " ativo: o formulário de abertura exige esse campo.";
            }
        }
        item.setAtivo(ativar);
        em.flush();
        return null;
    }

    @Transactional(readOnly = true)
    public long contarAtivos() {
        return todos().stream().filter(ItemNomeado::isAtivo).count();
    }

    private List<T> todos() {
        return em.createQuery("select i from " + tipo.getSimpleName() + " i", tipo).getResultList();
    }

    private Map<Long, Long> usoEmChamados() {
        Map<Long, Long> mapa = new HashMap<>();
        em.createQuery("select c." + campoNoChamado + ".id, count(c) from Chamado c group by c." + campoNoChamado + ".id",
                        Object[].class)
                .getResultList()
                .forEach(l -> mapa.put((Long) l[0], (Long) l[1]));
        return mapa;
    }
}
