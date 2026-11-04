package br.com.goias.chamados.admin.comum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasNomeTest {

    @Test
    @DisplayName("Nome único sem diferenciar maiúsculas, acentos e espaços")
    void unico() {
        List<String> existentes = List.of("Manutenção", "Departamento de Futebol");
        assertThat(RegrasNome.validar("manutencao", 80, existentes, "uma área")).isEqualTo("Já existe uma área com este nome.");
        assertThat(RegrasNome.validar("  DEPARTAMENTO   de futebol ", 100, existentes, "um setor"))
                .isEqualTo("Já existe um setor com este nome.");
        assertThat(RegrasNome.validar("Limpeza", 80, existentes, "uma área")).isNull();
    }

    @Test
    @DisplayName("Obrigatório e com tamanho máximo; espaços sobrando são limpos")
    void obrigatorioETamanho() {
        assertThat(RegrasNome.validar("   ", 100, List.of(), "um setor")).isEqualTo("Informe o nome.");
        assertThat(RegrasNome.validar("x".repeat(101), 100, List.of(), "um setor")).contains("no máximo 100");
        assertThat(RegrasNome.normalizar("  Sala   de  TI ")).isEqualTo("Sala de TI");
    }

    @Test
    @DisplayName("Filtro de situação: ativos (padrão), inativos e todos")
    void filtroSituacao() {
        assertThat(FiltroSituacao.de(null)).isEqualTo(FiltroSituacao.ATIVOS);
        assertThat(FiltroSituacao.de("xyz")).isEqualTo(FiltroSituacao.ATIVOS);
        assertThat(FiltroSituacao.ATIVOS.aceita(true)).isTrue();
        assertThat(FiltroSituacao.ATIVOS.aceita(false)).isFalse();
        assertThat(FiltroSituacao.de("inativos").aceita(false)).isTrue();
        assertThat(FiltroSituacao.de("todos").aceita(false)).isTrue();
    }

    @Test
    @DisplayName("Busca sem diferenciar acentos")
    void busca() {
        assertThat(Parametros.contem("Câmeras e CFTV", "camera")).isTrue();
        assertThat(Parametros.contem("Elétrica", "ELETR")).isTrue();
        assertThat(Parametros.contem("Elétrica", "hidr")).isFalse();
        assertThat(Parametros.contem("qualquer", null)).isTrue();
    }
}
