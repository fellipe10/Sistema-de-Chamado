package br.com.goias.chamados.config;

import br.com.goias.chamados.config.RecalculoPrazosV6.LinhaChamado;
import br.com.goias.chamados.config.RecalculoPrazosV6.Mudanca;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Recálculo único dos prazos antigos (migration Java V6). */
class RecalculoPrazosV6Test {

    private static final Map<Long, CalendarioExpediente> CALENDARIOS =
            Map.of(1L, CalendariosDeTeste.padrao());
    private static final Map<String, int[]> SLAS = Map.of(
            RecalculoPrazosV6.chave(1, "ALTA"), new int[]{120, 480},
            RecalculoPrazosV6.chave(1, "MEDIA"), new int[]{240, 1440});

    private static LocalDateTime em(int dia, int mes, int hora, int minuto) {
        return LocalDateTime.of(2026, mes, dia, hora, minuto);
    }

    /** Chamado criado na Etapa 2/3: sexta 30/10 16:50, Alta, prazos em tempo corrido (+2 h e +8 h). */
    private static LinhaChamado antigo() {
        return new LinhaChamado(10, 1, "ALTA", em(30, 10, 16, 50), null, 0,
                em(30, 10, 18, 50), em(31, 10, 0, 50));
    }

    @Test
    @DisplayName("Chamado antigo em tempo corrido ganha prazo em horário útil (pula fim de semana e Finados)")
    void recalculaAntigo() {
        List<Mudanca> mudancas = RecalculoPrazosV6.calcularMudancas(List.of(antigo()), CALENDARIOS, SLAS);

        assertThat(mudancas).containsExactly(new Mudanca(10, em(3, 11, 9, 50), em(3, 11, 16, 50)));
    }

    @Test
    @DisplayName("Idempotente: aplicar e rodar de novo não muda nada")
    void idempotente() {
        LinhaChamado a = antigo();
        LinhaChamado b = new LinhaChamado(11, 1, "MEDIA", em(31, 10, 10, 0), em(31, 10, 11, 0), 0,
                em(31, 10, 14, 0), em(1, 11, 10, 0));
        List<Mudanca> primeira = RecalculoPrazosV6.calcularMudancas(List.of(a, b), CALENDARIOS, SLAS);
        assertThat(primeira).hasSize(2);

        // "Grava" o resultado e roda de novo
        List<LinhaChamado> gravados = List.of(aplicar(a, primeira.get(0)), aplicar(b, primeira.get(1)));
        assertThat(RecalculoPrazosV6.calcularMudancas(gravados, CALENDARIOS, SLAS)).isEmpty();
        // e uma terceira vez, por garantia
        assertThat(RecalculoPrazosV6.calcularMudancas(gravados, CALENDARIOS, SLAS)).isEmpty();
    }

    @Test
    @DisplayName("Considera minutos pausados e se já houve 1ª resposta")
    void consideraPausas() {
        LinhaChamado comPausa = new LinhaChamado(12, 1, "ALTA", em(3, 11, 9, 0), em(3, 11, 9, 10), 60,
                null, null);
        List<Mudanca> m = RecalculoPrazosV6.calcularMudancas(List.of(comPausa), CALENDARIOS, SLAS);
        assertThat(m.get(0).primeiraResposta()).isEqualTo(em(3, 11, 11, 0)); // já respondido: sem pausa
        assertThat(m.get(0).resolucao()).isEqualTo(em(4, 11, 10, 0));        // 480 + 60
    }

    @Test
    @DisplayName("Sem SLA configurado para a prioridade: o chamado fica como está")
    void semSla() {
        LinhaChamado baixa = new LinhaChamado(13, 1, "BAIXA", em(3, 11, 9, 0), null, 0, null, null);
        assertThat(RecalculoPrazosV6.calcularMudancas(List.of(baixa), CALENDARIOS, SLAS)).isEmpty();
    }

    private static LinhaChamado aplicar(LinhaChamado c, Mudanca m) {
        return new LinhaChamado(c.id(), c.areaId(), c.prioridade(), c.abertura(), c.dataPrimeiraResposta(),
                c.minutosPausados(), m.primeiraResposta(), m.resolucao());
    }
}
