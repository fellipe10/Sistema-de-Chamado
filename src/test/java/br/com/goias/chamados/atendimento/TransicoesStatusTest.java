package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.StatusChamado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static br.com.goias.chamados.dominio.chamado.StatusChamado.*;
import static org.assertj.core.api.Assertions.assertThat;

class TransicoesStatusTest {

    @ParameterizedTest(name = "{0} → {1} é permitida")
    @CsvSource({
            "EM_ATENDIMENTO, AGUARDANDO",
            "AGUARDANDO, EM_ATENDIMENTO",
            "EM_ATENDIMENTO, RESOLVIDO",
            "AGUARDANDO, RESOLVIDO",
            "RESOLVIDO, FECHADO",
            "RESOLVIDO, REABERTO",
            "REABERTO, EM_ATENDIMENTO"})
    void permitidas(StatusChamado de, StatusChamado para) {
        assertThat(TransicoesStatus.permitida(de, para)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} é recusada")
    @CsvSource({
            "ABERTO, EM_ATENDIMENTO",   // só ao assumir
            "ABERTO, RESOLVIDO",
            "ABERTO, FECHADO",
            "EM_ATENDIMENTO, FECHADO",
            "EM_ATENDIMENTO, ABERTO",
            "EM_ATENDIMENTO, REABERTO",
            "AGUARDANDO, FECHADO",
            "RESOLVIDO, EM_ATENDIMENTO",
            "RESOLVIDO, AGUARDANDO",
            "REABERTO, RESOLVIDO",
            "FECHADO, REABERTO",
            "FECHADO, EM_ATENDIMENTO",
            "EM_ATENDIMENTO, EM_ATENDIMENTO"})
    void recusadas(StatusChamado de, StatusChamado para) {
        assertThat(TransicoesStatus.permitida(de, para)).isFalse();
    }

    @Test
    @DisplayName("FECHADO é final e ABERTO não tem transição manual")
    void semSaida() {
        assertThat(TransicoesStatus.permitidas(FECHADO)).isEmpty();
        assertThat(TransicoesStatus.permitidas(ABERTO)).isEmpty();
    }

    @Test
    @DisplayName("Nulos nunca são permitidos")
    void nulos() {
        assertThat(TransicoesStatus.permitida(null, RESOLVIDO)).isFalse();
        assertThat(TransicoesStatus.permitida(EM_ATENDIMENTO, null)).isFalse();
        assertThat(TransicoesStatus.permitidas(null)).isEmpty();
    }

    @Test
    @DisplayName("Só RESOLVIDO e REABERTO exigem mensagem")
    void exigeMensagem() {
        assertThat(TransicoesStatus.exigeMensagem(RESOLVIDO)).isTrue();
        assertThat(TransicoesStatus.exigeMensagem(REABERTO)).isTrue();
        assertThat(TransicoesStatus.exigeMensagem(AGUARDANDO)).isFalse();
        assertThat(TransicoesStatus.exigeMensagem(FECHADO)).isFalse();
        assertThat(TransicoesStatus.exigeMensagem(EM_ATENDIMENTO)).isFalse();
    }
}
