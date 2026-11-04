package br.com.goias.chamados.config;

import br.com.goias.chamados.dominio.usuario.RegrasLogin;
import br.com.goias.chamados.dominio.usuario.Usuario;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SÓ NO PERFIL PROD (Etapa 5): conferências de segurança ao subir.
 *
 * 1. Usuários de teste do perfil dev (logins atendente.ti, gestor.ti, atendente.man, gestor.man —
 *    senha conhecida "Teste@2026") nunca deveriam existir no servidor. Se existirem (ex.: alguém subiu com
 *    o perfil dev apontando para o banco de produção), são DESATIVADOS na hora, com aviso no log.
 *    Desde a V8 a identificação é pelo login (não mais pelo e-mail).
 * 2. Avisa no log enquanto o admin inicial (usuário "admin") ainda estiver com a senha da instalação.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class VerificacaoProducao implements ApplicationRunner {

    static final String ADMIN_INICIAL = RegrasLogin.ADMIN_INICIAL;

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (UsuariosTesteDev.UsuarioTeste teste : UsuariosTesteDev.USUARIOS) {
            usuarioRepository.findByLogin(teste.login()).filter(Usuario::isAtivo).ifPresent(u -> {
                u.setAtivo(false);
                log.error("[prod] Usuário de TESTE encontrado e DESATIVADO: {}. Ele só deve existir no perfil dev.",
                        u.getLogin());
            });
        }
        usuarioRepository.findByLogin(ADMIN_INICIAL)
                .filter(u -> u.isAtivo() && u.isTrocarSenha())
                .ifPresent(u -> log.warn("[prod] O admin inicial (usuário \"{}\") ainda não trocou a senha da instalação. "
                        + "Entre no sistema com o usuário \"{}\" e troque agora (ver guia de implantação).",
                        ADMIN_INICIAL, ADMIN_INICIAL));
        log.info("[prod] Conferência de produção concluída.");
    }
}
