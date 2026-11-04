package br.com.goias.chamados.admin.feriados;

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

import java.time.LocalDate;
import java.util.Map;

/**
 * Feriados (só ADMIN).
 *
 * GET  /admin/feriados?ano=2027&editar={id}
 * POST /admin/feriados               → salvar (id vazio = novo)
 * POST /admin/feriados/{id}/excluir  → exclui de verdade
 */
@Controller
@TelaInterna
@RequestMapping("/admin/feriados")
@RequiredArgsConstructor
public class FeriadosController {

    private final FeriadosService service;

    @GetMapping
    public String lista(@RequestParam(required = false) String ano,
                        @RequestParam(required = false) String editar,
                        Model model) {
        Long editarId = Parametros.numero(editar);
        FeriadosService.Edicao edicao = editarId == null ? null : service.edicao(editarId);
        int anoTela = anoValido(Parametros.inteiro(ano), edicao);
        Integer semFeriado = service.anoSemFeriado();
        String dataNova = semFeriado != null && anoTela == semFeriado ? semFeriado + "-01-01" : "";
        preparar(model, anoTela, edicao,
                edicao == null ? dataNova : edicao.data(),
                edicao == null ? "" : edicao.descricao(),
                edicao == null ? null : edicao.areaId(), Map.of());
        return "admin/feriados";
    }

    @PostMapping
    public String salvar(@RequestParam(required = false) String id,
                         @RequestParam(required = false) String data,
                         @RequestParam(required = false) String descricao,
                         @RequestParam(required = false) String areaId,
                         @RequestParam(required = false) String ano,
                         Model model, RedirectAttributes redirect) {
        Long idEditar = Parametros.numero(id);
        Long area = Parametros.numero(areaId);
        FeriadosService.Resultado r = service.salvar(idEditar, data, descricao, area);
        if (!r.sucesso()) {
            FeriadosService.Edicao edicao = idEditar == null ? null : service.edicao(idEditar);
            preparar(model, anoValido(Parametros.inteiro(ano), edicao), edicao, data, descricao, area, r.erros());
            return "admin/feriados";
        }
        redirect.addFlashAttribute("sucesso", idEditar == null ? "Feriado cadastrado." : "Feriado alterado.");
        redirect.addFlashAttribute("sugerirRecalculo", true);
        return "redirect:/admin/feriados?ano=" + r.data().getYear();
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        LocalDate data = service.excluir(id);
        redirect.addFlashAttribute("sucesso", "Feriado excluído.");
        redirect.addFlashAttribute("sugerirRecalculo", true);
        return "redirect:/admin/feriados?ano=" + data.getYear();
    }

    private int anoValido(Integer ano, FeriadosService.Edicao edicao) {
        if (edicao != null) {
            return LocalDate.parse(edicao.data()).getYear();
        }
        return ano != null && ano >= 2000 && ano <= 2100 ? ano : service.anoAtual();
    }

    private void preparar(Model model, int ano, FeriadosService.Edicao edicao, String data, String descricao,
                          Long areaId, Map<String, String> erros) {
        model.addAttribute("ano", ano);
        model.addAttribute("anos", service.anos(ano));
        model.addAttribute("feriados", service.doAno(ano));
        model.addAttribute("aviso", service.aviso());
        model.addAttribute("anoSemFeriado", service.anoSemFeriado());
        model.addAttribute("areas", service.areas());
        model.addAttribute("edicao", edicao);
        model.addAttribute("data", data);
        model.addAttribute("descricao", descricao);
        model.addAttribute("areaId", areaId);
        model.addAttribute("erros", erros);
        model.addAttribute("menuAtivo", "configuracoes");
        if (!model.containsAttribute("sugerirRecalculo")) {
            model.addAttribute("sugerirRecalculo", false);
        }
    }
}
