package br.com.goias.chamados.config;

import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.usuario.Perfil;
import br.com.goias.chamados.dominio.usuario.Usuario;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SÓ NO PERFIL DEV: cria usuários de teste ao subir a aplicação, se ainda não existirem.
 * Nunca vai para migration (não existe em produção).
 *
 * Todos com a senha "Teste@2026" e sem troca obrigatória de senha.
 * Se o usuário já existir (mesmo e-mail), nada é alterado.
 *
 * Etapa 5 — trava extra: se a variável de ambiente CHAMADOS_DB_URL existir (sinal de que é o servidor,
 * ver application-prod.yml), NÃO cria nada, mesmo que alguém suba com o perfil dev por engano.
 * Em produção, {@link VerificacaoProducao} ainda desativa esses e-mails se aparecerem.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class UsuariosTesteDev implements ApplicationRunner {

    static final String SENHA_TESTE = "Teste@2026";
    static final String VARIAVEL_DO_SERVIDOR = "CHAMADOS_DB_URL";

    record UsuarioTeste(String email, String nome, Perfil perfil, String siglaArea) {
    }

    static final List<UsuarioTeste> USUARIOS = List.of(
            new UsuarioTeste("atendente.ti@chamados.local", "Rafael Nunes", Perfil.ATENDENTE, "TI"),
            new UsuarioTeste("gestor.ti@chamados.local", "Carla Mendes", Perfil.GESTOR, "TI"),
            new UsuarioTeste("atendente.man@chamados.local", "José Ribeiro", Perfil.ATENDENTE, "MAN"),
            new UsuarioTeste("gestor.man@chamados.local", "Paula Teixeira", Perfil.GESTOR, "MAN"));

    private final UsuarioRepository usuarioRepository;
    private final AreaRepository areaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (System.getenv(VARIAVEL_DO_SERVIDOR) != null) {
            log.warn("[dev] Variável {} definida (servidor): usuários de teste NÃO serão criados. "
                    + "Em produção use --spring.profiles.active=prod.", VARIAVEL_DO_SERVIDOR);
            return;
        }
        for (UsuarioTeste teste : USUARIOS) {
            if (usuarioRepository.existsByEmail(teste.email())) {
                continue;
            }
            Area area = areaRepository.findBySigla(teste.siglaArea()).orElse(null);
            if (area == null) {
                log.warn("[dev] Área {} não encontrada; usuário de teste {} não foi criado.",
                        teste.siglaArea(), teste.email());
                continue;
            }
            Usuario usuario = new Usuario();
            usuario.setEmail(teste.email());
            usuario.setNome(teste.nome());
            usuario.setPerfil(teste.perfil());
            usuario.setArea(area);
            usuario.setAtivo(true);
            usuario.setTrocarSenha(false);
            usuario.setSenhaHash(passwordEncoder.encode(SENHA_TESTE));
            usuarioRepository.save(usuario);
            log.info("[dev] Usuário de teste criado: {} ({} · {})", teste.email(), teste.perfil(), teste.siglaArea());
        }
    }
}
