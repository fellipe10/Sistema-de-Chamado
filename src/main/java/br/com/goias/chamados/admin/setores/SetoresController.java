package br.com.goias.chamados.admin.setores;

import br.com.goias.chamados.admin.comum.CadastroNomeController;
import br.com.goias.chamados.admin.comum.CadastroNomeService;
import br.com.goias.chamados.config.TelaInterna;
import br.com.goias.chamados.dominio.cadastro.Setor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Cadastro de setores (só ADMIN). Telas e regras em {@link CadastroNomeController}.
 */
@Controller
@TelaInterna
@RequestMapping("/admin/setores")
@RequiredArgsConstructor
public class SetoresController extends CadastroNomeController<Setor> {

    private static final Textos TEXTOS = new Textos("/admin/setores", "Setores",
            "De onde vem quem abre o chamado. Aparece no 1º passo do formulário público.", "setor", "Novo setor", "bi-building", "Ex.: Departamento de Futebol");

    private final SetoresService service;

    @Override
    protected CadastroNomeService<Setor> service() {
        return service;
    }

    @Override
    protected Textos textos() {
        return TEXTOS;
    }
}
