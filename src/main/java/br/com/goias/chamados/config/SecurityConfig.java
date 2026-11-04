package br.com.goias.chamados.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Regras de acesso do sistema.
 *
 * Público: "/", abertura e consulta de chamado, login, páginas de erro, arquivos estáticos.
 * /admin/**  → só ADMIN
 * /painel/** → GESTOR ou ADMIN
 * Resto      → qualquer usuário logado (a área de cada chamado é conferida nas telas)
 *
 * "/" é público porque decide para onde levar: não logado → /abrir; GESTOR/ADMIN → /painel;
 * ATENDENTE → /atendimento.
 *
 * Login: mensagem única "E-mail ou senha incorretos." para qualquer falha
 * (senha errada, e-mail inexistente ou usuário inativo) — não revela se o e-mail existe.
 * Logout: só por POST com token CSRF (botão "Sair" do menu).
 * Troca de senha obrigatória: {@link TrocaSenhaObrigatoriaInterceptor}.
 *
 * Etapa 5 — sessões registradas: o {@link SessionRegistry} guarda as sessões abertas de cada usuário.
 * Ao desativar alguém ou redefinir a senha, o admin encerra essas sessões ({@link SessoesAtivas});
 * no próximo clique a pessoa cai em /login?encerrada ("Sua sessão foi encerrada pelo administrador.").
 * Sem limite de sessões por usuário (maximumSessions(-1)): pode entrar no computador e no celular.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] ROTAS_PUBLICAS = {
            "/",
            "/abrir/**",
            "/consultar/**",
            "/login",
            "/css/**",
            "/js/**",
            "/img/**",
            "/vendor/**",
            "/favicon.ico",
            "/error",
            "/error/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository) throws Exception {
        http
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/painel/**").hasAnyRole("GESTOR", "ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        .defaultSuccessUrl("/", false)
                        .failureUrl("/login?erro")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout") // com CSRF ligado, o Spring só aceita POST aqui
                        .logoutSuccessUrl("/login?saiu")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .sessionManagement(sessao -> sessao
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())
                        .expiredUrl("/login?encerrada")
                );

        return http.build();
    }

    /**
     * Onde o login fica guardado (na sessão HTTP). É um bean para a tela de troca de senha
     * conseguir atualizar os dados do usuário logado sem pedir login de novo.
     * (Mesma combinação que o Spring Security usa por padrão.)
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    /** Lista das sessões abertas de cada usuário (em memória, zera quando o sistema reinicia). */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Avisa o SessionRegistry quando uma sessão expira ou é encerrada (senão a lista só cresce). */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
