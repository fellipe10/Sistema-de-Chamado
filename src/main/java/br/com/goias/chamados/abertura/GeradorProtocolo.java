package br.com.goias.chamados.abertura;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Gera o código do chamado no formato ÁREA-ANO-XXXX (ex.: TI-2026-7K3F).
 *
 * Os 4 caracteres finais são sorteados com SecureRandom a partir de um alfabeto
 * SEM caracteres ambíguos (sem 0/O, 1/I/L), para a pessoa não errar ao digitar.
 * O formato bate com o CHECK ck_chamado_protocolo do banco.
 */
@Component
public class GeradorProtocolo {

    /** Letras e números permitidos na parte aleatória (31 caracteres). */
    public static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    /** Quantidade de caracteres aleatórios. */
    public static final int TAMANHO_ALEATORIO = 4;

    /** Mesma regex do CHECK ck_chamado_protocolo (V1__criar_tabelas.sql). */
    public static final Pattern FORMATO = Pattern.compile("^[A-Z]{2,10}-[0-9]{4}-[2-9A-HJKMNP-Z]{4}$");

    private final Random random;

    public GeradorProtocolo() {
        this(new SecureRandom());
    }

    /** Usado nos testes para passar um gerador previsível. */
    GeradorProtocolo(Random random) {
        this.random = random;
    }

    /**
     * @param siglaArea sigla da área (ex.: "TI", "MAN")
     * @param ano       ano com 4 dígitos (ex.: 2026)
     */
    public String gerar(String siglaArea, int ano) {
        if (siglaArea == null || !siglaArea.matches("[A-Z]{2,10}")) {
            throw new IllegalArgumentException("Sigla de área inválida: " + siglaArea);
        }
        if (ano < 1000 || ano > 9999) {
            throw new IllegalArgumentException("Ano inválido: " + ano);
        }
        StringBuilder codigo = new StringBuilder(siglaArea).append('-').append(ano).append('-');
        for (int i = 0; i < TAMANHO_ALEATORIO; i++) {
            codigo.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    public static boolean formatoValido(String protocolo) {
        return protocolo != null && FORMATO.matcher(protocolo).matches();
    }
}
