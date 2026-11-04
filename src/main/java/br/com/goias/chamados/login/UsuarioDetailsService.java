package br.com.goias.chamados.login;

import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Busca o usuário na tabela "usuario" pelo e-mail digitado no login.
 * A senha é conferida pelo Spring Security usando o BCrypt do SecurityConfig.
 * Usuário inativo é recusado automaticamente (isEnabled = false).
 */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String emailNormalizado = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return usuarioRepository.findByEmail(emailNormalizado)
                .map(UsuarioLogado::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
