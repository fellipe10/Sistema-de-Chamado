package br.com.goias.chamados.dominio.chamado;

import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente.Periodo;
import br.com.goias.chamados.dominio.cadastro.CalendariosDeTeste;
import br.com.goias.chamados.dominio.cadastro.CalendariosExpediente;
import br.com.goias.chamados.dominio.cadastro.Expediente;
import br.com.goias.chamados.dominio.cadastro.ExpedienteRepository;
import br.com.goias.chamados.dominio.cadastro.FeriadoRepository;
import br.com.goias.chamados.dominio.cadastro.SlaConfig;
import br.com.goias.chamados.dominio.cadastro.SlaConfigRepository;
import br.com.goias.chamados.dominio.chamado.CalculadoraPrazo.Prazos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SLA em horário de expediente (Etapa 4).
 * Expediente padrão do clube: segunda a sexta, 08:00–12:00 e 13:00–17:00 (480 min = 1 dia útil).
 * Datas de novembro/2026: ter 03, qua 04, qui 05, sex 06, sáb 07, dom 08, seg 09.
 * Feriados usados: Finados (seg 02/11) e Consciência Negra (sex 20/11).
 */
class CalculadoraPrazoExpedienteTest {

    static final List<Periodo> PERIODOS = CalendariosDeTeste.PERIODOS;
    static final EnumSet<DayOfWeek> SEG_A_SEX = CalendariosDeTeste.SEG_A_SEX;

    static LocalDateTime em(int dia, int mes, int hora, int minuto) {
        return LocalDateTime.of(2026, mes, dia, hora, minuto);
    }

    private final CalendarioExpediente cal = CalendariosDeTeste.padrao();

    @Nested
    @DisplayName("Somar minutos úteis")
    class Somar {

        @Test
        @DisplayName("Aberto dentro do expediente: soma direto")
        void dentroDoExpediente() {
            assertThat(cal.somarMinutosUteis(em(3, 11, 9, 0), 120)).isEqualTo(em(3, 11, 11, 0));
        }

        @Test
        @DisplayName("Atravessando o almoço: 12:00–13:00 não conta")
        void atravessandoAlmoco() {
            assertThat(cal.somarMinutosUteis(em(3, 11, 11, 0), 120)).isEqualTo(em(3, 11, 14, 0));
        }

        @Test
        @DisplayName("Aberto fora do expediente começa a contar no próximo período útil")
        void foraDoExpediente() {
            assertThat(cal.somarMinutosUteis(em(3, 11, 18, 30), 30)).isEqualTo(em(4, 11, 8, 30)); // noite
            assertThat(cal.somarMinutosUteis(em(3, 11, 7, 0), 30)).isEqualTo(em(3, 11, 8, 30));   // antes de abrir
            assertThat(cal.somarMinutosUteis(em(3, 11, 12, 30), 30)).isEqualTo(em(3, 11, 13, 30)); // no almoço
        }

        @Test
        @DisplayName("Sexta 16:50 + 1 dia útil (Alta) cai na segunda 16:50; 1ª resposta (2 h) segunda 09:50")
        void fimDeSemana() {
            assertThat(cal.somarMinutosUteis(em(6, 11, 16, 50), 480)).isEqualTo(em(9, 11, 16, 50));
            assertThat(cal.somarMinutosUteis(em(6, 11, 16, 50), 120)).isEqualTo(em(9, 11, 9, 50));
        }

        @Test
        @DisplayName("Aberto no sábado: começa segunda 08:00")
        void sabado() {
            assertThat(cal.somarMinutosUteis(em(7, 11, 10, 0), 60)).isEqualTo(em(9, 11, 9, 0));
        }

        @Test
        @DisplayName("Feriado nacional: sexta 30/10 16:50 + 480 pula o fim de semana E Finados (seg 02/11)")
        void feriadoNacional() {
            assertThat(cal.somarMinutosUteis(em(30, 10, 16, 50), 480)).isEqualTo(em(3, 11, 16, 50));
        }

