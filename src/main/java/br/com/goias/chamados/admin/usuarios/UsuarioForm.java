package br.com.goias.chamados.admin.usuarios;

import br.com.goias.chamados.dominio.usuario.Perfil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Formulário de criar/editar usuário.
 * A situação (ativo/inativo) muda por botões próprios (Desativar/Reativar), não por este formulário.
 */
@Getter
@Setter
@NoArgsConstructor
public class UsuarioForm {

    private String nome;
    private String email;
    private Perfil perfil;
    private Long areaId;

    /** Marcado quando o admin confirma a mudança de área de quem tem chamados em andamento. */
    private boolean confirmarMudancaArea;
}
