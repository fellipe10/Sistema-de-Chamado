package br.com.goias.chamados.dominio.chamado;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro no histórico do chamado.
 * autor = null quando foi o sistema (ex.: abertura pelo formulário público; o solicitante não tem login).
 * interno: segue a regra oficial de cada tipo (ver TipoInteracao; garantido pelo CHECK da V3).
 */
@Entity
@Table(name = "chamado_interacao")
@Getter
@Setter
@NoArgsConstructor
public class ChamadoInteracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id", nullable = false)
    private Chamado chamado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id")
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoInteracao tipo;

    @Column(columnDefinition = "text")
    private String texto;

    @Column(nullable = false)
    private boolean interno = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 20)
    private StatusChamado statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", length = 20)
    private StatusChamado statusNovo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime data;

    @PrePersist
    void aoCriar() {
        if (data == null) {
            data = LocalDateTime.now();
        }
    }
}
