package br.com.goias.chamados.login;

import br.com.goias.chamados.dominio.usuario.RegrasLogin;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca o usuário na tabela "usuario" pelo campo "Usuário" digitado no login (V8 — antes era o e-mail).
 *
 * O que a pessoa digitou é normalizado antes da busca ({@link RegrasLogin#normalizar}):
 * espaços nas pontas, maiúsculas e acentos não atrapalham ("  Rafael.Nunes " entra como "rafael.nunes").
 *
 * A senha é conferida pelo Spring Security usando o BCrypt do SecurityConfig.
 * Usuário inativo é recusado automaticamente (isEnabled = false).
 * Qualquer falha mostra a mesma mensagem: "Usuário ou senha incorretos."
 */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String digitado) throws UsernameNotFoundException {
        String login = RegrasLogin.normalizar(digitado);
        if (login.isEmpty()) {
            throw new UsernameNotFoundException("Usuário não informado");
        }
        return usuarioRepository.findByLogin(login)
                .map(UsuarioLogado::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
