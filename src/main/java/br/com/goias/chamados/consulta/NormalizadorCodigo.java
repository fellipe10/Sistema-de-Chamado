package br.com.goias.chamados.consulta;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Arruma o código digitado na consulta pública para o formato gravado no banco.
 *
 * Aceita: espaços em qualquer lugar, letras minúsculas e o código sem hífens.
 *   " ti-2026-7k3f " → "TI-2026-7K3F"
 *   "TI20267K3F"     → "TI-2026-7K3F"
 *   "man 2026 q9a2"  → "MAN-2026-Q9A2"
 * Qualquer coisa fora do formato ÁREA-ANO-XXXX → vazio (a tela mostra "não encontramos").
 */
public final class NormalizadorCodigo {

    /** Tamanho máximo aceito antes de normalizar (evita processar textos enormes). */
    static final int TAMANHO_MAXIMO_ENTRADA = 40;

    /** Sigla (2–10 letras) + ano (4 dígitos) + 4 caracteres sem ambíguos, tudo junto. */
    private static final Pattern COMPACTO = Pattern.compile("^([A-Z]{2,10})([0-9]{4})([2-9A-HJKMNP-Z]{4})$");

    private NormalizadorCodigo() {
    }

    public static Optional<String> normalizar(String digitado) {
        if (digitado == null || digitado.length() > TAMANHO_MAXIMO_ENTRADA) {
            return Optional.empty();
        }
        String compacto = digitado
                .replaceAll("[\\s\\u00A0]", "")          // espaços (inclusive o "não separável")
                .replaceAll("[-\\u2010-\\u2015]", "")    // hífen comum e travessões que o Word/WhatsApp colocam
                .toUpperCase(Locale.ROOT);
        Matcher m = COMPACTO.matcher(compacto);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(m.group(1) + "-" + m.group(2) + "-" + m.group(3));
    }
}
