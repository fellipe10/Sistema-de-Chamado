package br.com.goias.chamados.admin.areas;

import br.com.goias.chamados.admin.comum.FiltroSituacao;
import br.com.goias.chamados.admin.comum.Parametros;
import br.com.goias.chamados.admin.comum.RegrasNome;
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

import java.util.Map;

/**
 * Cadastro de áreas (só ADMIN).
 *
 * GET  /admin/areas?busca=&situacao=&editar={id}
 * POST /admin/areas                 → salvar (id vazio = nova área)
 * POST /admin/areas/{id}/desativar | /reativar
 */
@Controller
@TelaInterna
@RequestMapping("/admin/areas")
@RequiredArgsConstructor
public class AreasController {

    private final AreasService service;

    @GetMapping
    public String lista(@RequestParam(required = false) String busca,
                        @RequestParam(required = false) String situacao,
                        @RequestParam(required = false) String editar,
                        Model model) {
        Long editarId = Parametros.numero(editar);
        AreasService.Edicao edicao = editarId == null ? null : service.edicao(editarId);
        preparar(model, Parametros.busca(busca), FiltroSituacao.de(situacao), edicao,
                edicao == null ? "" : edicao.nome(), edicao == null ? "" : edicao.sigla(), Map.of());
        return "admin/areas";
    }

    @PostMapping
    public String salvar(@RequestParam(required = false) String id,
                         @RequestParam(required = false) String nome,
                         @RequestParam(required = false) String sigla,
                         Model model, RedirectAttributes redirect) {
        Long idEditar = Parametros.numero(id);
        AreasService.Resultado r = service.salvar(idEditar, nome, sigla);
        if (!r.sucesso()) {
            AreasService.Edicao edicao = idEditar == null ? null : service.edicao(idEditar);
            preparar(model, null, FiltroSituacao.ATIVOS, edicao, nome, sigla, r.erros());
            return "admin/areas";
        }
        if (idEditar == null) {
            redirect.addFlashAttribute("sucesso", "Área \"" + RegrasNome.normalizar(nome)
                    + "\" criada com o expediente e os prazos padrão. Cadastre as categorias dela para "
                    + "ela aparecer no formulário de abertura.");
            redirect.addFlashAttribute("areaCriada", r.id());
        } else {
            redirect.addFlashAttribute("sucesso", "Alterações salvas.");
        }
        return "redirect:/admin/areas";
    }

    @PostMapping("/{id}/desativar")
    public String desativar(@PathVariable Long id, RedirectAttributes redirect) {
        String erro = service.alterarSituacao(id, false);
        if (erro != null) {
            redirect.addFlashAttribute("erro", erro);
        } else {
            redirect.addFlashAttribute("sucesso", "Área \"" + service.buscar(id).getNome()
                    + "\" desativada. Não recebe mais chamados pelo formulário; a equipe continua vendo os chamados dela.");
        }
        return "redirect:/admin/areas";
    }

    @PostMapping("/{id}/reativar")
    public String reativar(@PathVariable Long id, RedirectAttributes redirect) {
        service.alterarSituacao(id, true);
        redirect.addFlashAttribute("sucesso", "Área \"" + service.buscar(id).getNome() + "\" reativada.");
        return "redirect:/admin/areas";
    }

    private void preparar(Model model, String busca, FiltroSituacao situacao, AreasService.Edicao edicao,
                          String nome, String sigla, Map<String, String> erros) {
        model.addAttribute("areas", service.listar(busca, situacao));
        model.addAttribute("busca", busca);
        model.addAttribute("situacao", situacao);
        model.addAttribute("situacoes", FiltroSituacao.values());
        model.addAttribute("edicao", edicao);
        model.addAttribute("nome", nome);
        model.addAttribute("sigla", sigla);
        model.addAttribute("erros", erros);
        model.addAttribute("menuAtivo", "configuracoes");
    }
}
