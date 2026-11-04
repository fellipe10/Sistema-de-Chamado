package br.com.goias.chamados.dominio.cadastro;

import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.chamado.Prioridade;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Prazo de SLA de uma área para uma prioridade, em minutos.
 * O gestor ajusta esses valores (Etapa 4).
 */
@Entity
@Table(name = "sla_config")
@Getter
@Setter
@NoArgsConstructor
public class SlaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Prioridade prioridade;

    @Column(name = "minutos_primeira_resposta", nullable = false)
    private Integer minutosPrimeiraResposta;

    @Column(name = "minutos_resolucao", nullable = false)
    private Integer minutosResolucao;
}
