package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.Prioridade;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Tudo o que a tela da fila mostra (montado pelo FilaService).
 */
public record FilaTela(
        AbaFila aba,
        List<Aba> abas,
        List<LinhaFila> linhas,
        Filtros filtros,
        Paginacao paginacao,
        List<OpcaoCategoria> categorias,
        List<OpcaoArea> areas,
        List<Prioridade> prioridades,
        boolean mostrarFiltroArea,
        String areaDescricao) {

    /** Uma aba com o contador. */
    public record Aba(AbaFila aba, long total, boolean ativa) {
    }

    /** Filtros que a pessoa escolheu (já conferidos). */
    /**
     * @param atendenteId   filtro por responsável (atalho da administração), com o nome para o aviso na tela
     */
    public record Filtros(String busca, Prioridade prioridade, Long categoriaId, Long areaId,
                          Long atendenteId, String atendenteNome) {

        public boolean algumAtivo() {
            return (busca != null && !busca.isBlank()) || prioridade != null || categoriaId != null || areaId != null
                    || atendenteId != null;
        }
    }

    public record Paginacao(int pagina, int totalPaginas, long totalItens, long primeiroItem, long ultimoItem) {

        public boolean temAnterior() {
            return pagina > 1;
        }

        public boolean temProxima() {
            return pagina < totalPaginas;
        }

        /** Números de página para mostrar (até 5 em volta da atual). */
        public List<Integer> paginasVisiveis() {
            int inicio = Math.max(1, pagina - 2);
            int fim = Math.min(totalPaginas, inicio + 4);
            inicio = Math.max(1, fim - 4);
            return java.util.stream.IntStream.rangeClosed(inicio, fim).boxed().toList();
        }
    }

    public record OpcaoCategoria(Long id, String nome, String areaNome) {
    }

    public record OpcaoArea(Long id, String nome) {
    }

    public String titulo() {
        return aba == AbaFila.MEUS ? "Meus chamados" : "Fila de chamados";
    }

    /** Link para outra aba mantendo os filtros (volta para a página 1). */
    public String urlAba(AbaFila outra) {
        return url(outra, 1);
    }

    /** Link para outra página da aba atual mantendo os filtros. */
    public String urlPagina(int pagina) {
        return url(aba, pagina);
    }

    /** Link que tira só o filtro de responsável (mantém aba e os outros filtros). */
    public String urlSemAtendente() {
        return new FilaTela(aba, abas, linhas,
                new Filtros(filtros.busca(), filtros.prioridade(), filtros.categoriaId(), filtros.areaId(), null, null),
                paginacao, categorias, areas, prioridades, mostrarFiltroArea, areaDescricao).url(aba, 1);
    }

    /** Botão "Kanban": a mesma fila em Kanban, mantendo os filtros (aba "Meus" vira "Só os meus"). */
    public String urlKanban() {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/atendimento").queryParam("visao", "kanban");
        if (aba == AbaFila.MEUS) {
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
        if (filtros.atendenteId() != null) {
            b.queryParam("atendente", filtros.atendenteId());
        }
        return b.encode().build().toUriString();
    }

    /** Link "Limpar filtros" (mantém só a aba). */
    public String urlLimpar() {
        return "/atendimento?filtro=" + aba.getParametro();
    }

    private String url(AbaFila destino, int pagina) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/atendimento")
                .queryParam("filtro", destino.getParametro());
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
        if (filtros.atendenteId() != null) {
            b.queryParam("atendente", filtros.atendenteId());
        }
        if (pagina > 1) {
            b.queryParam("pagina", pagina);
        }
        return b.encode().build().toUriString();
    }
}
