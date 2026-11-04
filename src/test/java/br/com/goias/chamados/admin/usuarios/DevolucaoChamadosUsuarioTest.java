package br.com.goias.chamados.admin.usuarios;

import br.com.goias.chamados.atendimento.DevolucaoFila;
import br.com.goias.chamados.dominio.usuario.Perfil;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import br.com.goias.chamados.login.UsuarioLogado;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ajuste 2: chamados em andamento voltam para a fila ao mudar a área do responsável ou ao desativá-lo.
 *
 * Roda no PostgreSQL de desenvolvimento (como o contextLoads), DENTRO de uma transação que é desfeita no fim:
 * nada do que o teste cria fica no banco.
 */
@SpringBootTest
@Transactional
class DevolucaoChamadosUsuarioTest {

    @Autowired
    UsuariosService service;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager em;

    UsuarioLogado admin;
    Long ti;
    Long man;
    Long atendenteId;

    @BeforeEach
    void preparar() {
        admin = new UsuarioLogado(usuarioRepository.findByLogin("admin").orElseThrow());
        ti = jdbc.queryForObject("select id from area where sigla = 'TI'", Long.class);
        man = jdbc.queryForObject("select id from area where sigla = 'MAN'", Long.class);

        UsuarioForm form = new UsuarioForm();
        form.setNome("Teste Devolução");
        form.setLogin("teste.devolucao");
        form.setPerfil(Perfil.ATENDENTE);
        form.setAreaId(ti);
        UsuariosService.Resultado r = service.criar(form);
        assertThat(r.sucesso()).as("%s", r.erros()).isTrue();
        atendenteId = r.id();
    }

    @Test
    void mudarAreaPedeConfirmacaoEDepoisDevolveParaAFila() {
        long semResposta = chamado("EM_ATENDIMENTO", null, false);
        long aguardando = chamado("AGUARDANDO", "SOLICITANTE", false);
        long jaRespondido = chamado("EM_ATENDIMENTO", null, true);
        long resolvido = chamado("RESOLVIDO", null, true);

        UsuarioForm form = formDoAtendente(man);

        // 1) sem confirmar: nada muda, pede confirmação com 3 chamados em andamento
        UsuariosService.Resultado semConfirmar = service.salvar(atendenteId, form, admin);
        assertThat(semConfirmar.confirmarArea()).isEqualTo(3);
        em.flush();
        assertThat(responsavel(semResposta)).isEqualTo(atendenteId);

        // 2) confirmando: muda a área e devolve os 3
        form.setConfirmarMudancaArea(true);
        UsuariosService.Resultado r = service.salvar(atendenteId, form, admin);
        assertThat(r.sucesso()).isTrue();
        assertThat(r.aviso()).startsWith("3 chamados em andamento voltaram para a fila da área TI")
                .contains("sem responsável");
        em.flush();

        assertThat(jdbc.queryForObject("select area_id from usuario where id = ?", Long.class, atendenteId)).isEqualTo(man);

        assertThat(responsavel(semResposta)).isNull();
        assertThat(status(semResposta)).isEqualTo("ABERTO");

        assertThat(responsavel(aguardando)).isNull();
        assertThat(status(aguardando)).isEqualTo("ABERTO");
        Map<String, Object> pausa = jdbc.queryForMap(
                "select aguardando_motivo, pausa_inicio from chamado where id = ?", aguardando);
        assertThat(pausa.get("aguardando_motivo")).isNull();
        assertThat(pausa.get("pausa_inicio")).isNull();

        assertThat(responsavel(jaRespondido)).isNull();
        assertThat(status(jaRespondido)).isEqualTo("EM_ATENDIMENTO");

        // resolvido não é "em andamento": continua com a pessoa
        assertThat(responsavel(resolvido)).isEqualTo(atendenteId);
        assertThat(status(resolvido)).isEqualTo("RESOLVIDO");

        // ATRIBUICAO interna com o texto combinado, autor = admin
        for (long id : new long[]{semResposta, aguardando, jaRespondido}) {
            Map<String, Object> atribuicao = jdbc.queryForMap("""
                    select texto, interno, autor_id from chamado_interacao
                     where chamado_id = ? and tipo = 'ATRIBUICAO' order by id desc limit 1""", id);
            assertThat(atribuicao.get("texto")).isEqualTo(DevolucaoFila.TEXTO_MUDANCA_AREA)
                    .isEqualTo("Devolvido à fila por mudança de área do responsável.");
            assertThat(atribuicao.get("interno")).isEqualTo(true);
            assertThat(((Number) atribuicao.get("autor_id")).longValue()).isEqualTo(admin.getId());
        }
        // quem voltou para ABERTO ganhou a MUDANCA_STATUS (aparece na consulta pública)
        assertThat(jdbc.queryForObject("""
                select count(*) from chamado_interacao
                 where chamado_id = ? and tipo = 'MUDANCA_STATUS' and status_novo = 'ABERTO'""", Long.class, semResposta))
                .isEqualTo(1);

        assertThat(service.contarEmAndamento(atendenteId)).isZero();
    }

