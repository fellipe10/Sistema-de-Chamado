package br.com.goias.chamados.admin.recalculo;

import br.com.goias.chamados.admin.comum.Parametros;
import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.login.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Botão "Recalcular prazos dos chamados em andamento" (só ADMIN).
 *
 * GET  /admin/recalcular?origem=sla|expediente|feriado&area={id}  → confirmação com a quantidade afetada
 * POST /admin/recalcular                                          → roda e volta para a tela de origem
 *
 * SLA e expediente: só a área da tela. Feriado: todas as áreas.
 */
@Controller
@TelaInterna
@RequestMapping("/admin/recalcular")
@RequiredArgsConstructor
public class RecalculoController {

    private final RecalculoPrazosService service;
    private final AreaRepository areaRepository;

    @GetMapping
    public String confirmar(@RequestParam(required = false) String origem,
                            @RequestParam(required = false) String area,
                            Model model) {
        OrigemRecalculo o = OrigemRecalculo.de(origem);
        if (o == null) {
            return "redirect:/admin";
        }
        Area a = alcance(o, area);
        model.addAttribute("origem", o);
        model.addAttribute("area", a);
        model.addAttribute("previa", service.previa(a == null ? null : a.getId()));
        model.addAttribute("voltar", voltar(o, a));
        model.addAttribute("menuAtivo", "configuracoes");
        return "admin/recalcular";
    }

    @PostMapping
    public String executar(@RequestParam(required = false) String origem,
                           @RequestParam(required = false) String area,
                           @AuthenticationPrincipal UsuarioLogado admin,
                           RedirectAttributes redirect) {
        OrigemRecalculo o = OrigemRecalculo.de(origem);
        if (o == null) {
            return "redirect:/admin";
        }
        Area a = alcance(o, area);
        try {
            int total = service.executar(a == null ? null : a.getId(), o, admin);
            redirect.addFlashAttribute("sucesso", total == 0
                    ? "Prazos conferidos: nenhum chamado em andamento precisou mudar."
                    : total + (total == 1 ? " chamado recalculado." : " chamados recalculados.")
                    + " Cada um ganhou um comentário interno registrando a mudança.");
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("erro", "Um dos chamados foi alterado por outra pessoa durante o recálculo. "
                    + "Nada foi gravado; tente de novo.");
        }
        return "redirect:" + voltar(o, a);
    }

    /** SLA/expediente: a área escolhida (precisa existir); feriado: todas (null). */
    private Area alcance(OrigemRecalculo o, String area) {
        if (!o.isPorArea()) {
            return null;
        }
        Long id = Parametros.numero(area);
        return id == null ? null : areaRepository.findById(id).orElse(null);
    }

    private static String voltar(OrigemRecalculo o, Area a) {
        return o.getTelaDeVolta() + (a == null ? "" : "?area=" + a.getId());
    }
}
