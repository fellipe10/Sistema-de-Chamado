package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.login.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Fila de chamados.
 *
 * GET /atendimento                  → aba "Novos"
 * GET /atendimento?filtro=meus      → "Meus chamados" (link do menu)
 * Outros parâmetros: filtro=novos|meus|andamento|aguardando|historico, busca, prioridade,
 * categoria (id), area (id, só ADMIN), pagina (começa em 1),
 * atendente (id do responsável — atalho da administração ao desativar alguém).
 */
@Controller
@TelaInterna
@RequestMapping("/atendimento")
@RequiredArgsConstructor
public class FilaController {

    private final FilaService filaService;

    @GetMapping
    public String fila(@AuthenticationPrincipal UsuarioLogado usuario,
                       @RequestParam(required = false) String filtro,
                       @RequestParam(required = false) String busca,
                       @RequestParam(required = false) String prioridade,
                       @RequestParam(required = false) String categoria,
                       @RequestParam(required = false) String area,
                       @RequestParam(required = false) String pagina,
                       @RequestParam(required = false) String atendente,
                       Model model) {
        FilaTela tela = filaService.montar(usuario, new FilaService.Parametros(
                filtro, busca, prioridade, numero(categoria), numero(area),
                numero(pagina) == null ? null : (int) Math.min(Integer.MAX_VALUE, numero(pagina)),
                numero(atendente)));
        model.addAttribute("tela", tela);
        model.addAttribute("menuAtivo", tela.aba() == AbaFila.MEUS ? "meus" : "fila");
        return "atendimento/fila";
    }

    /** Parâmetro numérico vindo da URL; texto inválido é ignorado (em vez de erro 400). */
    private static Long numero(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(valor.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
