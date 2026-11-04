package br.com.goias.chamados.painel;

import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.login.UsuarioLogado;
import br.com.goias.chamados.painel.RelatoriosService.Arquivo;
import br.com.goias.chamados.painel.RelatoriosService.Parametros;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Painel do gestor e relatórios (GESTOR ou ADMIN — regra em SecurityConfig; ATENDENTE recebe 403).
 *
 * GET /painel                      → painel (?periodo=hoje|7d|30d|6m ou ?de=&ate=; ADMIN: &area=)
 * GET /painel/exportar             → CSV dos chamados abertos no período
 * GET /painel/relatorios           → lista paginada com filtros (status, prioridade, categoria, atendente)
 * GET /painel/relatorios/exportar  → CSV com os mesmos filtros
 *
 * Parâmetros numéricos inválidos na URL (ex.: area=abc) viram "não informado", sem erro 400.
 */
@Controller
@TelaInterna
@RequestMapping("/painel")
@RequiredArgsConstructor
public class PainelController {

    private final PainelService painelService;
    private final RelatoriosService relatoriosService;

    @GetMapping
    public String painel(@RequestParam(required = false) String periodo,
                         @RequestParam(required = false) String de,
                         @RequestParam(required = false) String ate,
                         @RequestParam(required = false) String area,
                         @AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        PeriodoPainel p = PeriodoPainel.interpretar(periodo, de, ate, LocalDate.now());
        model.addAttribute("tela", painelService.montar(usuario, p, numero(area)));
        model.addAttribute("menuAtivo", "painel");
        model.addAttribute("hoje", LocalDate.now());
        return "painel/painel";
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarPainel(@RequestParam(required = false) String periodo,
                                                 @RequestParam(required = false) String de,
                                                 @RequestParam(required = false) String ate,
                                                 @RequestParam(required = false) String area,
                                                 @AuthenticationPrincipal UsuarioLogado usuario) {
        PeriodoPainel p = PeriodoPainel.interpretar(periodo, de, ate, LocalDate.now());
        return csv(relatoriosService.exportar(usuario, p, numero(area), Parametros.vazios()));
    }

    @GetMapping("/relatorios")
    public String relatorios(@RequestParam(required = false) String periodo,
                             @RequestParam(required = false) String de,
                             @RequestParam(required = false) String ate,
                             @RequestParam(required = false) String area,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String prioridade,
                             @RequestParam(required = false) String categoria,
                             @RequestParam(required = false) String atendente,
                             @RequestParam(required = false) String pagina,
                             @AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        PeriodoPainel p = PeriodoPainel.interpretar(periodo, de, ate, LocalDate.now());
        Parametros filtros = new Parametros(status, prioridade, numero(categoria), numero(atendente),
                numero(pagina) == null ? null : numero(pagina).intValue());
        model.addAttribute("tela", relatoriosService.montar(usuario, p, numero(area), filtros));
        model.addAttribute("menuAtivo", "relatorios");
        model.addAttribute("hoje", LocalDate.now());
        return "painel/relatorios";
    }

    @GetMapping("/relatorios/exportar")
    public ResponseEntity<byte[]> exportarRelatorio(@RequestParam(required = false) String periodo,
                                                    @RequestParam(required = false) String de,
                                                    @RequestParam(required = false) String ate,
                                                    @RequestParam(required = false) String area,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String prioridade,
                                                    @RequestParam(required = false) String categoria,
                                                    @RequestParam(required = false) String atendente,
                                                    @AuthenticationPrincipal UsuarioLogado usuario) {
        PeriodoPainel p = PeriodoPainel.interpretar(periodo, de, ate, LocalDate.now());
        Parametros filtros = new Parametros(status, prioridade, numero(categoria), numero(atendente), null);
        return csv(relatoriosService.exportar(usuario, p, numero(area), filtros));
    }

    private static ResponseEntity<byte[]> csv(Arquivo arquivo) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(arquivo.conteudo());
    }

    static Long numero(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(texto.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
