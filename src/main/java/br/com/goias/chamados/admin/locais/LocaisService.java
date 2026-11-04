package br.com.goias.chamados.admin.locais;

import br.com.goias.chamados.admin.comum.CadastroNomeService;
import br.com.goias.chamados.dominio.cadastro.Local;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

/**
 * Cadastro de locais (/admin/locais) — regras em {@link CadastroNomeService}.
 */
@Service
public class LocaisService extends CadastroNomeService<Local> {

    public LocaisService(EntityManager em) {
        super(em, Local.class, Local::new, "local", "um local");
    }
}
