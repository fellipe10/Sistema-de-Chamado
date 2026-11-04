package br.com.goias.chamados.dominio.chamado;

import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.Categoria;
import br.com.goias.chamados.dominio.cadastro.Local;
import br.com.goias.chamados.dominio.cadastro.Setor;
import br.com.goias.chamados.dominio.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chamado aberto pelo formulário público.
 * O protocolo segue o formato ÁREA-ANO-XXXX (ex.: TI-2026-7K3F).
 */
@Entity
@Table(name = "chamado")
@Getter
@Setter
@NoArgsConstructor
public class Chamado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, updatable = false)
    private String protocolo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setor_id", nullable = false)
    private Setor setor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "local_id", nullable = false)
    private Local local;

    // ---- Solicitante (sem login) ----

    @Column(name = "solicitante_nome", nullable = false, length = 120)
    private String solicitanteNome;

    @Column(name = "solicitante_email", length = 150)
    private String solicitanteEmail;

    @Column(name = "solicitante_telefone", length = 20)
    private String solicitanteTelefone;

    @Column(name = "solicitante_matricula", length = 30)
    private String solicitanteMatricula;

    // ---- Problema ----

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, columnDefinition = "text")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Urgencia urgencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Prioridade prioridade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusChamado status = StatusChamado.ABERTO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendente_id")
    private Usuario atendente;

    // ---- Datas ----

    @Column(name = "data_abertura", nullable = false, updatable = false)
    private LocalDateTime dataAbertura;

    @Column(name = "data_primeira_resposta")
    private LocalDateTime dataPrimeiraResposta;

    @Column(name = "data_resolucao")
    private LocalDateTime dataResolucao;

    @Column(name = "data_fechamento")
    private LocalDateTime dataFechamento;

    @Column(name = "prazo_primeira_resposta")
    private LocalDateTime prazoPrimeiraResposta;

    @Column(name = "prazo_resolucao")
    private LocalDateTime prazoResolucao;

    // ---- Pausa do SLA no "Aguardando" (V5) ----

    /** Motivo do AGUARDANDO; nulo em qualquer outro status (CHECK no banco). */
    @Enumerated(EnumType.STRING)
    @Column(name = "aguardando_motivo", length = 20)
    private MotivoAguardando aguardandoMotivo;

    /** Início da pausa atual ("Aguardando o solicitante"); nulo quando o prazo está correndo. */
    @Column(name = "pausa_inicio")
    private LocalDateTime pausaInicio;

    /** Minutos ÚTEIS já pausados em pausas encerradas (empurram os prazos). */
    @Column(name = "minutos_pausados", nullable = false)
    private Long minutosPausados = 0L;

    /**
     * Controle de concorrência (V3). O Hibernate soma 1 a cada alteração do chamado;
     * se duas pessoas alterarem ao mesmo tempo, a segunda gravação falha com
     * ObjectOptimisticLockingFailureException (tratada na tela de atendimento).
     */
    @Version
    @Column(nullable = false)
    private Long versao;

    // ---- Relacionamentos inversos (somente leitura pelo chamado) ----

    @OneToMany(mappedBy = "chamado")
    @OrderBy("data ASC, id ASC")
    private List<ChamadoInteracao> interacoes = new ArrayList<>();

    @OneToMany(mappedBy = "chamado")
    @OrderBy("data ASC, id ASC")
    private List<Anexo> anexos = new ArrayList<>();

    /** Prazo parado agora ("Aguardando o solicitante"). */
    public boolean isPausado() {
        return pausaInicio != null;
    }

    @PrePersist
    void aoCriar() {
        if (minutosPausados == null) {
            minutosPausados = 0L;
        }
        if (dataAbertura == null) {
            dataAbertura = LocalDateTime.now();
        }
        if (status == null) {
            status = StatusChamado.ABERTO;
        }
        if (prioridade == null && urgencia != null) {
            prioridade = urgencia.getPrioridadeSugerida();
        }
    }
}
