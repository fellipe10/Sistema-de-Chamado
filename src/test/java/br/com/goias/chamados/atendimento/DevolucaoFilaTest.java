package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.StatusChamado;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Regra pura de "devolver para a fila" (a mesma do botão Devolver, da mudança de área e da desativação). */
class DevolucaoFilaTest {

    @ParameterizedTest(name = "{0}, já respondeu = {1} → {2}")
    @CsvSource({
            // ainda sem resposta ao solicitante: em atendimento/aguardando voltam para ABERTO
            "EM_ATENDIMENTO, false, ABERTO",
            "AGUARDANDO,     false, ABERTO",
            // já respondeu: continua no mesmo status, só que sem responsável
            "EM_ATENDIMENTO, true,  EM_ATENDIMENTO",
            "AGUARDANDO,     true,  AGUARDANDO",
            // outros status não mudam
            "ABERTO,         false, ABERTO",
            "REABERTO,       false, REABERTO",
            "REABERTO,       true,  REABERTO"
    })
    void statusDepoisDeDevolver(StatusChamado atual, boolean jaRespondeu, StatusChamado esperado) {
        assertThat(DevolucaoFila.statusDepoisDeDevolver(atual, jaRespondeu)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"RESOLVIDO", "FECHADO"})
    void finalizadosNaoContamComoEmAndamento(StatusChamado status) {
        assertThat(DevolucaoFila.FINALIZADOS).contains(status);
    }
}
