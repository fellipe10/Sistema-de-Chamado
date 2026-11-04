package br.com.goias.chamados.admin;

/**
 * Ação recusada por uma regra da administração (ex.: redefinir a própria senha por aqui).
 * O controller mostra a mensagem como aviso de erro na própria tela.
 */
public class AcaoAdminRecusadaException extends RuntimeException {

    public AcaoAdminRecusadaException(String mensagem) {
        super(mensagem);
    }
}
