package br.com.goias.chamados.dominio.usuario;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Regras do "Usuário" usado para entrar no sistema (ajuste do login, V8). Sem banco, fáceis de testar.
 *
 * Formato: nome.sobrenome
 * - de 3 a 30 caracteres;
 * - só letras minúsculas sem acento, números, ponto e hífen;
 * - começa com letra;
 * - sem ponto/hífen no fim nem dois seguidos.
 * O banco tem o mesmo CHECK (ck_usuario_login_formato, V8).
 *
 * Normalização (vale na tela de login e no cadastro): tira os espaços das pontas, passa para
 * minúsculas e tira os acentos. Assim "  Rafael.Nunes " e "rafaél.nunes" viram "rafael.nunes".
 */
public final class RegrasLogin {

    public static final int MINIMO = 3;
    public static final int MAXIMO = 30;

    /** Login do administrador criado na instalação (V2 + V8). */
    public static final String ADMIN_INICIAL = "admin";

    static final String OBRIGATORIO = "Informe o usuário.";
    static final String CARACTERES = "Use só letras minúsculas sem acento, números, ponto e hífen (ex.: rafael.nunes).";
    static final String COMECO = "O usuário precisa começar com uma letra.";
    static final String CURTO = "O usuário precisa ter pelo menos " + MINIMO + " caracteres.";
    static final String LONGO = "O usuário pode ter no máximo " + MAXIMO + " caracteres.";
    static final String PONTUACAO = "Não termine com ponto ou hífen nem use dois seguidos (ex.: rafael.nunes).";

    private static final Pattern FORMATO = Pattern.compile("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$");
    private static final Pattern SO_PERMITIDOS = Pattern.compile("^[a-z0-9.-]+$");
    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");

    /** Partículas que não contam como sobrenome ("João da Silva" → "joao.silva"). */
    private static final Set<String> PARTICULAS = Set.of("da", "das", "de", "di", "do", "dos", "du", "e");

    private RegrasLogin() {
    }

    /** Sem espaços nas pontas, minúsculo e sem acentos. null vira "". */
    public static String normalizar(String login) {
        if (login == null) {
            return "";
        }
        return semAcento(login.strip()).toLowerCase(Locale.ROOT);
    }

    /** O login (já normalizado) está no formato certo? */
    public static boolean valido(String login) {
        return validar(login) == null;
    }

    /**
     * Confere o formato de um login já normalizado.
     *
     * @return mensagem para mostrar embaixo do campo, ou null se está certo
     */
    public static String validar(String login) {
        if (login == null || login.isEmpty()) {
            return OBRIGATORIO;
        }
        if (!SO_PERMITIDOS.matcher(login).matches()) {
            return CARACTERES;
        }
        if (!Character.isLetter(login.charAt(0))) {
            return COMECO;
        }
        if (login.length() < MINIMO) {
            return CURTO;
        }
        if (login.length() > MAXIMO) {
            return LONGO;
        }
        if (!FORMATO.matcher(login).matches()) {
            return PONTUACAO;
        }
        return null;
    }

    /**
     * Sugestão de login a partir do nome completo: primeiro nome + "." + último sobrenome.
     * <ul>
     *   <li>"Rafael Nunes" → "rafael.nunes"</li>
     *   <li>"José Ribeiro" → "jose.ribeiro" (sem acento)</li>
     *   <li>"Maria Clara de Souza Lima" → "maria.lima" (nome composto: primeiro + último)</li>
     *   <li>"João da Silva" → "joao.silva" (da/de/do/dos/das/e não contam)</li>
     *   <li>"Rafael" → "rafael" (sem sobrenome)</li>
     * </ul>
     * Pode devolver algo fora do formato quando o nome é curto demais (ex.: "Li" → "li");
     * o admin vê o erro e ajusta. Nome vazio → "".
     */
    public static String sugerir(String nome) {
        List<String> palavras = palavras(nome);
        if (palavras.isEmpty()) {
            return "";
        }
        String primeiro = palavras.get(0);
        String sobrenome = null;
        for (int i = palavras.size() - 1; i > 0; i--) {
            if (!PARTICULAS.contains(palavras.get(i))) {
                sobrenome = palavras.get(i);
                break;
            }
        }
        String sugestao = sobrenome == null ? primeiro : primeiro + "." + sobrenome;
        return cortar(sugestao, MAXIMO);
    }

    /**
     * Sugestão que ainda não está em uso: "rafael.nunes", senão "rafael.nunes-2", "rafael.nunes-3"...
     * (mesma regra dos duplicados da V8). Se a sugestão base não estiver no formato, devolve ela mesma.
     */
    public static String sugerirDisponivel(String nome, Predicate<String> emUso) {
        String base = sugerir(nome);
        if (!valido(base)) {
            return base;
        }
        String candidato = base;
        int n = 1;
        while (emUso.test(candidato)) {
            n++;
            String sufixo = "-" + n;
            candidato = cortar(base, MAXIMO - sufixo.length()) + sufixo;
        }
        return candidato;
    }

    // -----------------------------------------------------------------

    /** Palavras do nome só com letras a-z (sem acento, sem apóstrofo; hífen separa palavras). */
    private static List<String> palavras(String nome) {
        List<String> resultado = new ArrayList<>();
        if (nome == null) {
            return resultado;
        }
        String limpo = semAcento(nome).toLowerCase(Locale.ROOT)
                .replaceAll("['’`´]", "")
                .replaceAll("[^a-z]+", " ")
                .strip();
        for (String p : limpo.split(" +")) {
            if (!p.isEmpty()) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    /** Corta no tamanho e tira ponto/hífen que tenham sobrado no fim. */
    private static String cortar(String texto, int tamanho) {
        String cortado = texto.length() > tamanho ? texto.substring(0, tamanho) : texto;
        return cortado.replaceAll("[.-]+$", "");
    }

    private static String semAcento(String texto) {
        return ACENTOS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
    }
}