    @Test
    void mudarAreaSemChamadosNaoPedeConfirmacao() {
        UsuariosService.Resultado r = service.salvar(atendenteId, formDoAtendente(man), admin);
        assertThat(r.sucesso()).isTrue();
        assertThat(r.aviso()).doesNotContain("voltaram");
    }

    @Test
    void desativarDevolvendoOsChamados() {
        long a = chamado("EM_ATENDIMENTO", null, false);
        long b = chamado("AGUARDANDO", "TERCEIRO", true);

        UsuariosService.ResultadoSituacao r = service.alterarSituacao(atendenteId, false, true, admin);
        assertThat(r.erro()).isNull();
        assertThat(r.devolvidos()).isEqualTo(2);
        assertThat(r.emAndamento()).isZero();
        em.flush();

        assertThat(responsavel(a)).isNull();
        assertThat(status(a)).isEqualTo("ABERTO");
        assertThat(responsavel(b)).isNull();
        assertThat(status(b)).isEqualTo("AGUARDANDO"); // já tinha resposta: continua aguardando, sem responsável
        assertThat(jdbc.queryForObject("""
                select texto from chamado_interacao
                 where chamado_id = ? and tipo = 'ATRIBUICAO' order by id desc limit 1""", String.class, a))
                .isEqualTo(DevolucaoFila.TEXTO_DESATIVACAO);
        assertThat(jdbc.queryForObject("select ativo from usuario where id = ?", Boolean.class, atendenteId)).isFalse();
    }

    @Test
    void desativarSemDevolverMantemComAPessoa() {
        long a = chamado("EM_ATENDIMENTO", null, false);

        UsuariosService.ResultadoSituacao r = service.alterarSituacao(atendenteId, false, false, admin);
        assertThat(r.devolvidos()).isZero();
        assertThat(r.emAndamento()).isEqualTo(1);
        em.flush();

        assertThat(responsavel(a)).isEqualTo(atendenteId);
        assertThat(status(a)).isEqualTo("EM_ATENDIMENTO");
    }

    @Test
    void criarSemUsuarioDevolveSugestaoParaConferir() {
        UsuarioForm form = new UsuarioForm();
        form.setNome("Teste Devolução");          // "teste.devolucao" já existe (criado no @BeforeEach)
        form.setPerfil(Perfil.ADMIN);
        UsuariosService.Resultado r = service.criar(form);
        assertThat(r.sucesso()).isFalse();
        assertThat(r.erros()).containsEntry("login", RegrasUsuario.LOGIN_SUGERIDO);
        assertThat(form.getLogin()).isEqualTo("teste.devolucao-2");
    }

