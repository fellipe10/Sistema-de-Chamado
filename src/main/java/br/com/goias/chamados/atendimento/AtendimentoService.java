package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.atendimento.TelaChamado.AnexoTela;
import br.com.goias.chamados.atendimento.TelaChamado.ItemHistorico;
import br.com.goias.chamados.atendimento.TelaChamado.OpcaoStatus;
import br.com.goias.chamados.atendimento.TelaChamado.Pessoa;
import br.com.goias.chamados.dominio.cadastro.CalendariosExpediente;
import br.com.goias.chamados.dominio.chamado.Anexo;
import br.com.goias.chamados.dominio.chamado.AnexoRepository;
import br.com.goias.chamados.dominio.chamado.CalculadoraPrazo;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracao;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracaoRepository;
import br.com.goias.chamados.dominio.chamado.ChamadoRepository;
import br.com.goias.chamados.dominio.chamado.MotivoAguardando;
import br.com.goias.chamados.dominio.chamado.PausaSla;
import br.com.goias.chamados.dominio.chamado.Prioridade;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.chamado.TipoInteracao;
import br.com.goias.chamados.dominio.usuario.Perfil;
import br.com.goias.chamados.dominio.usuario.Usuario;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import br.com.goias.chamados.login.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Regras da tela do chamado e das ações de atendimento.
 * Cada ação roda em UMA transação; o controller faz o redirect com a mensagem (PRG).
 *
 * Erros:
 * - chamado inexistente ou de outra área → 404 (não revela que existe);
 * - sem permissão para a ação → 403 (AccessDeniedException);
 * - regra de negócio → AcaoRecusadaException (mensagem na tela);
 * - página desatualizada → ConflitoVersaoException (mensagem + recarregar).
 *
 * Etapa 4 — pausa do SLA (ver {@link PausaSla}):
 * - marcar AGUARDANDO exige o motivo; "Aguardando o solicitante" pausa o prazo;
 * - sair de AGUARDANDO (retomar, resolver ou devolver para a fila) encerra a pausa e empurra os prazos;
 * - mudar prioridade recalcula a partir da abertura, em horário útil, somando os minutos já pausados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtendimentoService {

    public static final int TAMANHO_MAXIMO_TEXTO = 4000;
    private static final EnumSet<StatusChamado> FINALIZADOS = EnumSet.of(StatusChamado.RESOLVIDO, StatusChamado.FECHADO);
    private static final EnumSet<Perfil> PERFIS_DE_ATENDIMENTO = EnumSet.of(Perfil.ATENDENTE, Perfil.GESTOR);

    private final ChamadoRepository chamadoRepository;
    private final ChamadoInteracaoRepository interacaoRepository;
    private final AnexoRepository anexoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalculadoraPrazo calculadoraPrazo;
    private final CalendariosExpediente calendarios;

    // =================================================================
    // Tela
    // =================================================================

    @Transactional(readOnly = true)
    public TelaChamado carregarTela(Long chamadoId, UsuarioLogado usuario) {
        Chamado chamado = buscarVisivel(chamadoId, usuario);
        PermissoesAtendimento perm = permissoes(chamado, usuario);
        StatusChamado status = chamado.getStatus();

        List<ItemHistorico> historico = interacaoRepository.findByChamadoIdOrderByDataAscIdAsc(chamado.getId())
                .stream()
                .map(AtendimentoService::paraHistorico)
                .sorted(Comparator.comparing(ItemHistorico::data).reversed()) // mais recente primeiro
                .toList();

        List<AnexoTela> anexos = anexoRepository.findByChamadoIdOrderByDataAscIdAsc(chamado.getId()).stream()
                .map(a -> new AnexoTela(a.getId(), CaminhoAnexo.nomeSeguro(a.getNomeOriginal()), a.getTipoMime(),
                        tamanhoLegivel(a.getTamanho()), iconeAnexo(a.getTipoMime())))
                .toList();

        List<OpcaoStatus> opcoes = perm.podeAlterar()
                ? TransicoesStatus.permitidas(status).stream().map(destino -> opcaoStatus(status, destino)).toList()
                : List.of();

        List<Pessoa> paraTransferir = perm.podeTransferir() && status != StatusChamado.FECHADO
                ? usuarioRepository.findByAreaIdAndAtivoTrueAndPerfilInOrderByNomeAsc(
                        chamado.getArea().getId(), PERFIS_DE_ATENDIMENTO).stream()
                .filter(u -> chamado.getAtendente() == null || !u.getId().equals(chamado.getAtendente().getId()))
                .map(u -> new Pessoa(u.getId(), u.getNome(), u.getPerfil().getDescricao()))
                .toList()
                : List.of();

        return new TelaChamado(
                chamado,
                perm,
                BadgeSla.doChamado(chamado, calendarios.daArea(chamado.getArea().getId()), LocalDateTime.now()),
                historico,
                anexos,
                opcoes,
                List.of(Prioridade.values()),
                paraTransferir,
                perm.podeAlterar() && status != StatusChamado.FECHADO,
                perm.podeAlterar() && !FINALIZADOS.contains(status),
                List.of(MotivoAguardando.values()));
    }

    // =================================================================
    // Ações
    // =================================================================

    /** Assumir: qualquer ATENDENTE/GESTOR da área, se não houver responsável. */
    @Transactional
    public String assumir(Long chamadoId, Long versao, UsuarioLogado usuario) {
        Chamado chamado = buscarVisivel(chamadoId, usuario);
        conferirVersao(chamado, versao);
        if (chamado.getStatus() == StatusChamado.FECHADO) {
            throw new AcaoRecusadaException("Chamado fechado não pode ser assumido.");
        }
        if (chamado.getAtendente() != null) {
            throw new AcaoRecusadaException("Este chamado já tem responsável: " + chamado.getAtendente().getNome() + ".");
        }
        exigir(permissoes(chamado, usuario).podeAssumir());

        LocalDateTime agora = LocalDateTime.now();
        Usuario eu = usuarioRepository.getReferenceById(usuario.getId());
        chamado.setAtendente(eu);
        gravar(chamado, eu, TipoInteracao.ATRIBUICAO, "Chamado assumido por " + usuario.getNome() + ".", null, null, agora);
        colocarEmAtendimento(chamado, eu, agora);
        RegraPrimeiraResposta.registrar(chamado, agora);
        return "Você assumiu o chamado " + chamado.getProtocolo() + ".";
    }

    /**
     * Devolver para a fila: só o próprio responsável.
     * Volta para ABERTO se ainda não houve resposta ao solicitante; senão continua no mesmo status, sem responsável.
     */
    @Transactional
    public String devolver(Long chamadoId, Long versao, UsuarioLogado usuario) {
        Chamado chamado = buscarVisivel(chamadoId, usuario);
        conferirVersao(chamado, versao);
        exigir(permissoes(chamado, usuario).podeDevolver());
        if (FINALIZADOS.contains(chamado.getStatus())) {
            throw new AcaoRecusadaException("Chamado resolvido ou fechado não pode ser devolvido para a fila.");
        }

        LocalDateTime agora = LocalDateTime.now();
        Usuario eu = usuarioRepository.getReferenceById(usuario.getId());
        chamado.setAtendente(null);
        gravar(chamado, eu, TipoInteracao.ATRIBUICAO, usuario.getNome() + " devolveu o chamado para a fila.", null, null, agora);

        boolean jaRespondeu = interacaoRepository.existsByChamadoIdAndTipo(chamado.getId(), TipoInteracao.RESPOSTA_SOLICITANTE);
        StatusChamado atual = chamado.getStatus();
        if (!jaRespondeu && (atual == StatusChamado.EM_ATENDIMENTO || atual == StatusChamado.AGUARDANDO)) {
            if (atual == StatusChamado.AGUARDANDO) {
                PausaSla.encerrar(chamado, agora, calculadoraPrazo);
            }
            mudar(chamado, eu, StatusChamado.ABERTO, null, agora);
        }
        return "Chamado devolvido para a fila.";
    }

    /** Transferir: GESTOR da área ou ADMIN, para outro ATENDENTE/GESTOR ativo da mesma área. */
    @Transactional
    public String transferir(Long chamadoId, Long versao, Long destinoId, UsuarioLogado usuario) {
        Chamado chamado = buscarVisivel(chamadoId, usuario);
        conferirVersao(chamado, versao);
        exigir(permissoes(chamado, usuario).podeTransferir());
        if (chamado.getStatus() == StatusChamado.FECHADO) {
            throw new AcaoRecusadaException("Chamado fechado não pode ser transferido.");
        }
        Usuario destino = destinoId == null ? null : usuarioRepository.findById(destinoId).orElse(null);
        if (destino == null || !destino.isAtivo() || !PERFIS_DE_ATENDIMENTO.contains(destino.getPerfil())
                || destino.getArea() == null || !destino.getArea().getId().equals(chamado.getArea().getId())) {
            throw new AcaoRecusadaException("Escolha um atendente ou gestor ativo da área " + chamado.getArea().getNome() + ".");
        }
        Usuario anterior = chamado.getAtendente();
        if (anterior != null && anterior.getId().equals(destino.getId())) {
            throw new AcaoRecusadaException(destino.getNome() + " já é o responsável por este chamado.");
        }

        LocalDateTime agora = LocalDateTime.now();
        Usuario eu = usuarioRepository.getReferenceById(usuario.getId());
        chamado.setAtendente(destino);
        String texto = anterior == null
                ? "Chamado atribuído a " + destino.getNome() + " por " + usuario.getNome() + "."
                : "Transferido de " + anterior.getNome() + " para " + destino.getNome() + " por " + usuario.getNome() + ".";
        gravar(chamado, eu, TipoInteracao.ATRIBUICAO, texto, null, null, agora);
        // Decisão da Etapa 3: atribuir um chamado ABERTO/REABERTO já o coloca em atendimento
        // (senão o novo responsável não teria como iniciar o atendimento).
        colocarEmAtendimento(chamado, eu, agora);
        return "Chamado transferido para " + destino.getNome() + ".";
    }

    /**
     * Mudar status: só o responsável, GESTOR da área ou ADMIN, e só pelas transições permitidas.
     *
     * @param motivo obrigatório ao marcar AGUARDANDO (ignorado nos outros status)
     */
    @Transactional
    public String mudarStatus(Long chamadoId, Long versao, StatusChamado novo, String mensagem,
                              MotivoAguardando motivo, UsuarioLogado usuario) {
        Chamado chamado = buscarVisivel(chamadoId, usuario);
        exigir(permissoes(chamado, usuario).podeAlterar());
        StatusChamado atual = chamado.getStatus();
        if (novo == null || !TransicoesStatus.permitida(atual, novo)) {
            throw new AcaoRecusadaException("Não é possível mudar de \"" + atual.getDescricao() + "\" para \""
                    + (novo == null ? "?" : novo.getDescricao()) + "\".");
        }
        conferirVersao(chamado, versao);
        String texto = limpar(mensagem);
        if (TransicoesStatus.exigeMensagem(novo)) {
            if (texto == null) {
                throw new AcaoRecusadaException(novo == StatusChamado.RESOLVIDO
                        ? "Escreva a mensagem de solução para o solicitante."
                        : "Informe o motivo da reabertura.");
            }
            conferirTamanho(texto);
        }
        if (novo == StatusChamado.AGUARDANDO && motivo == null) {
            throw new AcaoRecusadaException("Escolha o motivo: aguardando o solicitante ou aguardando peça/fornecedor/terceiro.");
        }

        LocalDateTime agora = LocalDateTime.now();
        Usuario eu = usuarioRepository.getReferenceById(usuario.getId());

        // Saindo de AGUARDANDO: encerra a pausa (empurra os prazos pelos minutos úteis parados)
        long minutosEmpurrados = 0;
        if (atual == StatusChamado.AGUARDANDO) {
            minutosEmpurrados = PausaSla.encerrar(chamado, agora, calculadoraPrazo);
        }
        String extra = minutosEmpurrados > 0
                ? " O prazo foi estendido em " + formatarMinutos(minutosEmpurrados) + " úteis (tempo pausado)."
                : "";

        switch (novo) {
            case RESOLVIDO -> {
                chamado.setDataResolucao(agora);
                mudar(chamado, eu, novo, null, agora);
                gravar(chamado, eu, TipoInteracao.RESPOSTA_SOLICITANTE, texto, null, null, agora);
                RegraPrimeiraResposta.registrar(chamado, agora);
            }
            case FECHADO -> {
                chamado.setDataFechamento(agora);
                mudar(chamado, eu, novo, null, agora);
            }
            case REABERTO -> {
                chamado.setDataResolucao(null);
                gravar(chamado, eu, TipoInteracao.COMENTARIO, "Motivo da reabertura: " + texto, null, null, agora);
                mudar(chamado, eu, novo, null, agora);
            }
            case AGUARDANDO -> {
                PausaSla.iniciar(chamado, motivo, agora);
                // O texto vai para a consulta pública ("Aguardando seu retorno" / "Aguardando peça ou terceiro")
                mudar(chamado, eu, novo, motivo.getTextoPublico(), agora);
                return "Status alterado para \"" + motivo.getDescricao() + "\"."
                        + (motivo.isPausaPrazo() ? " O prazo está pausado." : " O prazo continua correndo.");
            }
            default -> mudar(chamado, eu, novo, null, agora);
        }
        return "Status alterado para \"" + novo.getDescricao() + "\"." + extra;
    }

    /** Mudar prioridade (inclui CRÍTICA): recalcula os prazos a partir da abertura. */
    @Transactional
    public String mudarPrioridade(Long chamadoId, Long versao, Prioridade nova, UsuarioLogado usuario) {
        Chamado chamado = buscarVisivel(chamadoId, usuario);
        conferirVersao(chamado, versao);
        exigir(permissoes(chamado, usuario).podeAlterar());
        if (FINALIZADOS.contains(chamado.getStatus())) {
            throw new AcaoRecusadaException("A prioridade de um chamado resolvido ou fechado não pode mudar.");
        }
        if (nova == null) {
            throw new AcaoRecusadaException("Escolha a nova prioridade.");
        }
        Prioridade anterior = chamado.getPrioridade();
        if (anterior == nova) {
            throw new AcaoRecusadaException("O chamado já está com prioridade " + nova.getDescricao() + ".");
        }

        // A partir da abertura, em horário útil, somando os minutos já pausados (Etapa 4)
        CalculadoraPrazo.Prazos prazos = PausaSla.recalcular(chamado, nova, calculadoraPrazo);
        chamado.setPrioridade(nova);
        chamado.setPrazoPrimeiraResposta(prazos.primeiraResposta());
        chamado.setPrazoResolucao(prazos.resolucao());

        Usuario eu = usuarioRepository.getReferenceById(usuario.getId());
        gravar(chamado, eu, TipoInteracao.COMENTARIO,
                "Prioridade alterada de " + anterior.getDescricao() + " para " + nova.getDescricao(),
                null, null, LocalDateTime.now());
        return "Prioridade alterada para " + nova.getDescricao() + ". Os prazos foram recalculados.";
    }

    /**
     * Mensagem da caixa de texto:
     * - RESPOSTA_SOLICITANTE (aparece na consulta pública): responsável, GESTOR da área ou ADMIN; não em chamado fechado.
     * - COMENTARIO (interno): qualquer um da área (e ADMIN).
     */
    @Transactional
    public String registrarMensagem(Long chamadoId, TipoInteracao tipo, String mensagem, UsuarioLogado usuario) {
        Chamado chamado = buscarVisivel(chamadoId, usuario);
        PermissoesAtendimento perm = permissoes(chamado, usuario);
        if (tipo != TipoInteracao.RESPOSTA_SOLICITANTE && tipo != TipoInteracao.COMENTARIO) {
            throw new AcaoRecusadaException("Tipo de mensagem inválido.");
        }
        String texto = limpar(mensagem);
        if (texto == null) {
            throw new AcaoRecusadaException("Escreva a mensagem antes de enviar.");
        }
        conferirTamanho(texto);

        LocalDateTime agora = LocalDateTime.now();
        Usuario eu = usuarioRepository.getReferenceById(usuario.getId());
        if (tipo == TipoInteracao.RESPOSTA_SOLICITANTE) {
            exigir(perm.podeAlterar());
            if (chamado.getStatus() == StatusChamado.FECHADO) {
                throw new AcaoRecusadaException("Chamado fechado não recebe mais respostas.");
            }
            gravar(chamado, eu, tipo, texto, null, null, agora);
            RegraPrimeiraResposta.registrar(chamado, agora);
            return "Resposta enviada. O solicitante vê esta mensagem na consulta pelo código.";
        }
        exigir(perm.podeComentar());
        gravar(chamado, eu, tipo, texto, null, null, agora);
        return "Comentário interno registrado.";
    }

    // =================================================================
    // Apoio
    // =================================================================

    /** Chamado da área do usuário (ADMIN: qualquer um). Outra área ou inexistente → 404. */
    Chamado buscarVisivel(Long chamadoId, UsuarioLogado usuario) {
        Chamado chamado = chamadoId == null ? null : chamadoRepository.findComDetalhesById(chamadoId).orElse(null);
        if (chamado == null || !permissoes(chamado, usuario).podeVer()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return chamado;
    }

    static PermissoesAtendimento permissoes(Chamado chamado, UsuarioLogado usuario) {
        return PermissoesAtendimento.calcular(usuario.getPerfil(), usuario.getId(), usuario.getAreaId(),
                chamado.getArea().getId(),
                chamado.getAtendente() == null ? null : chamado.getAtendente().getId());
    }

    private static void exigir(boolean permitido) {
        if (!permitido) {
            throw new AccessDeniedException("Ação não permitida para este usuário");
        }
    }

    /** A página foi aberta com outra versão do chamado → alguém alterou no meio do caminho. */
    private static void conferirVersao(Chamado chamado, Long versaoDoFormulario) {
        if (versaoDoFormulario == null || !Objects.equals(versaoDoFormulario, chamado.getVersao())) {
            throw new ConflitoVersaoException();
        }
    }

    /** ABERTO ou REABERTO → EM_ATENDIMENTO (ao assumir/atribuir). */
    private void colocarEmAtendimento(Chamado chamado, Usuario autor, LocalDateTime agora) {
        if (chamado.getStatus() == StatusChamado.ABERTO || chamado.getStatus() == StatusChamado.REABERTO) {
            mudar(chamado, autor, StatusChamado.EM_ATENDIMENTO, null, agora);
        }
    }

    /** Troca o status e grava MUDANCA_STATUS (status anterior e novo). */
    private void mudar(Chamado chamado, Usuario autor, StatusChamado novo, String texto, LocalDateTime agora) {
        StatusChamado anterior = chamado.getStatus();
        chamado.setStatus(novo);
        gravar(chamado, autor, TipoInteracao.MUDANCA_STATUS, texto, anterior, novo, agora);
    }

    /** Grava uma interação; "interno" segue a regra oficial do tipo (TipoInteracao.isInterno). */
    private void gravar(Chamado chamado, Usuario autor, TipoInteracao tipo, String texto,
                        StatusChamado anterior, StatusChamado novo, LocalDateTime agora) {
        ChamadoInteracao interacao = new ChamadoInteracao();
        interacao.setChamado(chamado);
        interacao.setAutor(autor);
        interacao.setTipo(tipo);
        interacao.setTexto(texto);
        interacao.setInterno(tipo.isInterno());
        interacao.setStatusAnterior(anterior);
        interacao.setStatusNovo(novo);
        interacao.setData(agora);
        interacaoRepository.save(interacao);
    }

    private static String limpar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.replace("\r\n", "\n").strip();
        return limpo.isEmpty() ? null : limpo;
    }

    private static void conferirTamanho(String texto) {
        if (texto.length() > TAMANHO_MAXIMO_TEXTO) {
            throw new AcaoRecusadaException("A mensagem pode ter no máximo " + TAMANHO_MAXIMO_TEXTO + " caracteres.");
        }
    }

    private static OpcaoStatus opcaoStatus(StatusChamado atual, StatusChamado destino) {
        return switch (destino) {
            case EM_ATENDIMENTO -> new OpcaoStatus(destino,
                    atual == StatusChamado.REABERTO ? "Voltar a atender" : "Retomar atendimento",
                    "bi-play-circle", "btn-outline-primary", false, false, null,
                    atual == StatusChamado.AGUARDANDO ? "Se o prazo estava pausado, ele é estendido pelo tempo parado." : null);
            case AGUARDANDO -> new OpcaoStatus(destino, "Marcar como aguardando", "bi-hourglass-split",
                    "btn-secundario", false, true, "Aguardando o quê?",
                    "Só \"aguardando o solicitante\" pausa o prazo.");
            case RESOLVIDO -> new OpcaoStatus(destino, "Resolver chamado", "bi-check2-circle", "btn-primary", true,
                    false, "Mensagem de solução", "O solicitante vê esta mensagem na consulta pelo código.");
            case FECHADO -> new OpcaoStatus(destino, "Fechar chamado", "bi-lock", "btn-escuro", false, false, null,
                    "Fechado é final: o chamado não pode mais ser alterado.");
            case REABERTO -> new OpcaoStatus(destino, "Reabrir", "bi-arrow-counterclockwise", "btn-secundario", true,
                    false, "Motivo da reabertura", "Fica como comentário interno: o solicitante não vê o motivo.");
            default -> new OpcaoStatus(destino, destino.getDescricao(), "bi-arrow-right", "btn-secundario",
                    false, false, null, null);
        };
    }

    static ItemHistorico paraHistorico(ChamadoInteracao i) {
        String autor = i.getAutor() == null ? null : i.getAutor().getNome();
        String texto = limpar(i.getTexto());
        return switch (i.getTipo()) {
            case MUDANCA_STATUS -> {
                boolean abertura = i.getStatusAnterior() == null && i.getStatusNovo() == StatusChamado.ABERTO;
                if (abertura) {
                    yield new ItemHistorico(i.getData(), i.getTipo(), "Chamado aberto",
                            "Chamado aberto".equalsIgnoreCase(texto) ? null : texto,
                            autor == null ? "Formulário público" : autor, false, "bi-flag", "status");
                }
                String titulo = i.getStatusAnterior() == null
                        ? "Status: " + i.getStatusNovo().getDescricao()
                        : "Status: " + i.getStatusAnterior().getDescricao() + " → " + i.getStatusNovo().getDescricao();
                if (i.getStatusNovo() == StatusChamado.AGUARDANDO) {
                    // Gravado com o texto público; para a equipe mostra o motivo e se pausou o prazo
                    String publico = texto;
                    texto = MotivoAguardando.doTextoPublico(publico)
                            .map(m -> m.getDescricao() + (m.isPausaPrazo() ? " · prazo pausado" : " · prazo correndo"))
                            .orElse(publico);
                }
                yield new ItemHistorico(i.getData(), i.getTipo(), titulo, texto, autor, i.isInterno(),
                        "bi-arrow-repeat", "status");
            }
            case ATRIBUICAO -> new ItemHistorico(i.getData(), i.getTipo(), "Atribuição", texto, autor, true,
                    "bi-person-check", "atribuicao");
            case COMENTARIO -> new ItemHistorico(i.getData(), i.getTipo(), "Comentário interno", texto, autor, true,
                    "bi-chat-left-text", "comentario");
            case RESPOSTA_SOLICITANTE -> new ItemHistorico(i.getData(), i.getTipo(), "Resposta ao solicitante", texto,
                    autor, false, "bi-reply", "resposta");
        };
    }

    /** 95 → "1h 35min"; 45 → "45 min"; 120 → "2h". */
    static String formatarMinutos(long minutos) {
        if (minutos < 60) {
            return minutos + " min";
        }
        long h = minutos / 60;
        long m = minutos % 60;
        return m == 0 ? h + "h" : h + "h " + String.format("%02d", m) + "min";
    }

    static String tamanhoLegivel(Long bytes) {
        if (bytes == null) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0)).replace('.', ',');
    }

    static String iconeAnexo(String tipoMime) {
        if (tipoMime == null) {
            return "bi-file-earmark";
        }
        if (tipoMime.startsWith("image/")) {
            return "bi-file-earmark-image";
        }
        if (tipoMime.equals("application/pdf")) {
            return "bi-file-earmark-pdf";
        }
        if (tipoMime.contains("spreadsheet")) {
            return "bi-file-earmark-spreadsheet";
        }
        if (tipoMime.contains("wordprocessing")) {
            return "bi-file-earmark-word";
        }
        return "bi-file-earmark";
    }

    /** Usado pelo download: confere acesso ao chamado (404) e se o anexo é dele (404). */
    @Transactional(readOnly = true)
    public Anexo anexoParaDownload(Long chamadoId, Long anexoId, UsuarioLogado usuario) {
        Chamado chamado = buscarVisivel(chamadoId, usuario);
        Anexo anexo = anexoId == null ? null : anexoRepository.findById(anexoId).orElse(null);
        if (anexo == null || !anexo.getChamado().getId().equals(chamado.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return anexo;
    }
}
