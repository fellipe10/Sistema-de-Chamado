package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.StatusChamado;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Colunas do Kanban da fila (só visualização), nesta ordem.
 *
 * - ABERTO inclui REABERTO (o card ganha o selo "Reaberto");
 * - RESOLVIDO ("Resolvido / Fechado") junta RESOLVIDO e FECHADO resolvidos nos últimos
 *   {@link KanbanService#DIAS_RESOLVIDOS} dias (o card mostra o selo "Resolvido" ou "Fechado").
 *   Todo FECHADO passa antes por RESOLVIDO, então sempre tem data de resolução.
 *
 * abaDaLista = aba da Lista usada no link "ver todos" quando a coluna passa do limite de cards.
 */
@Getter
@RequiredArgsConstructor
public enum ColunaKanban {

    ABERTO("Aberto", "bi-inbox", EnumSet.of(StatusChamado.ABERTO, StatusChamado.REABERTO), AbaFila.NOVOS),
    EM_ATENDIMENTO("Em atendimento", "bi-tools", EnumSet.of(StatusChamado.EM_ATENDIMENTO), AbaFila.ANDAMENTO),
    AGUARDANDO("Aguardando", "bi-hourglass-split", EnumSet.of(StatusChamado.AGUARDANDO), AbaFila.AGUARDANDO),
    RESOLVIDO("Resolvido / Fechado", "bi-check2-circle", EnumSet.of(StatusChamado.RESOLVIDO, StatusChamado.FECHADO),
            AbaFila.HISTORICO);

    private final String rotulo;
    private final String icone;
    private final Set<StatusChamado> status;
    private final AbaFila abaDaLista;

    /** Em que coluna o chamado aparece (vazio = não aparece no Kanban). */
    public static Optional<ColunaKanban> doStatus(StatusChamado status) {
        return Arrays.stream(values()).filter(c -> c.status.contains(status)).findFirst();
    }

    /** Parâmetro/atributo usado na tela (ex.: "em-atendimento"). */
    public String getChave() {
        return name().toLowerCase().replace('_', '-');
    }
}
