package br.com.goias.chamados.painel;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosDeTeste;
import br.com.goias.chamados.painel.PeriodoPainel.Agrupamento;
import br.com.goias.chamados.painel.PeriodoPainel.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filtro de período e agrupamento do gráfico "Abertos x resolvidos"
 * (tabela do planejamento: hoje → hora; 7 dias → dia em barras; 30 dias → dia em linha; 6 meses → mês;
 * período personalizado: até 2 dias → hora; até 45 → dia; acima → mês). Hoje = terça 03/11/2026.
 */
class GraficoPainelTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);
    private final CalendarioExpediente cal = CalendariosDeTeste.padrao();

    private static LocalDateTime em(int dia, int mes, int hora, int minuto) {
        return LocalDateTime.of(2026, mes, dia, hora, minuto);
    }

    @Nested
    @DisplayName("Período")
    class Periodo {

        @Test
        @DisplayName("Sem parâmetro ou parâmetro desconhecido = 30 dias (padrão)")
        void padrao() {
            assertThat(PeriodoPainel.interpretar(null, null, null, HOJE).tipo()).isEqualTo(Tipo.TRINTA_DIAS);
            assertThat(PeriodoPainel.interpretar("abc", null, null, HOJE).tipo()).isEqualTo(Tipo.TRINTA_DIAS);
            PeriodoPainel p = PeriodoPainel.interpretar("30d", null, null, HOJE);
            assertThat(p.de()).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(p.inicio()).isEqualTo(em(5, 10, 0, 0));
            assertThat(p.fim()).isEqualTo(em(4, 11, 0, 0)); // fim exclusivo
            assertThat(p.erro()).isNull();
        }

        @Test
        @DisplayName("Hoje, 7 dias e 6 meses")
        void fixos() {
            assertThat(PeriodoPainel.interpretar("hoje", null, null, HOJE).agrupamento()).isEqualTo(Agrupamento.HORA);
            PeriodoPainel sete = PeriodoPainel.interpretar("7d", null, null, HOJE);
            assertThat(sete.de()).isEqualTo(LocalDate.of(2026, 10, 28));
            assertThat(sete.agrupamento()).isEqualTo(Agrupamento.DIA);
            PeriodoPainel seis = PeriodoPainel.interpretar("6m", null, null, HOJE);
            assertThat(seis.de()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(seis.agrupamento()).isEqualTo(Agrupamento.MES);
        }

        @Test
        @DisplayName("Personalizado escolhe o agrupamento sozinho: até 2 dias hora, até 45 dia, acima mês")
        void personalizado() {
            assertThat(PeriodoPainel.interpretar(null, "2026-11-02", "2026-11-03", HOJE).agrupamento()).isEqualTo(Agrupamento.HORA);
            assertThat(PeriodoPainel.interpretar(null, "2026-11-01", "2026-11-03", HOJE).agrupamento()).isEqualTo(Agrupamento.DIA);
            assertThat(PeriodoPainel.interpretar(null, "2026-09-20", "2026-11-03", HOJE).agrupamento()).isEqualTo(Agrupamento.DIA); // 45 dias
            assertThat(PeriodoPainel.interpretar(null, "2026-09-19", "2026-11-03", HOJE).agrupamento()).isEqualTo(Agrupamento.MES); // 46 dias
            PeriodoPainel p = PeriodoPainel.interpretar(null, "2026-10-01", "2026-10-15", HOJE);
            assertThat(p.tipo()).isEqualTo(Tipo.PERSONALIZADO);
            assertThat(p.descricao()).isEqualTo("de 01/10/2026 a 15/10/2026");
            assertThat(p.parametros()).containsEntry("de", "2026-10-01").containsEntry("ate", "2026-10-15");
        }

        @Test
        @DisplayName("Personalizado inválido volta para 30 dias com mensagem")
        void invalido() {
            assertThat(PeriodoPainel.interpretar(null, "2026-11-03", "2026-11-01", HOJE).erro()).contains("antes");
            assertThat(PeriodoPainel.interpretar(null, "2026-11-01", "2026-11-10", HOJE).erro()).contains("depois de hoje");
            assertThat(PeriodoPainel.interpretar(null, "2026-11-01", null, HOJE).erro()).contains("duas datas");
            assertThat(PeriodoPainel.interpretar(null, "xx", "2026-11-01", HOJE).erro()).contains("duas datas");
            assertThat(PeriodoPainel.interpretar(null, "2023-01-01", "2026-11-01", HOJE).erro()).contains("3 anos");
            assertThat(PeriodoPainel.interpretar(null, "2026-11-03", "2026-11-01", HOJE).tipo()).isEqualTo(Tipo.TRINTA_DIAS);
        }
    }

    @Nested
    @DisplayName("Gráfico")
    class Grafico {

        @Test
        @DisplayName("Hoje: barras por hora do expediente (8h…16h), com zero nas horas sem chamado")
        void hoje() {
            PeriodoPainel p = PeriodoPainel.interpretar("hoje", null, null, HOJE);
            GraficoPainel g = GraficoPainel.montar(p,
                    List.of(em(3, 11, 9, 15), em(3, 11, 9, 40), em(2, 11, 10, 0) /* fora do período */),
                    List.of(em(3, 11, 14, 5)), cal);

            assertThat(g.tipo()).isEqualTo("bar");
            assertThat(g.rotulos()).containsExactly("8h", "9h", "10h", "11h", "12h", "13h", "14h", "15h", "16h");
            assertThat(g.abertos()).containsExactly(0L, 2L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
            assertThat(g.resolvidos()).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L);
            assertThat(g.descricao()).isEqualTo("Por hora · hoje");
        }

        @Test
        @DisplayName("Hoje: chamado fora do expediente (18h20) aparece, com as horas do meio zeradas")
        void horaForaDoExpediente() {
            GraficoPainel g = GraficoPainel.montar(PeriodoPainel.interpretar("hoje", null, null, HOJE),
                    List.of(em(3, 11, 18, 20)), List.of(), cal);
            assertThat(g.rotulos()).endsWith("16h", "17h", "18h");
            assertThat(g.abertos().get(g.abertos().size() - 1)).isEqualTo(1L);
            assertThat(g.abertos().get(g.abertos().size() - 2)).isZero();
        }

        @Test
        @DisplayName("Sem nenhum chamado: tudo zero, mesmo número de intervalos")
        void semDados() {
            GraficoPainel g = GraficoPainel.montar(PeriodoPainel.interpretar("30d", null, null, HOJE),
                    List.of(), List.of(), cal);
            assertThat(g.rotulos()).hasSize(30);
            assertThat(g.abertos()).hasSize(30).containsOnly(0L);
            assertThat(g.resolvidos()).hasSize(30).containsOnly(0L);
        }

        @Test
        @DisplayName("7 dias: barras por dia com rótulo \"Qua 28\" (fim de semana com zero)")
        void seteDias() {
            GraficoPainel g = GraficoPainel.montar(PeriodoPainel.interpretar("7d", null, null, HOJE),
                    List.of(em(28, 10, 9, 0), em(3, 11, 9, 0), em(3, 11, 10, 0)), List.of(em(30, 10, 15, 0)), cal);
            assertThat(g.tipo()).isEqualTo("bar");
            assertThat(g.rotulos()).containsExactly("Qua 28", "Qui 29", "Sex 30", "Sáb 31", "Dom 01", "Seg 02", "Ter 03");
            assertThat(g.abertos()).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 2L);
            assertThat(g.resolvidos()).containsExactly(0L, 0L, 1L, 0L, 0L, 0L, 0L);
            assertThat(g.rotulos()).noneMatch(r -> r.startsWith("S4") || r.contains("semana"));
        }

        @Test
        @DisplayName("30 dias: linha por dia, rótulo \"05/10\" a cada 5 dias (e o último)")
        void trintaDias() {
            GraficoPainel g = GraficoPainel.montar(PeriodoPainel.interpretar("30d", null, null, HOJE),
                    List.of(em(10, 10, 9, 0)), List.of(), cal);
            assertThat(g.tipo()).isEqualTo("line");
            assertThat(g.rotulos().get(0)).isEqualTo("05/10");
            assertThat(g.rotulos().get(29)).isEqualTo("03/11");
            List<Integer> visiveis = IntStream.range(0, 30).filter(i -> g.mostrarRotulo().get(i)).boxed().toList();
            assertThat(visiveis).containsExactly(0, 5, 10, 15, 20, 25, 29);
            assertThat(g.abertos().get(5)).isEqualTo(1L); // 10/10
        }

        @Test
        @DisplayName("6 meses: barras por mês (Jun…Nov)")
        void seisMeses() {
            GraficoPainel g = GraficoPainel.montar(PeriodoPainel.interpretar("6m", null, null, HOJE),
                    List.of(em(1, 6, 9, 0), em(30, 6, 9, 0), em(3, 11, 9, 0)), List.of(em(15, 8, 9, 0)), cal);
            assertThat(g.tipo()).isEqualTo("bar");
            assertThat(g.rotulos()).containsExactly("Jun", "Jul", "Ago", "Set", "Out", "Nov");
            assertThat(g.abertos()).containsExactly(2L, 0L, 0L, 0L, 0L, 1L);
            assertThat(g.resolvidos()).containsExactly(0L, 0L, 1L, 0L, 0L, 0L);
        }

        @Test
        @DisplayName("Personalizado de 2 dias: por hora com o dia no rótulo (\"Qui 8h\")")
        void personalizadoPorHora() {
            GraficoPainel g = GraficoPainel.montar(PeriodoPainel.interpretar(null, "2026-10-29", "2026-10-30", HOJE),
                    List.of(em(30, 10, 9, 10)), List.of(), cal);
            assertThat(g.tipo()).isEqualTo("bar");
            assertThat(g.rotulos()).hasSize(18).startsWith("Qui 8h").endsWith("Sex 16h");
            assertThat(g.abertos().get(g.rotulos().indexOf("Sex 9h"))).isEqualTo(1L);
        }

        @Test
        @DisplayName("Personalizado por dia: até 14 dias barras \"Qua 28\"; mais que isso linha \"05/10\"")
        void personalizadoPorDia() {
            GraficoPainel dez = GraficoPainel.montar(PeriodoPainel.interpretar(null, "2026-10-25", "2026-11-03", HOJE),
                    List.of(), List.of(), cal);
            assertThat(dez.tipo()).isEqualTo("bar");
            assertThat(dez.rotulos().get(0)).isEqualTo("Dom 25");

            GraficoPainel vinte = GraficoPainel.montar(PeriodoPainel.interpretar(null, "2026-10-15", "2026-11-03", HOJE),
                    List.of(), List.of(), cal);
            assertThat(vinte.tipo()).isEqualTo("line");
            assertThat(vinte.rotulos()).hasSize(20);
            assertThat(vinte.rotulos().get(0)).isEqualTo("15/10");
        }

        @Test
        @DisplayName("Personalizado longo atravessando o ano: por mês com o ano (\"Dez/25\", \"Jan/26\")")
        void personalizadoPorMes() {
            GraficoPainel g = GraficoPainel.montar(
                    PeriodoPainel.interpretar(null, "2025-11-15", "2026-02-10", HOJE),
                    List.of(LocalDateTime.of(2025, 12, 5, 9, 0)), List.of(), cal);
            assertThat(g.rotulos()).containsExactly("Nov/25", "Dez/25", "Jan/26", "Fev/26");
            assertThat(g.abertos()).containsExactly(0L, 1L, 0L, 0L);
        }
    }
}
