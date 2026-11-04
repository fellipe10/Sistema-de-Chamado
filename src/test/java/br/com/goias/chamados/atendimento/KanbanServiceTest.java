package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import br.com.goias.chamados.login.UsuarioLogado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Montagem do Kanban no PostgreSQL de desenvolvimento, dentro de uma transação desfeita no fim
 * (nada fica no banco). Os chamados do teste ficam numa categoria própria criada aqui, e o teste
 * filtra por ela — assim os chamados que já existem no banco não atrapalham.
 */
@SpringBootTest
@Transactional
class KanbanServiceTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired
    KanbanService service;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    JdbcTemplate jdbc;

    final LocalDateTime agora = LocalDateTime.now().withNano(0);
    Long ti;
    Long man;
    Long categoriaTi;
    Long categoriaMan;
    UsuarioLogado gestorTi;
    UsuarioLogado atendenteTi;
    UsuarioLogado admin;

    @BeforeEach
    void preparar() {
        ti = jdbc.queryForObject("select id from area where sigla = 'TI'", Long.class);
        man = jdbc.queryForObject("select id from area where sigla = 'MAN'", Long.class);
        categoriaTi = jdbc.queryForObject("insert into categoria (area_id, nome) values (?, 'Kanban teste TI') returning id",
                Long.class, ti);
        categoriaMan = jdbc.queryForObject("insert into categoria (area_id, nome) values (?, 'Kanban teste MAN') returning id",
                Long.class, man);
        gestorTi = new UsuarioLogado(usuarioRepository.findByLogin("gestor.ti").orElseThrow());
        atendenteTi = new UsuarioLogado(usuarioRepository.findByLogin("atendente.ti").orElseThrow());
        admin = new UsuarioLogado(usuarioRepository.findByLogin("admin").orElseThrow());
    }

    @Test
    void colunasComOsStatusCertos() {
        long aberto = chamado(ti, categoriaTi, "ABERTO", null, 5, null);
        long reaberto = chamado(ti, categoriaTi, "REABERTO", atendenteTi.getId(), 2, null);
        long emAtendimento = chamado(ti, categoriaTi, "EM_ATENDIMENTO", atendenteTi.getId(), 3, null);
        long aguardando = chamado(ti, categoriaTi, "AGUARDANDO", atendenteTi.getId(), 4, null);
        long resolvido6 = chamado(ti, categoriaTi, "RESOLVIDO", atendenteTi.getId(), 1, agora.minusDays(6));
        long fechado1 = chamado(ti, categoriaTi, "FECHADO", atendenteTi.getId(), 1, agora.minusDays(1));
        chamado(ti, categoriaTi, "RESOLVIDO", atendenteTi.getId(), 1, agora.minusDays(8));     // fora: mais de 7 dias
        chamado(ti, categoriaTi, "FECHADO", atendenteTi.getId(), 1, agora.minusDays(9));       // fora: mais de 7 dias

        KanbanTela tela = montar(gestorTi, categoriaTi, null, false);

        assertThat(ids(tela, ColunaKanban.ABERTO)).containsExactlyInAnyOrder(aberto, reaberto);
        assertThat(ids(tela, ColunaKanban.EM_ATENDIMENTO)).containsExactly(emAtendimento);
        assertThat(ids(tela, ColunaKanban.AGUARDANDO)).containsExactly(aguardando);
        // "Resolvido / Fechado": mais recente primeiro
        assertThat(ids(tela, ColunaKanban.RESOLVIDO)).containsExactly(fechado1, resolvido6);
        assertThat(coluna(tela, ColunaKanban.RESOLVIDO).cards()).allSatisfy(c -> assertThat(c.finalizado()).isTrue());
        assertThat(tela.totalNoQuadro()).isEqualTo(6);

        KanbanTela.Card cardReaberto = coluna(tela, ColunaKanban.ABERTO).cards().stream()
                .filter(c -> c.linha().id() == reaberto).findFirst().orElseThrow();
        assertThat(cardReaberto.reaberto()).isTrue();
        KanbanTela.Card cardAguardando = coluna(tela, ColunaKanban.AGUARDANDO).cards().get(0);
        assertThat(cardAguardando.motivo()).isNotNull();
        assertThat(cardAguardando.linha().badgeSla().texto()).isNotBlank();
    }

    @Test
    void ordemPeloPrazoMaisProximoEResolvidoMaisRecentePrimeiro() {
        long depois = chamado(ti, categoriaTi, "ABERTO", null, 48, null);
        long atrasado = chamado(ti, categoriaTi, "ABERTO", null, -2, null);
        long logo = chamado(ti, categoriaTi, "ABERTO", null, 1, null);
        long resolvidoAntigo = chamado(ti, categoriaTi, "RESOLVIDO", null, 1, agora.minusDays(5));
        long resolvidoRecente = chamado(ti, categoriaTi, "RESOLVIDO", null, 1, agora.minusHours(2));

        KanbanTela tela = montar(gestorTi, categoriaTi, null, false);

        assertThat(ids(tela, ColunaKanban.ABERTO)).containsExactly(atrasado, logo, depois);
        assertThat(ids(tela, ColunaKanban.RESOLVIDO)).containsExactly(resolvidoRecente, resolvidoAntigo);
    }

    @Test
    void limiteDe30CardsPorColunaComOTotal() {
        for (int i = 0; i < 35; i++) {
            chamado(ti, categoriaTi, "ABERTO", null, i + 1, null);
        }
        KanbanTela.Coluna aberto = coluna(montar(gestorTi, categoriaTi, null, false), ColunaKanban.ABERTO);
        assertThat(aberto.cards()).hasSize(KanbanService.LIMITE_POR_COLUNA);
        assertThat(aberto.total()).isEqualTo(35);
        assertThat(aberto.restantes()).isEqualTo(5);
        assertThat(aberto.urlVerTodos()).contains("filtro=novos").contains("categoria=" + categoriaTi);
    }

    @Test
    void soOsMeus() {
        long meu = chamado(ti, categoriaTi, "EM_ATENDIMENTO", atendenteTi.getId(), 3, null);
        chamado(ti, categoriaTi, "EM_ATENDIMENTO", gestorTi.getId(), 3, null);
        chamado(ti, categoriaTi, "ABERTO", null, 3, null);

        KanbanTela tela = montar(atendenteTi, categoriaTi, null, true);
        assertThat(tela.soMeus()).isTrue();
        assertThat(tela.totalNoQuadro()).isEqualTo(1);
        assertThat(ids(tela, ColunaKanban.EM_ATENDIMENTO)).containsExactly(meu);
    }

    @Test
    void atendenteEGestorSoVeemAPropriaArea() {
        chamado(man, categoriaMan, "ABERTO", null, 3, null);

        // categoria de outra área é ignorada: o gestor da TI continua vendo só a TI
        KanbanTela tela = montar(gestorTi, categoriaMan, man, false);
        assertThat(tela.filtros().categoriaId()).isNull();
        assertThat(tela.filtros().areaId()).isNull();
        assertThat(tela.colunas()).flatExtracting(KanbanTela.Coluna::cards)
                .allSatisfy(c -> assertThat(((KanbanTela.Card) c).linha().areaSigla()).isEqualTo("TI"));
    }

    @Test
    void adminEscolheAArea() {
        long daManutencao = chamado(man, categoriaMan, "ABERTO", null, 3, null);
        KanbanTela tela = montar(admin, categoriaMan, man, false);
        assertThat(tela.mostrarFiltroArea()).isTrue();
        assertThat(tela.filtros().areaId()).isEqualTo(man);
        assertThat(ids(tela, ColunaKanban.ABERTO)).containsExactly(daManutencao);
    }

    // -----------------------------------------------------------------

    private KanbanTela montar(UsuarioLogado usuario, Long categoria, Long area, boolean soMeus) {
        return service.montar(usuario, new KanbanService.Parametros(null, null, categoria, area, null, soMeus), agora);
    }

    private static KanbanTela.Coluna coluna(KanbanTela tela, ColunaKanban qual) {
        return tela.colunas().stream().filter(c -> c.coluna() == qual).findFirst().orElseThrow();
    }

    private static List<Long> ids(KanbanTela tela, ColunaKanban qual) {
        return coluna(tela, qual).cards().stream().map(c -> c.linha().id()).toList();
    }

    /** Chamado com prazo de resolução daqui a N horas (negativo = atrasado). */
    private long chamado(Long area, Long categoria, String status, Long atendente, int horasAtePrazo,
                         LocalDateTime resolucao) {
        String sigla = area.equals(ti) ? "TI" : "MAN";
        String protocolo = sigla + "-2026-" + codigo();
        return jdbc.queryForObject("""
                        insert into chamado (protocolo, area_id, categoria_id, setor_id, local_id, solicitante_nome,
                                             titulo, descricao, urgencia, prioridade, status, atendente_id,
                                             data_abertura, prazo_resolucao, data_resolucao, data_fechamento,
                                             aguardando_motivo)
                        values (?, ?, ?, (select min(id) from setor), (select min(id) from local), 'Teste', 'Kanban',
                                'Teste', 'MEDIA', 'MEDIA', ?, ?, ?, ?, ?, ?, ?)
                        returning id""", Long.class,
                protocolo, area, categoria, status, atendente, Timestamp.valueOf(agora.minusDays(10)),
                Timestamp.valueOf(agora.plusHours(horasAtePrazo)),
                resolucao == null ? null : Timestamp.valueOf(resolucao),
                "FECHADO".equals(status) ? Timestamp.valueOf(agora) : null,
                "AGUARDANDO".equals(status) ? "TERCEIRO" : null);
    }

    /** Código único de 4 caracteres do alfabeto do protocolo (sem 0/O/1/I/L). */
    private static String codigo() {
        String alfabeto = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
        int n = SEQUENCIA.incrementAndGet() + 20_000;
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            s.append(alfabeto.charAt(n % alfabeto.length()));
            n /= alfabeto.length();
        }
        return s.reverse().toString();
    }
}
