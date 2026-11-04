package br.com.goias.chamados.admin.setores;

import br.com.goias.chamados.admin.comum.CadastroNomeService;
import br.com.goias.chamados.dominio.cadastro.Setor;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

/**
 * Cadastro de setores (/admin/setores) — regras em {@link CadastroNomeService}.
 */
@Service
public class SetoresService extends CadastroNomeService<Setor> {

    public SetoresService(EntityManager em) {
        super(em, Setor.class, Setor::new, "setor", "um setor");
    }
}
