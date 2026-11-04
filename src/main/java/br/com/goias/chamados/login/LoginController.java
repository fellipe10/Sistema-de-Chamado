package br.com.goias.chamados.login;

import br.com.goias.chamados.dominio.usuario.Perfil;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tela de login e página inicial ("/").
 *
 * Mensagens da tela de login:
 * - /login?erro → "E-mail ou senha incorretos." (vale também para usuário inativo)
 * - /login?saiu → "Você saiu do sistema."
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(Authentication authentication, Model model) {
        if (estaLogado(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("menuAtivo", "login");
        return "login/login";
    }

    /**
     * Página inicial: decide por perfil.
     * - Não logado → formulário público de abertura.
     * - GESTOR e ADMIN → painel do gestor (Etapa 4).
     * - ATENDENTE → fila de atendimento.
     * Se o usuário precisar trocar a senha, o TrocaSenhaObrigatoriaInterceptor leva antes para /conta/senha.
     */
    @GetMapping("/")
    public String inicio(Authentication authentication) {
        if (!estaLogado(authentication)) {
            return "redirect:/abrir";
        }
        if (authentication.getPrincipal() instanceof UsuarioLogado usuario
                && (usuario.getPerfil() == Perfil.GESTOR || usuario.getPerfil() == Perfil.ADMIN)) {
            return "redirect:/painel";
        }
        return "redirect:/atendimento";
    }

    private boolean estaLogado(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
