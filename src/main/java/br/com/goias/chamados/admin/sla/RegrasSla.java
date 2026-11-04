package br.com.goias.chamados.admin.sla;

import br.com.goias.chamados.dominio.chamado.Prioridade;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Regras da grade de SLA (Etapa 5) — sem banco, fáceis de testar.
 *
 * O admin digita horas e minutos; o banco guarda minutos.
 * - horas: número inteiro de 0 a 999; minutos: de 0 a 59 (vazio conta como 0);
 * - cada prazo precisa ser maior que zero;
 * - resolução ≥ 1ª resposta (mesmo CHECK da tabela sla_config).
 */
public final class RegrasSla {

    public static final int HORAS_MAXIMO = 999;

    private RegrasSla() {
    }

    /** O que veio da tela para uma prioridade (texto, pode estar vazio ou inválido). */
    public record Digitado(String primeiraHoras, String primeiraMinutos, String resolucaoHoras, String resolucaoMinutos) {
    }

    /** Prazos já convertidos em minutos. */
    public record Minutos(int primeiraResposta, int resolucao) {
    }

    /** Resultado: minutos por prioridade (quando tudo está certo) e erros por campo ("CRITICA.primeira"...). */
    public record Resultado(Map<Prioridade, Minutos> minutos, Map<String, String> erros) {
        public boolean sucesso() {
            return erros.isEmpty();
        }
    }

    public static Resultado validar(Map<Prioridade, Digitado> grade) {
        Map<Prioridade, Minutos> minutos = new EnumMap<>(Prioridade.class);
        Map<String, String> erros = new LinkedHashMap<>();
        for (Prioridade p : Prioridade.values()) {
            Digitado d = grade.get(p);
            if (d == null) {
                erros.put(p.name() + ".primeira", "Informe o prazo.");
                continue;
            }
            Integer primeira = converter(d.primeiraHoras(), d.primeiraMinutos(), p.name() + ".primeira", erros);
            Integer resolucao = converter(d.resolucaoHoras(), d.resolucaoMinutos(), p.name() + ".resolucao", erros);
            if (primeira != null && resolucao != null) {
                if (resolucao < primeira) {
                    erros.put(p.name() + ".resolucao", "A resolução não pode ser menor que a 1ª resposta.");
                } else {
                    minutos.put(p, new Minutos(primeira, resolucao));
                }
            }
        }
        return new Resultado(erros.isEmpty() ? minutos : Map.of(), erros);
    }

    /** Horas + minutos → minutos; grava a mensagem em "erros" e devolve null se algo estiver errado. */
    static Integer converter(String horasTexto, String minutosTexto, String campo, Map<String, String> erros) {
        Integer horas = numero(horasTexto);
        Integer min = numero(minutosTexto);
        if (horas == null || min == null) {
            erros.put(campo, "Use só números.");
            return null;
        }
        if (horas > HORAS_MAXIMO) {
            erros.put(campo, "No máximo " + HORAS_MAXIMO + " horas.");
            return null;
        }
        if (min > 59) {
            erros.put(campo, "Os minutos vão de 0 a 59.");
            return null;
        }
        int total = horas * 60 + min;
        if (total <= 0) {
            erros.put(campo, "O prazo precisa ser maior que zero.");
            return null;
        }
        return total;
    }

    /** Vazio = 0; texto que não é número inteiro ≥ 0 = null. */
    private static Integer numero(String texto) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        String t = texto.strip();
        if (!t.matches("\\d{1,4}")) {
            return null;
        }
        return Integer.parseInt(t);
    }

    /** 1440 → "3 dias úteis"; 90 → "1h 30min"; usado como dica embaixo do campo. */
    public static String descrever(int minutos) {
        if (minutos >= 480 && minutos % 480 == 0) {
            int dias = minutos / 480;
            return dias == 1 ? "1 dia útil" : dias + " dias úteis";
        }
        int h = minutos / 60;
        int m = minutos % 60;
        String texto = h == 0 ? m + " min" : (m == 0 ? h + "h" : h + "h " + m + "min");
        return minutos > 480 ? texto + " úteis" : texto;
    }
}
