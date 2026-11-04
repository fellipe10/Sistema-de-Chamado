package br.com.goias.chamados.dominio.chamado;

import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static br.com.goias.chamados.dominio.chamado.CalculadoraPrazoExpedienteTest.em;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pausa do SLA no "Aguardando" (decisão oficial da Etapa 4).
 * Chamado de prioridade Média (240/1440) aberto terça 03/11 09:00:
 * 1ª resposta até 14:00 (09–12 + 13–14, o almoço não conta); resolução até sexta 06/11 09:00.
 */
class PausaSlaTest {

    /** Calculadora de teste: mesma regra da real, com o calendário padrão e SLA Média. */
    static class CalculadoraDeTeste implements CalculadoraPrazo {
        final CalendarioExpediente cal = CalendariosDeTeste.padrao();

        @Override
        public Prazos recalcular(Long areaId, Prioridade prioridade, LocalDateTime abertura,
                                 long minutosPausados, boolean jaTevePrimeiraResposta) {
            int[] sla = switch (prioridade) {
                case CRITICA -> new int[]{30, 240};
                case ALTA -> new int[]{120, 480};
                case MEDIA -> new int[]{240, 1440};
                case BAIXA -> new int[]{480, 2400};
            };
            return CalculadoraPrazoExpediente.prazos(cal, abertura, sla[0], sla[1], minutosPausados, jaTevePrimeiraResposta);
        }

        @Override
        public LocalDateTime somarMinutosUteis(Long areaId, LocalDateTime inicio, long minutos) {
            return cal.somarMinutosUteis(inicio, minutos);
        }

        @Override
        public long minutosUteisEntre(Long areaId, LocalDateTime de, LocalDateTime ate) {
            return cal.minutosUteisEntre(de, ate);
        }
    }

    private final CalculadoraDeTeste calc = new CalculadoraDeTeste();
    private Chamado chamado;

    @BeforeEach
    void novoChamado() {
        Area area = new Area();
        area.setId(1L);
        chamado = new Chamado();
        chamado.setArea(area);
        chamado.setPrioridade(Prioridade.MEDIA);
        chamado.setStatus(StatusChamado.EM_ATENDIMENTO);
        chamado.setDataAbertura(em(3, 11, 9, 0));
        CalculadoraPrazo.Prazos p = calc.calcular(1L, Prioridade.MEDIA, chamado.getDataAbertura());
        chamado.setPrazoPrimeiraResposta(p.primeiraResposta());
        chamado.setPrazoResolucao(p.resolucao());
        assertThat(chamado.getPrazoPrimeiraResposta()).isEqualTo(em(3, 11, 14, 0));
        assertThat(chamado.getPrazoResolucao()).isEqualTo(em(6, 11, 9, 0));
    }

    @Test
    @DisplayName("Aguardando o solicitante: entrar pausa; sair empurra os dois prazos pelos minutos úteis parados")
    void solicitanteEmpurraPrazo() {
        PausaSla.iniciar(chamado, MotivoAguardando.SOLICITANTE, em(3, 11, 10, 0));
        assertThat(chamado.isPausado()).isTrue();
        assertThat(chamado.getAguardandoMotivo()).isEqualTo(MotivoAguardando.SOLICITANTE);

        long empurrado = PausaSla.encerrar(chamado, em(3, 11, 14, 0), calc); // 10–12 + 13–14 = 180 min

        assertThat(empurrado).isEqualTo(180);
        assertThat(chamado.getPrazoPrimeiraResposta()).isEqualTo(em(3, 11, 17, 0)); // ainda sem 1ª resposta: 14:00 + 3 h
        assertThat(chamado.getPrazoResolucao()).isEqualTo(em(6, 11, 12, 0));
        assertThat(chamado.getMinutosPausados()).isEqualTo(180);
        assertThat(chamado.isPausado()).isFalse();
        assertThat(chamado.getAguardandoMotivo()).isNull();
    }

