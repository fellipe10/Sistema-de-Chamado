package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.login.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Preenche o "totalFila" (contador ao lado de "Fila de chamados" no menu) para todas as telas
 * internas (controllers com @TelaInterna). Valor = chamados Novos da área do usuário
 * (ADMIN: de todas as áreas).
 */
@ControllerAdvice(annotations = TelaInterna.class)
@RequiredArgsConstructor
public class ContadorFilaAdvice {

    private final FilaService filaService;

    @ModelAttribute("totalFila")
    public Long totalFila(Authentication autenticacao) {
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioLogado usuario) {
            return filaService.contarNovos(usuario);
        }
        return null;
    }
}
