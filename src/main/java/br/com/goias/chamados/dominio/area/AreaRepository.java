package br.com.goias.chamados.dominio.area;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<Area, Long> {

    Optional<Area> findBySigla(String sigla);

    List<Area> findByAtivoTrueOrderByNomeAsc();
}
