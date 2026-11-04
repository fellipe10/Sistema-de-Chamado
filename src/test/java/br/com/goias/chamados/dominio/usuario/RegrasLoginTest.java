package br.com.goias.chamados.dominio.usuario;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Formato, normalização e sugestão do "Usuário" (ajuste do login, V8). */
class RegrasLoginTest {

    @Nested
    class Formato {

        @ParameterizedTest
        @ValueSource(strings = {
                "rafael.nunes", "admin", "abc", "ana-paula.souza", "joao.silva2", "rafael.nunes-2",
                "a1b", "atendente.ti", "maria.de.souza", "abcdefghijklmnopqrstuvwxyz.abc" // 30
        })
        void validos(String login) {
            assertThat(RegrasLogin.validar(login)).isNull();
            assertThat(RegrasLogin.valido(login)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        void vazio(String login) {
            assertThat(RegrasLogin.validar(login)).isEqualTo(RegrasLogin.OBRIGATORIO);
        }

        @ParameterizedTest
        @ValueSource(strings = {"rafael nunes", "rafael_nunes", "rafaél", "Rafael", "rafael@goias", "ra+fa"})
        void caracteresProibidos(String login) {
            assertThat(RegrasLogin.validar(login)).isEqualTo(RegrasLogin.CARACTERES);
        }

        @ParameterizedTest
        @ValueSource(strings = {"2rafael", ".rafael", "-rafael", "1234"})
        void precisaComecarComLetra(String login) {
            assertThat(RegrasLogin.validar(login)).isEqualTo(RegrasLogin.COMECO);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "ab", "a."})
        void curto(String login) {
            assertThat(RegrasLogin.validar(login)).isEqualTo(RegrasLogin.CURTO);
        }

        @Test
        void longo() {
            assertThat(RegrasLogin.validar("abcdefghijklmnopqrstuvwxyz.abcd")).isEqualTo(RegrasLogin.LONGO); // 31
        }

        @ParameterizedTest
        @ValueSource(strings = {"rafael.", "rafael-", "rafael..nunes", "rafael.-nunes", "rafael--nunes", "ab.-"})
        void pontuacaoNoFimOuRepetida(String login) {
            assertThat(RegrasLogin.validar(login)).isEqualTo(RegrasLogin.PONTUACAO);
        }

        /** O mesmo formato do CHECK ck_usuario_login_formato da V8. */
        @ParameterizedTest
        @CsvSource({"rafael.nunes,true", "ra,false", "rafael..nunes,false", "Rafael,false", "9abc,false"})
        void mesmoResultadoDoCheckDoBanco(String login, boolean esperado) {
            boolean check = login.length() >= 3 && login.length() <= 30
                    && login.matches("^[a-z][a-z0-9]*([.-][a-z0-9]+)*$");
            assertThat(check).isEqualTo(esperado);
            assertThat(RegrasLogin.valido(login)).isEqualTo(esperado);
        }
    }

    @Nested
    class Normalizacao {

        @ParameterizedTest
        @CsvSource({
                "'  rafael.nunes  ', rafael.nunes",
                "Rafael.Nunes, rafael.nunes",
                "'  ADMIN ', admin",
                "rafaél.núnes, rafael.nunes",
                "JOSÉ.RIBEIRO, jose.ribeiro",
                "conceição, conceicao"
        })
        void espacosMaiusculasEAcentos(String digitado, String esperado) {
            assertThat(RegrasLogin.normalizar(digitado)).isEqualTo(esperado);
        }

        @Test
        void nuloViraVazio() {
            assertThat(RegrasLogin.normalizar(null)).isEmpty();
        }

        @Test
        void espacoNoMeioNaoSome() {
            // "rafael nunes" continua inválido (não adivinhamos que o espaço era um ponto)
            assertThat(RegrasLogin.normalizar(" Rafael Nunes ")).isEqualTo("rafael nunes");
            assertThat(RegrasLogin.valido(RegrasLogin.normalizar(" Rafael Nunes "))).isFalse();
        }
    }

