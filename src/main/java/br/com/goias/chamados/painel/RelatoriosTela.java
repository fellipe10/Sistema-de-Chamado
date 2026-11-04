package br.com.goias.chamados.painel;

import br.com.goias.chamados.atendimento.FilaTela.Paginacao;
import br.com.goias.chamados.atendimento.LinhaFila;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.painel.PeriodoPainel.Tipo;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tudo o que a tela de relatórios (GET /painel/relatorios) mostra. */
public record RelatoriosTela(
        AreaEscolhida area,
        PeriodoPainel periodo,
        Filtros filtros,
        List<LinhaRelatorio> linhas,
        Paginacao paginacao,
        List<StatusChamado> opcoesStatus,
        List<Prioridade> opcoesPrioridade,
        List<Opcao> opcoesCategoria,
        List<Opcao> opcoesAtendente) {

    /** Filtros já conferidos (null = todos). atendenteId = 0 → "Sem atendente". */
    public record Filtros(StatusChamado status, Prioridade prioridade, Long categoriaId, Long atendenteId) {

        public boolean algumAtivo() {
            return status != null || prioridade != null || categoriaId != null || atendenteId != null;
        }

        Map<String, String> parametros() {
            Map<String, String> p = new LinkedHashMap<>();
            if (status != null) {
                p.put("status", status.name());
            }
            if (prioridade != null) {
                p.put("prioridade", prioridade.name());
            }
            if (categoriaId != null) {
                p.put("categoria", categoriaId.toString());
            }
            if (atendenteId != null) {
                p.put("atendente", atendenteId.toString());
            }
            return p;
        }
    }

    /** Linha da lista = linha da fila + data de abertura. */
    public record LinhaRelatorio(LinhaFila linha, LocalDateTime abertura) {
    }

    public record Opcao(Long id, String nome) {
    }

    public List<PainelTela.OpcaoPeriodo> opcoesPeriodo() {
        return List.of(Tipo.HOJE, Tipo.SETE_DIAS, Tipo.TRINTA_DIAS, Tipo.SEIS_MESES).stream()
                .map(t -> new PainelTela.OpcaoPeriodo(t.getRotulo(),
                        Urls.montar("/painel/relatorios", Map.of("periodo", t.getParametro()), area.parametro(),
                                filtros.parametros()),
                        periodo.tipo() == t))
                .toList();
    }

    /** Campos escondidos do formulário "Período" (mantém área e filtros). */
    public Map<String, String> ocultosPeriodo() {
        Map<String, String> m = new LinkedHashMap<>(area.parametro());
        m.putAll(filtros.parametros());
        return m;
    }

    public String urlPagina(int pagina) {
        return Urls.montar("/painel/relatorios", periodo.parametros(), area.parametro(), filtros.parametros(),
                Map.of("pagina", String.valueOf(pagina)));
    }

    public String urlExportar() {
        return Urls.montar("/painel/relatorios/exportar", periodo.parametros(), area.parametro(), filtros.parametros());
    }

    public String urlLimpar() {
        return Urls.montar("/painel/relatorios", periodo.parametros(), area.parametro());
    }
}
