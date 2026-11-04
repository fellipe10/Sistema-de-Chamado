package br.com.goias.chamados.admin;

import br.com.goias.chamados.config.TelaInterna;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Página inicial de Configurações (GET /admin) — só ADMIN.
 * Um card por cadastro (ícone, nome, descrição curta e quantidade) e o aviso de feriados do próximo ano.
 */
@Controller
@TelaInterna
@RequiredArgsConstructor
public class AdminController {

    private final AdminService service;

    @GetMapping("/admin")
    public String inicio(Model model) {
        model.addAttribute("cards", service.cards());
        model.addAttribute("avisoFeriados", service.avisoFeriados());
        model.addAttribute("menuAtivo", "configuracoes");
        return "admin/inicio";
    }
}
