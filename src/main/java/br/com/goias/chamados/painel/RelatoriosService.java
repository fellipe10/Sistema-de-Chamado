package br.com.goias.chamados.painel;

import br.com.goias.chamados.atendimento.FilaService;
import br.com.goias.chamados.atendimento.FilaTela.Paginacao;
import br.com.goias.chamados.dominio.area.CategoriaRepository;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosExpediente;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.usuario.Perfil;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import br.com.goias.chamados.login.UsuarioLogado;
import br.com.goias.chamados.painel.PainelConsultas.FiltroLista;
import br.com.goias.chamados.painel.RelatoriosTela.Filtros;
import br.com.goias.chamados.painel.RelatoriosTela.LinhaRelatorio;
import br.com.goias.chamados.painel.RelatoriosTela.Opcao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Relatórios (GET /painel/relatorios) e exportação CSV (painel e relatórios).
 * Lista os chamados ABERTOS no período, da área, com filtros opcionais; 20 por página.
 */
@Service
@RequiredArgsConstructor
public class RelatoriosService {

    public static final int TAMANHO_PAGINA = 20;
    /** Proteção: a planilha nunca passa disso (6 meses de TI ficam bem abaixo). */
    static final int LIMITE_EXPORTACAO = 50_000;

    private final PainelService painelService;
    private final PainelConsultas consultas;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalendariosExpediente calendarios;

    /** Parâmetros como vieram da URL (texto), conferidos aqui. */
    public record Parametros(String status, String prioridade, Long categoria, Long atendente, Integer pagina) {

        public static Parametros vazios() {
            return new Parametros(null, null, null, null, null);
        }
    }

    /** Arquivo pronto para download. */
    public record Arquivo(String nome, byte[] conteudo) {
    }

    @Transactional(readOnly = true)
    public RelatoriosTela montar(UsuarioLogado usuario, PeriodoPainel periodo, Long areaParametro, Parametros p) {
        AreaEscolhida area = painelService.escolherArea(usuario, areaParametro);
        List<Opcao> categorias = categoriaRepository.findByAreaIdOrderByNomeAsc(area.id()).stream()
                .map(c -> new Opcao(c.getId(), c.getNome() + (c.isAtivo() ? "" : " (inativa)")))
                .toList();
        List<Opcao> atendentes = new ArrayList<>();
        atendentes.add(new Opcao(0L, "Sem atendente"));
        usuarioRepository.findByAreaIdAndAtivoTrueAndPerfilInOrderByNomeAsc(area.id(),
                        EnumSet.of(Perfil.ATENDENTE, Perfil.GESTOR))
                .forEach(u -> atendentes.add(new Opcao(u.getId(), u.getNome())));

        Filtros filtros = conferir(p, categorias, atendentes);
        FiltroLista filtro = filtro(area, periodo, filtros);

        long total = consultas.contar(filtro);
        int totalPaginas = (int) Math.max(1, (total + TAMANHO_PAGINA - 1) / TAMANHO_PAGINA);
        int pagina = p.pagina() == null ? 1 : Math.min(Math.max(1, p.pagina()), totalPaginas);
        int primeiro = (pagina - 1) * TAMANHO_PAGINA;

        CalendarioExpediente calendario = calendarios.daArea(area.id());
        LocalDateTime agora = LocalDateTime.now();
        List<LinhaRelatorio> linhas = total == 0 ? List.of()
                : consultas.listar(filtro, primeiro, TAMANHO_PAGINA).stream()
                .map(c -> new LinhaRelatorio(FilaService.paraLinha(c, calendario, agora), c.getDataAbertura()))
                .toList();
        Paginacao paginacao = new Paginacao(pagina, totalPaginas, total,
                total == 0 ? 0 : primeiro + 1, primeiro + linhas.size());

        return new RelatoriosTela(area, periodo, filtros, linhas, paginacao,
                Arrays.asList(StatusChamado.values()), Arrays.asList(Prioridade.values()), categorias, atendentes);
    }

    /** CSV dos chamados abertos no período (com os filtros dos relatórios, se houver). */
    @Transactional(readOnly = true)
    public Arquivo exportar(UsuarioLogado usuario, PeriodoPainel periodo, Long areaParametro, Parametros p) {
        AreaEscolhida area = painelService.escolherArea(usuario, areaParametro);
        Filtros filtros = conferir(p, null, null);
        List<Chamado> chamados = consultas.listar(filtro(area, periodo, filtros), 0, LIMITE_EXPORTACAO);
        byte[] conteudo = ExportacaoCsv.gerar(chamados, calendarios.daArea(area.id()));
        String nome = "chamados-" + area.sigla().toLowerCase(Locale.ROOT) + "-" + periodo.sufixoArquivo() + ".csv";
        return new Arquivo(nome, conteudo);
    }

    private static FiltroLista filtro(AreaEscolhida area, PeriodoPainel periodo, Filtros f) {
        return new FiltroLista(area.id(), periodo.inicio(), periodo.fim(), f.status(), f.prioridade(),
                f.categoriaId(), f.atendenteId());
    }

    /**
     * Valores inválidos na URL são ignorados (sem erro 400).
     * Na exportação (listas nulas) os ids são usados como vieram: a consulta já filtra pela área,
     * então um id de outra área só resulta em planilha vazia.
     */
    private static Filtros conferir(Parametros p, List<Opcao> categorias, List<Opcao> atendentes) {
        StatusChamado status = Arrays.stream(StatusChamado.values())
                .filter(s -> s.name().equalsIgnoreCase(p.status() == null ? "" : p.status().strip()))
                .findFirst().orElse(null);
        Prioridade prioridade = Arrays.stream(Prioridade.values())
                .filter(s -> s.name().equalsIgnoreCase(p.prioridade() == null ? "" : p.prioridade().strip()))
                .findFirst().orElse(null);
        Long categoria = p.categoria();
        if (categoria != null && categorias != null && categorias.stream().noneMatch(o -> o.id().equals(p.categoria()))) {
            categoria = null;
        }
        Long atendente = p.atendente();
        if (atendente != null && atendentes != null && atendentes.stream().noneMatch(o -> o.id().equals(p.atendente()))) {
            atendente = null;
        }
        return new Filtros(status, prioridade, categoria, atendente);
    }
}