    @Test
    void usuarioEEmailRepetidos() {
        jdbc.update("update usuario set email = 'teste.dev@goiasec.com.br' where id = ?", atendenteId);
        UsuarioForm form = new UsuarioForm();
        form.setNome("Outra Pessoa");
        form.setLogin(" Teste.Devolucao ");            // normaliza e encontra o repetido
        form.setEmail("TESTE.DEV@goiasec.com.br");
        form.setPerfil(Perfil.ADMIN);
        UsuariosService.Resultado r = service.criar(form);
        assertThat(r.erros()).containsEntry("login", RegrasUsuario.LOGIN_REPETIDO)
                .containsEntry("email", RegrasUsuario.EMAIL_REPETIDO);
        assertThat(service.sugerirLogin("Teste Devolução", null)).isEqualTo("teste.devolucao-2");
        assertThat(service.sugerirLogin("Teste Devolução", atendenteId)).isEqualTo("teste.devolucao");
        assertThat(service.sugerirLogin("Atendente Ti", null)).isEqualTo("atendente.ti-2"); // reservado
    }

    @Test
    void criarComEmailVazioGravaNulo() {
        UsuarioForm form = new UsuarioForm();
        form.setNome("Sem Email");
        form.setLogin("SEM.Email");
        form.setEmail("   ");
        form.setPerfil(Perfil.ADMIN);
        UsuariosService.Resultado r = service.criar(form);
        assertThat(r.sucesso()).as("%s", r.erros()).isTrue();
        Map<String, Object> linha = jdbc.queryForMap("select login, email from usuario where id = ?", r.id());
        assertThat(linha.get("login")).isEqualTo("sem.email");
        assertThat(linha.get("email")).isNull();
    }

    // -----------------------------------------------------------------

    private UsuarioForm formDoAtendente(Long area) {
        UsuarioForm form = new UsuarioForm();
        form.setNome("Teste Devolução");
        form.setLogin("teste.devolucao");
        form.setPerfil(Perfil.ATENDENTE);
        form.setAreaId(area);
        return form;
    }

    /** Chamado da TI com o atendente de teste como responsável. */
    private long chamado(String status, String motivo, boolean comResposta) {
        LocalDateTime abertura = LocalDateTime.now().minusDays(3);
        String protocolo = "TI-2026-" + codigo();
        Long id = jdbc.queryForObject("""
                        insert into chamado (protocolo, area_id, categoria_id, setor_id, local_id, solicitante_nome,
                                             titulo, descricao, urgencia, prioridade, status, atendente_id,
                                             data_abertura, prazo_resolucao, aguardando_motivo, pausa_inicio)
                        values (?, ?, (select min(id) from categoria where area_id = ?), (select min(id) from setor),
                                (select min(id) from local), 'Solicitante Teste', 'Teste', 'Teste', 'MEDIA', 'MEDIA',
                                ?, ?, ?, ?, ?, ?)
                        returning id""", Long.class,
                protocolo, ti, ti, status, atendenteId, Timestamp.valueOf(abertura),
                Timestamp.valueOf(abertura.plusDays(5)), motivo,
                "SOLICITANTE".equals(motivo) ? Timestamp.valueOf(LocalDateTime.now().minusDays(1)) : null);
        if (comResposta) {
            jdbc.update("""
                    insert into chamado_interacao (chamado_id, autor_id, tipo, texto, interno)
                    values (?, ?, 'RESPOSTA_SOLICITANTE', 'Olá', false)""", id, atendenteId);
        }
        return id;
    }

    private static String codigo() {
        String alfabeto = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            s.append(alfabeto.charAt(ThreadLocalRandom.current().nextInt(alfabeto.length())));
        }
        return s.toString();
    }

    private Long responsavel(long chamadoId) {
        return jdbc.queryForObject("select atendente_id from chamado where id = ?", Long.class, chamadoId);
    }

    private String status(long chamadoId) {
        return jdbc.queryForObject("select status from chamado where id = ?", String.class, chamadoId);
    }
}
