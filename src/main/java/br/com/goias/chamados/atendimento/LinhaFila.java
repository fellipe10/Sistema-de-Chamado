package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;

/** Uma linha da fila (tabela no computador, card no celular). */
public record LinhaFila(
        Long id,
        String protocolo,
        String titulo,
        String solicitanteNome,
        String setorNome,
        String categoriaNome,
        String areaSigla,
        Prioridade prioridade,
        StatusChamado status,
        String atendentePrimeiroNome,
        BadgeSla badgeSla) {

    public String classeStatus() {
        return "status-" + status.name().toLowerCase();
    }

    public String classePrioridade() {
        return "prioridade-" + prioridade.name().toLowerCase();
    }
}
