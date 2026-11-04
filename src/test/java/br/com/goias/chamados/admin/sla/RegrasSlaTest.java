package br.com.goias.chamados.admin.sla;

import br.com.goias.chamados.admin.sla.RegrasSla.Digitado;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasSlaTest {

    /** Grade padrão (Crítica 0h30/4h, Alta 2h/8h, Média 4h/24h, Baixa 8h/40h) com uma linha trocada. */
    private static Map<Prioridade, Digitado> grade(Prioridade trocar, Digitado d) {
        Map<Prioridade, Digitado> g = new EnumMap<>(Prioridade.class);
        g.put(Prioridade.CRITICA, new Digitado("0", "30", "4", "0"));
        g.put(Prioridade.ALTA, new Digitado("2", "0", "8", "0"));
        g.put(Prioridade.MEDIA, new Digitado("4", "0", "24", "0"));
        g.put(Prioridade.BAIXA, new Digitado("8", "", "40", ""));
        if (trocar != null) {
            g.put(trocar, d);
        }
        return g;
    }

    @Test
    @DisplayName("Horas e minutos viram minutos (vazio = 0 minutos)")
    void converte() {
        var r = RegrasSla.validar(grade(null, null));
        assertThat(r.sucesso()).isTrue();
        assertThat(r.minutos().get(Prioridade.CRITICA)).isEqualTo(new RegrasSla.Minutos(30, 240));
        assertThat(r.minutos().get(Prioridade.MEDIA)).isEqualTo(new RegrasSla.Minutos(240, 1440));
        assertThat(r.minutos().get(Prioridade.BAIXA)).isEqualTo(new RegrasSla.Minutos(480, 2400));
    }

    @Test
    @DisplayName("Valor zero: recusado")
    void zero() {
        var r = RegrasSla.validar(grade(Prioridade.CRITICA, new Digitado("0", "0", "4", "0")));
        assertThat(r.erros()).containsEntry("CRITICA.primeira", "O prazo precisa ser maior que zero.");
        assertThat(RegrasSla.validar(grade(Prioridade.ALTA, new Digitado("", "", "", ""))).erros())
                .containsKeys("ALTA.primeira", "ALTA.resolucao");
    }

    @Test
    @DisplayName("Resolução menor que a 1ª resposta: recusado; igual é aceito")
    void resolucaoMenor() {
        assertThat(RegrasSla.validar(grade(Prioridade.ALTA, new Digitado("2", "0", "1", "59"))).erros())
                .containsEntry("ALTA.resolucao", "A resolução não pode ser menor que a 1ª resposta.");
        assertThat(RegrasSla.validar(grade(Prioridade.ALTA, new Digitado("2", "0", "2", "0"))).sucesso()).isTrue();
    }

    @Test
    @DisplayName("Minutos acima de 59, texto, negativo e horas demais: recusados")
    void invalidos() {
        assertThat(RegrasSla.validar(grade(Prioridade.BAIXA, new Digitado("8", "75", "40", "0"))).erros())
                .containsEntry("BAIXA.primeira", "Os minutos vão de 0 a 59.");
        assertThat(RegrasSla.validar(grade(Prioridade.MEDIA, new Digitado("4", "0", "abc", "0"))).erros())
                .containsEntry("MEDIA.resolucao", "Use só números.");
        assertThat(RegrasSla.validar(grade(Prioridade.MEDIA, new Digitado("-1", "0", "24", "0"))).erros())
                .containsEntry("MEDIA.primeira", "Use só números.");
        assertThat(RegrasSla.validar(grade(Prioridade.MEDIA, new Digitado("4", "0", "1000", "0"))).erros())
                .containsEntry("MEDIA.resolucao", "No máximo 999 horas.");
    }

    @Test
    @DisplayName("Com erro, nada é devolvido para gravar")
    void comErroNaoGrava() {
        assertThat(RegrasSla.validar(grade(Prioridade.CRITICA, new Digitado("0", "0", "4", "0"))).minutos()).isEmpty();
    }

    @Test
    @DisplayName("Dica embaixo do campo: dias úteis (480 min) ou horas")
    void descrever() {
        assertThat(RegrasSla.descrever(30)).isEqualTo("30 min");
        assertThat(RegrasSla.descrever(90)).isEqualTo("1h 30min");
        assertThat(RegrasSla.descrever(240)).isEqualTo("4h");
        assertThat(RegrasSla.descrever(480)).isEqualTo("1 dia útil");
        assertThat(RegrasSla.descrever(1440)).isEqualTo("3 dias úteis");
        assertThat(RegrasSla.descrever(960)).isEqualTo("2 dias úteis");
        assertThat(RegrasSla.descrever(600)).isEqualTo("10h úteis");
    }
}
