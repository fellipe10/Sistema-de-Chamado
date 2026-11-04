package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosDeTeste;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Badge de prazo na Etapa 4: "Pausado" e contagem em horas úteis. */
class BadgeSlaExpedienteTest {

    private final CalendarioExpediente cal = CalendariosDeTeste.padrao();

    private static LocalDateTime em(int dia, int mes, int hora, int minuto) {
        return LocalDateTime.of(2026, mes, dia, hora, minuto);
    }

    private BadgeSla emAndamento(LocalDateTime abertura, LocalDateTime prazo, LocalDateTime pausa, LocalDateTime agora) {
        return BadgeSla.calcular(StatusChamado.EM_ATENDIMENTO, abertura, prazo, null, null, pausa, cal, agora);
    }

    @Test
    @DisplayName("Aguardando o solicitante: \"Pausado\", sem contar atraso (mesmo com o prazo vencido)")
    void pausado() {
        BadgeSla b = BadgeSla.calcular(StatusChamado.AGUARDANDO, em(3, 11, 9, 0), em(3, 11, 10, 0), null, null,
                em(3, 11, 9, 30), cal, em(3, 11, 15, 0));
        assertThat(b.texto()).isEqualTo("Pausado");
        assertThat(b.classe()).isEqualTo(BadgeSla.PAUSADO);
        assertThat(b.dica()).contains("03/11/2026 às 09:30");
    }

    @Test
    @DisplayName("Sexta 16:30 com prazo segunda 09:00: faltam 1h 30min ÚTEIS → \"Vence em\" (em tempo corrido seria \"No prazo\")")
    void venceEmHorasUteis() {
        LocalDateTime abertura = em(6, 11, 9, 0);
        LocalDateTime prazo = em(9, 11, 9, 0); // 480 min úteis depois
        BadgeSla b = emAndamento(abertura, prazo, null, em(6, 11, 16, 30));
        assertThat(b.texto()).isEqualTo("Vence em 1h 30min");
        assertThat(b.classe()).isEqualTo(BadgeSla.VENCE);

        BadgeSla corrido = BadgeSla.calcular(StatusChamado.EM_ATENDIMENTO, abertura, prazo, null, null, em(6, 11, 16, 30));
        assertThat(corrido.texto()).isEqualTo("No prazo");
    }

    @Test
    @DisplayName("Atraso em horas úteis; vencido sexta 17:00 e agora é sábado → só \"Atrasado\"")
    void atrasoUtil() {
        assertThat(emAndamento(em(3, 11, 8, 0), em(6, 11, 17, 0), null, em(7, 11, 10, 0)).texto()).isEqualTo("Atrasado");
        assertThat(emAndamento(em(3, 11, 8, 0), em(6, 11, 17, 0), null, em(9, 11, 10, 0)).texto()).isEqualTo("Atrasado 2h");
    }

    @Test
    @DisplayName("Resolvido continua comparando a data de resolução com o prazo (pausa não importa)")
    void resolvido() {
        BadgeSla b = BadgeSla.calcular(StatusChamado.RESOLVIDO, em(3, 11, 9, 0), em(3, 11, 17, 0),
                em(3, 11, 16, 0), null, null, cal, em(5, 11, 9, 0));
        assertThat(b.texto()).isEqualTo("Resolvido no prazo");
    }
}
