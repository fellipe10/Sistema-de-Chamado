package br.com.goias.chamados.dominio.cadastro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpedienteRepository extends JpaRepository<Expediente, Long> {

    /** Todos os períodos de trabalho da área, em ordem de dia e hora. */
    List<Expediente> findByAreaIdOrderByDiaSemanaAscHoraInicioAsc(Long areaId);
}
