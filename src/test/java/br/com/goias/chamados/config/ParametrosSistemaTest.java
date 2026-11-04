package br.com.goias.chamados.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParametrosSistemaTest {

    // ---------- dias do fechamento automático ----------

    @Test
    @DisplayName("Parâmetro vazio: usa o valor do application.yml")
    void vazioUsaYml() {
        assertThat(ParametrosSistema.resolverDiasFechamento(null, 5)).isEqualTo(5);
        assertThat(ParametrosSistema.resolverDiasFechamento("", 5)).isEqualTo(5);
        assertThat(ParametrosSistema.resolverDiasFechamento("   ", 7)).isEqualTo(7);
    }

    @Test
    @DisplayName("Parâmetro preenchido substitui o application.yml; 0 desliga")
    void preenchidoSubstitui() {
        assertThat(ParametrosSistema.resolverDiasFechamento("3", 5)).isEqualTo(3);
        assertThat(ParametrosSistema.resolverDiasFechamento(" 10 ", 5)).isEqualTo(10);
        assertThat(ParametrosSistema.resolverDiasFechamento("0", 5)).isZero();
    }

    @Test
    @DisplayName("Valor inválido gravado (texto, negativo): volta para o application.yml")
    void invalidoUsaYml() {
        assertThat(ParametrosSistema.resolverDiasFechamento("abc", 5)).isEqualTo(5);
        assertThat(ParametrosSistema.resolverDiasFechamento("-2", 5)).isEqualTo(5);
        assertThat(ParametrosSistema.resolverDiasFechamento("2.5", 4)).isEqualTo(4);
    }

    // ---------- rodapé público ----------

    @Test
    @DisplayName("Rodapé com os ramais trocados")
    void rodapeComRamais() {
        assertThat(ParametrosSistema.montarRodape("TI {ramal_ti} · Manutenção {ramal_manutencao}", "214", " 230 "))
                .isEqualTo("TI 214 · Manutenção 230");
    }

    @Test
    @DisplayName("Ramal usado no texto e vazio, ou texto vazio: texto genérico (nunca '{ramal_ti}')")
    void rodapeGenerico() {
        assertThat(ParametrosSistema.montarRodape("Ligue {ramal_ti}", "", "230")).isEqualTo(ParametrosSistema.RODAPE_GENERICO);
        assertThat(ParametrosSistema.montarRodape("Ligue {ramal_manutencao}", "214", null)).isEqualTo(ParametrosSistema.RODAPE_GENERICO);
        assertThat(ParametrosSistema.montarRodape(null, "214", "230")).isEqualTo(ParametrosSistema.RODAPE_GENERICO);
        assertThat(ParametrosSistema.montarRodape("  ", "214", "230")).isEqualTo(ParametrosSistema.RODAPE_GENERICO);
    }

    @Test
    @DisplayName("Texto sem marcadores aparece como foi escrito (mesmo sem ramais)")
    void textoLivre() {
        assertThat(ParametrosSistema.montarRodape("Emergência? Procure a portaria.", null, null))
                .isEqualTo("Emergência? Procure a portaria.");
        assertThat(ParametrosSistema.montarRodape("Só TI: {ramal_ti}", "214", "")).isEqualTo("Só TI: 214");
    }
}
