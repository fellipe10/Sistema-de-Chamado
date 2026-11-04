package br.com.goias.chamados.admin.usuarios;

import br.com.goias.chamados.admin.AcaoAdminRecusadaException;
import br.com.goias.chamados.admin.RegistroNaoEncontradoException;
import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.dominio.usuario.Perfil;
import br.com.goias.chamados.login.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.Map;

/**
 * Cadastro de usuários (só ADMIN — /admin/** no SecurityConfig).
 *
 * GET  /admin/usuarios                     → lista (busca, situação, perfil, área)
 * GET  /admin/usuarios/novo                → formulário novo
 * POST /admin/usuarios/novo                → cria e mostra a senha temporária uma vez
 * GET  /admin/usuarios/{id}                → editar
 * POST /admin/usuarios/{id}                → salvar
 * POST /admin/usuarios/{id}/redefinir-senha
 * POST /admin/usuarios/{id}/desativar (com devolverChamados=true para devolver os chamados à fila) | /reativar
 * GET  /admin/usuarios/sugerir-login?nome=...&id=... → sugestão de usuário livre (texto puro, usado pelo admin.js)
 */
@Controller
@TelaInterna
@RequestMapping("/admin/usuarios")
@RequiredArgsConstructor
public class UsuariosController {

    static final String MENSAGEM_CONFLITO = "Um dos chamados desta pessoa foi alterado por outro atendente "
            + "neste mesmo instante. Nada foi salvo: confira e tente de novo.";

    private final UsuariosService service;

    @GetMapping
    public String lista(@AuthenticationPrincipal UsuarioLogado admin,
                        @RequestParam(required = false) String busca,
                        @RequestParam(required = false) String situacao,
                        @RequestParam(required = false) String perfil,
                        @RequestParam(required = false) String area,
                        Model model) {
        String sit = situacao == null ? "ativos" : switch (situacao) {
            case "inativos", "todos" -> situacao;
            default -> "ativos";
        };
        UsuariosService.Filtros filtros = new UsuariosService.Filtros(
                busca == null || busca.isBlank() ? null : busca.strip(), sit, perfilDe(perfil), numero(area));
        model.addAttribute("filtros", filtros);
        model.addAttribute("usuarios", service.listar(filtros, admin));
        model.addAttribute("areas", service.areas());
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("menuAtivo", "configuracoes");
        return "admin/usuarios/lista";
    }

