package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.Chamado;

import java.time.LocalDateTime;

/**
 * data_primeira_resposta = momento da PRIMEIRA ação da equipe entre:
 * assumir o chamado OU enviar a primeira RESPOSTA_SOLICITANTE (o que vier primeiro).
 * Nunca sobrescreve um valor já gravado.
 */
public final class RegraPrimeiraResposta {

    private RegraPrimeiraResposta() {
    }

    /** @return true se gravou agora (era a primeira ação). */
    public static boolean registrar(Chamado chamado, LocalDateTime momento) {
        if (chamado.getDataPrimeiraResposta() != null) {
            return false;
        }
        chamado.setDataPrimeiraResposta(momento);
        return true;
    }
}
