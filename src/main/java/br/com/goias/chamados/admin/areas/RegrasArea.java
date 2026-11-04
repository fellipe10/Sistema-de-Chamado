package br.com.goias.chamados.admin.areas;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras da sigla da área (Etapa 5) — sem banco, fáceis de testar.
 *
 * - de 2 a 10 letras maiúsculas, sem acento, sem número nem espaço (mesma regra do CHECK ck_area_sigla
 *   e da regex do código do chamado ÁREA-ANO-XXXX);
 * - única;
 * - não muda depois que existir chamado na área (o código dos chamados antigos usa a sigla).
 */
public final class RegrasArea {

    public static final int NOME_MAXIMO = 80;

    static final String SIGLA_OBRIGATORIA = "Informe a sigla.";
    static final String SIGLA_INVALIDA = "Use de 2 a 10 letras, sem acento, número ou espaço (ex.: RH, LIMPEZA).";
    static final String SIGLA_REPETIDA = "Já existe uma área com esta sigla.";
    static final String SIGLA_TRAVADA = "A sigla não pode mudar: já existem chamados com ela no código.";

    private static final Pattern FORMATO = Pattern.compile("^[A-Z]{2,10}$");

    private RegrasArea() {
    }

    /** " rh " → "RH" (null continua null). */
    public static String normalizarSigla(String sigla) {
        return sigla == null ? null : sigla.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * @param outrasSiglas siglas das outras áreas (sem a própria, na edição)
     * @return mensagem de erro, ou null se a sigla serve
     */
    public static String validarSigla(String sigla, Collection<String> outrasSiglas) {
        String s = normalizarSigla(sigla);
        if (s == null || s.isEmpty()) {
            return SIGLA_OBRIGATORIA;
        }
        if (!FORMATO.matcher(s).matches()) {
            return SIGLA_INVALIDA;
        }
        if (outrasSiglas.stream().anyMatch(o -> o.equalsIgnoreCase(s))) {
            return SIGLA_REPETIDA;
        }
        return null;
    }

    /**
     * Ao editar: a sigla só pode mudar enquanto a área não tiver nenhum chamado.
     *
     * @return mensagem de erro, ou null se pode
     */
    public static String podeMudarSigla(String siglaAtual, String siglaNova, long chamadosNaArea) {
        String nova = normalizarSigla(siglaNova);
        if (nova == null || nova.equals(siglaAtual)) {
            return null;
        }
        return chamadosNaArea > 0 ? SIGLA_TRAVADA : null;
    }
}
