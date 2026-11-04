package br.com.goias.chamados.dominio.usuario;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Perfis de acesso de quem faz login.
 * O solicitante não tem login, por isso não aparece aqui.
 */
@Getter
@RequiredArgsConstructor
public enum Perfil {

    ATENDENTE("Atendente"),
    GESTOR("Gestor"),
    ADMIN("Administrador");

    private final String descricao;

    /** Nome da autoridade usada pelo Spring Security (ROLE_ATENDENTE etc.). */
    public String getAutoridade() {
        return "ROLE_" + name();
    }
}
