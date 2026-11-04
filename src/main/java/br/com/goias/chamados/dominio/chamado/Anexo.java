package br.com.goias.chamados.dominio.chamado;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Metadados de um arquivo anexado ao chamado.
 * O arquivo em si fica em disco, no caminho informado.
 */
@Entity
@Table(name = "anexo")
@Getter
@Setter
@NoArgsConstructor
public class Anexo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id", nullable = false)
    private Chamado chamado;

    @Column(name = "nome_original", nullable = false, length = 255)
    private String nomeOriginal;

    @Column(nullable = false, length = 500)
    private String caminho;

    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;

    @Column(nullable = false)
    private Long tamanho;

    @Column(nullable = false, updatable = false)
    private LocalDateTime data;

    @PrePersist
    void aoCriar() {
        if (data == null) {
            data = LocalDateTime.now();
        }
    }
}
