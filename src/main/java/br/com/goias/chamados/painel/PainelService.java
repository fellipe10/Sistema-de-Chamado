package br.com.goias.chamados.painel;

import br.com.goias.chamados.atendimento.FilaService;
import br.com.goias.chamados.atendimento.LinhaFila;
import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosExpediente;
import br.com.goias.chamados.dominio.cadastro.SlaConfigRepository;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.usuario.Perfil;
import br.com.goias.chamados.dominio.usuario.Usuario;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import br.com.goias.chamados.login.UsuarioLogado;
import br.com.goias.chamados.painel.Indicadores.Resolvido;
import br.com.goias.chamados.painel.PainelTela.ItemAtendente;
import br.com.goias.chamados.painel.PainelTela.ItemCategoria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Monta o painel do gestor (GET /painel).
 * GESTOR vê a área dele; ADMIN escolhe a área (padrão: TI); ATENDENTE não entra (403).
 */
@Service
@RequiredArgsConstructor
public class PainelService {

    static final int LIMITE_ATENCAO = 5;
    static final int LIMITE_CATEGORIAS = 10;

    private final PainelConsultas consultas;
    private final AreaRepository areaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SlaConfigRepository slaConfigRepository;
    private final CalendariosExpediente calendarios;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PainelTela montar(UsuarioLogado usuario, PeriodoPainel periodo, Long areaParametro) {
        AreaEscolhida area = escolherArea(usuario, areaParametro);
        Long areaId = area.id();
        CalendarioExpediente calendario = calendarios.daArea(areaId);
        LocalDateTime agora = LocalDateTime.now();

        // ---- Situação atual
        long abertos = consultas.contarPorStatus(areaId, EnumSet.of(StatusChamado.ABERTO, StatusChamado.REABERTO));
        long novosHoje = consultas.contarAbertosDesde(areaId, LocalDate.now().atStartOfDay());
        long emAtendimento = consultas.contarPorStatus(areaId, EnumSet.of(StatusChamado.EM_ATENDIMENTO));
        long atendentesAtivos = consultas.contarAtendentesEmAtendimento(areaId);
        long atrasados = consultas.contarAtrasados(areaId, agora);

        // ---- Do período
        List<Resolvido> resolvidos = consultas.resolvidos(areaId, periodo.inicio(), periodo.fim());
        OptionalDouble mediaPrimeira = consultas.primeirasRespostas(areaId, periodo.inicio(), periodo.fim()).stream()
                .mapToLong(par -> calendario.minutosUteisEntre(par[0], par[1]))
                .average();
        OptionalDouble mediaResolucao = Indicadores.mediaResolucao(calendario, resolvidos);
        String notaPrimeira = slaConfigRepository.findByAreaIdAndPrioridade(areaId, Prioridade.MEDIA)
                .map(s -> "Meta da prioridade média: " + Indicadores.formatarDuracao(s.getMinutosPrimeiraResposta()))
                .orElse("Em horas úteis");

        GraficoPainel grafico = GraficoPainel.montar(periodo,
                consultas.aberturas(areaId, periodo.inicio(), periodo.fim()),
                resolvidos.stream().map(Resolvido::resolucao).toList(),
                calendario);

        List<LinhaFila> atencao = consultas.precisamDeAtencao(areaId, LIMITE_ATENCAO).stream()
                .map(c -> FilaService.paraLinha(c, calendario, agora))
                .toList();

        return new PainelTela(area, periodo,
                abertos, novosHoje, emAtendimento, atendentesAtivos, atrasados,
                resolvidos.size(),
                Indicadores.formatarDuracao(mediaPrimeira), notaPrimeira,
                Indicadores.formatarDuracao(mediaResolucao),
                Indicadores.sla(resolvidos),
                grafico, json(grafico),
                categorias(consultas.porCategoria(areaId, periodo.inicio(), periodo.fim())),
                atencao,
                atendentes(areaId, resolvidos, calendario));
    }

    /** GESTOR: a própria área. ADMIN: a escolhida (ou TI, ou a primeira). ATENDENTE: 403. */
    @Transactional(readOnly = true)
    public AreaEscolhida escolherArea(UsuarioLogado usuario, Long areaParametro) {
        if (usuario.getPerfil() == Perfil.GESTOR && usuario.getAreaId() != null) {
            return new AreaEscolhida(usuario.getAreaId(), usuario.getAreaNome(), usuario.getAreaSigla(), false, List.of());
        }
        if (usuario.getPerfil() != Perfil.ADMIN) {
            throw new AccessDeniedException("Painel só para gestor ou admin");
        }
        List<Area> areas = areaRepository.findAll().stream()
                .sorted(Comparator.comparing(a -> a.getNome().toLowerCase(Locale.ROOT)))
                .toList();
        if (areas.isEmpty()) {
            throw new IllegalStateException("Nenhuma área cadastrada");
        }
        Area escolhida = areas.stream().filter(a -> a.getId().equals(areaParametro)).findFirst()
                .or(() -> areas.stream().filter(a -> "TI".equalsIgnoreCase(a.getSigla())).findFirst())
                .orElse(areas.get(0));
        return new AreaEscolhida(escolhida.getId(), escolhida.getNome(), escolhida.getSigla(), true,
                areas.stream().map(a -> new AreaEscolhida.Opcao(a.getId(),
                        a.getNome() + (a.isAtivo() ? "" : " (inativa)"))).toList());
    }