        @Test
        @DisplayName("Prazo de vários dias: Baixa (2400 min = 5 dias úteis)")
        void variosDias() {
            assertThat(cal.somarMinutosUteis(em(3, 11, 10, 0), 2400)).isEqualTo(em(10, 11, 10, 0));
            // Com a Consciência Negra (sex 20/11) no meio: um dia a mais
            assertThat(cal.somarMinutosUteis(em(17, 11, 10, 0), 2400)).isEqualTo(em(25, 11, 10, 0));
        }

        @Test
        @DisplayName("Terminando exatamente no fim de um período, o prazo é esse fim (17:00 / 12:00)")
        void terminaNoFimDoPeriodo() {
            assertThat(cal.somarMinutosUteis(em(3, 11, 16, 0), 60)).isEqualTo(em(3, 11, 17, 0));
            assertThat(cal.somarMinutosUteis(em(3, 11, 11, 0), 60)).isEqualTo(em(3, 11, 12, 0));
        }

        @Test
        @DisplayName("Zero minutos = próximo momento útil")
        void zeroMinutos() {
            assertThat(cal.proximoMomentoUtil(em(3, 11, 9, 15))).isEqualTo(em(3, 11, 9, 15));
            assertThat(cal.proximoMomentoUtil(em(7, 11, 9, 15))).isEqualTo(em(9, 11, 8, 0));
        }

        @Test
        @DisplayName("Área sem expediente cadastrado conta em tempo corrido")
        void semExpediente() {
            CalendarioExpediente vazio = new CalendarioExpediente(Map.of(), List.of());
            assertThat(vazio.temExpediente()).isFalse();
            assertThat(vazio.somarMinutosUteis(em(7, 11, 10, 0), 60)).isEqualTo(em(7, 11, 11, 0));
        }

