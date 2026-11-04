package br.com.goias.chamados.abertura;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Regras do service recusaram o formulário (ex.: categoria de outra área, setor inativo, anexo inválido).
 * Cada entrada é campo do formulário → mensagem para mostrar embaixo dele.
 */
@Getter
public class AberturaInvalidaException extends RuntimeException {

    private final Map<String, String> erros;

    public AberturaInvalidaException(Map<String, String> erros) {
        super("Formulário de abertura recusado: " + erros.keySet());
        this.erros = new LinkedHashMap<>(erros);
    }
}
