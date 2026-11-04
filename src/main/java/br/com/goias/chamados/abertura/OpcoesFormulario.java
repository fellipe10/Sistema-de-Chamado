package br.com.goias.chamados.abertura;

import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.area.CategoriaRepository;
import br.com.goias.chamados.dominio.cadastro.LocalRepository;
import br.com.goias.chamados.dominio.cadastro.SetorRepository;
import br.com.goias.chamados.dominio.chamado.Urgencia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Carrega as listas do formulário (setores, áreas, categorias e locais ativos) para o Model.
 * Etapa 5: área ativa sem nenhuma categoria ativa não aparece (não haveria categoria para escolher).
 * Área sem ícone mapeado usa o ícone genérico (bi-life-preserver).
 * Usa DTOs simples para a tela não depender de entidades com relacionamentos LAZY.
 */
@Component
@RequiredArgsConstructor
public class OpcoesFormulario {

    /** Ícone (Bootstrap Icons) e texto de apoio de cada área, iguais à prévia. */
    private static final Map<String, AreaVisual> VISUAL_POR_SIGLA = Map.of(
            "TI", new AreaVisual("bi-display", "Computador, impressora, internet, sistemas e acessos", false),
            "MAN", new AreaVisual("bi-tools", "Elétrica, hidráulica, ar-condicionado e estrutura", true));
    private static final AreaVisual VISUAL_PADRAO = new AreaVisual("bi-life-preserver", "", false);

    private final SetorRepository setorRepository;
    private final AreaRepository areaRepository;
    private final CategoriaRepository categoriaRepository;
    private final LocalRepository localRepository;

    public record Opcao(Long id, String nome) {
    }

    public record OpcaoCategoria(Long id, Long areaId, String nome) {
    }

    public record OpcaoArea(Long id, String sigla, String nome, String icone, String descricao, boolean escuro) {
    }

    private record AreaVisual(String icone, String descricao, boolean escuro) {
    }

    public void carregar(Model model) {
        model.addAttribute("setores", setorRepository.findByAtivoTrueOrderByNomeAsc().stream()
                .map(s -> new Opcao(s.getId(), s.getNome())).toList());
        model.addAttribute("locais", localRepository.findByAtivoTrueOrderByNomeAsc().stream()
                .map(l -> new Opcao(l.getId(), l.getNome())).toList());

        List<OpcaoArea> areas = new ArrayList<>();
        List<OpcaoCategoria> categorias = new ArrayList<>();
        for (Area area : ordenarTiPrimeiro(areaRepository.findByAtivoTrueOrderByNomeAsc())) {
            var ativas = categoriaRepository.findByAreaIdAndAtivoTrueOrderByNomeAsc(area.getId());
            if (ativas.isEmpty()) {
                // Etapa 5: área nova (ou com todas as categorias desativadas) só aparece quando tiver categoria
                continue;
            }
            AreaVisual visual = VISUAL_POR_SIGLA.getOrDefault(area.getSigla(), VISUAL_PADRAO);
            areas.add(new OpcaoArea(area.getId(), area.getSigla(), area.getNome(),
                    visual.icone(), visual.descricao(), visual.escuro()));
            ativas.forEach(c -> categorias.add(new OpcaoCategoria(c.getId(), area.getId(), c.getNome())));
        }
        model.addAttribute("areas", areas);
        model.addAttribute("categorias", categorias);
        model.addAttribute("urgencias", Urgencia.values());
    }

    /** A prévia mostra TI à esquerda e Manutenção à direita. */
    private static List<Area> ordenarTiPrimeiro(List<Area> areas) {
        List<Area> ordenadas = new ArrayList<>(areas);
        ordenadas.sort((a, b) -> Boolean.compare(!"TI".equals(a.getSigla()), !"TI".equals(b.getSigla())));
        return ordenadas;
    }
}
