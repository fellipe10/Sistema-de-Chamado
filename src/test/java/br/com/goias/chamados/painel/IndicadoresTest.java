package br.com.goias.chamados.painel;

import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.Categoria;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.Local;
import br.com.goias.chamados.dominio.cadastro.Setor;
import br.com.goias.chamados.dominio.cadastro.CalendariosDeTeste;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.painel.Indicadores.Resolvido;
import br.com.goias.chamados.painel.Indicadores.Sla;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

/** Indicadores dos cards, "Por categoria" e exportação CSV. */
class IndicadoresTest {

    private final CalendarioExpediente cal = CalendariosDeTeste.padrao();

    private static LocalDateTime em(int dia, int mes, int hora, int minuto) {
        return LocalDateTime.of(2026, mes, dia, hora, minuto);
    }

    private static Resolvido resolvido(LocalDateTime abertura, LocalDateTime resolucao, LocalDateTime prazo, long pausados) {
        return new Resolvido(1L, "Rafael Nunes", abertura, resolucao, prazo, pausados);
    }

    @Test
    @DisplayName("Dentro do prazo: resolvidos até o prazo sobre os que têm prazo; arredonda para baixo")
    void sla() {
        List<Resolvido> lista = List.of(
                resolvido(em(3, 11, 9, 0), em(3, 11, 10, 0), em(3, 11, 17, 0), 0), // no prazo
                resolvido(em(3, 11, 9, 0), em(3, 11, 17, 0), em(3, 11, 17, 0), 0), // exatamente no prazo
                resolvido(em(3, 11, 9, 0), em(4, 11, 9, 0), em(3, 11, 17, 0), 0),  // atrasado
                resolvido(em(3, 11, 9, 0), em(4, 11, 9, 0), null, 0));             // sem prazo: fora da conta
        Sla s = Indicadores.sla(lista);
        assertThat(s.noPrazo()).isEqualTo(2);
        assertThat(s.total()).isEqualTo(3);
        assertThat(s.percentual()).isEqualTo(66);
        assertThat(s.texto()).isEqualTo("2 de 3 chamados resolvidos no prazo");

        assertThat(new Sla(199, 200).percentual()).isEqualTo(99); // nunca mostra 100% com atraso
        assertThat(new Sla(1, 1).texto()).isEqualTo("1 de 1 chamado resolvido no prazo");
        assertThat(new Sla(0, 0).percentual()).isNull();
        assertThat(new Sla(0, 0).texto()).isEqualTo("Nenhum chamado resolvido no período");
    }

    @Test
    @DisplayName("Tempo até resolver: horas úteis menos pausas (nunca negativo)")
    void tempoResolucao() {
        // ter 09:00 → ter 15:00 = 09–12 + 13–15 = 300 min úteis; pausado 60 → 240
        assertThat(Indicadores.minutosAteResolver(cal, resolvido(em(3, 11, 9, 0), em(3, 11, 15, 0), null, 60))).isEqualTo(240);
        // sex 16:00 → seg 09:00 = 120 min úteis (fim de semana não conta)
        assertThat(Indicadores.minutosAteResolver(cal, resolvido(em(6, 11, 16, 0), em(9, 11, 9, 0), null, 0))).isEqualTo(120);
        assertThat(Indicadores.minutosAteResolver(cal, resolvido(em(3, 11, 9, 0), em(3, 11, 9, 30), null, 90))).isZero();

        OptionalDouble media = Indicadores.mediaResolucao(cal, List.of(
                resolvido(em(3, 11, 9, 0), em(3, 11, 10, 0), null, 0),    // 60
                resolvido(em(3, 11, 9, 0), em(3, 11, 11, 0), null, 0)));  // 120
        assertThat(media).hasValue(90.0);
        assertThat(Indicadores.formatarDuracao(media)).isEqualTo("1h 30min");
        assertThat(Indicadores.formatarDuracao(Indicadores.mediaResolucao(cal, List.of()))).isEqualTo("—");
    }

    @Test
    @DisplayName("Formato dos tempos: \"45 min\", \"1h\", \"1h 12min\", \"9h 40min\"")
    void formato() {
        assertThat(Indicadores.formatarDuracao(45)).isEqualTo("45 min");
        assertThat(Indicadores.formatarDuracao(60)).isEqualTo("1h");
        assertThat(Indicadores.formatarDuracao(72)).isEqualTo("1h 12min");
        assertThat(Indicadores.formatarDuracao(580)).isEqualTo("9h 40min");
    }

