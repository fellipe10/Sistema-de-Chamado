package br.com.goias.chamados.admin;

import br.com.goias.chamados.admin.feriados.AnoReferencia;
import br.com.goias.chamados.admin.feriados.AvisoFeriados;
import br.com.goias.chamados.dominio.cadastro.FeriadoRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Números da página inicial de Configurações.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final EntityManager em;
    private final FeriadoRepository feriadoRepository;
    private final AnoReferencia anoReferencia;

    /**
     * Um card da página inicial.
     *
     * @param quantidade número em destaque; null = card sem número (ex.: Configurações gerais)
     * @param rotulo     texto depois do número ("ativos", "áreas"...)
     */
    public record Card(String url, String icone, String nome, String descricao, Long quantidade, String rotulo) {
    }

    @Transactional(readOnly = true)
    public List<Card> cards() {
        int ano = anoReferencia.anoAtual();
        return List.of(
                new Card("/admin/usuarios", "bi-people", "Usuários",
                        "Atendentes, gestores e administradores. Senhas e acesso.",
                        contar("select count(u) from Usuario u where u.ativo = true"), "ativos"),
                new Card("/admin/areas", "bi-diagram-3", "Áreas",
                        "Equipes que atendem chamados (TI, Manutenção...).",
                        contar("select count(a) from Area a where a.ativo = true"), "ativas"),
                new Card("/admin/categorias", "bi-tags", "Categorias",
                        "Tipos de problema de cada área.",
                        contar("select count(c) from Categoria c where c.ativo = true"), "ativas"),
                new Card("/admin/setores", "bi-building", "Setores",
                        "De onde vem quem abre o chamado.",
                        contar("select count(s) from Setor s where s.ativo = true"), "ativos"),
                new Card("/admin/locais", "bi-geo-alt", "Locais",
                        "Onde está o problema (sede, CT, estádio...).",
                        contar("select count(l) from Local l where l.ativo = true"), "ativos"),
                new Card("/admin/sla", "bi-stopwatch", "Prazos (SLA)",
                        "Tempo de 1ª resposta e de resolução por prioridade.",
                        contar("select count(distinct s.area.id) from SlaConfig s"), "áreas configuradas"),
                new Card("/admin/expediente", "bi-clock", "Expediente",
                        "Horário de trabalho de cada área (conta para o prazo).",
                        contar("select count(distinct e.area.id) from Expediente e"), "áreas configuradas"),
                new Card("/admin/feriados", "bi-calendar-event", "Feriados",
                        "Dias sem expediente. O prazo não conta nesses dias.",
                        em.createQuery("select count(f) from Feriado f where f.data between :de and :ate", Long.class)
                                .setParameter("de", LocalDate.of(ano, 1, 1))
                                .setParameter("ate", LocalDate.of(ano, 12, 31))
                                .getSingleResult(), "em " + ano),
                new Card("/admin/configuracoes", "bi-sliders", "Configurações gerais",
                        "Ramais do rodapé público e fechamento automático.", null, null));
    }

    /** Mensagem do aviso de feriados do próximo ano, ou null. */
    @Transactional(readOnly = true)
    public String avisoFeriados() {
        Integer ano = AvisoFeriados.anoSemFeriado(anoReferencia.anoAtual(), feriadoRepository.anosComFeriado());
        return ano == null ? null : AvisoFeriados.mensagem(ano);
    }

    private long contar(String jpql) {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }
}
