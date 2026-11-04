package br.com.goias.chamados.painel;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.chamado.Chamado;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Planilha CSV dos chamados (botão "Exportar" do painel e dos relatórios).
 *
 * Feita para abrir direto no Excel em português, com dois cliques:
 * - UTF-8 COM BOM (sem o BOM o Excel troca os acentos por símbolos);
 * - separador ponto e vírgula (a vírgula é o separador decimal no Brasil);
 * - datas "dd/MM/aaaa HH:mm" (o Excel reconhece como data);
 * - fim de linha CRLF; campos com ; " ou quebra de linha vão entre aspas.
 * Segurança: texto que começa com = + - @ ganha um apóstrofo na frente, para o Excel
 * não executar como fórmula (ex.: título "=HYPERLINK(...)").
 *
 * Colunas: Código, Título, Abertura, Categoria, Local, Setor, Prioridade, Status, Atendente,
 * 1ª resposta, Resolução, Prazo, No prazo, Minutos úteis até resolver.
 * "No prazo" e "Minutos úteis até resolver" só são preenchidos para chamados resolvidos/fechados
 * (minutos úteis entre abertura e resolução, descontando as pausas).
 */
public final class ExportacaoCsv {

    static final String SEPARADOR = ";";
    static final String FIM_LINHA = "\r\n";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static final List<String> CABECALHO = List.of("Código", "Título", "Abertura", "Categoria", "Local", "Setor",
            "Prioridade", "Status", "Atendente", "1ª resposta", "Resolução", "Prazo", "No prazo",
            "Minutos úteis até resolver");

    private ExportacaoCsv() {
    }

    public static byte[] gerar(List<Chamado> chamados, CalendarioExpediente calendario) {
        StringBuilder sb = new StringBuilder();
        linha(sb, CABECALHO);
        for (Chamado c : chamados) {
            boolean resolvido = c.getDataResolucao() != null && !c.getStatus().isEmAndamento();
            String noPrazo = "";
            String minutos = "";
            if (resolvido) {
                noPrazo = c.getPrazoResolucao() == null ? ""
                        : (c.getDataResolucao().isAfter(c.getPrazoResolucao()) ? "Não" : "Sim");
                long pausados = c.getMinutosPausados() == null ? 0 : c.getMinutosPausados();
                minutos = String.valueOf(Math.max(0,
                        calendario.minutosUteisEntre(c.getDataAbertura(), c.getDataResolucao()) - pausados));
            }
            linha(sb, List.of(
                    c.getProtocolo(),
                    c.getTitulo(),
                    data(c.getDataAbertura()),
                    c.getCategoria().getNome(),
                    c.getLocal().getNome(),
                    c.getSetor().getNome(),
                    c.getPrioridade().getDescricao(),
                    c.getStatus().getDescricao(),
                    c.getAtendente() == null ? "" : c.getAtendente().getNome(),
                    data(c.getDataPrimeiraResposta()),
                    data(c.getDataResolucao()),
                    data(c.getPrazoResolucao()),
                    noPrazo,
                    minutos));
        }
        byte[] texto = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] arquivo = new byte[BOM.length + texto.length];
        System.arraycopy(BOM, 0, arquivo, 0, BOM.length);
        System.arraycopy(texto, 0, arquivo, BOM.length, texto.length);
        return arquivo;
    }

    private static void linha(StringBuilder sb, List<String> campos) {
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                sb.append(SEPARADOR);
            }
            sb.append(campo(campos.get(i)));
        }
        sb.append(FIM_LINHA);
    }

    /** Protege contra fórmula e coloca aspas quando precisa. */
    static String campo(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        String v = valor;
        char primeiro = v.charAt(0);
        if (primeiro == '=' || primeiro == '+' || primeiro == '-' || primeiro == '@'
                || primeiro == '\t' || primeiro == '\r') {
            v = "'" + v;
        }
        if (v.contains(SEPARADOR) || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String data(LocalDateTime t) {
        return t == null ? "" : DATA_HORA.format(t);
    }
}
