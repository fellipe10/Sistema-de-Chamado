package br.com.goias.chamados.consulta;

import br.com.goias.chamados.dominio.chamado.StatusChamado;

import java.time.LocalDateTime;
import java.util.List;

/**
 * O que a consulta pública pode mostrar de um chamado.
 *
 * Privacidade: NÃO tem e-mail, telefone, matrícula, nome do solicitante nem anexos.
 * O atendente aparece só pelo primeiro nome.
 * aguardandoTexto: motivo do "Aguardando" em linguagem do solicitante (Etapa 4), nulo nos outros status.
 */
public record ConsultaResultado(
        String protocolo,
        StatusChamado status,
        String area,
        String categoria,
        String local,
        String titulo,
        LocalDateTime dataAbertura,
        String atendentePrimeiroNome,
        String aguardandoTexto,
        boolean aguardandoSolicitante,
        List<ItemLinhaTempo> linhaTempo) {

    /** Um acontecimento na linha do tempo (mudança de status ou resposta ao solicitante). */
    public record ItemLinhaTempo(
            LocalDateTime data,
            String titulo,
            String texto,
            boolean resposta) {
    }
}
