package br.com.goias.chamados.admin.usuarios;

import br.com.goias.chamados.dominio.usuario.Perfil;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Regras do cadastro de usuários (sem banco, fáceis de testar).
 *
 * - nome e e-mail obrigatórios; e-mail com formato válido, sempre em minúsculas;
 * - ATENDENTE e GESTOR precisam de área; ADMIN fica sem área;
 * - o admin não pode desativar a própria conta nem mudar o próprio perfil;
 * - o sistema nunca pode ficar sem nenhum ADMIN ativo.
 */
public final class RegrasUsuario {

    public static final int NOME_MAXIMO = 120;
    public static final int EMAIL_MAXIMO = 150;

    static final String NOME_OBRIGATORIO = "Informe o nome.";
    static final String NOME_LONGO = "O nome pode ter no máximo " + NOME_MAXIMO + " caracteres.";
    static final String EMAIL_OBRIGATORIO = "Informe o e-mail.";
    static final String EMAIL_INVALIDO = "Informe um e-mail válido.";
    static final String EMAIL_LONGO = "O e-mail pode ter no máximo " + EMAIL_MAXIMO + " caracteres.";
    static final String EMAIL_REPETIDO = "Já existe um usuário com este e-mail.";
    static final String PERFIL_OBRIGATORIO = "Escolha o perfil.";
    static final String AREA_OBRIGATORIA = "Atendente e gestor precisam de uma área.";
    static final String AREA_INVALIDA = "Escolha uma área ativa.";
    static final String NAO_DESATIVAR_A_SI = "Você não pode desativar a sua própria conta.";
    static final String NAO_MUDAR_PROPRIO_PERFIL = "Você não pode mudar o seu próprio perfil.";
    static final String ULTIMO_ADMIN = "O sistema precisa ter pelo menos um administrador ativo. "
            + "Cadastre ou reative outro administrador antes.";

    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private RegrasUsuario() {
    }

    /** E-mail sem espaços nas pontas e em minúsculas (null continua null). */
    public static String normalizarEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Nome sem espaços sobrando (null continua null). */
    public static String normalizarNome(String nome) {
        return nome == null ? null : nome.strip().replaceAll("\\s+", " ");
    }

    /**
     * Confere os campos do formulário.
     *
     * @param areaAtiva true se a área escolhida existe e está ativa (ignorado quando não há área)
     * @return erros por campo (nome, email, perfil, areaId); vazio = ok
     */
    public static Map<String, String> validarCampos(String nome, String email, Perfil perfil,
                                                    Long areaId, boolean areaAtiva) {
        Map<String, String> erros = new LinkedHashMap<>();
        String n = normalizarNome(nome);
        if (n == null || n.isEmpty()) {
            erros.put("nome", NOME_OBRIGATORIO);
        } else if (n.length() > NOME_MAXIMO) {
            erros.put("nome", NOME_LONGO);
        }

        String e = normalizarEmail(email);
        if (e == null || e.isEmpty()) {
            erros.put("email", EMAIL_OBRIGATORIO);
        } else if (e.length() > EMAIL_MAXIMO) {
            erros.put("email", EMAIL_LONGO);
        } else if (!FORMATO_EMAIL.matcher(e).matches()) {
            erros.put("email", EMAIL_INVALIDO);
        }

        if (perfil == null) {
            erros.put("perfil", PERFIL_OBRIGATORIO);
        } else if (precisaDeArea(perfil)) {
            if (areaId == null) {
                erros.put("areaId", AREA_OBRIGATORIA);
            } else if (!areaAtiva) {
                erros.put("areaId", AREA_INVALIDA);
            }
        }
        return erros;
    }

    /** ATENDENTE e GESTOR trabalham numa área; ADMIN vê todas. */
    public static boolean precisaDeArea(Perfil perfil) {
        return perfil == Perfil.ATENDENTE || perfil == Perfil.GESTOR;
    }

    /**
     * Proteções ao alterar perfil ou situação (ativo) de um usuário.
     *
     * @param ehOProprio        o admin está mexendo na própria conta
     * @param perfilAtual       perfil gravado hoje
     * @param ativoAtual        situação gravada hoje
     * @param perfilNovo        perfil depois da alteração
     * @param ativoNovo         situação depois da alteração
     * @param outrosAdminsAtivos quantos OUTROS administradores ativos existem (sem contar este usuário)
     * @return mensagem de recusa, ou null se pode
     */
    public static String protecao(boolean ehOProprio, Perfil perfilAtual, boolean ativoAtual,
                                  Perfil perfilNovo, boolean ativoNovo, long outrosAdminsAtivos) {
        if (ehOProprio && ativoAtual && !ativoNovo) {
            return NAO_DESATIVAR_A_SI;
        }
        if (ehOProprio && perfilAtual != perfilNovo) {
            return NAO_MUDAR_PROPRIO_PERFIL;
        }
        boolean eraAdminAtivo = perfilAtual == Perfil.ADMIN && ativoAtual;
        boolean continuaAdminAtivo = perfilNovo == Perfil.ADMIN && ativoNovo;
        if (eraAdminAtivo && !continuaAdminAtivo && outrosAdminsAtivos <= 0) {
            return ULTIMO_ADMIN;
        }
        return null;
    }

    /** A área mudou? (ADMIN sem área conta como "nenhuma área"). */
    public static boolean mudouArea(Long areaAtual, Long areaNova) {
        return !Objects.equals(areaAtual, areaNova);
    }
}
