package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.Chamado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RegraPrimeiraRespostaTest {

    private static final LocalDateTime ASSUMIU = LocalDateTime.of(2026, 11, 3, 9, 15);
    private static final LocalDateTime RESPONDEU = LocalDateTime.of(2026, 11, 3, 10, 40);

    @Test
    @DisplayName("Primeira ação (assumir) grava a data")
    void primeiraAcaoGrava() {
        Chamado chamado = new Chamado();
        assertThat(RegraPrimeiraResposta.registrar(chamado, ASSUMIU)).isTrue();
        assertThat(chamado.getDataPrimeiraResposta()).isEqualTo(ASSUMIU);
    }

    @Test
    @DisplayName("Resposta depois de assumir NÃO sobrescreve")
    void naoSobrescreve() {
        Chamado chamado = new Chamado();
        RegraPrimeiraResposta.registrar(chamado, ASSUMIU);
        assertThat(RegraPrimeiraResposta.registrar(chamado, RESPONDEU)).isFalse();
        assertThat(chamado.getDataPrimeiraResposta()).isEqualTo(ASSUMIU);
    }

    @Test
    @DisplayName("Se a resposta vier primeiro (ex.: gestor respondeu sem assumir), vale a resposta")
    void respostaPrimeiro() {
        Chamado chamado = new Chamado();
        RegraPrimeiraResposta.registrar(chamado, RESPONDEU);
        RegraPrimeiraResposta.registrar(chamado, RESPONDEU.plusHours(1)); // assumiu depois
        assertThat(chamado.getDataPrimeiraResposta()).isEqualTo(RESPONDEU);
    }
}
