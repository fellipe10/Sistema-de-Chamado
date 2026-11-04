package br.com.goias.chamados.consulta;

import br.com.goias.chamados.config.LimitadorPorIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.util.Optional;

/**
 * Consulta pública do chamado (sem login).
 *
 * GET /consultar               → campo para digitar o código
 * GET /consultar?codigo=...    → resultado (ou "não encontramos")
 */
@Controller
@RequestMapping("/consultar")
@RequiredArgsConstructor
public class ConsultaController {

    static final String MENSAGEM_NAO_ENCONTRADO =
            "Não encontramos um chamado com esse código. Confira e tente de novo.";
    static final String MENSAGEM_LIMITE =
            "Você fez muitas consultas em pouco tempo. Aguarde um minuto e tente de novo.";

    /** No máximo 30 consultas por IP por minuto. */
    private final LimitadorPorIp limitador = new LimitadorPorIp(30, Duration.ofMinutes(1));

    private final ConsultaService consultaService;

    @GetMapping
    public String consultar(@RequestParam(required = false) String codigo,
                            HttpServletRequest request, HttpServletResponse response, Model model) {
        model.addAttribute("menuAtivo", "consultar");
        String digitado = codigo == null ? "" : codigo.strip();
        model.addAttribute("codigo", digitado.length() > NormalizadorCodigo.TAMANHO_MAXIMO_ENTRADA
                ? digitado.substring(0, NormalizadorCodigo.TAMANHO_MAXIMO_ENTRADA) : digitado);

        if (digitado.isEmpty()) {
            return "consulta/consulta";
        }
        if (!limitador.tentar(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("erro", MENSAGEM_LIMITE);
            return "consulta/consulta";
        }

        Optional<ConsultaResultado> resultado = NormalizadorCodigo.normalizar(digitado)
                .flatMap(consultaService::buscar);
        if (resultado.isPresent()) {
            model.addAttribute("resultado", resultado.get());
            model.addAttribute("codigo", resultado.get().protocolo());
        } else {
            model.addAttribute("erro", MENSAGEM_NAO_ENCONTRADO);
        }
        return "consulta/consulta";
    }
}
