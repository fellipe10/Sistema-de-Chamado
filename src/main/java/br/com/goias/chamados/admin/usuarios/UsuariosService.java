package br.com.goias.chamados.admin.usuarios;

import br.com.goias.chamados.admin.AcaoAdminRecusadaException;
import br.com.goias.chamados.admin.RegistroNaoEncontradoException;
import br.com.goias.chamados.config.SessoesAtivas;
import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.usuario.Perfil;
import br.com.goias.chamados.dominio.usuario.Usuario;
import br.com.goias.chamados.dominio.usuario.UsuarioRepository;
import br.com.goias.chamados.login.UsuarioLogado;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cadastro de usuários (/admin/usuarios) — Etapa 5.
 *
 * - Criar: gera senha temporária (mostrada uma vez) e trocar_senha = true.
 * - Redefinir senha: nova senha temporária, trocar_senha = true e derruba as sessões abertas.
 * - Desativar: derruba as sessões abertas; os chamados continuam com a pessoa.
 * - Mudar e-mail, perfil ou área de outra pessoa também derruba as sessões dela
 *   (assim a mudança vale na hora, em vez de só no próximo login).
 * - Proteções: ver {@link RegrasUsuario}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuariosService {

    /** Status que contam como "em andamento" para o aviso de chamados do usuário. */
    static final EnumSet<StatusChamado> FINALIZADOS = EnumSet.of(StatusChamado.RESOLVIDO, StatusChamado.FECHADO);

    private final UsuarioRepository usuarioRepository;
    private final AreaRepository areaRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessoesAtivas sessoesAtivas;
    private final EntityManager em;

    // =================================================================
    // Tipos da tela
    // =================================================================

    public record Filtros(String busca, String situacao, Perfil perfil, Long areaId) {

        public boolean algumAtivo() {
            return (busca != null && !busca.isBlank()) || !"ativos".equals(situacao) || perfil != null || areaId != null;
        }
    }

    public record LinhaUsuario(Long id, String nome, String email, Perfil perfil, String areaNome,
                               boolean ativo, boolean trocarSenha, long emAndamento, boolean ehVoce) {

        public String iniciais() {
            String[] partes = nome.strip().split("\\s+");
            if (partes.length == 1) {
                return partes[0].substring(0, Math.min(2, partes[0].length())).toUpperCase(Locale.ROOT);
            }
            return (partes[0].substring(0, 1) + partes[partes.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
        }
    }

    public record OpcaoArea(Long id, String nome, boolean ativo) {

        public String rotulo() {
            return ativo ? nome : nome + " (inativa)";
        }
    }

    /** Dados do usuário que está sendo editado. */
    public record Detalhe(Long id, String nome, String email, Perfil perfil, Long areaId, String areaNome,
                          boolean ativo, boolean trocarSenha, long emAndamento, int sessoesAbertas, boolean ehVoce) {
    }

    /**
     * Resultado de criar/salvar.
     *
     * @param confirmarArea quantos chamados em andamento a pessoa tem (> 0 = precisa confirmar a mudança de área)
     */
    public record Resultado(Long id, Map<String, String> erros, String senhaTemporaria, long confirmarArea,
                            String aviso) {

        public boolean sucesso() {
            return erros.isEmpty() && confirmarArea == 0;
        }

        static Resultado comErros(Map<String, String> erros) {
            return new Resultado(null, erros, null, 0, null);
        }
    }

    /** Resultado de desativar/reativar. */
    public record ResultadoSituacao(String erro, String nome, long emAndamento, Long areaId) {
    }

    // =================================================================
    // Lista
    // =================================================================

    @Transactional(readOnly = true)
    public List<LinhaUsuario> listar(Filtros f, UsuarioLogado admin) {
        StringBuilder jpql = new StringBuilder("select u from Usuario u left join fetch u.area a where 1 = 1");
        Map<String, Object> parametros = new HashMap<>();
        if (f.busca() != null && !f.busca().isBlank()) {
            jpql.append(" and (lower(u.nome) like :busca escape '\\' or lower(u.email) like :busca escape '\\')");
            parametros.put("busca", contem(f.busca().strip().toLowerCase(Locale.ROOT)));
        }
        if ("ativos".equals(f.situacao())) {
            jpql.append(" and u.ativo = true");
        } else if ("inativos".equals(f.situacao())) {
            jpql.append(" and u.ativo = false");
        }
        if (f.perfil() != null) {
            jpql.append(" and u.perfil = :perfil");
            parametros.put("perfil", f.perfil());
        }
        if (f.areaId() != null) {
            jpql.append(" and a.id = :area");
            parametros.put("area", f.areaId());
        }
        jpql.append(" order by u.ativo desc, lower(u.nome)");
        var query = em.createQuery(jpql.toString(), Usuario.class);
        parametros.forEach(query::setParameter);
        List<Usuario> usuarios = query.getResultList();

        Map<Long, Long> emAndamento = contarEmAndamentoPorAtendente();
        return usuarios.stream()
                .map(u -> new LinhaUsuario(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
                        u.getArea() == null ? null : u.getArea().getNome(),
                        u.isAtivo(), u.isTrocarSenha(), emAndamento.getOrDefault(u.getId(), 0L),
                        u.getId().equals(admin.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpcaoArea> areas() {
        return areaRepository.findAll().stream()
                .sorted(Comparator.comparing((Area a) -> !a.isAtivo()).thenComparing(a -> a.getNome().toLowerCase(Locale.ROOT)))
                .map(a -> new OpcaoArea(a.getId(), a.getNome(), a.isAtivo()))
                .toList();
    }

    // =================================================================
    // Detalhe
    // =================================================================

    @Transactional(readOnly = true)
    public Optional<Detalhe> detalhe(Long id, UsuarioLogado admin) {
        return usuarioRepository.findById(id).map(u -> new Detalhe(u.getId(), u.getNome(), u.getEmail(),
                u.getPerfil(),
                u.getArea() == null ? null : u.getArea().getId(),
                u.getArea() == null ? null : u.getArea().getNome(),
                u.isAtivo(), u.isTrocarSenha(), contarEmAndamento(u.getId()),
                sessoesAtivas.contar(u.getId()), u.getId().equals(admin.getId())));
    }

    // =================================================================
    // Criar
    // =================================================================

    @Transactional
    public Resultado criar(UsuarioForm form) {
        Map<String, String> erros = validar(form, null);
        if (!erros.isEmpty()) {
            return Resultado.comErros(erros);
        }
        String senha = GeradorSenhaTemporaria.gerar();
        Usuario usuario = new Usuario();
        preencher(usuario, form);
        usuario.setAtivo(true);
        usuario.setTrocarSenha(true);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuarioRepository.saveAndFlush(usuario);
        log.info("Usuário criado: {} ({}).", usuario.getEmail(), usuario.getPerfil());
        return new Resultado(usuario.getId(), Map.of(), senha, 0, null);
    }

    // =================================================================
    // Editar
    // =================================================================

    @Transactional
    public Resultado salvar(Long id, UsuarioForm form, UsuarioLogado admin) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuário", id));
        boolean ehOProprio = usuario.getId().equals(admin.getId());

        Map<String, String> erros = validar(form, usuario.getId());
        Perfil perfilNovo = form.getPerfil() == null ? usuario.getPerfil() : form.getPerfil();
        String protecao = RegrasUsuario.protecao(ehOProprio, usuario.getPerfil(), usuario.isAtivo(),
                perfilNovo, usuario.isAtivo(), outrosAdminsAtivos(usuario.getId()));
        if (protecao != null) {
            erros.putIfAbsent("perfil", protecao);
        }
        if (!erros.isEmpty()) {
            return Resultado.comErros(erros);
        }

        Long areaAtual = usuario.getArea() == null ? null : usuario.getArea().getId();
        Long areaNova = RegrasUsuario.precisaDeArea(form.getPerfil()) ? form.getAreaId() : null;
        long emAndamento = 0;
        if (RegrasUsuario.mudouArea(areaAtual, areaNova)) {
            emAndamento = contarEmAndamento(usuario.getId());
            if (emAndamento > 0 && !form.isConfirmarMudancaArea()) {
                return new Resultado(usuario.getId(), Map.of(), null, emAndamento, null);
            }
        }

        boolean mudouAcesso = !Objects.equals(usuario.getEmail(), RegrasUsuario.normalizarEmail(form.getEmail()))
                || usuario.getPerfil() != form.getPerfil()
                || RegrasUsuario.mudouArea(areaAtual, areaNova);
        preencher(usuario, form);
        usuarioRepository.saveAndFlush(usuario);

        String aviso = null;
        if (mudouAcesso && !ehOProprio) {
            depoisDoCommit(() -> sessoesAtivas.encerrar(id));
            aviso = "Se a pessoa estava usando o sistema, ela precisa entrar de novo para a mudança valer.";
        } else if (ehOProprio) {
            aviso = "O seu nome no menu é atualizado no próximo login.";
        }
        if (emAndamento > 0) {
            aviso = emAndamento + (emAndamento == 1 ? " chamado em andamento continua" : " chamados em andamento continuam")
                    + " com " + primeiroNome(usuario.getNome()) + " na área antiga. Peça ao gestor dessa área para transferir.";
        }
        log.info("Usuário {} alterado por {}.", usuario.getEmail(), admin.getEmail());
        return new Resultado(usuario.getId(), Map.of(), null, 0, aviso);
    }

    // =================================================================
    // Redefinir senha
    // =================================================================

    @Transactional
    public String redefinirSenha(Long id, UsuarioLogado admin) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuário", id));
        if (usuario.getId().equals(admin.getId())) {
            throw new AcaoAdminRecusadaException("Para trocar a sua própria senha, use a chave no rodapé do menu.");
        }
        String senha = GeradorSenhaTemporaria.gerar();
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setTrocarSenha(true);
        usuarioRepository.saveAndFlush(usuario);
        depoisDoCommit(() -> sessoesAtivas.encerrar(id));
        log.info("Senha do usuário {} redefinida por {}.", usuario.getEmail(), admin.getEmail());
        return senha;
    }

    // =================================================================
    // Desativar / reativar
    // =================================================================

    @Transactional
    public ResultadoSituacao alterarSituacao(Long id, boolean ativar, UsuarioLogado admin) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuário", id));
        if (usuario.isAtivo() == ativar) {
            return new ResultadoSituacao(null, usuario.getNome(), 0, null);
        }
        if (ativar && usuario.getArea() != null && !usuario.getArea().isAtivo()
                && RegrasUsuario.precisaDeArea(usuario.getPerfil())) {
            return new ResultadoSituacao("A área de " + primeiroNome(usuario.getNome())
                    + " está inativa. Mude a área antes de reativar.", usuario.getNome(), 0, null);
        }
        String protecao = RegrasUsuario.protecao(usuario.getId().equals(admin.getId()), usuario.getPerfil(),
                usuario.isAtivo(), usuario.getPerfil(), ativar, outrosAdminsAtivos(usuario.getId()));
        if (protecao != null) {
            return new ResultadoSituacao(protecao, usuario.getNome(), 0, null);
        }
        usuario.setAtivo(ativar);
        usuarioRepository.saveAndFlush(usuario);
        long emAndamento = 0;
        if (!ativar) {
            emAndamento = contarEmAndamento(usuario.getId());
            depoisDoCommit(() -> sessoesAtivas.encerrar(id));
        }
        log.info("Usuário {} {} por {}.", usuario.getEmail(), ativar ? "reativado" : "desativado", admin.getEmail());
        return new ResultadoSituacao(null, usuario.getNome(), emAndamento,
                usuario.getArea() == null ? null : usuario.getArea().getId());
    }

    // =================================================================
    // Apoio
    // =================================================================

    private Map<String, String> validar(UsuarioForm form, Long idAtual) {
        boolean areaAtiva = form.getAreaId() != null
                && areaRepository.findById(form.getAreaId()).map(Area::isAtivo).orElse(false);
        // Ao editar, a área atual (mesmo que inativa) continua aceita se não mudou
        if (!areaAtiva && idAtual != null && form.getAreaId() != null) {
            areaAtiva = usuarioRepository.findById(idAtual)
                    .map(u -> u.getArea() != null && u.getArea().getId().equals(form.getAreaId()))
                    .orElse(false);
        }
        Map<String, String> erros = new LinkedHashMap<>(RegrasUsuario.validarCampos(
                form.getNome(), form.getEmail(), form.getPerfil(), form.getAreaId(), areaAtiva));
        if (!erros.containsKey("email")) {
            String email = RegrasUsuario.normalizarEmail(form.getEmail());
            usuarioRepository.findByEmail(email)
                    .filter(outro -> !outro.getId().equals(idAtual))
                    .ifPresent(outro -> erros.put("email", RegrasUsuario.EMAIL_REPETIDO));
        }
        return erros;
    }

    private void preencher(Usuario usuario, UsuarioForm form) {
        usuario.setNome(RegrasUsuario.normalizarNome(form.getNome()));
        usuario.setEmail(RegrasUsuario.normalizarEmail(form.getEmail()));
        usuario.setPerfil(form.getPerfil());
        usuario.setArea(RegrasUsuario.precisaDeArea(form.getPerfil())
                ? areaRepository.getReferenceById(form.getAreaId())
                : null);
    }

    /** Outros administradores ativos (trava as linhas: dois admins não se desativam ao mesmo tempo). */
    private long outrosAdminsAtivos(Long usuarioId) {
        return usuarioRepository.findByPerfilAndAtivoTrue(Perfil.ADMIN).stream()
                .filter(u -> !u.getId().equals(usuarioId))
                .count();
    }

    long contarEmAndamento(Long usuarioId) {
        return em.createQuery("""
                        select count(c) from Chamado c
                         where c.atendente.id = :usuario and c.status not in :finalizados""", Long.class)
                .setParameter("usuario", usuarioId)
                .setParameter("finalizados", FINALIZADOS)
                .getSingleResult();
    }

    private Map<Long, Long> contarEmAndamentoPorAtendente() {
        Map<Long, Long> mapa = new HashMap<>();
        em.createQuery("""
                        select c.atendente.id, count(c) from Chamado c
                         where c.atendente is not null and c.status not in :finalizados
                         group by c.atendente.id""", Object[].class)
                .setParameter("finalizados", FINALIZADOS)
                .getResultList()
                .forEach(l -> mapa.put((Long) l[0], (Long) l[1]));
        return mapa;
    }

    /** Encerra sessões só depois que a mudança foi gravada (se a transação falhar, ninguém é deslogado). */
    private static void depoisDoCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
        } else {
            acao.run();
        }
    }

    static String primeiroNome(String nome) {
        return nome == null ? "" : nome.strip().split("\\s+")[0];
    }

    static String contem(String termo) {
        String escapado = termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }
}
