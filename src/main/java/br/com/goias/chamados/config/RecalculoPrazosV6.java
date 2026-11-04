package br.com.goias.chamados.config;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente.Periodo;
import br.com.goias.chamados.dominio.chamado.CalculadoraPrazo.Prazos;
import br.com.goias.chamados.dominio.chamado.CalculadoraPrazoExpediente;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Migration V6 (em Java): recalcula UMA ÚNICA VEZ os prazos dos chamados ainda não resolvidos/fechados
 * que foram criados com a calculadora provisória em tempo corrido (Etapas 2 e 3).
 *
 * POR QUE FLYWAY JAVA MIGRATION (e não um runner com marcador no banco):
 * - O Flyway já é o "marcador": a linha da versão 6 em flyway_schema_history garante que roda
 *   uma vez só, em todo banco (dev, homologação, produção), sem tabela ou flag extra.
 * - Roda na MESMA transação do Flyway, antes do sistema subir: ou recalcula tudo ou nada,
 *   e ninguém usa a fila com prazo antigo no meio do caminho.
 * - Fica registrado no histórico do Flyway (data, duração, sucesso), como as outras migrations.
 *
 * IDEMPOTENTE: o novo prazo é sempre calculado do zero (abertura + SLA + minutos pausados, em tempo útil),
 * e só atualiza a linha quando o valor muda. Rodar de novo (ex.: banco restaurado de backup sem o
 * histórico) não muda nada e registra "0 recalculados".
 *
 * É um bean do Spring porque o Spring Boot entrega ao Flyway todo bean do tipo JavaMigration.
 * Usa só JDBC (o Hibernate ainda não subiu nesse momento) e a mesma regra pura de
 * {@link CalculadoraPrazoExpediente#prazos}.
 */
@Slf4j
@Component
public class RecalculoPrazosV6 implements JavaMigration {

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("6");
    }

    @Override
    public String getDescription() {
        return "recalcular prazos em horario de expediente";
    }

    @Override
    public Integer getChecksum() {
        return null;
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection conexao = context.getConnection();
        Map<String, int[]> slas = carregarSlas(conexao);
        Map<Long, CalendarioExpediente> calendarios = carregarCalendarios(conexao);
        List<LinhaChamado> chamados = carregarChamadosEmAndamento(conexao);

        List<Mudanca> mudancas = calcularMudancas(chamados, calendarios, slas);
        try (PreparedStatement update = conexao.prepareStatement(
                "UPDATE chamado SET prazo_primeira_resposta = ?, prazo_resolucao = ? WHERE id = ?")) {
            for (Mudanca m : mudancas) {
                update.setObject(1, m.primeiraResposta());
                update.setObject(2, m.resolucao());
                update.setLong(3, m.id());
                update.addBatch();
            }
            if (!mudancas.isEmpty()) {
                update.executeBatch();
            }
        }
        log.info("Recálculo de prazos em horário de expediente (Etapa 4): {} de {} chamados em andamento recalculados.",
                mudancas.size(), chamados.size());
    }

    // =================================================================
    // Regra pura (testada em RecalculoPrazosV6Test)
    // =================================================================

    /** Dados do chamado necessários para o recálculo. */
    public record LinhaChamado(long id, long areaId, String prioridade, LocalDateTime abertura,
                               LocalDateTime dataPrimeiraResposta, long minutosPausados,
                               LocalDateTime prazoPrimeiraResposta, LocalDateTime prazoResolucao) {
    }

    /** Novo par de prazos de um chamado. */
    public record Mudanca(long id, LocalDateTime primeiraResposta, LocalDateTime resolucao) {
    }

    /**
     * Calcula os prazos certos e devolve só os chamados cujo prazo MUDA.
     * Chamado sem SLA configurado para a área/prioridade fica como está.
     *
     * @param slas chave "areaId:PRIORIDADE" → {minutos 1ª resposta, minutos resolução}
     */
    public static List<Mudanca> calcularMudancas(List<LinhaChamado> chamados,
                                                 Map<Long, CalendarioExpediente> calendarios,
                                                 Map<String, int[]> slas) {
        List<Mudanca> mudancas = new ArrayList<>();
        for (LinhaChamado c : chamados) {
            int[] sla = slas.get(chave(c.areaId(), c.prioridade()));
            if (sla == null) {
                continue;
            }
            CalendarioExpediente calendario = calendarios.getOrDefault(c.areaId(),
                    new CalendarioExpediente(Map.of(), List.of()));
            Prazos novos = CalculadoraPrazoExpediente.prazos(calendario, c.abertura(), sla[0], sla[1],
                    c.minutosPausados(), c.dataPrimeiraResposta() != null);
            if (!Objects.equals(novos.primeiraResposta(), c.prazoPrimeiraResposta())
                    || !Objects.equals(novos.resolucao(), c.prazoResolucao())) {
                mudancas.add(new Mudanca(c.id(), novos.primeiraResposta(), novos.resolucao()));
            }
        }
        return mudancas;
    }

    public static String chave(long areaId, String prioridade) {
        return areaId + ":" + prioridade;
    }

    // =================================================================
    // Leitura (JDBC)
    // =================================================================

    private static Map<String, int[]> carregarSlas(Connection conexao) throws Exception {
        Map<String, int[]> slas = new HashMap<>();
        try (Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT area_id, prioridade, minutos_primeira_resposta, minutos_resolucao FROM sla_config")) {
            while (rs.next()) {
                slas.put(chave(rs.getLong(1), rs.getString(2)), new int[]{rs.getInt(3), rs.getInt(4)});
            }
        }
        return slas;
    }

    private static Map<Long, CalendarioExpediente> carregarCalendarios(Connection conexao) throws Exception {
        Map<Long, Map<DayOfWeek, List<Periodo>>> periodos = new HashMap<>();
        try (Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT area_id, dia_semana, hora_inicio, hora_fim FROM expediente ORDER BY area_id, dia_semana, hora_inicio")) {
            while (rs.next()) {
                periodos.computeIfAbsent(rs.getLong(1), a -> new EnumMap<>(DayOfWeek.class))
                        .computeIfAbsent(DayOfWeek.of(rs.getInt(2)), d -> new ArrayList<>())
                        .add(new Periodo(rs.getObject(3, LocalTime.class), rs.getObject(4, LocalTime.class)));
            }
        }
        List<LocalDate> feriadosGerais = new ArrayList<>();
        Map<Long, List<LocalDate>> feriadosDaArea = new HashMap<>();
        try (Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery("SELECT data, area_id FROM feriado")) {
            while (rs.next()) {
                LocalDate data = rs.getObject(1, LocalDate.class);
                long area = rs.getLong(2);
                if (rs.wasNull()) {
                    feriadosGerais.add(data);
                } else {
                    feriadosDaArea.computeIfAbsent(area, a -> new ArrayList<>()).add(data);
                }
            }
        }
        Map<Long, CalendarioExpediente> calendarios = new HashMap<>();
        try (Statement st = conexao.createStatement(); ResultSet rs = st.executeQuery("SELECT id FROM area")) {
            while (rs.next()) {
                long area = rs.getLong(1);
                List<LocalDate> feriados = new ArrayList<>(feriadosGerais);
                feriados.addAll(feriadosDaArea.getOrDefault(area, List.of()));
                calendarios.put(area, new CalendarioExpediente(periodos.getOrDefault(area, Map.of()), feriados));
            }
        }
        return calendarios;
    }

    private static List<LinhaChamado> carregarChamadosEmAndamento(Connection conexao) throws Exception {
        List<LinhaChamado> chamados = new ArrayList<>();
        try (Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT id, area_id, prioridade, data_abertura, data_primeira_resposta, minutos_pausados,
                            prazo_primeira_resposta, prazo_resolucao
                       FROM chamado
                      WHERE status NOT IN ('RESOLVIDO', 'FECHADO')
                      ORDER BY id""")) {
            while (rs.next()) {
                chamados.add(new LinhaChamado(
                        rs.getLong("id"),
                        rs.getLong("area_id"),
                        rs.getString("prioridade"),
                        rs.getObject("data_abertura", LocalDateTime.class),
                        rs.getObject("data_primeira_resposta", LocalDateTime.class),
                        rs.getLong("minutos_pausados"),
                        rs.getObject("prazo_primeira_resposta", LocalDateTime.class),
                        rs.getObject("prazo_resolucao", LocalDateTime.class)));
            }
        }
        return chamados;
    }
}