        @Test
        @DisplayName("Períodos sobrepostos são recusados")
        void sobrepostos() {
            assertThatThrownBy(() -> CalendarioExpediente.padrao(EnumSet.of(DayOfWeek.MONDAY), List.of(
                    new Periodo(LocalTime.of(8, 0), LocalTime.of(12, 0)),
                    new Periodo(LocalTime.of(11, 0), LocalTime.of(14, 0))), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Medir minutos úteis entre dois momentos")
    class Medir {

        @Test
        @DisplayName("Sexta 16:00 → segunda 09:00 = 2 h úteis")
        void atravessandoFimDeSemana() {
            assertThat(cal.minutosUteisEntre(em(6, 11, 16, 0), em(9, 11, 9, 0))).isEqualTo(120);
        }

        @Test
        @DisplayName("Almoço não conta; mesmo dia; fim antes do início = 0")
        void casosSimples() {
            assertThat(cal.minutosUteisEntre(em(3, 11, 11, 30), em(3, 11, 13, 30))).isEqualTo(60);
            assertThat(cal.minutosUteisEntre(em(3, 11, 9, 0), em(3, 11, 9, 45))).isEqualTo(45);
            assertThat(cal.minutosUteisEntre(em(3, 11, 10, 0), em(3, 11, 9, 0))).isZero();
            assertThat(cal.minutosUteisEntre(em(7, 11, 9, 0), em(8, 11, 18, 0))).isZero(); // fim de semana
        }

        @Test
        @DisplayName("Medir e somar são inversos: somar(a, medir(a, b)) volta em b (b dentro do expediente)")
        void inversos() {
            LocalDateTime a = em(30, 10, 16, 50);
            LocalDateTime b = em(5, 11, 14, 20);
            assertThat(cal.somarMinutosUteis(a, cal.minutosUteisEntre(a, b))).isEqualTo(b);
        }
    }

    @Nested
    @DisplayName("CalculadoraPrazoExpediente (com sla_config e calendário da área)")
    class Calculadora {

        private CalculadoraPrazoExpediente calculadora(ExpedienteRepository expedientes, FeriadoRepository feriados) {
            SlaConfigRepository slas = mock(SlaConfigRepository.class);
            when(slas.findByAreaIdAndPrioridade(org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.eq(Prioridade.ALTA))).thenReturn(Optional.of(sla(120, 480)));
            when(slas.findByAreaIdAndPrioridade(org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.eq(Prioridade.BAIXA))).thenReturn(Optional.empty());
            return new CalculadoraPrazoExpediente(slas, new CalendariosExpediente(expedientes, feriados));
        }

        private ExpedienteRepository expedientePadrao() {
            ExpedienteRepository repo = mock(ExpedienteRepository.class);
            for (long area : new long[]{1L, 2L}) {
                List<Expediente> lista = new ArrayList<>();
                for (DayOfWeek d : SEG_A_SEX) {
                    for (Periodo p : PERIODOS) {
                        Expediente e = new Expediente();
                        e.setDiaSemana(d.getValue());
                        e.setHoraInicio(p.inicio());
                        e.setHoraFim(p.fim());
                        lista.add(e);
                    }
                }
                when(repo.findByAreaIdOrderByDiaSemanaAscHoraInicioAsc(area)).thenReturn(lista);
            }
            return repo;
        }

        @Test
        @DisplayName("Chamado novo de prioridade Alta aberto sexta 16:50")
        void chamadoNovo() {
            FeriadoRepository feriados = mock(FeriadoRepository.class);
            when(feriados.datasDaArea(1L)).thenReturn(List.of());
            Prazos p = calculadora(expedientePadrao(), feriados).calcular(1L, Prioridade.ALTA, em(6, 11, 16, 50));
            assertThat(p.primeiraResposta()).isEqualTo(em(9, 11, 9, 50));
            assertThat(p.resolucao()).isEqualTo(em(9, 11, 16, 50));
        }

        @Test
        @DisplayName("Feriado só de uma área: TI para na quarta 04/11, Manutenção não")
        void feriadoSoDeUmaArea() {
            FeriadoRepository feriados = mock(FeriadoRepository.class);
            when(feriados.datasDaArea(1L)).thenReturn(List.of(LocalDate.of(2026, 11, 4))); // só TI
            when(feriados.datasDaArea(2L)).thenReturn(List.of());
            CalculadoraPrazoExpediente c = calculadora(expedientePadrao(), feriados);

            assertThat(c.calcular(1L, Prioridade.ALTA, em(3, 11, 16, 0)).primeiraResposta()).isEqualTo(em(5, 11, 9, 0));
            assertThat(c.calcular(2L, Prioridade.ALTA, em(3, 11, 16, 0)).primeiraResposta()).isEqualTo(em(4, 11, 9, 0));
        }

        @Test
        @DisplayName("Sem SLA para a prioridade: sem prazo")
        void semSla() {
            FeriadoRepository feriados = mock(FeriadoRepository.class);
            Prazos p = calculadora(expedientePadrao(), feriados).calcular(1L, Prioridade.BAIXA, em(3, 11, 9, 0));
            assertThat(p.primeiraResposta()).isNull();
            assertThat(p.resolucao()).isNull();
        }

        @Test
        @DisplayName("Recalcular soma os minutos pausados (e só empurra a 1ª resposta se ela ainda não aconteceu)")
        void recalcularComPausas() {
            Prazos semResposta = CalculadoraPrazoExpediente.prazos(cal, em(3, 11, 9, 0), 120, 480, 60, false);
            assertThat(semResposta.primeiraResposta()).isEqualTo(em(3, 11, 12, 0));
            assertThat(semResposta.resolucao()).isEqualTo(em(4, 11, 10, 0));

            Prazos comResposta = CalculadoraPrazoExpediente.prazos(cal, em(3, 11, 9, 0), 120, 480, 60, true);
            assertThat(comResposta.primeiraResposta()).isEqualTo(em(3, 11, 11, 0));
            assertThat(comResposta.resolucao()).isEqualTo(em(4, 11, 10, 0));
        }
    }

    static SlaConfig sla(int primeira, int resolucao) {
        SlaConfig s = new SlaConfig();
        Area area = new Area();
        area.setId(1L);
        s.setArea(area);
        s.setMinutosPrimeiraResposta(primeira);
        s.setMinutosResolucao(resolucao);
        return s;
    }
}
