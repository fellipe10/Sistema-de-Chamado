package br.com.goias.chamados.admin.sla;

import br.com.goias.chamados.admin.comum.EscolhaArea;
import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Prazos de SLA (só ADMIN).
 *
 * GET  /admin/sla?area={id}
 * POST /admin/sla  → campos {PRIORIDADE}_primeiraHoras, _primeiraMinutos, _resolucaoHoras, _resolucaoMinutos
 */
@Controller
@TelaInterna
@RequestMapping("/admin/sla")
@RequiredArgsConstructor
public class SlaController {

    private final SlaService service;
    private final EscolhaArea escolhaArea;

    @GetMapping
    public String tela(@RequestParam(required = false) String area, Model model) {
        EscolhaArea.Escolha escolha = escolhaArea.escolher(area);
        preparar(model, escolha, service.grade(escolha.atual().id()), Map.of());
        return "admin/sla";
    }

    @PostMapping
    public String salvar(@RequestParam Map<String, String> campos, Model model, RedirectAttributes redirect) {
        EscolhaArea.Escolha escolha = escolhaArea.escolher(campos.get("area"));
        Map<Prioridade, RegrasSla.Digitado> digitado = new EnumMap<>(Prioridade.class);
        for (Prioridade p : Prioridade.values()) {
            digitado.put(p, new RegrasSla.Digitado(
                    campos.get(p.name() + "_primeiraHoras"), campos.get(p.name() + "_primeiraMinutos"),
                    campos.get(p.name() + "_resolucaoHoras"), campos.get(p.name() + "_resolucaoMinutos")));
        }
        RegrasSla.Resultado r = service.salvar(escolha.atual().id(), digitado);
        if (!r.sucesso()) {
            preparar(model, escolha, SlaService.gradeDigitada(digitado), r.erros());
            return "admin/sla";
        }
        redirect.addFlashAttribute("sucesso", "Prazos da área " + escolha.atual().nome() + " salvos.");
        redirect.addFlashAttribute("sugerirRecalculo", true);
        return "redirect:/admin/sla?area=" + escolha.atual().id();
    }

    private void preparar(Model model, EscolhaArea.Escolha escolha, List<SlaService.LinhaGrade> grade,
                          Map<String, String> erros) {
        model.addAttribute("escolha", escolha);
        model.addAttribute("grade", grade);
        model.addAttribute("erros", erros);
        model.addAttribute("menuAtivo", "configuracoes");
        if (!model.containsAttribute("sugerirRecalculo")) {
            model.addAttribute("sugerirRecalculo", false);
        }
    }
}
