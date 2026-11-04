package br.com.goias.chamados.admin.configuracoes;

import br.com.goias.chamados.config.ParametrosSistema;
import br.com.goias.chamados.config.TelaInterna;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Configurações gerais (só ADMIN): ramais, rodapé público e dias do fechamento automático.
 *
 * GET  /admin/configuracoes
 * POST /admin/configuracoes
 */
@Controller
@TelaInterna
@RequestMapping("/admin/configuracoes")
@RequiredArgsConstructor
public class ConfiguracoesController {

    private final ConfiguracoesService service;
    private final ParametrosSistema parametros;

    @GetMapping
    public String tela(Model model) {
        preparar(model, service.atual(), Map.of());
        return "admin/configuracoes";
    }

    @PostMapping
    public String salvar(@RequestParam(required = false) String ramalTi,
                         @RequestParam(required = false) String ramalManutencao,
                         @RequestParam(required = false) String rodape,
                         @RequestParam(required = false) String diasFechamento,
                         Model model, RedirectAttributes redirect) {
        ConfiguracoesService.Form form = new ConfiguracoesService.Form(ramalTi, ramalManutencao, rodape, diasFechamento);
        Map<String, String> erros = service.salvar(form);
        if (!erros.isEmpty()) {
            preparar(model, form, erros);
            return "admin/configuracoes";
        }
        redirect.addFlashAttribute("sucesso", "Configurações salvas. O rodapé público já está atualizado.");
        return "redirect:/admin/configuracoes";
    }

    private void preparar(Model model, ConfiguracoesService.Form form, Map<String, String> erros) {
        model.addAttribute("form", form);
        model.addAttribute("erros", erros);
        model.addAttribute("previaRodape", parametros.rodapePublico());
        model.addAttribute("diasPadrao", parametros.diasFechamentoPadrao());
        model.addAttribute("diasEmUso", parametros.diasFechamentoAutomatico());
        model.addAttribute("rodapeGenerico", ParametrosSistema.RODAPE_GENERICO);
        model.addAttribute("menuAtivo", "configuracoes");
    }
}
