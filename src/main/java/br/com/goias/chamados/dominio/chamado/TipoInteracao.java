package br.com.goias.chamados.dominio.chamado;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tipos de registro no histórico do chamado — REGRA OFICIAL (planejamento,
 * "Tipos de interação"). O banco garante a coluna "interno" com o CHECK
 * ck_interacao_tipo_interno (V3).
 *
 * <table>
 *   <tr><th>Tipo</th><th>Quem grava</th><th>interno</th><th>Consulta pública?</th></tr>
 *   <tr><td>MUDANCA_STATUS</td><td>sistema (abertura, autor nulo) ou atendente</td><td>false</td><td>Sim</td></tr>
 *   <tr><td>ATRIBUICAO</td><td>atendente/gestor ao assumir, devolver ou transferir</td><td>true</td><td>Não</td></tr>
 *   <tr><td>COMENTARIO</td><td>atendente/gestor — anotação da equipe</td><td>sempre true</td><td>Não</td></tr>
 *   <tr><td>RESPOSTA_SOLICITANTE</td><td>atendente/gestor — mensagem DA EQUIPE PARA o solicitante</td><td>sempre false</td><td>Sim</td></tr>
 * </table>
 *
 * O solicitante não escreve no sistema no MVP (não tem login).
 */
@Getter
@RequiredArgsConstructor
public enum TipoInteracao {

    /** Anotação interna da equipe. Sempre interno = true. */
    COMENTARIO("Comentário interno", true),
    /** Mensagem da equipe para o solicitante (aparece na consulta). Sempre interno = false. */
    RESPOSTA_SOLICITANTE("Resposta ao solicitante", false),
    /** Troca de status (aparece na consulta). Gravado com interno = false. */
    MUDANCA_STATUS("Mudança de status", false),
    /** Assumir, devolver ou transferir o responsável. Sempre interno = true. */
    ATRIBUICAO("Atribuição", true);

    private final String descricao;

    /** Valor que a coluna "interno" deve ter para este tipo. */
    private final boolean interno;
}
