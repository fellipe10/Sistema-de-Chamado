package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.atendimento.FilaConsultas.CriteriosColuna;
import br.com.goias.chamados.atendimento.FilaService.FiltrosConferidos;
import br.com.goias.chamados.atendimento.FilaTela.Filtros;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosExpediente;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.login.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta a fila em Kanban (GET /atendimento?visao=kanban e GET /atendimento/quadro) — SÓ VISUALIZAÇÃO.
 *
 * - Mesmas regras de visibilidade e filtros da Lista ({@link FilaService#conferir}):
 *   ATENDENTE e GESTOR só a área deles; ADMIN todas, com filtro de área.
 * - Colunas: {@link ColunaKanban}. Até {@link #LIMITE_POR_COLUNA} cards por coluna, com o total no cabeçalho.
 * - Ordem dentro da coluna: igual à Lista (prazo de resolução mais próximo primeiro);
 *   na coluna "Resolvido / Fechado", a resolução mais recente primeiro.
 * - "Só os meus" = responsável é o usuário logado.
 */
@Service
@RequiredArgsConstructor
public class KanbanService {

    public static final int LIMITE_POR_COLUNA = 30;
    public static final int DIAS_RESOLVIDOS = 7;
    /** Intervalo da atualização automática (usado pela tela). */
    public static final int INTERVALO_ATUALIZACAO_SEGUNDOS = 30;

    private final FilaService filaService;
    private final FilaConsultas consultas;
    private final CalendariosExpediente calendarios;

    /** Parâmetros como vieram da URL. */
    public record Parametros(String busca, String prioridade, Long categoria, Long area, Long atendente,
                             boolean soMeus) {
    }

    @Transactional(readOnly = true)
    public KanbanTela montar(UsuarioLogado usuario, Parametros p) {
        return montar(usuario, p, LocalDateTime.now());
    }

    /** Versão com o "agora" informado (usada nos testes). */
    KanbanTela montar(UsuarioLogado usuario, Parametros p, LocalDateTime agora) {
        FiltrosConferidos fc = filaService.conferir(usuario, p.busca(), p.prioridade(), p.categoria(), p.area(),
                p.soMeus() ? null : p.atendente());
        Long responsavel = p.soMeus() ? usuario.getId() : fc.atendenteId();
        Filtros filtros = p.soMeus()
                ? new Filtros(fc.busca(), fc.prioridade(), fc.categoriaId(), fc.areaFiltro(), null, null)
                : fc.paraTela();

        Map<Long, CalendarioExpediente> calendarioPorArea = new HashMap<>();
        List<KanbanTela.Coluna> colunas = new ArrayList<>();
        for (ColunaKanban coluna : ColunaKanban.values()) {
            CriteriosColuna criterios = criterios(coluna, fc, responsavel, agora);
            long total = consultas.contarColuna(criterios);
            List<KanbanTela.Card> cards = total == 0 ? List.of()
                    : consultas.listarColuna(criterios, LIMITE_POR_COLUNA).stream()
                    .map(c -> new KanbanTela.Card(
                            FilaService.paraLinha(c, calendarioPorArea.computeIfAbsent(c.getArea().getId(),
                                    calendarios::daArea), agora),
                            c.getAguardandoMotivo()))
                    .toList();
            colunas.add(new KanbanTela.Coluna(coluna, total, cards, urlVerTodos(coluna, filtros, p.soMeus())));
        }

        return new KanbanTela(colunas, filtros, p.soMeus(), fc.categorias(), fc.opcoesArea(),
                List.of(Prioridade.values()), usuario.isAdmin(), fc.areaDescricao(), agora);
    }

    /** Critérios de uma coluna (regra pura, testável). */
    static CriteriosColuna criterios(ColunaKanban coluna, FiltrosConferidos fc, Long responsavel, LocalDateTime agora) {
        return new CriteriosColuna(fc.areaEscopo(), fc.busca(), fc.prioridade(), fc.categoriaId(), responsavel,
                coluna.getStatus(),
                coluna == ColunaKanban.RESOLVIDO ? inicioResolvidos(agora) : null,
                coluna == ColunaKanban.RESOLVIDO);
    }

    /** A coluna "Resolvido / Fechado" mostra os resolvidos a partir deste momento (hoje − 7 dias, mesmo horário). */
    static LocalDateTime inicioResolvidos(LocalDateTime agora) {
        return agora.minusDays(DIAS_RESOLVIDOS);
    }

    /** Link "ver todos" de uma coluna: a Lista na aba equivalente, com os mesmos filtros. */
    static String urlVerTodos(ColunaKanban coluna, Filtros f, boolean soMeus) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/atendimento")
                .queryParam("visao", "lista")
                .queryParam("filtro", soMeus ? AbaFila.MEUS.getParametro() : coluna.getAbaDaLista().getParametro());
        if (f.busca() != null && !f.busca().isBlank()) {
            b.queryParam("busca", f.busca());
        }
        if (f.prioridade() != null) {
            b.queryParam("prioridade", f.prioridade().name());
        }
        if (f.categoriaId() != null) {
            b.queryParam("categoria", f.categoriaId());
        }
        if (f.areaId() != null) {
            b.queryParam("area", f.areaId());
        }
        if (f.atendenteId() != null) {
            b.queryParam("atendente", f.atendenteId());
        }
        return b.encode().build().toUriString();
    }
}