    @Nested
    class Sugestao {

        @ParameterizedTest
        @CsvSource({
                "Rafael Nunes, rafael.nunes",
                "'  rafael   nunes  ', rafael.nunes",
                "José Ribeiro, jose.ribeiro",
                "Conceição Araújo, conceicao.araujo",
                "Maria Clara Souza, maria.souza",
                "Maria Clara de Souza Lima, maria.lima",
                "João da Silva, joao.silva",
                "Ana dos Santos e Silva, ana.silva",
                "Pedro Henrique, pedro.henrique",
                "Rafael, rafael",
                "Maria da, maria",
                "Ana-Paula Souza, ana.souza",
                "Joana D'Ávila, joana.davila",
                "ÉRICA MÜLLER, erica.muller",
                "Paulo 2º Silva, paulo.silva"
        })
        void aPartirDoNome(String nome, String esperado) {
            assertThat(RegrasLogin.sugerir(nome)).isEqualTo(esperado);
        }

        @Test
        void nomeVazioNaoSugereNada() {
            assertThat(RegrasLogin.sugerir(null)).isEmpty();
            assertThat(RegrasLogin.sugerir("   ")).isEmpty();
            assertThat(RegrasLogin.sugerir("123")).isEmpty();
        }

        @Test
        void nomeLongoCortaEm30SemPontoNoFim() {
            String s = RegrasLogin.sugerir("Maximiliano Bartolomeu Wolfeschlegelsteinhausen");
            assertThat(s).hasSizeLessThanOrEqualTo(30).doesNotEndWith(".").startsWith("maximiliano.");
            assertThat(RegrasLogin.valido(s)).isTrue();

            String cortadoNoPonto = RegrasLogin.sugerir("Abcdefghijklmnopqrstuvwxyzabcd Silva"); // 30 + "."
            assertThat(cortadoNoPonto).isEqualTo("abcdefghijklmnopqrstuvwxyzabcd");
        }

        @Test
        void nomeCurtoDemaisDevolveSugestaoInvalida() {
            // O admin vê o erro e ajusta (não inventamos letras)
            assertThat(RegrasLogin.sugerir("Li")).isEqualTo("li");
            assertThat(RegrasLogin.valido("li")).isFalse();
            assertThat(RegrasLogin.sugerir("Li Wu")).isEqualTo("li.wu");
        }

        @Test
        void sugestaoSempreNoFormatoQuandoONomeTemLetrasSuficientes() {
            for (String nome : new String[]{"Rafael Nunes", "José Ribeiro", "Maria Clara de Souza Lima", "Joana D'Ávila"}) {
                assertThat(RegrasLogin.valido(RegrasLogin.sugerir(nome))).as(nome).isTrue();
            }
        }

        @Test
        void disponivelUsaSufixoQuandoJaExiste() {
            Set<String> usados = Set.of("rafael.nunes", "rafael.nunes-2");
            assertThat(RegrasLogin.sugerirDisponivel("Rafael Nunes", usados::contains)).isEqualTo("rafael.nunes-3");
            assertThat(RegrasLogin.sugerirDisponivel("Carla Mendes", usados::contains)).isEqualTo("carla.mendes");
        }

        @Test
        void disponivelComSufixoContinuaCom30() {
            String base = RegrasLogin.sugerir("Abcdefghijklmnopqrstuvwxyz Silva"); // "abcdefghijklmnopqrstuvwxyz.sil"
            assertThat(base).hasSize(30);
            String outro = RegrasLogin.sugerirDisponivel("Abcdefghijklmnopqrstuvwxyz Silva", base::equals);
            assertThat(outro).hasSize(30).endsWith("-2");
            assertThat(RegrasLogin.valido(outro)).isTrue();
        }
    }
}
