package br.com.goias.chamados.dominio.cadastro;

import br.com.goias.chamados.dominio.area.Area;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Um período de trabalho de uma área num dia da semana (V4).
 * Um dia pode ter vários períodos (ex.: 08:00–12:00 e 13:00–17:00).
 * Dia sem nenhum período = sem expediente (o prazo de SLA não conta).
 */
@Entity
@Table(name = "expediente")
@Getter
@Setter
@NoArgsConstructor
public class Expediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    /** ISO-8601: 1 = segunda ... 7 = domingo (mesmo número de {@link DayOfWeek#getValue()}). */
    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    public DayOfWeek getDiaDaSemana() {
        return DayOfWeek.of(diaSemana);
    }
}
