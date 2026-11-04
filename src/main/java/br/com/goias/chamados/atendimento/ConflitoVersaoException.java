package br.com.goias.chamados.atendimento;

/**
 * O chamado mudou desde que a página foi aberta (a "versao" enviada pelo formulário
 * é diferente da gravada). Mostra "Este chamado foi alterado por outra pessoa..." e recarrega.
 */
public class ConflitoVersaoException extends RuntimeException {

    public ConflitoVersaoException() {
        super("Versão do chamado desatualizada");
    }
}
