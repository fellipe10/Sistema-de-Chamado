package br.com.goias.chamados.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Registro do link (usuário, área, categoria...) não existe → página 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RegistroNaoEncontradoException extends RuntimeException {

    public RegistroNaoEncontradoException(String tipo, Object id) {
        super(tipo + " não encontrado(a): " + id);
    }

    public RegistroNaoEncontradoException(Long id) {
        this("Registro", id);
    }
}
