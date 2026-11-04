package br.com.goias.chamados.admin.categorias;

import br.com.goias.chamados.admin.comum.FiltroSituacao;
import br.com.goias.chamados.admin.comum.Parametros;
import br.com.goias.chamados.config.TelaInterna;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Cadastro de categorias (só ADMIN).
 *
 * GET  /admin/categorias?area=&busca=&situacao=&editar={id}
 * POST /admin/categorias                 → salvar (id vazio = nova)
 * POST /admin/categorias/{id}/desativar | /reativar
 */
@Controller
@TelaInterna
@RequestMapping("/admin/categorias")
@RequiredArgsConstructor
public class CategoriasController {

    private final CategoriasService service;

    @GetMapping
    public String lista(@RequestParam(required = false) String area,
                        @RequestParam(required = false) String busca,
                        @RequestParam(required = false) String situacao,
                        @RequestParam(required = false) String editar,
                        Model model) {
        Long areaId = Parametros.numero(area);
        Long editarId = Parametros.numero(editar);
        CategoriasService.Edicao edicao = editarId == null ? null : service.edicao(editarId);
        preparar(model, areaId, Parametros.busca(busca), FiltroSituacao.de(situacao), edicao,
                edicao == null ? "" : edicao.nome(), edicao == null ? areaId : edicao.areaId(), Map.of());
        return "admin/categorias";
    }

    @PostMapping
    public String salvar(@RequestParam(required = false) String id,
                         @RequestParam(required = false) String areaId,
                         @RequestParam(required = false) String nome,
                         @RequestParam(required = false) String filtroArea,
                         @RequestParam(required = false) String busca,
                         @RequestParam(required = false) String situacao,
                         Model model, RedirectAttributes redirect) {
        Long idEditar = Parametros.numero(id);
        Long area = Parametros.numero(areaId);
        CategoriasService.Resultado r = service.salvar(idEditar, area, nome);
        if (!r.sucesso()) {
            CategoriasService.Edicao edicao = idEditar == null ? null : service.edicao(idEditar);
            preparar(model, Parametros.numero(filtroArea), Parametros.busca(busca), FiltroSituacao.de(situacao),
                    edicao, nome, area, r.erros());
            return "admin/categorias";
        }
        redirect.addFlashAttribute("sucesso", idEditar == null ? "Categoria cadastrada." : "Alterações salvas.");
        return "redirect:" + url(filtroArea == null || filtroArea.isBlank() ? String.valueOf(area) : filtroArea,
                busca, situacao);
    }

    @PostMapping("/{id}/desativar")
    public String desativar(@PathVariable Long id,
                            @RequestParam(required = false) String filtroArea,
                            @RequestParam(required = false) String busca,
                            @RequestParam(required = false) String situacao,
                            RedirectAttributes redirect) {
        String aviso = service.alterarSituacao(id, false);
        redirect.addFlashAttribute("sucesso", "Categoria \"" + service.buscar(id).getNome()
                + "\" desativada. Não aparece mais no formulário; os chamados antigos continuam com ela.");
        if (aviso != null) {
            redirect.addFlashAttribute("erro", aviso);
        }
        return "redirect:" + url(filtroArea, busca, situacao);
    }

    @PostMapping("/{id}/reativar")
    public String reativar(@PathVariable Long id,
                           @RequestParam(required = false) String filtroArea,
                           @RequestParam(required = false) String busca,
                           @RequestParam(required = false) String situacao,
                           RedirectAttributes redirect) {
        service.alterarSituacao(id, true);
        redirect.addFlashAttribute("sucesso", "Categoria \"" + service.buscar(id).getNome() + "\" reativada.");
        return "redirect:" + url(filtroArea, busca, situacao);
    }

    private void preparar(Model model, Long filtroArea, String busca, FiltroSituacao situacao,
                          CategoriasService.Edicao edicao, String nome, Long areaForm, Map<String, String> erros) {
        model.addAttribute("categorias", service.listar(filtroArea, busca, situacao));
        model.addAttribute("areas", service.areas());
        model.addAttribute("filtroArea", filtroArea);
        model.addAttribute("busca", busca);
        model.addAttribute("situacao", situacao);
        model.addAttribute("situacoes", FiltroSituacao.values());
        model.addAttribute("edicao", edicao);
        model.addAttribute("nome", nome);
        model.addAttribute("areaForm", areaForm);
        model.addAttribute("erros", erros);
        model.addAttribute("menuAtivo", "configuracoes");
    }

    private static String url(String area, String busca, String situacao) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/admin/categorias");
        Long a = Parametros.numero(area);
        if (a != null) {
            b.queryParam("area", a);
        }
        String bb = Parametros.busca(busca);
        if (bb != null) {
            b.queryParam("busca", bb);
        }
        FiltroSituacao s = FiltroSituacao.de(situacao);
        if (s != FiltroSituacao.ATIVOS) {
            b.queryParam("situacao", s.getParametro());
        }
        return b.encode().build().toUriString();
    }
}