    @Test
    @DisplayName("Se a 1ª resposta já aconteceu, a pausa só empurra o prazo de resolução")
    void comPrimeiraResposta() {
        chamado.setDataPrimeiraResposta(em(3, 11, 9, 30));
        PausaSla.iniciar(chamado, MotivoAguardando.SOLICITANTE, em(3, 11, 10, 0));
        PausaSla.encerrar(chamado, em(3, 11, 14, 0), calc);

        assertThat(chamado.getPrazoPrimeiraResposta()).isEqualTo(em(3, 11, 14, 0));
        assertThat(chamado.getPrazoResolucao()).isEqualTo(em(6, 11, 12, 0));
    }

    @Test
    @DisplayName("Aguardando peça/fornecedor/terceiro NÃO pausa: sair não muda os prazos")
    void terceiroNaoEmpurra() {
        PausaSla.iniciar(chamado, MotivoAguardando.TERCEIRO, em(3, 11, 10, 0));
        assertThat(chamado.isPausado()).isFalse();

        long empurrado = PausaSla.encerrar(chamado, em(5, 11, 10, 0), calc);

        assertThat(empurrado).isZero();
        assertThat(chamado.getPrazoPrimeiraResposta()).isEqualTo(em(3, 11, 14, 0));
        assertThat(chamado.getPrazoResolucao()).isEqualTo(em(6, 11, 9, 0));
        assertThat(chamado.getMinutosPausados()).isZero();
        assertThat(chamado.getAguardandoMotivo()).isNull();
    }

    @Test
    @DisplayName("Pausa atravessando o fim de semana conta só o tempo útil (sex 16:00 → seg 09:00 = 2 h)")
    void pausaNoFimDeSemana() {
        chamado.setDataPrimeiraResposta(em(3, 11, 9, 30));
        PausaSla.iniciar(chamado, MotivoAguardando.SOLICITANTE, em(6, 11, 16, 0));

        long empurrado = PausaSla.encerrar(chamado, em(9, 11, 9, 0), calc);

        assertThat(empurrado).isEqualTo(120);
        assertThat(chamado.getPrazoResolucao()).isEqualTo(em(6, 11, 11, 0)); // sex 09:00 + 2 h úteis
    }

    @Test
    @DisplayName("Pausas somam: duas pausas de 60 min = 120 min pausados")
    void pausasAcumulam() {
        PausaSla.iniciar(chamado, MotivoAguardando.SOLICITANTE, em(3, 11, 9, 0));
        PausaSla.encerrar(chamado, em(3, 11, 10, 0), calc);
        PausaSla.iniciar(chamado, MotivoAguardando.SOLICITANTE, em(3, 11, 11, 30));
        PausaSla.encerrar(chamado, em(3, 11, 13, 30), calc); // 11:30–12 + 13–13:30
        assertThat(chamado.getMinutosPausados()).isEqualTo(120);
        assertThat(chamado.getPrazoResolucao()).isEqualTo(em(6, 11, 11, 0));
    }

    @Test
    @DisplayName("Mudar prioridade recalcula da abertura, em horário útil, somando os minutos pausados")
    void mudarPrioridade() {
        chamado.setDataPrimeiraResposta(em(3, 11, 9, 30));
        PausaSla.iniciar(chamado, MotivoAguardando.SOLICITANTE, em(3, 11, 10, 0));
        PausaSla.encerrar(chamado, em(3, 11, 14, 0), calc); // 180 min

        CalculadoraPrazo.Prazos alta = PausaSla.recalcular(chamado, Prioridade.ALTA, calc);
        assertThat(alta.primeiraResposta()).isEqualTo(em(3, 11, 11, 0)); // 120 sem pausa (já respondido)
        assertThat(alta.resolucao()).isEqualTo(em(4, 11, 12, 0));        // 480 + 180 = 660 min úteis
    }

    @Test
    @DisplayName("Texto público do motivo volta a ser o motivo (histórico interno)")
    void motivoPeloTextoPublico() {
        assertThat(MotivoAguardando.doTextoPublico("Aguardando seu retorno")).contains(MotivoAguardando.SOLICITANTE);
        assertThat(MotivoAguardando.doTextoPublico(" aguardando peça ou terceiro ")).contains(MotivoAguardando.TERCEIRO);
        assertThat(MotivoAguardando.doTextoPublico("outro texto")).isEqualTo(Optional.empty());
    }
}
