package br.com.goias.chamados.consulta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorCodigoTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @DisplayName("Aceita espaços, minúsculas e código sem hífen")
    @CsvSource(delimiter = '|', value = {
            "TI-2026-7K3F        | TI-2026-7K3F",
            "ti-2026-7k3f        | TI-2026-7K3F",
            "'  TI-2026-7K3F  '  | TI-2026-7K3F",
            "TI20267K3F          | TI-2026-7K3F",
            "ti20267k3f          | TI-2026-7K3F",
            "TI 2026 7K3F        | TI-2026-7K3F",
            "T I - 2 0 2 6 - 7K3F| TI-2026-7K3F",
            "TI2026-7K3F         | TI-2026-7K3F",
            "MAN-2026-Q9A2       | MAN-2026-Q9A2",
            "man2026q9a2         | MAN-2026-Q9A2",
            "TI–2026–7K3F        | TI-2026-7K3F"
    })
    void normaliza(String digitado, String esperado) {
        assertThat(NormalizadorCodigo.normalizar(digitado)).contains(esperado);
    }

    @ParameterizedTest(name = "\"{0}\" é inválido")
    @DisplayName("Recusa o que não está no formato")
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "TI-2026",
            "TI-26-7K3F",
            "TI-2026-7K3",
            "TI-2026-7K3FF",
            "2026-7K3F",
            "T-2026-7K3F",
            "TI-2026-0K3F",      // 0 é ambíguo
            "TI-2026-OK3F",      // O é ambíguo
            "TI-2026-1K3F",      // 1 é ambíguo
            "TI-2026-IK3F",      // I é ambíguo
            "TI-2026-LK3F",      // L é ambíguo
            "TI_2026_7K3F",
            "<script>alert(1)</script>",
            "'; drop table chamado; --"
    })
    void recusa(String digitado) {
        assertThat(NormalizadorCodigo.normalizar(digitado)).isEmpty();
    }

    @Test
    @DisplayName("Recusa texto muito comprido sem processar")
    void recusaTextoComprido() {
        assertThat(NormalizadorCodigo.normalizar("TI-2026-7K3F" + " ".repeat(40))).isEmpty();
    }
}
