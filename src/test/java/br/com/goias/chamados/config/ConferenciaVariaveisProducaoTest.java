package br.com.goias.chamados.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConferenciaVariaveisProducaoTest {

    @Test
    @DisplayName("Perfil prod: lista as variáveis de ambiente que faltam ou estão vazias")
    void faltando() {
        Map<String, String> amb = Map.of("CHAMADOS_DB_URL", "jdbc:postgresql://localhost:5432/chamados",
                "CHAMADOS_DB_USER", "chamados", "CHAMADOS_DB_PASSWORD", "  ");
        assertThat(ConferenciaVariaveisProducao.faltando(amb::get))
                .containsExactly("CHAMADOS_DB_PASSWORD", "CHAMADOS_ANEXOS_DIR");
    }

    @Test
    @DisplayName("Todas preenchidas: nada falta")
    void completo() {
        Map<String, String> amb = Map.of("CHAMADOS_DB_URL", "jdbc:postgresql://x/y", "CHAMADOS_DB_USER", "a",
                "CHAMADOS_DB_PASSWORD", "b", "CHAMADOS_ANEXOS_DIR", "D:/chamados/anexos");
        assertThat(ConferenciaVariaveisProducao.faltando(amb::get)).isEmpty();
    }
}
