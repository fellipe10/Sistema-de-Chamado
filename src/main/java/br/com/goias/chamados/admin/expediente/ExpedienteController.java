package br.com.goias.chamados.admin.expediente;

import br.com.goias.chamados.admin.comum.EscolhaArea;
import br.com.goias.chamados.admin.comum.Parametros;
import br.com.goias.chamados.config.TelaInterna;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Expediente das áreas (só ADMIN).
 *
 * GET  /admin/expediente?area={id}
 * POST /admin/expediente          → campos {DIA}_inicio_{n} e {DIA}_fim_{n} (n = 0, 1, 2...)
 * POST /admin/expediente/copiar   → area (destino) + origem
 */
@Controller
@TelaInterna
@RequestMapping("/admin/expediente")
@RequiredArgsConstructor
public class ExpedienteController {

    private final ExpedienteService service;
    private final EscolhaArea escolhaArea;

    @GetMapping
    public String tela(@RequestParam(required = false) String area, Model model) {
        EscolhaArea.Escolha escolha = escolhaArea.escolher(area);
        preparar(model, escolha, service.semana(escolha.atual().id()), Map.of());
        return "admin/expediente";
    }

    @PostMapping
    public String salvar(@RequestParam Map<String, String> campos, Model model, RedirectAttributes redirect) {
        EscolhaArea.Escolha escolha = escolhaArea.escolher(campos.get("area"));
        Map<DayOfWeek, List<RegrasExpediente.Digitado>> digitado = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dia : DayOfWeek.values()) {
            List<RegrasExpediente.Digitado> periodos = new ArrayList<>();
            for (int n = 0; n < 10; n++) {
                String inicio = campos.get(dia.name() + "_inicio_" + n);
                String fim = campos.get(dia.name() + "_fim_" + n);
                if (inicio == null && fim == null) {
                    break;
                }
                periodos.add(new RegrasExpediente.Digitado(inicio, fim));
            }
            digitado.put(dia, periodos);
        }
        RegrasExpediente.Resultado r = service.salvar(escolha.atual().id(), digitado);
        if (!r.sucesso()) {
            preparar(model, escolha, ExpedienteService.montar(digitado), r.erros());
            return "admin/expediente";
        }
        redirect.addFlashAttribute("sucesso", "Expediente da área " + escolha.atual().nome() + " salvo.");
        redirect.addFlashAttribute("sugerirRecalculo", true);
        return "redirect:/admin/expediente?area=" + escolha.atual().id();
    }

    @PostMapping("/copiar")
    public String copiar(@RequestParam(required = false) String area,
                         @RequestParam(required = false) String origem,
                         RedirectAttributes redirect) {
        EscolhaArea.Escolha escolha = escolhaArea.escolher(area);
        Long origemId = Parametros.numero(origem);
        String erro = service.copiar(origemId, escolha.atual().id());
        if (erro != null) {
            redirect.addFlashAttribute("erro", erro);
        } else {
            String nomeOrigem = escolha.opcoes().stream().filter(o -> o.id().equals(origemId))
                    .map(EscolhaArea.OpcaoArea::nome).findFirst().orElse("");
            redirect.addFlashAttribute("sucesso", "Expediente copiado de " + nomeOrigem + " para "
                    + escolha.atual().nome() + ".");
            redirect.addFlashAttribute("sugerirRecalculo", true);
        }
        return "redirect:/admin/expediente?area=" + escolha.atual().id();
    }

    private void preparar(Model model, EscolhaArea.Escolha escolha, List<ExpedienteService.DiaTela> dias,
                          Map<String, String> erros) {
        model.addAttribute("escolha", escolha);
        model.addAttribute("dias", dias);
        model.addAttribute("totalSemana", ExpedienteService.totalSemana(dias));
        model.addAttribute("erros", erros);
        model.addAttribute("menuAtivo", "configuracoes");
        if (!model.containsAttribute("sugerirRecalculo")) {
            model.addAttribute("sugerirRecalculo", false);
        }
    }
}
