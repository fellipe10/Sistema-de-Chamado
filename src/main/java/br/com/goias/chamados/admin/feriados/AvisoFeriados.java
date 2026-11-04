package br.com.goias.chamados.admin.feriados;

import java.util.Collection;

/**
 * Aviso "o próximo ano ainda não tem feriados cadastrados" (Etapa 5).
 *
 * Aparece no topo de /admin e da tela de feriados. Sem feriados, o SLA do próximo ano
 * contaria dias de feriado como dias úteis.
 */
public final class AvisoFeriados {

    private AvisoFeriados() {
    }

    /**
     * @param anoAtual       ano de hoje (ou o ano de referência configurado para teste)
     * @param anosComFeriado anos que têm pelo menos um feriado cadastrado (qualquer área)
     * @return o próximo ano, se ele não tiver nenhum feriado; null se está tudo certo
     */
    public static Integer anoSemFeriado(int anoAtual, Collection<Integer> anosComFeriado) {
        int proximo = anoAtual + 1;
        return anosComFeriado.contains(proximo) ? null : proximo;
    }

    /** Texto do aviso. */
    public static String mensagem(int ano) {
        return "Ainda não há feriados cadastrados para " + ano
                + ". Sem eles, os prazos de SLA de " + ano + " vão contar feriados como dias úteis.";
    }
}
