package br.com.goias.chamados.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Perfil PROD (Etapa 5): antes de qualquer coisa subir, confere se as variáveis de ambiente obrigatórias
 * existem. Se faltar alguma, para com uma mensagem clara em português (em vez de um erro técnico do banco).
 *
 * Registrada em META-INF/spring.factories (roda antes dos beans do Spring).
 */
public class ConferenciaVariaveisProducao implements EnvironmentPostProcessor {

    static final List<String> OBRIGATORIAS = List.of(
            "CHAMADOS_DB_URL", "CHAMADOS_DB_USER", "CHAMADOS_DB_PASSWORD", "CHAMADOS_ANEXOS_DIR");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment ambiente, SpringApplication aplicacao) {
        if (!ambiente.matchesProfiles("prod")) {
            return;
        }
        List<String> faltando = faltando(ambiente::getProperty);
        if (!faltando.isEmpty()) {
            throw new IllegalStateException("\n\n*** Perfil prod: faltam as variáveis de ambiente " + String.join(", ", faltando)
                    + ".\n*** Defina todas antes de subir o sistema (ver claude/guia-implantacao.md, passo das variáveis).\n");
        }
        String url = ambiente.getProperty("CHAMADOS_DB_URL");
        if (url != null && !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("\n\n*** CHAMADOS_DB_URL precisa começar com jdbc:postgresql:// "
                    + "(ex.: jdbc:postgresql://localhost:5432/chamados).\n");
        }
    }

    /** Variáveis obrigatórias vazias ou ausentes (regra pura, testada em ConferenciaVariaveisProducaoTest). */
    static List<String> faltando(Function<String, String> ler) {
        List<String> faltando = new ArrayList<>();
        for (String nome : OBRIGATORIAS) {
            String valor = ler.apply(nome);
            if (valor == null || valor.isBlank()) {
                faltando.add(nome);
            }
        }
        return faltando;
    }
}
