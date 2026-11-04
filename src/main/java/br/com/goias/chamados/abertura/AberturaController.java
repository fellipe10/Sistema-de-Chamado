package br.com.goias.chamados.abertura;

import br.com.goias.chamados.config.LimitadorPorIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;

/**
 * Formulário público "Abrir chamado" (sem login).
 *
 * GET  /abrir                       → formulário em 3 etapas (troca de etapa só no navegador)
 * POST /abrir                       → valida e grava; se der certo, redireciona (PRG)
 * GET  /abrir/sucesso/{protocolo}   → tela "Chamado aberto!" com o código
 */
@Controller
@RequestMapping("/abrir")
@RequiredArgsConstructor
public class AberturaController {

    static final String MENSAGEM_LIMITE =
            "Você abriu vários chamados em pouco tempo. Aguarde alguns minutos e tente de novo. "
            + "Se for urgente, ligue para o ramal da TI ou da Manutenção.";

    /** No máximo 5 chamados por IP a cada 10 minutos. */
    private final LimitadorPorIp limitador = new LimitadorPorIp(5, Duration.ofMinutes(10));

    private final AberturaService aberturaService;
    private final OpcoesFormulario opcoesFormulario;

    /** Tira espaços das pontas e transforma campo vazio em null. */
    @InitBinder("form")
    void configurarBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @ModelAttribute("menuAtivo")
    String menuAtivo() {
        return "abrir";
    }

    @GetMapping
    public String formulario(Model model) {
        model.addAttribute("form", new AberturaForm());
        return exibirFormulario(model, 1, false);
    }

    @PostMapping
    public String enviar(@Valid @ModelAttribute("form") AberturaForm form, BindingResult erros,
                         HttpServletRequest request, Model model) {
        boolean teveAnexos = form.isTemAnexos();
        String ip = request.getRemoteAddr();

        if (!limitador.podeRegistrar(ip)) {
            model.addAttribute("erroGeral", MENSAGEM_LIMITE);
            return exibirFormulario(model, 3, teveAnexos);
        }

        if (!erros.hasErrors()) {
            try {
                String protocolo = aberturaService.abrir(form);
                limitador.registrar(ip);
                return "redirect:/abrir/sucesso/" + protocolo;
            } catch (AberturaInvalidaException e) {
                e.getErros().forEach((campo, mensagem) -> erros.rejectValue(campo, "invalido", mensagem));
            }
        }
        return exibirFormulario(model, etapaDoPrimeiroErro(erros), teveAnexos);
    }

    @GetMapping("/sucesso/{protocolo}")
    public String sucesso(@PathVariable String protocolo, Model model) {
        if (!GeradorProtocolo.formatoValido(protocolo) || !aberturaService.existe(protocolo)) {
            return "redirect:/abrir";
        }
        model.addAttribute("protocolo", protocolo);
        return "abertura/sucesso";
    }

    private String exibirFormulario(Model model, int etapaInicial, boolean teveAnexos) {
        opcoesFormulario.carregar(model);
        model.addAttribute("etapaInicial", etapaInicial);
        model.addAttribute("anexosPerdidos", teveAnexos);
        return "abertura/formulario";
    }

    /** Volta para a etapa do primeiro campo com erro (erros sem campo contam como etapa 1). */
    private static int etapaDoPrimeiroErro(BindingResult erros) {
        int etapa = erros.hasGlobalErrors() ? 1 : 3;
        for (FieldError erro : erros.getFieldErrors()) {
            etapa = Math.min(etapa, AberturaForm.etapaDoCampo(erro.getField()));
        }
        return etapa;
    }
}
