package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.usuario.Perfil;

import java.util.Objects;

/**
 * Quem pode fazer o quê em um chamado (sem banco, fácil de testar).
 *
 * - Ver o chamado / comentar internamente: ATENDENTE ou GESTOR da área do chamado; ADMIN vê todos.
 * - Assumir: ATENDENTE ou GESTOR da área, se o chamado não tiver responsável.
 * - Mudar status, mudar prioridade e responder ao solicitante: o responsável, GESTOR da área ou ADMIN.
 * - Devolver para a fila: só o próprio responsável.
 * - Transferir: GESTOR da área ou ADMIN.
 *
 * Usuário de outra área: não vê nada (a tela responde 404, sem revelar que o chamado existe).
 * Regras que dependem do status (ex.: FECHADO é final) ficam no AtendimentoService.
 */
public record PermissoesAtendimento(
        boolean podeVer,
        boolean podeComentar,
        boolean podeAssumir,
        boolean podeAlterar,
        boolean podeDevolver,
        boolean podeTransferir,
        boolean ehResponsavel) {

    public static PermissoesAtendimento calcular(Perfil perfil, Long usuarioId, Long usuarioAreaId,
                                                 Long chamadoAreaId, Long responsavelId) {
        boolean admin = perfil == Perfil.ADMIN;
        boolean daArea = (perfil == Perfil.ATENDENTE || perfil == Perfil.GESTOR)
                && usuarioAreaId != null
                && Objects.equals(usuarioAreaId, chamadoAreaId);
        boolean gestorDaArea = daArea && perfil == Perfil.GESTOR;
        boolean ehResponsavel = daArea && responsavelId != null && Objects.equals(responsavelId, usuarioId);

        boolean podeVer = admin || daArea;
        return new PermissoesAtendimento(
                podeVer,
                podeVer,
                daArea && responsavelId == null,
                ehResponsavel || gestorDaArea || admin,
                ehResponsavel,
                gestorDaArea || admin,
                ehResponsavel);
    }

    /** Ninguém pode nada (usado quando o usuário não é da área). */
    public static PermissoesAtendimento nenhuma() {
        return new PermissoesAtendimento(false, false, false, false, false, false, false);
    }
}
