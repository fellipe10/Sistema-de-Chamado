package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.atendimento.FilaTela.Filtros;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras puras do Kanban: status → coluna, corte dos resolvidos, links e parâmetros. */
class ColunaKanbanTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "ABERTO,         ABERTO",
            "REABERTO,       ABERTO",
            "EM_ATENDIMENTO, EM_ATENDIMENTO",
            "AGUARDANDO,     AGUARDANDO",
            "RESOLVIDO,      RESOLVIDO",
            "FECHADO,        RESOLVIDO"
    })
    void statusVaiParaAColunaCerta(StatusChamado status, ColunaKanban esperada) {
        assertThat(ColunaKanban.doStatus(status)).contains(esperada);
    }

    @Test
    void todoStatusTemColuna() {
        for (StatusChamado s : StatusChamado.values()) {
            assertThat(ColunaKanban.doStatus(s)).as(s.name()).isPresent();
        }
    }

    @Test
    void ordemEChavesDasColunas() {
        assertThat(ColunaKanban.values()).extracting(ColunaKanban::getRotulo)
                .containsExactly("Aberto", "Em atendimento", "Aguardando", "Resolvido / Fechado");
        assertThat(ColunaKanban.EM_ATENDIMENTO.getChave()).isEqualTo("em-atendimento");
    }

    @Test
    void cadaStatusEmNoMaximoUmaColuna() {
        for (StatusChamado s : StatusChamado.values()) {
            long colunas = java.util.Arrays.stream(ColunaKanban.values()).filter(c -> c.getStatus().contains(s)).count();
            assertThat(colunas).as(s.name()).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void resolvidosDosUltimos7Dias() {
        LocalDateTime agora = LocalDateTime.of(2026, 11, 4, 14, 30);
        assertThat(KanbanService.inicioResolvidos(agora)).isEqualTo(LocalDateTime.of(2026, 10, 28, 14, 30));
    }

    @Test
    void verTodosAbreAListaNaAbaEquivalenteComOsFiltros() {
        Filtros f = new Filtros("impressora", Prioridade.ALTA, 3L, null, null, null);
        assertThat(KanbanService.urlVerTodos(ColunaKanban.ABERTO, f, false))
                .isEqualTo("/atendimento?visao=lista&filtro=novos&busca=impressora&prioridade=ALTA&categoria=3");
        assertThat(KanbanService.urlVerTodos(ColunaKanban.EM_ATENDIMENTO, f, false)).contains("filtro=andamento");
        assertThat(KanbanService.urlVerTodos(ColunaKanban.AGUARDANDO, f, false)).contains("filtro=aguardando");
        assertThat(KanbanService.urlVerTodos(ColunaKanban.RESOLVIDO, f, false)).contains("filtro=historico");
        // "Só os meus" vira a aba "Meus" da Lista
        assertThat(KanbanService.urlVerTodos(ColunaKanban.RESOLVIDO, f, true)).contains("filtro=meus");
    }

    @Test
    void linksDaTela() {
        KanbanTela meus = tela(new Filtros(null, null, null, 2L, null, null), true);
        assertThat(meus.urlQuadro()).isEqualTo("/atendimento/quadro?meus=true&area=2");
        assertThat(meus.urlLista()).isEqualTo("/atendimento?visao=lista&filtro=meus&area=2");
        assertThat(meus.titulo()).isEqualTo("Meus chamados");

        KanbanTela comResponsavel = tela(new Filtros("a b", null, null, null, 7L, "Rafael"), false);
        assertThat(comResponsavel.url("/atendimento", "kanban"))
                .isEqualTo("/atendimento?visao=kanban&busca=a%20b&atendente=7");
        assertThat(comResponsavel.urlLista()).isEqualTo("/atendimento?visao=lista&busca=a%20b&atendente=7");
    }

    @Test
    void parametrosDoKanban() {
        assertThat(FilaController.parametrosKanban("meus", null, null, null, null, null, null).soMeus()).isTrue();
        assertThat(FilaController.parametrosKanban(null, null, null, null, null, null, "true").soMeus()).isTrue();
        assertThat(FilaController.parametrosKanban("novos", null, null, "abc", "2", null, null))
                .satisfies(p -> {
                    assertThat(p.soMeus()).isFalse();
                    assertThat(p.categoria()).isNull(); // texto inválido é ignorado
                    assertThat(p.area()).isEqualTo(2L);
                });
    }

    @Test
    void visaoDaFila() {
        assertThat(FilaController.Visao.de(" Kanban ")).isEqualTo(FilaController.Visao.KANBAN);
        assertThat(FilaController.Visao.de("lista")).isEqualTo(FilaController.Visao.LISTA);
        assertThat(FilaController.Visao.de("tabela")).isNull();
        assertThat(FilaController.Visao.de(null)).isNull();
    }

    private static KanbanTela tela(Filtros f, boolean soMeus) {
        return new KanbanTela(List.of(), f, soMeus, List.of(), List.of(), List.of(), false, "TI",
                LocalDateTime.of(2026, 11, 4, 14, 32, 10));
    }
}
