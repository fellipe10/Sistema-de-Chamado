package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.login.UsuarioLogado;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;

/**
 * Fila de chamados — em Lista (como sempre foi) ou em Kanban (só visualização).
 *
 * GET /atendimento                  → Lista, aba "Novos" (ou o Kanban, se foi a última escolha da pessoa)
 * GET /atendimento?filtro=meus      → "Meus chamados" (link do menu; no Kanban vira "Só os meus")
 * GET /atendimento?visao=kanban     → Kanban (a escolha fica guardada num cookie do navegador)
 * GET /atendimento?visao=lista      → Lista (idem)
 * GET /atendimento/quadro?...       → só o quadro do Kanban (atualização automática a cada 30 s, via kanban.js)
 *
 * Parâmetros da Lista: filtro=novos|meus|andamento|aguardando|historico, busca, prioridade,
 * categoria (id), area (id, só ADMIN), pagina (começa em 1),
 * atendente (id do responsável — atalho da administração ao desativar alguém).
 * Parâmetros do Kanban: os mesmos filtros (menos aba e página) + meus=true ("Só os meus").
 */
@Controller
@TelaInterna
@RequestMapping("/atendimento")
@RequiredArgsConstructor
public class FilaController {

    /** Cookie que lembra a última visão escolhida (lista ou kanban). */
    static final String COOKIE_VISAO = "chamados_visao_fila";

    private final FilaService filaService;
    private final KanbanService kanbanService;

    @GetMapping
    public String fila(@AuthenticationPrincipal UsuarioLogado usuario,
                       @RequestParam(required = false) String visao,
                       @CookieValue(name = COOKIE_VISAO, required = false) String visaoSalva,
                       @RequestParam(required = false) String filtro,
                       @RequestParam(required = false) String busca,
                       @RequestParam(required = false) String prioridade,
                       @RequestParam(required = false) String categoria,
                       @RequestParam(required = false) String area,
                       @RequestParam(required = false) String pagina,
                       @RequestParam(required = false) String atendente,
                       @RequestParam(required = false) String meus,
                       HttpServletResponse response,
                       Model model) {
        Visao escolhida = Visao.de(visao);
        if (escolhida != null) {
            lembrar(escolhida, response);
        }
        Visao atual = escolhida != null ? escolhida : (Visao.de(visaoSalva) != null ? Visao.de(visaoSalva) : Visao.LISTA);

        if (atual == Visao.KANBAN) {
            KanbanTela tela = kanbanService.montar(usuario, parametrosKanban(filtro, busca, prioridade, categoria, area,
                    atendente, meus));
            model.addAttribute("tela", tela);
            model.addAttribute("intervaloSegundos", KanbanService.INTERVALO_ATUALIZACAO_SEGUNDOS);
            model.addAttribute("menuAtivo", tela.soMeus() ? "meus" : "fila");
            return "atendimento/kanban";
        }

        FilaTela tela = filaService.montar(usuario, new FilaService.Parametros(
                filtro, busca, prioridade, numero(categoria), numero(area),
                numero(pagina) == null ? null : (int) Math.min(Integer.MAX_VALUE, numero(pagina)),
                numero(atendente)));
        model.addAttribute("tela", tela);
        model.addAttribute("menuAtivo", tela.aba() == AbaFila.MEUS ? "meus" : "fila");
        return "atendimento/fila";
    }

    /** Só o quadro do Kanban, para a atualização automática (não mexe na escolha de visão). */
    @GetMapping("/quadro")
    public String quadro(@AuthenticationPrincipal UsuarioLogado usuario,
                         @RequestParam(required = false) String busca,
                         @RequestParam(required = false) String prioridade,
                         @RequestParam(required = false) String categoria,
                         @RequestParam(required = false) String area,
                         @RequestParam(required = false) String atendente,
                         @RequestParam(required = false) String meus,
                         HttpServletResponse response,
                         Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        model.addAttribute("tela", kanbanService.montar(usuario,
                parametrosKanban(null, busca, prioridade, categoria, area, atendente, meus)));
        return "atendimento/kanban :: quadro";
    }

    static KanbanService.Parametros parametrosKanban(String filtro, String busca, String prioridade, String categoria,
                                                     String area, String atendente, String meus) {
        boolean soMeus = "true".equalsIgnoreCase(meus == null ? "" : meus.strip())
                || AbaFila.MEUS.getParametro().equalsIgnoreCase(filtro == null ? "" : filtro.strip());
        return new KanbanService.Parametros(busca, prioridade, numero(categoria), numero(area), numero(atendente), soMeus);
    }

    private static void lembrar(Visao visao, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_VISAO, visao.parametro)
                .path("/")
                .maxAge(Duration.ofDays(365))
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    enum Visao {
        LISTA("lista"), KANBAN("kanban");

        final String parametro;

        Visao(String parametro) {
            this.parametro = parametro;
        }

        /** null se o texto não for uma visão conhecida. */
        static Visao de(String valor) {
            if (valor == null) {
                return null;
            }
            for (Visao v : values()) {
                if (v.parametro.equalsIgnoreCase(valor.strip())) {
                    return v;
                }
            }
            return null;
        }
    }

    /** Parâmetro numérico vindo da URL; texto inválido é ignorado (em vez de erro 400). */
    private static Long numero(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(valor.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
