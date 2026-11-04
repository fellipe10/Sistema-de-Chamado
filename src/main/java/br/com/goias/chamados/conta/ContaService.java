package br.com.goias.chamados.conta;

import br.com.goias.chamados.dominio.usuario.Usuario;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Troca de senha do próprio usuário.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContaService {

    static final String SENHA_ATUAL_OBRIGATORIA = "Informe sua senha atual.";
    static final String SENHA_ATUAL_INCORRETA = "Senha atual incorreta.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    /** Resultado: usuário atualizado (quando deu certo) ou os erros por campo. */
    public record Resultado(Usuario usuario, Map<String, String> erros) {
        public boolean sucesso() {
            return erros.isEmpty();
        }
    }

    @Transactional
    public Resultado trocarSenha(Long usuarioId, TrocaSenhaForm form) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("Usuário logado não existe mais: " + usuarioId));

        Map<String, String> erros = new LinkedHashMap<>();
        String atual = form.getSenhaAtual();
        if (atual == null || atual.isEmpty()) {
            erros.put("senhaAtual", SENHA_ATUAL_OBRIGATORIA);
        } else if (!confere(atual, usuario.getSenhaHash())) {
            erros.put("senhaAtual", SENHA_ATUAL_INCORRETA);
        }
        erros.putAll(ValidadorNovaSenha.validar(form.getNovaSenha(), form.getConfirmacao(),
                senha -> confere(senha, usuario.getSenhaHash())));

        if (!erros.isEmpty()) {
            return new Resultado(null, erros);
        }

        usuario.setSenhaHash(passwordEncoder.encode(form.getNovaSenha()));
        usuario.setTrocarSenha(false);
        // a área é usada para montar os dados da sessão de novo (evita LazyInitializationException)
        if (usuario.getArea() != null) {
            usuario.getArea().getNome();
        }
        log.info("Usuário {} trocou a senha.", usuario.getLogin());
        return new Resultado(usuario, Map.of());
    }

    /** Confere a senha digitada com o hash. Senha longa demais para o BCrypt conta como "não confere". */
    private boolean confere(String senha, String hash) {
        try {
            return passwordEncoder.matches(senha, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
