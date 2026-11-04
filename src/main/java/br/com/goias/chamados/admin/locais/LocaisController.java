package br.com.goias.chamados.admin.locais;

import br.com.goias.chamados.admin.comum.CadastroNomeController;
import br.com.goias.chamados.admin.comum.CadastroNomeService;
import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.dominio.cadastro.Local;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Cadastro de locais (só ADMIN). Telas e regras em {@link CadastroNomeController}.
 */
@Controller
@TelaInterna
@RequestMapping("/admin/locais")
@RequiredArgsConstructor
public class LocaisController extends CadastroNomeController<Local> {

    private static final Textos TEXTOS = new Textos("/admin/locais", "Locais",
            "Onde está o problema. Aparece no 2º passo do formulário público.", "local", "Novo local", "bi-geo-alt", "Ex.: Vestiário do CT");

    private final LocaisService service;

    @Override
    protected CadastroNomeService<Local> service() {
        return service;
    }

    @Override
    protected Textos textos() {
        return TEXTOS;
    }
}
