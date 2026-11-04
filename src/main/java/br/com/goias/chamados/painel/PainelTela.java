package br.com.goias.chamados.painel;

import br.com.goias.chamados.atendimento.LinhaFila;
import br.com.goias.chamados.painel.Indicadores.Sla;
import br.com.goias.chamados.painel.PeriodoPainel.Tipo;

import java.util.List;
import java.util.Map;

/**
 * Tudo o que o painel do gestor (GET /painel) mostra — mesma ordem da prévia aprovada.
 *
 * Situação atual (não depende do período): abertos, em atendimento, atrasados.
 * Do período: resolvidos, tempos médios (horas úteis, descontando pausas), SLA, gráfico,
 * por categoria e desempenho por atendente.
 */
public record PainelTela(
        AreaEscolhida area,
        PeriodoPainel periodo,
        // cards da situação atual
        long abertos,
        long novosHoje,
        long emAtendimento,
        long atendentesAtivos,
        long atrasados,
        // cards do período
        long resolvidos,
        String tempoPrimeiraResposta,
        String notaPrimeiraResposta,
        String tempoResolucao,
        Sla sla,
        // gráfico e listas
        GraficoPainel grafico,
        String graficoJson,
        List<ItemCategoria> categorias,
        List<LinhaFila> precisamDeAtencao,
        List<ItemAtendente> atendentes) {

    /** Barra de "Por categoria" (pct = largura em relação à maior; agrupado = "Demais categorias"). */
    public record ItemCategoria(String nome, long quantidade, int pct, boolean agrupado) {
    }

    /** Linha de "Desempenho por atendente". */
    public record ItemAtendente(String iniciais, String nome, long resolvidos, int pct, String tempoMedio) {
    }

    /** Botão do filtro de período. */
    public record OpcaoPeriodo(String rotulo, String url, boolean ativo) {
    }

    public String titulo() {
        return "Painel " + area.preposicao() + " " + area.nome();
    }

    public List<OpcaoPeriodo> opcoesPeriodo() {
        return List.of(Tipo.HOJE, Tipo.SETE_DIAS, Tipo.TRINTA_DIAS, Tipo.SEIS_MESES).stream()
                .map(t -> new OpcaoPeriodo(t.getRotulo(),
                        Urls.montar("/painel", Map.of("periodo", t.getParametro()), area.parametro()),
                        periodo.tipo() == t))
                .toList();
    }

    /** Campos escondidos do formulário "Período" (mantém a área do admin). */
    public Map<String, String> ocultosPeriodo() {
        return area.parametro();
    }

    public String urlExportar() {
        return Urls.montar("/painel/exportar", periodo.parametros(), area.parametro());
    }

    public String notaAbertos() {
        return novosHoje == 1 ? "1 novo hoje" : novosHoje + " novos hoje";
    }

    public String notaEmAtendimento() {
        return atendentesAtivos == 1 ? "1 atendente ativo" : atendentesAtivos + " atendentes ativos";
    }
}
