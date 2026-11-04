package br.com.goias.chamados.abertura;

import br.com.goias.chamados.abertura.ArmazenamentoAnexos.ArquivoSalvo;
import br.com.goias.chamados.abertura.ValidadorAnexo.AnexoInvalidoException;
import br.com.goias.chamados.abertura.ValidadorAnexo.AnexoValidado;
import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.area.CategoriaRepository;
import br.com.goias.chamados.dominio.cadastro.LocalRepository;
import br.com.goias.chamados.dominio.cadastro.SetorRepository;
import br.com.goias.chamados.dominio.chamado.Anexo;
import br.com.goias.chamados.dominio.chamado.AnexoRepository;
import br.com.goias.chamados.dominio.chamado.CalculadoraPrazo;
import br.com.goias.chamados.dominio.chamado.CalculadoraPrazo.Prazos;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracao;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracaoRepository;
import br.com.goias.chamados.dominio.chamado.ChamadoRepository;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.chamado.TipoInteracao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Regras de abertura de chamado pelo formulário público.
 *
 * Passo a passo de {@link #abrir(AberturaForm)}:
 * 1. Confere no banco setor, área, categoria (ativa e da área escolhida) e local; confere os anexos.
 * 2. Grava os anexos em disco.
 * 3. Em UMA transação: chamado + interação inicial "Chamado aberto" + registros de anexo.
 *    Se o protocolo sorteado colidir no índice único, sorteia outro (até 5 tentativas).
 * 4. Se qualquer coisa falhar depois do passo 2, apaga os arquivos gravados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AberturaService {

    static final int MAX_TENTATIVAS_PROTOCOLO = 5;
    private static final String INDICE_PROTOCOLO = "uk_chamado_protocolo";

    private final SetorRepository setorRepository;
    private final AreaRepository areaRepository;
    private final CategoriaRepository categoriaRepository;
    private final LocalRepository localRepository;
    private final ChamadoRepository chamadoRepository;
    private final ChamadoInteracaoRepository interacaoRepository;
    private final AnexoRepository anexoRepository;
    private final CalculadoraPrazo calculadoraPrazo;
    private final GeradorProtocolo geradorProtocolo;
    private final ValidadorAnexo validadorAnexo;
    private final ArmazenamentoAnexos armazenamento;
    private final TransactionTemplate transacao;

    /**
     * Abre o chamado e devolve o protocolo gerado.
     *
     * @throws AberturaInvalidaException quando alguma regra recusa os dados (mensagens por campo)
     */
    public String abrir(AberturaForm form) {
        DadosConferidos dados = conferir(form);

        List<ArquivoSalvo> gravados = new ArrayList<>();
        try {
            for (AnexoValidado anexo : dados.anexos()) {
                gravados.add(armazenamento.salvar(anexo));
            }
            return gravarComProtocoloUnico(form, dados, gravados);
        } catch (RuntimeException e) {
            gravados.forEach(armazenamento::apagar);
            throw e;
        }
    }

    public boolean existe(String protocolo) {
        return chamadoRepository.existsByProtocolo(protocolo);
    }

    // ------------------------------------------------------------------
    // 1. Conferência dos dados no banco
    // ------------------------------------------------------------------

    private record DadosConferidos(Area area, List<AnexoValidado> anexos) {
    }

    private DadosConferidos conferir(AberturaForm form) {
        Map<String, String> erros = new LinkedHashMap<>();

        boolean setorOk = setorRepository.findById(form.getSetorId()).map(s -> s.isAtivo()).orElse(false);
        if (!setorOk) {
            erros.put("setorId", "Selecione um setor da lista.");
        }

        Area area = areaRepository.findById(form.getAreaId()).filter(Area::isAtivo).orElse(null);
        if (area == null) {
            erros.put("areaId", "Escolha a área: TI ou Manutenção.");
        } else {
            boolean categoriaDaArea = categoriaRepository.findByAreaIdAndAtivoTrueOrderByNomeAsc(area.getId())
                    .stream().anyMatch(c -> c.getId().equals(form.getCategoriaId()));
            if (!categoriaDaArea) {
                erros.put("categoriaId", "Selecione uma categoria da área escolhida.");
            }
        }

        boolean localOk = localRepository.findById(form.getLocalId()).map(l -> l.isAtivo()).orElse(false);
        if (!localOk) {
            erros.put("localId", "Selecione um local da lista.");
        }

        List<AnexoValidado> anexos = List.of();
        try {
            anexos = validadorAnexo.validar(form.getAnexosEnviados());
        } catch (AnexoInvalidoException e) {
            erros.put("anexos", e.getMessage());
        }

        if (!erros.isEmpty()) {
            throw new AberturaInvalidaException(erros);
        }
        return new DadosConferidos(area, anexos);
    }

    // ------------------------------------------------------------------
    // 3. Gravação (uma transação por tentativa de protocolo)
    // ------------------------------------------------------------------

    private String gravarComProtocoloUnico(AberturaForm form, DadosConferidos dados, List<ArquivoSalvo> arquivos) {
        LocalDateTime agora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS_PROTOCOLO; tentativa++) {
            String protocolo = geradorProtocolo.gerar(dados.area().getSigla(), agora.getYear());
            if (chamadoRepository.existsByProtocolo(protocolo)) {
                log.info("Protocolo {} já existe (tentativa {}); sorteando outro.", protocolo, tentativa);
                continue;
            }
            try {
                transacao.executeWithoutResult(status -> gravar(protocolo, agora, form, dados, arquivos));
                log.info("Chamado {} aberto ({} anexo(s)).", protocolo, arquivos.size());
                return protocolo;
            } catch (DataIntegrityViolationException e) {
                if (!colisaoDeProtocolo(e)) {
                    throw e;
                }
                log.warn("Colisão no índice único do protocolo {} (tentativa {}); sorteando outro.",
                        protocolo, tentativa);
            }
        }
        throw new IllegalStateException("Não foi possível gerar um protocolo único após "
                + MAX_TENTATIVAS_PROTOCOLO + " tentativas.");
    }

    private void gravar(String protocolo, LocalDateTime agora, AberturaForm form,
                        DadosConferidos dados, List<ArquivoSalvo> arquivos) {
        Long areaId = dados.area().getId();
        Prioridade prioridade = form.getUrgencia().getPrioridadeSugerida(); // BAIXA→BAIXA, MEDIA→MEDIA, ALTA→ALTA
        Prazos prazos = calculadoraPrazo.calcular(areaId, prioridade, agora);

        Chamado chamado = new Chamado();
        chamado.setProtocolo(protocolo);
        chamado.setArea(areaRepository.getReferenceById(areaId));
        chamado.setCategoria(categoriaRepository.getReferenceById(form.getCategoriaId()));
        chamado.setSetor(setorRepository.getReferenceById(form.getSetorId()));
        chamado.setLocal(localRepository.getReferenceById(form.getLocalId()));
        chamado.setSolicitanteNome(form.getNome());
        chamado.setSolicitanteEmail(form.getEmail() == null ? null : form.getEmail().toLowerCase(Locale.ROOT));
        chamado.setSolicitanteTelefone(form.getTelefone());
        chamado.setSolicitanteMatricula(form.getMatricula());
        chamado.setTitulo(form.getTitulo());
        chamado.setDescricao(form.getDescricao());
        chamado.setUrgencia(form.getUrgencia());
        chamado.setPrioridade(prioridade);
        chamado.setStatus(StatusChamado.ABERTO);
        chamado.setDataAbertura(agora);
        chamado.setPrazoPrimeiraResposta(prazos.primeiraResposta());
        chamado.setPrazoResolucao(prazos.resolucao());
        chamadoRepository.saveAndFlush(chamado); // flush aqui para a colisão de protocolo aparecer já

        ChamadoInteracao inicial = new ChamadoInteracao();
        inicial.setChamado(chamado);
        inicial.setAutor(null);
        inicial.setTipo(TipoInteracao.MUDANCA_STATUS);
        inicial.setStatusAnterior(null);
        inicial.setStatusNovo(StatusChamado.ABERTO);
        inicial.setTexto("Chamado aberto");
        inicial.setInterno(false);
        inicial.setData(agora);
        interacaoRepository.save(inicial);

        for (ArquivoSalvo arquivo : arquivos) {
            Anexo anexo = new Anexo();
            anexo.setChamado(chamado);
            anexo.setNomeOriginal(arquivo.anexo().nomeOriginal());
            anexo.setCaminho(arquivo.caminhoRelativo());
            anexo.setTipoMime(arquivo.anexo().tipoMime());
            anexo.setTamanho(arquivo.anexo().tamanho());
            anexo.setData(agora);
            anexoRepository.save(anexo);
        }
    }

    private static boolean colisaoDeProtocolo(Throwable erro) {
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && INDICE_PROTOCOLO.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().contains(INDICE_PROTOCOLO)) {
                return true;
            }
        }
        return false;
    }
}
