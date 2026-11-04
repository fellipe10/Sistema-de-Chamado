package br.com.goias.chamados.conta;

import lombok.Getter;
import lombok.Setter;

/**
 * Campos da tela "Trocar senha". As regras ficam em {@link ValidadorNovaSenha}
 * (e a conferência da senha atual em {@link ContaService}).
 */
@Getter
@Setter
public class TrocaSenhaForm {

    private String senhaAtual;
    private String novaSenha;
    private String confirmacao;
}
