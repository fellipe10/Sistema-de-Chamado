package br.com.goias.chamados.config;

import br.com.goias.chamados.login.UsuarioLogado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Troca de senha obrigatória: enquanto o usuário logado estiver com trocar_senha = true,
 * qualquer página interna leva para /conta/senha.
 *
 * As rotas que NÃO passam por aqui (públicas, estáticas, login/logout, erros e a própria
 * /conta/senha) estão em {@link WebConfig}.
 */
@Component
public class TrocaSenhaObrigatoriaInterceptor implements HandlerInterceptor {

    static final String ROTA_TROCA_SENHA = "/conta/senha";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null
                && autenticacao.getPrincipal() instanceof UsuarioLogado usuario
                && usuario.isTrocarSenha()) {
            response.sendRedirect(request.getContextPath() + ROTA_TROCA_SENHA);
            return false;
        }
        return true;
    }
}
