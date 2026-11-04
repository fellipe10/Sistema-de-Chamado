package br.com.goias.chamados.admin.recalculo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrigemRecalculoTest {

    @Test
    @DisplayName("Comentário interno automático de cada origem")
    void comentario() {
        assertThat(OrigemRecalculo.SLA.comentario("Ana Lima")).isEqualTo("Prazos recalculados após mudança de SLA por Ana Lima.");
        assertThat(OrigemRecalculo.EXPEDIENTE.comentario("Ana")).isEqualTo("Prazos recalculados após mudança de expediente por Ana.");
        assertThat(OrigemRecalculo.FERIADO.comentario("Ana")).isEqualTo("Prazos recalculados após mudança de feriado por Ana.");
    }

    @Test
    @DisplayName("SLA e expediente recalculam só a área; feriado, todas as áreas")
    void alcance() {
        assertThat(OrigemRecalculo.SLA.isPorArea()).isTrue();
        assertThat(OrigemRecalculo.EXPEDIENTE.isPorArea()).isTrue();
        assertThat(OrigemRecalculo.FERIADO.isPorArea()).isFalse();
        assertThat(OrigemRecalculo.de("Feriado")).isEqualTo(OrigemRecalculo.FERIADO);
        assertThat(OrigemRecalculo.de("x")).isNull();
    }
}
