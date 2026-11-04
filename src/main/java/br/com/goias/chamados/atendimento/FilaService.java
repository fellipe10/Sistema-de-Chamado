package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.atendimento.FilaConsultas.Criterios;
import br.com.goias.chamados.atendimento.FilaTela.Aba;
import br.com.goias.chamados.atendimento.FilaTela.Filtros;
import br.com.goias.chamados.atendimento.FilaTela.OpcaoArea;
import br.com.goias.chamados.atendimento.FilaTela.OpcaoCategoria;
import br.com.goias.chamados.atendimento.FilaTela.Paginacao;
import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosExpediente;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.usuario.Usuario;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import br.com.goias.chamados.login.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta a fila de chamados (GET /atendimento).
 *
 * - ATENDENTE e GESTOR: só a área deles.
 * - ADMIN: todas as áreas, com filtro de área opcional.
 * - Os contadores das abas usam os mesmos filtros (busca, prioridade, categoria, área).
 */
@Service
@RequiredArgsConstructor
public class FilaService {

    public static final int TAMANHO_PAGINA = 20;
    static final int TAMANHO_MAXIMO_BUSCA = 100;

    private final FilaConsultas consultas;
    private final AreaRepository areaRepository;
    private final CalendariosExpediente calendarios;
    private final UsuarioRepository usuarioRepository;

    /** Parâmetros como vieram da URL (texto), conferidos aqui. */
    public record Parametros(String filtro, String busca, String prioridade, Long categoria, Long area, Integer pagina,
                             Long atendente) {
    }

    @Transactional(readOnly = true)
    public FilaTela montar(UsuarioLogado usuario, Parametros p) {
        AbaFila aba = AbaFila.doParametro(p.filtro());

        // Área visível
        List<Area> areasAtivas = usuario.isAdmin() ? areaRepository.findAll().stream()
                .sorted((a, b) -> a.getNome().compareToIgnoreCase(b.getNome())).toList() : List.of();
        Long areaFiltro = null;
        Long areaEscopo;
        if (usuario.isAdmin()) {
            if (p.area() != null && areasAtivas.stream().anyMatch(a -> a.getId().equals(p.area()))) {
                areaFiltro = p.area();
            }
            areaEscopo = areaFiltro;
        } else {
            areaEscopo = usuario.getAreaId();
        }

        // Filtros conferidos
        String busca = limparBusca(p.busca());
        Prioridade prioridade = Arrays.stream(Prioridade.values())
                .filter(pr -> pr.name().equalsIgnoreCase(p.prioridade() == null ? "" : p.prioridade().strip()))
                .findFirst().orElse(null);
        List<OpcaoCategoria> categorias = consultas.categorias(areaEscopo).stream()
                .map(c -> new OpcaoCategoria(c.getId(), c.getNome() + (c.isAtivo() ? "" : " (inativa)"),
                        c.getArea().getNome()))
                .toList();
        Long categoriaId = p.categoria() != null && categorias.stream().anyMatch(c -> c.id().equals(p.categoria()))
                ? p.categoria() : null;

        // Responsável (Etapa 5): atalho "ver chamados desta pessoa". Só vale para alguém da área visível.
        final Long escopo = areaEscopo;
        Usuario responsavel = p.atendente() == null ? null : usuarioRepository.findById(p.atendente())
                .filter(u -> escopo == null || (u.getArea() != null && u.getArea().getId().equals(escopo))
                        || usuario.isAdmin())
                .orElse(null);
        Long atendenteId = responsavel == null ? null : responsavel.getId();
        String atendenteNome = responsavel == null ? null
                : responsavel.getNome() + (responsavel.isAtivo() ? "" : " (inativo)");

        Criterios criterios = new Criterios(areaEscopo, usuario.getId(), aba, busca, prioridade, categoriaId, atendenteId);

        // Contadores das abas
        List<Aba> abas = new ArrayList<>();
        long totalDaAba = 0;
        for (AbaFila a : AbaFila.values()) {
            long total = consultas.contar(criterios.naAba(a));
            abas.add(new Aba(a, total, a == aba));
            if (a == aba) {
                totalDaAba = total;
            }
        }

        // Página
        int totalPaginas = (int) Math.max(1, (totalDaAba + TAMANHO_PAGINA - 1) / TAMANHO_PAGINA);
        int pagina = p.pagina() == null ? 1 : Math.min(Math.max(1, p.pagina()), totalPaginas);
        int primeiro = (pagina - 1) * TAMANHO_PAGINA;

        LocalDateTime agora = LocalDateTime.now();
        Map<Long, CalendarioExpediente> calendarioPorArea = new HashMap<>();
        List<LinhaFila> linhas = totalDaAba == 0 ? List.of()
                : consultas.listar(criterios, primeiro, TAMANHO_PAGINA).stream()
                .map(c -> paraLinha(c, calendarioPorArea.computeIfAbsent(c.getArea().getId(), calendarios::daArea), agora))
                .toList();

        Paginacao paginacao = new Paginacao(pagina, totalPaginas, totalDaAba,
                totalDaAba == 0 ? 0 : primeiro + 1, primeiro + linhas.size());

        String areaDescricao = usuario.isAdmin()
                ? (areaFiltro == null ? "Todas as áreas"
                : areasAtivas.stream().filter(a -> a.getId().equals(p.area())).map(Area::getNome).findFirst().orElse(""))
                : usuario.getAreaNome();

        return new FilaTela(aba, abas, linhas,
                new Filtros(busca, prioridade, categoriaId, areaFiltro, atendenteId, atendenteNome),
                paginacao, categorias,
                areasAtivas.stream().map(a -> new OpcaoArea(a.getId(), a.getNome() + (a.isAtivo() ? "" : " (inativa)"))).toList(),
                List.of(Prioridade.values()),
                usuario.isAdmin(),
                areaDescricao);
    }

    /** Contador do menu lateral: chamados Novos da área do usuário (ADMIN: todas). */
    @Transactional(readOnly = true)
    public long contarNovos(UsuarioLogado usuario) {
        return consultas.contarNovos(usuario.isAdmin() ? null : usuario.getAreaId());
    }

    /** Linha da fila; o badge de prazo é medido em horas úteis da área. */
    public static LinhaFila paraLinha(Chamado c, CalendarioExpediente calendario, LocalDateTime agora) {
        return new LinhaFila(
                c.getId(),
                c.getProtocolo(),
                c.getTitulo(),
                c.getSolicitanteNome(),
                c.getSetor().getNome(),
                c.getCategoria().getNome(),
                c.getArea().getSigla(),
                c.getPrioridade(),
                c.getStatus(),
                primeiroNome(c.getAtendente() == null ? null : c.getAtendente().getNome()),
                BadgeSla.doChamado(c, calendario, agora));
    }

    public static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        return nome.strip().split("\\s+")[0];
    }

    private static String limparBusca(String busca) {
        if (busca == null) {
            return null;
        }
        String limpa = busca.strip().replaceAll("\\s+", " ");
        if (limpa.isEmpty()) {
            return null;
        }
        return limpa.length() > TAMANHO_MAXIMO_BUSCA ? limpa.substring(0, TAMANHO_MAXIMO_BUSCA) : limpa;
    }

}
