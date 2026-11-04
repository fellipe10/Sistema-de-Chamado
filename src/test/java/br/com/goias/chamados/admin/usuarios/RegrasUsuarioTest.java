package br.com.goias.chamados.admin.usuarios;

import br.com.goias.chamados.dominio.usuario.Perfil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasUsuarioTest {

    // ---------- área obrigatória por perfil ----------

    @Test
    @DisplayName("Atendente e gestor sem área: recusado")
    void areaObrigatoriaParaAtendenteEGestor() {
        assertThat(RegrasUsuario.validarCampos("Rafael", "r@goias.com.br", Perfil.ATENDENTE, null, false))
                .containsEntry("areaId", RegrasUsuario.AREA_OBRIGATORIA);
        assertThat(RegrasUsuario.validarCampos("Carla", "c@goias.com.br", Perfil.GESTOR, null, false))
                .containsEntry("areaId", RegrasUsuario.AREA_OBRIGATORIA);
    }

    @Test
    @DisplayName("Atendente com área inativa ou inexistente: recusado")
    void areaInativa() {
        assertThat(RegrasUsuario.validarCampos("Rafael", "r@goias.com.br", Perfil.ATENDENTE, 9L, false))
                .containsEntry("areaId", RegrasUsuario.AREA_INVALIDA);
    }

    @Test
    @DisplayName("Admin sem área é aceito; atendente com área ativa também")
    void adminSemArea() {
        assertThat(RegrasUsuario.validarCampos("Ana", "ana@goias.com.br", Perfil.ADMIN, null, false)).isEmpty();
        assertThat(RegrasUsuario.validarCampos("Rafael", "r@goias.com.br", Perfil.ATENDENTE, 1L, true)).isEmpty();
        assertThat(RegrasUsuario.precisaDeArea(Perfil.ADMIN)).isFalse();
        assertThat(RegrasUsuario.precisaDeArea(Perfil.ATENDENTE)).isTrue();
        assertThat(RegrasUsuario.precisaDeArea(Perfil.GESTOR)).isTrue();
    }

    @Test
    @DisplayName("Nome, e-mail e perfil obrigatórios; e-mail com formato válido")
    void camposObrigatorios() {
        var erros = RegrasUsuario.validarCampos("  ", "", null, null, false);
        assertThat(erros).containsEntry("nome", RegrasUsuario.NOME_OBRIGATORIO)
                .containsEntry("email", RegrasUsuario.EMAIL_OBRIGATORIO)
                .containsEntry("perfil", RegrasUsuario.PERFIL_OBRIGATORIO);
        assertThat(RegrasUsuario.validarCampos("A", "rafael@", Perfil.ADMIN, null, false))
                .containsEntry("email", RegrasUsuario.EMAIL_INVALIDO);
        assertThat(RegrasUsuario.validarCampos("A", "ra fa@x.com", Perfil.ADMIN, null, false))
                .containsEntry("email", RegrasUsuario.EMAIL_INVALIDO);
        assertThat(RegrasUsuario.validarCampos("A".repeat(121), "a@x.com", Perfil.ADMIN, null, false))
                .containsEntry("nome", RegrasUsuario.NOME_LONGO);
    }

    @Test
    @DisplayName("E-mail vai para minúsculas e nome sem espaços sobrando")
    void normalizacao() {
        assertThat(RegrasUsuario.normalizarEmail("  Marcos.Tavares@GoiasEC.com.BR ")).isEqualTo("marcos.tavares@goiasec.com.br");
        assertThat(RegrasUsuario.normalizarNome("  Marcos   Tavares ")).isEqualTo("Marcos Tavares");
        assertThat(RegrasUsuario.normalizarEmail(null)).isNull();
    }

    // ---------- proteções do admin ----------

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
