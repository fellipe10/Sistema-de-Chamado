package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.usuario.Perfil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissoesAtendimentoTest {

    private static final Long TI = 1L;
    private static final Long MAN = 2L;
    private static final Long RAFAEL = 10L;   // atendente TI, responsável
    private static final Long BRUNO = 11L;    // outro atendente TI
    private static final Long CARLA = 20L;    // gestora TI
    private static final Long JOSE = 30L;     // atendente MAN
    private static final Long PAULA = 31L;    // gestora MAN
    private static final Long ADMIN = 1L;

    private static PermissoesAtendimento p(Perfil perfil, Long usuario, Long area, Long responsavel) {
        return PermissoesAtendimento.calcular(perfil, usuario, area, TI, responsavel);
    }

    @Test
    @DisplayName("Atendente responsável: vê, comenta, altera e devolve; não assume nem transfere")
    void atendenteResponsavel() {
        PermissoesAtendimento r = p(Perfil.ATENDENTE, RAFAEL, TI, RAFAEL);
        assertThat(r.podeVer()).isTrue();
        assertThat(r.podeComentar()).isTrue();
        assertThat(r.podeAlterar()).isTrue();
        assertThat(r.podeDevolver()).isTrue();
        assertThat(r.ehResponsavel()).isTrue();
        assertThat(r.podeAssumir()).isFalse();
        assertThat(r.podeTransferir()).isFalse();
    }

    @Test
    @DisplayName("Atendente da área (não responsável): vê e comenta; não altera, não devolve, não transfere")
    void atendenteDaArea() {
        PermissoesAtendimento r = p(Perfil.ATENDENTE, BRUNO, TI, RAFAEL);
        assertThat(r.podeVer()).isTrue();
        assertThat(r.podeComentar()).isTrue();
        assertThat(r.podeAlterar()).isFalse();
        assertThat(r.podeDevolver()).isFalse();
        assertThat(r.podeTransferir()).isFalse();
        assertThat(r.podeAssumir()).isFalse(); // já tem responsável
    }

    @Test
    @DisplayName("Sem responsável: atendente e gestor da área podem assumir")
    void assumirSemResponsavel() {
        assertThat(p(Perfil.ATENDENTE, BRUNO, TI, null).podeAssumir()).isTrue();
        assertThat(p(Perfil.GESTOR, CARLA, TI, null).podeAssumir()).isTrue();
        assertThat(p(Perfil.ADMIN, ADMIN, null, null).podeAssumir()).isFalse();
    }

    @Test
    @DisplayName("Gestor da área: altera e transfere mesmo sem ser o responsável")
    void gestorDaArea() {
        PermissoesAtendimento r = p(Perfil.GESTOR, CARLA, TI, RAFAEL);
        assertThat(r.podeVer()).isTrue();
        assertThat(r.podeAlterar()).isTrue();
        assertThat(r.podeTransferir()).isTrue();
        assertThat(r.podeDevolver()).isFalse(); // devolver é só do próprio responsável
    }

    @Test
    @DisplayName("Admin: vê todas as áreas, altera e transfere")
    void admin() {
        PermissoesAtendimento r = p(Perfil.ADMIN, ADMIN, null, RAFAEL);
        assertThat(r.podeVer()).isTrue();
        assertThat(r.podeComentar()).isTrue();
        assertThat(r.podeAlterar()).isTrue();
        assertThat(r.podeTransferir()).isTrue();
        assertThat(r.podeDevolver()).isFalse();
    }

    @Test
    @DisplayName("Outra área: atendente e gestor não podem nada (tela responde 404)")
    void outraArea() {
        for (PermissoesAtendimento r : new PermissoesAtendimento[]{
                p(Perfil.ATENDENTE, JOSE, MAN, null),
                p(Perfil.GESTOR, PAULA, MAN, RAFAEL),
                // mesmo que (por erro de dados) o responsável seja de outra área
                p(Perfil.ATENDENTE, JOSE, MAN, JOSE)}) {
            assertThat(r).isEqualTo(PermissoesAtendimento.nenhuma());
        }
    }

    @Test
    @DisplayName("Atendente sem área não vê nada")
    void semArea() {
        assertThat(p(Perfil.ATENDENTE, BRUNO, null, null).podeVer()).isFalse();
    }
}
