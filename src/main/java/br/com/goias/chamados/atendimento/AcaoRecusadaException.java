package br.com.goias.chamados.atendimento;

/**
 * Ação que a regra de negócio não permite (ex.: transição de status inválida, mensagem vazia).
 * A mensagem é mostrada para o usuário na tela do chamado.
 */
public class AcaoRecusadaException extends RuntimeException {

    public AcaoRecusadaException(String mensagem) {
        super(mensagem);
    }
}
