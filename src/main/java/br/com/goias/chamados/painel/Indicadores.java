package br.com.goias.chamados.painel;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.OptionalDouble;

/**
 * Contas dos cards do painel (regras puras, testadas em IndicadoresTest).
 *
 * - Tempo até resolver = minutos ÚTEIS entre abertura e resolução, MENOS os minutos pausados
 *   ("Aguardando o solicitante"); nunca negativo.
 * - Tempo até a 1ª resposta = minutos úteis entre abertura e 1ª resposta.
 * - Dentro do prazo (SLA) = resolvidos no período com data_resolucao <= prazo_resolucao,
 *   sobre os resolvidos no período que TÊM prazo (sem SLA configurado não entra na conta).
 *   A porcentagem é arredondada para BAIXO (99,6% aparece 99%: só mostra 100% se todos cumpriram).
 */
public final class Indicadores {

    private Indicadores() {
    }

    /** Um chamado resolvido no período (o que os cards e o desempenho por atendente precisam). */
    public record Resolvido(Long atendenteId, String atendenteNome, LocalDateTime abertura,
                            LocalDateTime resolucao, LocalDateTime prazoResolucao, long minutosPausados) {

        public boolean noPrazo() {
            return prazoResolucao != null && !resolucao.isAfter(prazoResolucao);
        }
    }

    /** Resultado do card "Dentro do prazo (SLA)". */
    public record Sla(long noPrazo, long total) {

        /** Porcentagem inteira arredondada para baixo; null sem chamados com prazo. */
        public Integer percentual() {
            return total == 0 ? null : (int) Math.floor(noPrazo * 100.0 / total);
        }

        public String texto() {
            if (total == 0) {
                return "Nenhum chamado resolvido no período";
            }
            return noPrazo + " de " + total + (total == 1 ? " chamado resolvido no prazo" : " chamados resolvidos no prazo");
        }
    }

    public static long minutosAteResolver(CalendarioExpediente calendario, Resolvido r) {
        return Math.max(0, calendario.minutosUteisEntre(r.abertura(), r.resolucao()) - Math.max(0, r.minutosPausados()));
    }

    public static Sla sla(Collection<Resolvido> resolvidos) {
        long total = resolvidos.stream().filter(r -> r.prazoResolucao() != null).count();
        long noPrazo = resolvidos.stream().filter(Resolvido::noPrazo).count();
        return new Sla(noPrazo, total);
    }

    /** Média do tempo até resolver (minutos úteis, descontando pausas). Vazio sem resolvidos. */
    public static OptionalDouble mediaResolucao(CalendarioExpediente calendario, Collection<Resolvido> resolvidos) {
        return resolvidos.stream().mapToLong(r -> minutosAteResolver(calendario, r)).average();
    }

    /** "1h 12min", "45 min", "2h", "0 min"; "—" quando não há dado. */
    public static String formatarDuracao(OptionalDouble minutos) {
        return minutos.isPresent() ? formatarDuracao(Math.round(minutos.getAsDouble())) : "—";
    }

    public static String formatarDuracao(long minutos) {
        if (minutos < 60) {
            return minutos + " min";
        }
        long h = minutos / 60;
        long m = minutos % 60;
        return m == 0 ? h + "h" : h + "h " + String.format("%02d", m) + "min";
    }
}
