package br.com.goias.chamados.admin.usuarios;

import br.com.goias.chamados.dominio.usuario.Perfil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasUsuarioTest {

    /** Atalho: login válido, sem login atual (criação). */
    private static java.util.Map<String, String> validar(String nome, String email, Perfil perfil, Long area, boolean ativa) {
        return RegrasUsuario.validarCampos(nome, "rafael.nunes", null, email, perfil, area, ativa);
    }

    // ---------- área obrigatória por perfil ----------

    @Test
    @DisplayName("Atendente e gestor sem área: recusado")
    void areaObrigatoriaParaAtendenteEGestor() {
        assertThat(validar("Rafael", "r@goias.com.br", Perfil.ATENDENTE, null, false))
                .containsEntry("areaId", RegrasUsuario.AREA_OBRIGATORIA);
        assertThat(validar("Carla", "c@goias.com.br", Perfil.GESTOR, null, false))
                .containsEntry("areaId", RegrasUsuario.AREA_OBRIGATORIA);
    }

    @Test
    @DisplayName("Atendente com área inativa ou inexistente: recusado")
    void areaInativa() {
        assertThat(validar("Rafael", "r@goias.com.br", Perfil.ATENDENTE, 9L, false))
                .containsEntry("areaId", RegrasUsuario.AREA_INVALIDA);
    }

    @Test
    @DisplayName("Admin sem área é aceito; atendente com área ativa também")
    void adminSemArea() {
        assertThat(validar("Ana", "ana@goias.com.br", Perfil.ADMIN, null, false)).isEmpty();
        assertThat(validar("Rafael", "r@goias.com.br", Perfil.ATENDENTE, 1L, true)).isEmpty();
        assertThat(RegrasUsuario.precisaDeArea(Perfil.ADMIN)).isFalse();
        assertThat(RegrasUsuario.precisaDeArea(Perfil.ATENDENTE)).isTrue();
        assertThat(RegrasUsuario.precisaDeArea(Perfil.GESTOR)).isTrue();
    }

    @Test
    @DisplayName("Nome, usuário e perfil obrigatórios; e-mail opcional, mas com formato válido se preenchido")
    void camposObrigatorios() {
        var erros = RegrasUsuario.validarCampos("  ", "  ", null, "", null, null, false);
        assertThat(erros).containsEntry("nome", RegrasUsuario.NOME_OBRIGATORIO)
                .containsEntry("login", "Informe o usuário.")
                .containsEntry("perfil", RegrasUsuario.PERFIL_OBRIGATORIO)
                .doesNotContainKey("email");
        assertThat(validar("A", "rafael@", Perfil.ADMIN, null, false))
                .containsEntry("email", RegrasUsuario.EMAIL_INVALIDO);
        assertThat(validar("A", "ra fa@x.com", Perfil.ADMIN, null, false))
                .containsEntry("email", RegrasUsuario.EMAIL_INVALIDO);
        assertThat(validar("A".repeat(121), "a@x.com", Perfil.ADMIN, null, false))
                .containsEntry("nome", RegrasUsuario.NOME_LONGO);
    }

    @Test
    @DisplayName("E-mail vazio ou só espaços é aceito (vira null)")
    void emailOpcional() {
        assertThat(validar("Ana", null, Perfil.ADMIN, null, false)).isEmpty();
        assertThat(validar("Ana", "   ", Perfil.ADMIN, null, false)).isEmpty();
        assertThat(RegrasUsuario.normalizarEmail("   ")).isNull();
        assertThat(RegrasUsuario.normalizarEmail("")).isNull();
    }

    @Test
    @DisplayName("Usuário (login): formato conferido depois de normalizar (maiúsculas, acentos e espaços nas pontas)")
    void login() {
        assertThat(RegrasUsuario.validarCampos("Ana", "  Ana.Souza ", null, null, Perfil.ADMIN, null, false)).isEmpty();
        assertThat(RegrasUsuario.validarCampos("Ana", "anä.souza", null, null, Perfil.ADMIN, null, false)).isEmpty();
        assertThat(RegrasUsuario.normalizarLogin("  Ana.Souza ")).isEqualTo("ana.souza");
        assertThat(RegrasUsuario.validarCampos("Ana", "ana souza", null, null, Perfil.ADMIN, null, false))
                .containsKey("login");
        assertThat(RegrasUsuario.validarCampos("Ana", "ana..souza", null, null, Perfil.ADMIN, null, false))
                .containsKey("login");
        assertThat(RegrasUsuario.validarCampos("Ana", "an", null, null, Perfil.ADMIN, null, false))
                .containsKey("login");
    }

    @Test
    @DisplayName("Logins dos usuários de teste são reservados, exceto para quem já tem")
    void loginReservado() {
        assertThat(RegrasUsuario.validarCampos("Rafael", "atendente.ti", null, null, Perfil.ATENDENTE, 1L, true))
                .containsEntry("login", RegrasUsuario.LOGIN_RESERVADO);
        assertThat(RegrasUsuario.validarCampos("Rafael", " Gestor.Man ", null, null, Perfil.GESTOR, 1L, true))
                .containsEntry("login", RegrasUsuario.LOGIN_RESERVADO);
        // Editar o próprio usuário de teste (login não muda) continua funcionando no dev
        assertThat(RegrasUsuario.validarCampos("Rafael Nunes", "atendente.ti", "atendente.ti", null,
                Perfil.ATENDENTE, 1L, true)).isEmpty();
        assertThat(RegrasUsuario.loginReservado("atendente.ti")).isTrue();
        assertThat(RegrasUsuario.loginReservado("rafael.nunes")).isFalse();
    }

    @Test
    @DisplayName("Mudar usuário, perfil ou área muda o acesso (encerra sessões); só o e-mail, não")
    void mudouAcesso() {
        assertThat(RegrasUsuario.mudouAcesso("rafael.nunes", " Rafael.Nunes ", Perfil.ATENDENTE, Perfil.ATENDENTE, 1L, 1L))
                .isFalse();
        assertThat(RegrasUsuario.mudouAcesso("rafael.nunes", "rafael.n", Perfil.ATENDENTE, Perfil.ATENDENTE, 1L, 1L))
                .isTrue();
        assertThat(RegrasUsuario.mudouAcesso("rafael.nunes", "rafael.nunes", Perfil.ATENDENTE, Perfil.GESTOR, 1L, 1L))
                .isTrue();
        assertThat(RegrasUsuario.mudouAcesso("rafael.nunes", "rafael.nunes", Perfil.ATENDENTE, Perfil.ATENDENTE, 1L, 2L))
                .isTrue();
    }

    @Test
    @DisplayName("E-mail vai para minúsculas e nome sem espaços sobrando")
    void normalizacao() {
        assertThat(RegrasUsuario.normalizarEmail("  Marcos.Tavares@GoiasEC.com.BR ")).isEqualTo("marcos.tavares@goiasec.com.br");
        assertThat(RegrasUsuario.normalizarNome("  Marcos   Tavares ")).isEqualTo("Marcos Tavares");
        assertThat(RegrasUsuario.normalizarEmail(null)).isNull();
    }

    @Test
    @DisplayName("Admin não desativa a si mesmo")
    void autoDesativacao() {
        assertThat(RegrasUsuario.protecao(true, Perfil.ADMIN, true, Perfil.ADMIN, false, 3))
                .isEqualTo(RegrasUsuario.NAO_DESATIVAR_A_SI);
    }

    @Test
    @DisplayName("Admin não muda o próprio perfil (nem para gestor, nem para atendente)")
    void autoRebaixamento() {
        assertThat(RegrasUsuario.protecao(true, Perfil.ADMIN, true, Perfil.GESTOR, true, 3))
                .isEqualTo(RegrasUsuario.NAO_MUDAR_PROPRIO_PERFIL);
        assertThat(RegrasUsuario.protecao(true, Perfil.ADMIN, true, Perfil.ADMIN, true, 0)).isNull();
    }

    @Test
    @DisplayName("Último admin ativo: não pode ser desativado nem rebaixado")
    void ultimoAdmin() {
        assertThat(RegrasUsuario.protecao(false, Perfil.ADMIN, true, Perfil.ADMIN, false, 0))
                .isEqualTo(RegrasUsuario.ULTIMO_ADMIN);
        assertThat(RegrasUsuario.protecao(false, Perfil.ADMIN, true, Perfil.ATENDENTE, true, 0))
                .isEqualTo(RegrasUsuario.ULTIMO_ADMIN);
    }

    @Test
    @DisplayName("Com outro admin ativo, pode desativar/rebaixar; mexer em atendente nunca esbarra na regra")
    void comOutroAdmin() {
        assertThat(RegrasUsuario.protecao(false, Perfil.ADMIN, true, Perfil.ADMIN, false, 1)).isNull();
        assertThat(RegrasUsuario.protecao(false, Perfil.ADMIN, true, Perfil.GESTOR, true, 1)).isNull();
        assertThat(RegrasUsuario.protecao(false, Perfil.ATENDENTE, true, Perfil.ATENDENTE, false, 0)).isNull();
        // admin inativo sendo transformado em gestor: não conta como "último admin ativo"
        assertThat(RegrasUsuario.protecao(false, Perfil.ADMIN, false, Perfil.GESTOR, false, 0)).isNull();
        // promover a admin é sempre permitido
        assertThat(RegrasUsuario.protecao(false, Perfil.GESTOR, true, Perfil.ADMIN, true, 0)).isNull();
    }

    @Test
    @DisplayName("Mudou de área? (admin sem área = nenhuma área)")
    void mudouArea() {
        assertThat(RegrasUsuario.mudouArea(1L, 1L)).isFalse();
        assertThat(RegrasUsuario.mudouArea(1L, 2L)).isTrue();
        assertThat(RegrasUsuario.mudouArea(1L, null)).isTrue();
        assertThat(RegrasUsuario.mudouArea(null, null)).isFalse();
    }
}