    // =================================================================

    static List<ItemCategoria> categorias(List<Object[]> linhas) {
        if (linhas.isEmpty()) {
            return List.of();
        }
        List<ItemCategoria> itens = new ArrayList<>();
        long maior = (Long) linhas.get(0)[1];
        long demais = 0;
        int quantasDemais = 0;
        for (int i = 0; i < linhas.size(); i++) {
            String nome = (String) linhas.get(i)[0];
            long qtd = (Long) linhas.get(i)[1];
            // Mostra até 10 barras; se sobrar mais, a 10ª vira "Demais categorias" (barra cinza)
            if (i < LIMITE_CATEGORIAS - 1 || linhas.size() == LIMITE_CATEGORIAS) {
                itens.add(new ItemCategoria(nome, qtd, pct(qtd, maior), false));
            } else {
                demais += qtd;
                quantasDemais++;
            }
        }
        if (quantasDemais > 0) {
            itens.add(new ItemCategoria("Demais categorias (" + quantasDemais + ")", demais,
                    Math.min(100, pct(demais, maior)), true));
        }
        return itens;
    }

    /**
     * Pessoas ativas da área (atendentes e gestores) + quem resolveu chamado no período
     * (mesmo se hoje estiver inativo). Mais resolvidos primeiro; empate pelo nome.
     */
    private List<ItemAtendente> atendentes(Long areaId, List<Resolvido> resolvidos, CalendarioExpediente calendario) {
        Map<Long, String> pessoas = new LinkedHashMap<>();
        for (Usuario u : usuarioRepository.findByAreaIdAndAtivoTrueAndPerfilInOrderByNomeAsc(
                areaId, EnumSet.of(Perfil.ATENDENTE, Perfil.GESTOR))) {
            pessoas.put(u.getId(), u.getNome());
        }
        resolvidos.stream().filter(r -> r.atendenteId() != null)
                .forEach(r -> pessoas.putIfAbsent(r.atendenteId(), r.atendenteNome()));

        Map<Long, List<Resolvido>> porPessoa = resolvidos.stream().filter(r -> r.atendenteId() != null)
                .collect(Collectors.groupingBy(Resolvido::atendenteId));
        long maior = porPessoa.values().stream().mapToLong(List::size).max().orElse(0);

        return pessoas.entrySet().stream()
                .map(e -> {
                    List<Resolvido> dele = porPessoa.getOrDefault(e.getKey(), List.of());
                    return new ItemAtendente(iniciais(e.getValue()), e.getValue(), dele.size(),
                            pct(dele.size(), maior),
                            Indicadores.formatarDuracao(Indicadores.mediaResolucao(calendario, dele)));
                })
                .sorted(Comparator.comparingLong(ItemAtendente::resolvidos).reversed()
                        .thenComparing(i -> i.nome().toLowerCase(Locale.ROOT)))
                .toList();
    }

    static int pct(long valor, long maior) {
        return maior <= 0 ? 0 : (int) Math.round(valor * 100.0 / maior);
    }

    static String iniciais(String nome) {
        String[] partes = nome == null ? new String[0] : nome.strip().split("\\s+");
        if (partes.length == 0 || partes[0].isEmpty()) {
            return "?";
        }
        if (partes.length == 1) {
            return partes[0].substring(0, Math.min(2, partes[0].length())).toUpperCase(Locale.ROOT);
        }
        return (partes[0].substring(0, 1) + partes[partes.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    /** JSON do gráfico para o Chart.js (vai dentro de um <script type="application/json">). */
    private String json(GraficoPainel g) {
        try {
            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("tipo", g.tipo());
            dados.put("rotulos", g.rotulos());
            dados.put("mostrarRotulo", g.mostrarRotulo());
            dados.put("dicas", g.dicas());
            dados.put("abertos", g.abertos());
            dados.put("resolvidos", g.resolvidos());
            // "</" não pode aparecer dentro de <script>
            return objectMapper.writeValueAsString(dados).replace("</", "<\\/");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
