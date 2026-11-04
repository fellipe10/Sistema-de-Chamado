package br.com.goias.chamados.config;

import br.com.goias.chamados.login.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

/**
 * Encerra as sessões abertas de um usuário (Etapa 5).
 *
 * Usado ao desativar o usuário, redefinir a senha dele ou mudar e-mail/perfil/área.
 * A sessão é marcada como "expirada" no {@link SessionRegistry}; no próximo clique
 * o Spring Security desloga a pessoa e leva para /login?encerrada (ver {@link SecurityConfig}).
 *
 * O registro é em memória: se o sistema reiniciar, todas as sessões já caem de qualquer jeito.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessoesAtivas {

    private final SessionRegistry sessionRegistry;

    /** @return quantas sessões foram encerradas */
    public int encerrar(Long usuarioId) {
        if (usuarioId == null) {
            return 0;
        }
        int encerradas = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UsuarioLogado usuario && usuarioId.equals(usuario.getId())) {
                for (SessionInformation sessao : sessionRegistry.getAllSessions(principal, false)) {
                    sessao.expireNow();
                    encerradas++;
                }
            }
        }
        if (encerradas > 0) {
            log.info("{} sessão(ões) do usuário {} encerrada(s) pelo administrador.", encerradas, usuarioId);
        }
        return encerradas;
    }

    /** Quantas sessões abertas (não expiradas) o usuário tem agora. */
    public int contar(Long usuarioId) {
        int total = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UsuarioLogado usuario && usuarioId.equals(usuario.getId())) {
                total += sessionRegistry.getAllSessions(principal, false).size();
            }
        }
        return total;
    }
}
