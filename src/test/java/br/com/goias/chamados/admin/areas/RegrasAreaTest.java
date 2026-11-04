package br.com.goias.chamados.admin.areas;

import br.com.goias.chamados.dominio.chamado.Prioridade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasAreaTest {

    private static final List<String> EXISTENTES = List.of("TI", "MAN");

    @Test
    @DisplayName("Sigla válida: 2 a 10 letras; minúsculas e espaços nas pontas são corrigidos")
    void valida() {
        assertThat(RegrasArea.validarSigla("RH", EXISTENTES)).isNull();
        assertThat(RegrasArea.validarSigla(" limpeza ", EXISTENTES)).isNull();
        assertThat(RegrasArea.validarSigla("ABCDEFGHIJ", EXISTENTES)).isNull();
        assertThat(RegrasArea.normalizarSigla(" limp ")).isEqualTo("LIMP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"L", "ABCDEFGHIJK", "LIMP 1", "LI MP", "SEGURANÇA", "R2", "T-I", "Á"})
    @DisplayName("Sigla inválida (tamanho, número, espaço, acento, símbolo)")
    void invalida(String sigla) {
        assertThat(RegrasArea.validarSigla(sigla, EXISTENTES)).isEqualTo(RegrasArea.SIGLA_INVALIDA);
    }

    @Test
    @DisplayName("Sigla vazia e sigla repetida (sem diferenciar maiúsculas)")
    void vaziaERepetida() {
        assertThat(RegrasArea.validarSigla("  ", EXISTENTES)).isEqualTo(RegrasArea.SIGLA_OBRIGATORIA);
        assertThat(RegrasArea.validarSigla(null, EXISTENTES)).isEqualTo(RegrasArea.SIGLA_OBRIGATORIA);
        assertThat(RegrasArea.validarSigla("ti", EXISTENTES)).isEqualTo(RegrasArea.SIGLA_REPETIDA);
    }

    @Test
    @DisplayName("Toda sigla aceita cabe no código do chamado (regex do banco)")
    void cabeNoProtocolo() {
        Pattern protocolo = Pattern.compile("^[A-Z]{2,10}-[0-9]{4}-[2-9A-HJKMNP-Z]{4}$");
        for (String s : List.of("RH", "LIMPEZA", "ABCDEFGHIJ")) {
            assertThat(RegrasArea.validarSigla(s, EXISTENTES)).isNull();
            assertThat(protocolo.matcher(s + "-2026-7K3F").matches()).isTrue();
        }
    }

    @Test
    @DisplayName("Sigla trava depois do primeiro chamado")
    void travaComChamados() {
        assertThat(RegrasArea.podeMudarSigla("LIMP", "LIMPEZA", 0)).isNull();
        assertThat(RegrasArea.podeMudarSigla("LIMP", "LIMPEZA", 1)).isEqualTo(RegrasArea.SIGLA_TRAVADA);
        assertThat(RegrasArea.podeMudarSigla("TI", "ti", 40)).isNull(); // mesma sigla: não é mudança
    }

    @Test
    @DisplayName("Área nova: expediente seg–sex 08–12/13–17 (40 h) e SLA padrão do planejamento")
    void padroes() {
        assertThat(PadroesAreaNova.EXPEDIENTE).hasSize(10);
        long minutos = PadroesAreaNova.EXPEDIENTE.stream()
                .mapToLong(p -> java.time.Duration.between(p.inicio(), p.fim()).toMinutes()).sum();
        assertThat(minutos).isEqualTo(40 * 60);
        assertThat(PadroesAreaNova.EXPEDIENTE).allMatch(p -> p.diaSemana() >= 1 && p.diaSemana() <= 5);
        assertThat(PadroesAreaNova.EXPEDIENTE.get(0).inicio()).isEqualTo(LocalTime.of(8, 0));
        assertThat(PadroesAreaNova.SLA.get(Prioridade.CRITICA)).containsExactly(30, 240);
        assertThat(PadroesAreaNova.SLA.get(Prioridade.ALTA)).containsExactly(120, 480);
        assertThat(PadroesAreaNova.SLA.get(Prioridade.MEDIA)).containsExactly(240, 1440);
        assertThat(PadroesAreaNova.SLA.get(Prioridade.BAIXA)).containsExactly(480, 2400);
    }
}
