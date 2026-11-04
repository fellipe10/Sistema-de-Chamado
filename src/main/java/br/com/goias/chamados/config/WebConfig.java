package br.com.goias.chamados.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configurações do Spring MVC.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TrocaSenhaObrigatoriaInterceptor trocaSenhaObrigatoria;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Vale para tudo, menos o que é público ou necessário para trocar a senha / sair.
        registry.addInterceptor(trocaSenhaObrigatoria)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/abrir", "/abrir/**",
                        "/consultar", "/consultar/**",
                        "/login", "/logout",
                        TrocaSenhaObrigatoriaInterceptor.ROTA_TROCA_SENHA,
                        "/error", "/error/**",
                        "/css/**", "/js/**", "/img/**", "/vendor/**", "/favicon.ico");
    }
}
