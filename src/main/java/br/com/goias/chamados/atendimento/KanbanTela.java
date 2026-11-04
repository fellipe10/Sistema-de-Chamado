package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.atendimento.FilaTela.Filtros;
import br.com.goias.chamados.atendimento.FilaTela.OpcaoArea;
import br.com.goias.chamados.atendimento.FilaTela.OpcaoCategoria;
import br.com.goias.chamados.dominio.chamado.MotivoAguardando;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tudo o que a fila em Kanban mostra (montado pelo {@link KanbanService}).
 *
 * @param soMeus        "Só os meus" marcado (responsável = usuário logado)
 * @param atualizadoEm  quando o quadro foi montado ("Atualizado às HH:MM:SS")
 */
public record KanbanTela(
        List<Coluna> colunas,
        Filtros filtros,
        boolean soMeus,
        List<OpcaoCategoria> categorias,
        List<OpcaoArea> areas,
        List<Prioridade> prioridades,
        boolean mostrarFiltroArea,
        String areaDescricao,
        LocalDateTime atualizadoEm) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Uma coluna: os primeiros cards (até o limite) e o total de chamados da coluna.
     *
     * @param urlVerTodos Lista já filtrada, para quando há mais cards do que o limite
     */
    public record Coluna(ColunaKanban coluna, long total, List<Card> cards, String urlVerTodos) {

        /** Quantos chamados ficaram de fora do quadro (passaram do limite). */
        public long restantes() {
            return Math.max(0, total - cards.size());
        }
    }

    /**
     * Um card. Reaproveita a linha da Lista (mesmos dados e mesmo selo de prazo).
     *
     * @param motivo motivo do AGUARDANDO (null nos outros status)
     */
    public record Card(LinhaFila linha, MotivoAguardando motivo) {

        public boolean reaberto() {
            return linha.status() == StatusChamado.REABERTO;
        }

        /** Na coluna "Resolvido / Fechado": selo com o status do chamado. */
        public boolean finalizado() {
            return linha.status() == StatusChamado.RESOLVIDO || linha.status() == StatusChamado.FECHADO;
        }
    }

    public long totalNoQuadro() {
        return colunas.stream().mapToLong(Coluna::total).sum();
    }

    public String horaAtualizacao() {
        return HORA.format(atualizadoEm);
    }

    public String titulo() {
        return soMeus ? "Meus chamados" : "Fila de chamados";
    }

    public boolean algumFiltro() {
        return filtros.algumAtivo() || soMeus;
    }

    /** Endereço da página (ou, com "/atendimento/quadro", só do quadro) com os filtros atuais. */
    public String url(String caminho, String visao) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(caminho);
        if (visao != null) {
            b.queryParam("visao", visao);
        }
        aplicarFiltros(b, true);
        return b.encode().build().toUriString();
    }

    /** Endereço do quadro para a atualização automática. */
    public String urlQuadro() {
        return url("/atendimento/quadro", null);
    }

    /** Botão "Lista": mesma fila na Lista, mantendo os filtros ("Só os meus" vira a aba Meus). */
    public String urlLista() {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/atendimento")
                .queryParam("visao", "lista");
        if (soMeus) {
            b.queryParam("filtro", AbaFila.MEUS.getParametro());
        }
        aplicarFiltros(b, false);
        return b.encode().build().toUriString();
    }

    /** "Limpar" filtros (continua no Kanban). */
    public String urlLimpar() {
        return "/atendimento?visao=kanban";
    }

    private void aplicarFiltros(UriComponentsBuilder b, boolean comMeus) {
        if (comMeus && soMeus) {
            b.queryParam("meus", "true");
        }
        if (filtros.busca() != null && !filtros.busca().isBlank()) {
            b.queryParam("busca", filtros.busca());
        }
        if (filtros.prioridade() != null) {
            b.queryParam("prioridade", filtros.prioridade().name());
        }
        if (filtros.categoriaId() != null) {
            b.queryParam("categoria", filtros.categoriaId());
        }
        if (filtros.areaId() != null) {
            b.queryParam("area", filtros.areaId());
        }
        if (!soMeus && filtros.atendenteId() != null) {
            b.queryParam("atendente", filtros.atendenteId());
        }
    }
}
