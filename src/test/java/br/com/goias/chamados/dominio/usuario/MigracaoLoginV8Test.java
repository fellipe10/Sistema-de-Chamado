package br.com.goias.chamados.dominio.usuario;

import br.com.goias.chamados.config.RecalculoPrazosV6;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration V8 (login por usuário) rodada de verdade no PostgreSQL, num schema separado e descartável
 * (o banco de desenvolvimento não é tocado):
 * - caminho de ATUALIZAÇÃO: banco parado na V7 com usuários "difíceis" → aplica a V8;
 * - banco ZERADO: V1 a V8 de uma vez.
 */
@SpringBootTest
class MigracaoLoginV8Test {

    @Autowired
    DataSource dataSource;
    @Autowired
    RecalculoPrazosV6 v6;

    String schema;
    JdbcTemplate jdbc;

    private Flyway flyway(String alvo) {
        schema = schema == null ? "teste_v8_" + Math.abs(ThreadLocalRandom.current().nextInt(1_000_000)) : schema;
        jdbc = new JdbcTemplate(dataSource);
        var config = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .javaMigrations(v6);
        if (alvo != null) {
            config.target(alvo);
        }
        return config.load();
    }

    @AfterEach
    void apagarSchema() {
        if (schema != null) {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void atualizacaoDaV7ComDuplicados() {
        flyway("7").migrate();
        String hash = jdbc.queryForObject("select senha_hash from " + schema + ".usuario where email = 'admin@chamados.local'",
                String.class);
        Long ti = jdbc.queryForObject("select id from " + schema + ".area where sigla = 'TI'", Long.class);
        String[] emails = {
                "atendente.ti@chamados.local",
                "rafael.nunes@goiasec.com.br",
                "rafael.nunes@outro.com.br",
                "rafael.nunes@terceiro.com",
                "joao_silva+ti@goiasec.com.br",
                "2024.maria@goiasec.com.br",
                "ti@goiasec.com.br",
                "..@x.com",
                "ana..--paula.@goiasec.com.br",
                "abcdefghijklmnopqrstuvwxyz.abcdefghij@x.com",
                "abcdefghijklmnopqrstuvwxyz.abcdefghik@x.com",
                "admin@outro.com"
        };
        for (String email : emails) {
            jdbc.update("insert into " + schema + ".usuario (nome, email, senha_hash, perfil, area_id) values (?, ?, ?, 'ATENDENTE', ?)",
                    "Pessoa " + email, email, hash, ti);
        }

        flyway(null).migrate();

        Map<String, String> loginPorEmail = new LinkedHashMap<>();
        jdbc.query("select email, login from " + schema + ".usuario order by id",
                rs -> {
                    loginPorEmail.put(rs.getString("email"), rs.getString("login"));
                });

        // admin inicial: login "admin"; o e-mail ti@goiasec.com.br já era de outra pessoa → mantém o antigo
        assertThat(loginPorEmail).containsEntry("admin@chamados.local", "admin");
        assertThat(loginPorEmail)
                .containsEntry("atendente.ti@chamados.local", "atendente.ti")
                .containsEntry("rafael.nunes@goiasec.com.br", "rafael.nunes")
                .containsEntry("rafael.nunes@outro.com.br", "rafael.nunes-2")
                .containsEntry("rafael.nunes@terceiro.com", "rafael.nunes-3")
                .containsEntry("joao_silva+ti@goiasec.com.br", "joao.silva.ti")
                .containsEntry("2024.maria@goiasec.com.br", "maria")
                .containsEntry("ti@goiasec.com.br", "usuario.ti")
                .containsEntry("..@x.com", "usuario")
                .containsEntry("ana..--paula.@goiasec.com.br", "ana.paula")
                .containsEntry("abcdefghijklmnopqrstuvwxyz.abcdefghij@x.com", "abcdefghijklmnopqrstuvwxyz.abc")
                .containsEntry("abcdefghijklmnopqrstuvwxyz.abcdefghik@x.com", "abcdefghijklmnopqrstuvwxyz.a-2")
                .containsEntry("admin@outro.com", "admin-2");
        // todo login gerado passa na mesma régua do Java
        assertThat(loginPorEmail.values()).allSatisfy(login -> assertThat(RegrasLogin.validar(login)).as(login).isNull());
        assertThat(loginPorEmail.values()).doesNotHaveDuplicates();
    }

    @Test
    void bancoZerado() {
        Flyway f = flyway(null);
        assertThat(f.migrate().migrationsExecuted).isEqualTo(8);

        Map<String, Object> admin = jdbc.queryForMap("select login, email, perfil, trocar_senha from " + schema + ".usuario");
        assertThat(admin).containsEntry("login", "admin")
                .containsEntry("email", "ti@goiasec.com.br")
                .containsEntry("perfil", "ADMIN")
                .containsEntry("trocar_senha", true);

        String insert = "insert into " + schema + ".usuario (nome, login, email, senha_hash, perfil) values ('X', ?, ?, 'h', 'ADMIN')";
        // e-mail opcional: vários sem e-mail
        jdbc.update(insert, "sem.email.um", null);
        jdbc.update(insert, "sem.email.dois", null);
        // CHECK do formato do login
        for (String ruim : new String[]{"Maiuscula", "ab", "rafael..nunes", "rafael.", "2rafael", "com espaco",
                "abcdefghijklmnopqrstuvwxyz.abcd"}) {
            assertThatThrownBy(() -> jdbc.update(insert, ruim, null)).as(ruim)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        // login único
        assertThatThrownBy(() -> jdbc.update(insert, "admin", null)).isInstanceOf(DataIntegrityViolationException.class);
        // e-mail único quando preenchido, minúsculo e não vazio
        assertThatThrownBy(() -> jdbc.update(insert, "outro.ti", "ti@goiasec.com.br"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "outro.ti", "Ti@Goias.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "outro.ti", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