    /** Sugestão de usuário (login) ainda livre, a partir do nome. Texto puro; vazio se o nome não der sugestão. */
    @GetMapping(value = "/sugerir-login", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String sugerirLogin(@RequestParam(required = false) String nome,
                               @RequestParam(required = false) Long id) {
        return service.sugerirLogin(nome, id);
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        prepararFormulario(model, new UsuarioForm(), Map.of(), null);
        return "admin/usuarios/formulario";
    }

    @PostMapping("/novo")
    public String criar(@ModelAttribute("form") UsuarioForm form, BindingResult ignorado,
                        Model model, RedirectAttributes redirect) {
        UsuariosService.Resultado r = service.criar(form);
        if (!r.sucesso()) {
            prepararFormulario(model, form, r.erros(), null);
            return "admin/usuarios/formulario";
        }
        redirect.addFlashAttribute("sucesso", "Usuário criado. Passe a senha temporária para a pessoa.");
        redirect.addFlashAttribute("senhaTemporaria", r.senhaTemporaria());
        return "redirect:/admin/usuarios/" + r.id();
    }

    @GetMapping("/{id}")
    public String editar(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado admin, Model model) {
        UsuariosService.Detalhe d = service.detalhe(id, admin)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuário", id));
        UsuarioForm form = new UsuarioForm();
        form.setNome(d.nome());
        form.setLogin(d.login());
        form.setEmail(d.email());
        form.setPerfil(d.perfil());
        form.setAreaId(d.areaId());
        prepararFormulario(model, form, Map.of(), d);
        return "admin/usuarios/formulario";
    }

    @PostMapping("/{id}")
    public String salvar(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado admin,
                         @ModelAttribute("form") UsuarioForm form, BindingResult ignorado,
                         Model model, RedirectAttributes redirect) {
        UsuariosService.Detalhe d = service.detalhe(id, admin)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuário", id));
        UsuariosService.Resultado r;
        try {
            r = service.salvar(id, form, admin);
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("erro", MENSAGEM_CONFLITO);
            return "redirect:/admin/usuarios/" + id;
        }
        if (!r.sucesso()) {
            prepararFormulario(model, form, r.erros(), d);
            model.addAttribute("confirmarArea", r.confirmarArea());
            return "admin/usuarios/formulario";
        }
        redirect.addFlashAttribute("sucesso", "Alterações salvas.");
        if (r.aviso() != null) {
            redirect.addFlashAttribute("avisoUsuario", r.aviso());
        }
        return "redirect:/admin/usuarios/" + id;
    }

    @PostMapping("/{id}/redefinir-senha")
    public String redefinirSenha(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado admin,
                                 RedirectAttributes redirect) {
        try {
            String senha = service.redefinirSenha(id, admin);
            redirect.addFlashAttribute("sucesso",
                    "Senha redefinida. Se a pessoa estava usando o sistema, ela foi desconectada.");
            redirect.addFlashAttribute("senhaTemporaria", senha);
        } catch (AcaoAdminRecusadaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/usuarios/" + id;
    }

    /**
     * Desativar. A caixa "Devolver os chamados dele para a fila" já vem marcada na tela
     * (só aparece quando a pessoa tem chamados em andamento). Caixa desmarcada = o navegador não envia o campo
     * = os chamados continuam com a pessoa (comportamento da Etapa 5).
     */
    @PostMapping("/{id}/desativar")
    public String desativar(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado admin,
                            @RequestParam(defaultValue = "false") boolean devolverChamados,
                            RedirectAttributes redirect) {
        UsuariosService.ResultadoSituacao r;
        try {
            r = service.alterarSituacao(id, false, devolverChamados, admin);
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("erro", MENSAGEM_CONFLITO);
            return "redirect:/admin/usuarios/" + id;
        }
        if (r.erro() != null) {
            redirect.addFlashAttribute("erro", r.erro());
        } else {
            redirect.addFlashAttribute("sucesso", r.nome() + " foi desativado(a) e desconectado(a) do sistema."
                    + (r.devolvidos() > 0
                    ? " " + r.devolvidos() + (r.devolvidos() == 1 ? " chamado voltou" : " chamados voltaram")
                      + " para a fila, sem responsável."
                    : ""));
            if (r.emAndamento() > 0) {
                redirect.addFlashAttribute("chamadosDoDesativado", r.emAndamento());
                redirect.addFlashAttribute("linkFilaDesativado", linkFila(id, r.areaId()));
            }
        }
        return "redirect:/admin/usuarios/" + id;
    }

    @PostMapping("/{id}/reativar")
    public String reativar(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado admin,
                           RedirectAttributes redirect) {
        UsuariosService.ResultadoSituacao r = service.alterarSituacao(id, true, false, admin);
        if (r.erro() != null) {
            redirect.addFlashAttribute("erro", r.erro());
        } else {
            redirect.addFlashAttribute("sucesso", r.nome() + " foi reativado(a) e já pode entrar no sistema.");
        }
        return "redirect:/admin/usuarios/" + id;
    }

    /** Fila "Em andamento" filtrada pelo responsável (e pela área dele, para o admin). */
    static String linkFila(Long usuarioId, Long areaId) {
        return "/atendimento?filtro=andamento&atendente=" + usuarioId + (areaId == null ? "" : "&area=" + areaId);
    }

    private void prepararFormulario(Model model, UsuarioForm form, Map<String, String> erros,
                                    UsuariosService.Detalhe detalhe) {
        model.addAttribute("form", form);
        model.addAttribute("erros", erros);
        model.addAttribute("detalhe", detalhe);
        model.addAttribute("areas", service.areas());
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("menuAtivo", "configuracoes");
        if (detalhe != null) {
            model.addAttribute("linkFila", linkFila(detalhe.id(), detalhe.areaId()));
        }
        if (!model.containsAttribute("confirmarArea")) {
            model.addAttribute("confirmarArea", 0L);
        }
    }

    private static Perfil perfilDe(String valor) {
        if (valor == null) {
            return null;
        }
        return Arrays.stream(Perfil.values()).filter(p -> p.name().equalsIgnoreCase(valor.strip()))
                .findFirst().orElse(null);
    }

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
