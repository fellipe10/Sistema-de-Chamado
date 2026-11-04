package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.dominio.chamado.MotivoAguardando;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.chamado.TipoInteracao;
import br.com.goias.chamados.login.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Tela do chamado e ações de atendimento (todas com PRG: POST → redirect → GET com mensagem).
 *
 * GET  /atendimento/{id}               → tela do chamado
 * POST /atendimento/{id}/assumir       → assumir
 * POST /atendimento/{id}/devolver      → devolver para a fila
 * POST /atendimento/{id}/transferir    → transferir (usuarioId)
 * POST /atendimento/{id}/status        → mudar status (status, mensagem, motivo do aguardando)
 * POST /atendimento/{id}/prioridade    → mudar prioridade (prioridade)
 * POST /atendimento/{id}/interacao     → responder ao solicitante ou comentário interno (tipo, mensagem)
 *
 * Os formulários enviam "versao" (versão do chamado quando a página abriu). Se alguém alterou
 * o chamado nesse meio-tempo, aparece "Este chamado foi alterado por outra pessoa..." e a página recarrega.
 */
@Slf4j
@Controller
@TelaInterna
@RequestMapping("/atendimento/{id}")
@RequiredArgsConstructor
public class ChamadoController {

    static final String MENSAGEM_CONFLITO = "Este chamado foi alterado por outra pessoa. A página foi atualizada.";

    private final AtendimentoService atendimentoService;

    @GetMapping
    public String tela(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        TelaChamado tela = atendimentoService.carregarTela(id, usuario);
        model.addAttribute("tela", tela);
        model.addAttribute("c", tela.chamado());
        model.addAttribute("menuAtivo", tela.permissoes().ehResponsavel() ? "meus" : "fila");
        model.addAttribute("tamanhoMaximoTexto", AtendimentoService.TAMANHO_MAXIMO_TEXTO);
        return "atendimento/chamado";
    }

    @PostMapping("/assumir")
    public String assumir(@PathVariable Long id, @RequestParam(required = false) Long versao,
                          @AuthenticationPrincipal UsuarioLogado usuario, RedirectAttributes redirect) {
        return executar(id, redirect, null, null, () -> atendimentoService.assumir(id, versao, usuario));
    }

    @PostMapping("/devolver")
    public String devolver(@PathVariable Long id, @RequestParam(required = false) Long versao,
                           @AuthenticationPrincipal UsuarioLogado usuario, RedirectAttributes redirect) {
        return executar(id, redirect, null, null, () -> atendimentoService.devolver(id, versao, usuario));
    }

    @PostMapping("/transferir")
    public String transferir(@PathVariable Long id, @RequestParam(required = false) Long versao,
                             @RequestParam(required = false) Long usuarioId,
                             @AuthenticationPrincipal UsuarioLogado usuario, RedirectAttributes redirect) {
        return executar(id, redirect, null, null,
                () -> atendimentoService.transferir(id, versao, usuarioId, usuario));
    }

    @PostMapping("/status")
    public String status(@PathVariable Long id, @RequestParam(required = false) Long versao,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String mensagem,
                         @RequestParam(required = false) String motivo,
                         @AuthenticationPrincipal UsuarioLogado usuario, RedirectAttributes redirect) {
        StatusChamado novo = enumOuNulo(StatusChamado.class, status);
        MotivoAguardando motivoAguardando = enumOuNulo(MotivoAguardando.class, motivo);
        return executar(id, redirect, mensagem, "status-" + (novo == null ? "" : novo.name()),
                () -> atendimentoService.mudarStatus(id, versao, novo, mensagem, motivoAguardando, usuario));
    }

    @PostMapping("/prioridade")
    public String prioridade(@PathVariable Long id, @RequestParam(required = false) Long versao,
                             @RequestParam(required = false) String prioridade,
                             @AuthenticationPrincipal UsuarioLogado usuario, RedirectAttributes redirect) {
        Prioridade nova = enumOuNulo(Prioridade.class, prioridade);
        return executar(id, redirect, null, null,
                () -> atendimentoService.mudarPrioridade(id, versao, nova, usuario));
    }

    @PostMapping("/interacao")
    public String interacao(@PathVariable Long id,
                            @RequestParam(required = false) String tipo,
                            @RequestParam(required = false) String mensagem,
                            @AuthenticationPrincipal UsuarioLogado usuario, RedirectAttributes redirect) {
        TipoInteracao tipoInteracao = enumOuNulo(TipoInteracao.class, tipo);
        return executar(id, redirect, mensagem, tipoInteracao == null ? null : tipoInteracao.name(),
                () -> atendimentoService.registrarMensagem(id, tipoInteracao, mensagem, usuario));
    }

    /**
     * Roda a ação e volta para a tela do chamado com a mensagem de sucesso ou de erro.
     * Se der erro e a pessoa tinha digitado um texto, ele volta preenchido ("rascunho").
     * (404 e 403 seguem para as páginas de erro.)
     */
    private String executar(Long id, RedirectAttributes redirect, String textoDigitado, String origemTexto,
                            Supplier<String> acao) {
        try {
            redirect.addFlashAttribute("sucesso", acao.get());
        } catch (AcaoRecusadaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            guardarRascunho(redirect, textoDigitado, origemTexto);
        } catch (ConflitoVersaoException | ObjectOptimisticLockingFailureException e) {
            log.info("Conflito de versão no chamado {}: {}", id, e.getMessage());
            redirect.addFlashAttribute("erro", MENSAGEM_CONFLITO);
            guardarRascunho(redirect, textoDigitado, origemTexto);
        }
        return "redirect:/atendimento/" + id;
    }

    private static void guardarRascunho(RedirectAttributes redirect, String texto, String origem) {
        if (texto != null && !texto.isBlank() && origem != null) {
            redirect.addFlashAttribute("rascunho", texto.length() > AtendimentoService.TAMANHO_MAXIMO_TEXTO + 1000
                    ? texto.substring(0, AtendimentoService.TAMANHO_MAXIMO_TEXTO + 1000) : texto);
            redirect.addFlashAttribute("rascunhoOrigem", origem);
        }
    }

    private static <E extends Enum<E>> E enumOuNulo(Class<E> tipo, String valor) {
        if (valor == null) {
            return null;
        }
        return Arrays.stream(tipo.getEnumConstants())
                .filter(e -> e.name().equalsIgnoreCase(valor.strip()))
                .findFirst().orElse(null);
    }
}
