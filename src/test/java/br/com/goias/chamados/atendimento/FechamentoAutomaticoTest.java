package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosDeTeste;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracaoRepository;
import br.com.goias.chamados.dominio.chamado.ChamadoRepository;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Fechamento automático: RESOLVIDO há N dias úteis vira FECHADO. */
class FechamentoAutomaticoTest {

    private final CalendarioExpediente cal = CalendariosDeTeste.padrao();

    private static LocalDateTime em(int dia, int mes, int hora, int minuto) {
        return LocalDateTime.of(2026, mes, dia, hora, minuto);
    }

    @Test
    @DisplayName("Resolvido seg 09/11 15:00 com N = 5 fecha a partir de seg 16/11 15:00")
    void cincoDiasUteis() {
        LocalDateTime resolvido = em(9, 11, 15, 0);
        assertThat(RegraFechamentoAutomatico.deveFechar(StatusChamado.RESOLVIDO, resolvido, em(16, 11, 14, 59), cal, 5)).isFalse();
        assertThat(RegraFechamentoAutomatico.deveFechar(StatusChamado.RESOLVIDO, resolvido, em(16, 11, 15, 0), cal, 5)).isTrue();
    }

    @Test
    @DisplayName("Fim de semana e feriado não contam (Consciência Negra, sex 20/11)")
    void feriadoNaoConta() {
        LocalDateTime resolvido = em(19, 11, 10, 0); // quinta
        assertThat(RegraFechamentoAutomatico.deveFechar(StatusChamado.RESOLVIDO, resolvido, em(23, 11, 9, 59), cal, 1)).isFalse();
        assertThat(RegraFechamentoAutomatico.deveFechar(StatusChamado.RESOLVIDO, resolvido, em(23, 11, 10, 0), cal, 1)).isTrue();
    }

    @Test
    @DisplayName("Só fecha chamado RESOLVIDO (reaberto, em atendimento ou já fechado: não)")
    void soResolvido() {
        LocalDateTime resolvido = em(2, 10, 10, 0);
        LocalDateTime muitoDepois = em(3, 11, 10, 0);
        for (StatusChamado s : StatusChamado.values()) {
            assertThat(RegraFechamentoAutomatico.deveFechar(s, resolvido, muitoDepois, cal, 5))
                    .as(s.name()).isEqualTo(s == StatusChamado.RESOLVIDO);
        }
        assertThat(RegraFechamentoAutomatico.deveFechar(StatusChamado.RESOLVIDO, null, muitoDepois, cal, 5)).isFalse();
    }

    @Test
    @DisplayName("N = 0 desliga: nem consulta o banco")
    void zeroDesliga() {
        assertThat(RegraFechamentoAutomatico.deveFechar(StatusChamado.RESOLVIDO, em(2, 10, 10, 0), em(3, 11, 10, 0), cal, 0)).isFalse();

        ChamadoRepository chamados = mock(ChamadoRepository.class);
        ChamadoInteracaoRepository interacoes = mock(ChamadoInteracaoRepository.class);
        CalendariosExpediente calendarios = mock(CalendariosExpediente.class);
        FechamentoAutomatico job = new FechamentoAutomatico(chamados, interacoes, calendarios,
                mock(PlatformTransactionManager.class), 0);
        assertThat(job.executar(em(3, 11, 10, 0))).isZero();
        verifyNoInteractions(chamados, interacoes, calendarios);
    }

    @Test
    @DisplayName("Texto do histórico no singular e no plural")
    void texto() {
        assertThat(RegraFechamentoAutomatico.texto(5)).isEqualTo("Fechado automaticamente após 5 dias úteis sem retorno.");
        assertThat(RegraFechamentoAutomatico.texto(1)).isEqualTo("Fechado automaticamente após 1 dia útil sem retorno.");
    }
}
