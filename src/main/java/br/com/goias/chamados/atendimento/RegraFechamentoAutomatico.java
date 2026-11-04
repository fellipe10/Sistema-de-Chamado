package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.chamado.StatusChamado;

import java.time.LocalDateTime;

/**
 * Quando um chamado RESOLVIDO vira FECHADO sozinho (Etapa 4).
 *
 * Regra: está RESOLVIDO há N dias úteis ou mais, contando só o expediente da área
 * (1 dia útil = 480 min, como no SLA). N = 0 desliga o fechamento automático.
 * Ex.: resolvido segunda 15:00, N = 5 → fecha na segunda seguinte a partir das 15:00
 * (se não houver feriado no meio).
 */
public final class RegraFechamentoAutomatico {

    /** "Dia útil" do sistema (decisão da Etapa 1). */
    public static final long MINUTOS_DIA_UTIL = 480;

    private RegraFechamentoAutomatico() {
    }

    public static boolean deveFechar(StatusChamado status, LocalDateTime dataResolucao, LocalDateTime agora,
                                     CalendarioExpediente calendario, int diasUteis) {
        if (diasUteis <= 0 || status != StatusChamado.RESOLVIDO || dataResolucao == null) {
            return false;
        }
        return calendario.minutosUteisEntre(dataResolucao, agora) >= diasUteis * MINUTOS_DIA_UTIL;
    }

    /** Texto gravado no histórico (aparece também na consulta pública). */
    public static String texto(int diasUteis) {
        return "Fechado automaticamente após " + diasUteis + (diasUteis == 1 ? " dia útil" : " dias úteis")
                + " sem retorno.";
    }
}
