package br.com.goias.chamados.conta;

import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.dominio.usuario.Perfil;
import br.com.goias.chamados.login.UsuarioLogado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Tela "Trocar senha" (/conta/senha).
 *
 * - Obrigatória quando trocar_senha = true (o TrocaSenhaObrigatoriaInterceptor manda para cá).
 * - Também acessível pelo menu (ícone de chave junto do nome).
 * Depois de trocar: trocar_senha = false e volta para /atendimento.
 */
@Controller
@TelaInterna
@RequestMapping("/conta")
@RequiredArgsConstructor
public class ContaController {

    static final String MENSAGEM_SUCESSO = "Senha alterada com sucesso.";

    private final ContaService contaService;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/senha")
    public String formulario(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        prepararTela(usuario, new TrocaSenhaForm(), model);
        return "conta/senha";
    }

    @PostMapping("/senha")
    public String trocar(@AuthenticationPrincipal UsuarioLogado usuario,
                         @ModelAttribute("form") TrocaSenhaForm form,
                         HttpServletRequest request, HttpServletResponse response,
                         Model model, RedirectAttributes redirect) {
        ContaService.Resultado resultado = contaService.trocarSenha(usuario.getId(), form);
        if (!resultado.sucesso()) {
            // senhas nunca voltam preenchidas para a tela
            prepararTela(usuario, new TrocaSenhaForm(), model);
            model.addAttribute("erros", resultado.erros());
            return "conta/senha";
        }

        atualizarUsuarioDaSessao(new UsuarioLogado(resultado.usuario()), request, response);
        redirect.addFlashAttribute("sucesso", MENSAGEM_SUCESSO);
        // Etapa 4: gestor e admin começam pelo painel; atendente pela fila (direto, para a mensagem não se perder)
        Perfil perfil = resultado.usuario().getPerfil();
        return perfil == Perfil.GESTOR || perfil == Perfil.ADMIN ? "redirect:/painel" : "redirect:/atendimento";
    }

    private void prepararTela(UsuarioLogado usuario, TrocaSenhaForm form, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("obrigatoria", usuario.isTrocarSenha());
        model.addAttribute("menuAtivo", "conta");
        model.addAttribute("tamanhoMinimo", ValidadorNovaSenha.TAMANHO_MINIMO);
        if (!model.containsAttribute("erros")) {
            model.addAttribute("erros", java.util.Map.of());
        }
    }

    /** Troca os dados guardados na sessão (o objeto UsuarioLogado é imutável). */
    private void atualizarUsuarioDaSessao(UsuarioLogado atualizado,
                                          HttpServletRequest request, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken novaAutenticacao =
                UsernamePasswordAuthenticationToken.authenticated(atualizado, null, atualizado.getAuthorities());
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(novaAutenticacao);
        SecurityContextHolder.setContext(contexto);
        securityContextRepository.saveContext(contexto, request, response);
    }
}