    @Test
    @DisplayName("Por categoria: maiores primeiro; acima de 10 categorias, as menores viram \"Demais categorias\"")
    void categorias() {
        List<Object[]> linhas = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            linhas.add(new Object[]{"Cat " + i, (long) (40 - i * 3)});
        }
        List<PainelTela.ItemCategoria> itens = PainelService.categorias(linhas);
        assertThat(itens).hasSize(10);
        assertThat(itens.get(0).pct()).isEqualTo(100);
        assertThat(itens.get(1).pct()).isEqualTo(93); // 37/40
        PainelTela.ItemCategoria ultimo = itens.get(9);
        assertThat(ultimo.nome()).isEqualTo("Demais categorias (3)");
        assertThat(ultimo.quantidade()).isEqualTo(13 + 10 + 7);
        assertThat(ultimo.agrupado()).isTrue();

        assertThat(PainelService.categorias(linhas.subList(0, 10))).hasSize(10).noneMatch(PainelTela.ItemCategoria::agrupado);
        assertThat(PainelService.categorias(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Iniciais do avatar")
    void iniciais() {
        assertThat(PainelService.iniciais("Carlos Andrade")).isEqualTo("CA");
        assertThat(PainelService.iniciais("Bianca de Souza Torres")).isEqualTo("BT");
        assertThat(PainelService.iniciais("Rafael")).isEqualTo("RA");
    }

    @Test
    @DisplayName("CSV: campos com ; aspas ou quebra de linha vão entre aspas; fórmula ganha apóstrofo")
    void campoCsv() {
        assertThat(ExportacaoCsv.campo("Impressora; sala 2")).isEqualTo("\"Impressora; sala 2\"");
        assertThat(ExportacaoCsv.campo("Monitor \"novo\"")).isEqualTo("\"Monitor \"\"novo\"\"\"");
        assertThat(ExportacaoCsv.campo("linha 1\nlinha 2")).isEqualTo("\"linha 1\nlinha 2\"");
        assertThat(ExportacaoCsv.campo("=HYPERLINK(\"x\")")).startsWith("\"'=HYPERLINK");
        assertThat(ExportacaoCsv.campo("+55 62")).isEqualTo("'+55 62");
        assertThat(ExportacaoCsv.campo(null)).isEmpty();
    }

    @Test
    @DisplayName("CSV: BOM do UTF-8, cabeçalho e linha de um chamado resolvido (no prazo e minutos úteis sem pausa)")
    void arquivoCsv() {
        Chamado c = new Chamado();
        Area area = new Area();
        area.setId(1L);
        c.setArea(area);
        Categoria cat = new Categoria();
        cat.setNome("Impressora e scanner");
        c.setCategoria(cat);
        Local local = new Local();
        local.setNome("Sede administrativa");
        c.setLocal(local);
        Setor setor = new Setor();
        setor.setNome("Financeiro");
        c.setSetor(setor);
        c.setProtocolo("TI-2026-7K3F");
        c.setTitulo("Impressora não imprime");
        c.setPrioridade(Prioridade.ALTA);
        c.setStatus(StatusChamado.RESOLVIDO);
        c.setDataAbertura(em(3, 11, 9, 0));
        c.setDataPrimeiraResposta(em(3, 11, 9, 20));
        c.setDataResolucao(em(3, 11, 15, 0));
        c.setPrazoResolucao(em(3, 11, 17, 0));
        c.setMinutosPausados(60L);

        byte[] bytes = ExportacaoCsv.gerar(List.of(c), cal);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        String texto = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        String[] linhas = texto.split("\r\n");
        assertThat(linhas[0]).startsWith("Código;Título;Abertura;Categoria;Local;Setor;Prioridade;Status;Atendente;")
                .endsWith("No prazo;Minutos úteis até resolver");
        assertThat(linhas[1]).isEqualTo("TI-2026-7K3F;Impressora não imprime;03/11/2026 09:00;Impressora e scanner;"
                + "Sede administrativa;Financeiro;Alta;Resolvido;;03/11/2026 09:20;03/11/2026 15:00;03/11/2026 17:00;Sim;240");
    }
}
