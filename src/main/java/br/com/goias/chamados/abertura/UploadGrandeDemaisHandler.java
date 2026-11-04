package br.com.goias.chamados.abertura;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Quando o envio passa do limite (10 MB por arquivo / 35 MB no total), o Spring recusa antes
 * de chegar no controller. Aqui a pessoa volta para o formulário com uma mensagem amigável.
 * (Normalmente o JavaScript do formulário já impede isso antes do envio.)
 */
@ControllerAdvice
public class UploadGrandeDemaisHandler {

    static final String MENSAGEM = "Os anexos passaram do limite (até 3 arquivos de 10 MB cada). "
            + "Preencha o formulário de novo e escolha arquivos menores.";

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadGrandeDemais(RedirectAttributes redirect) {
        redirect.addFlashAttribute("erroGeral", MENSAGEM);
        return "redirect:/abrir";
    }
}
