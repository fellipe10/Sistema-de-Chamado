package br.com.goias.chamados.dominio.area;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findByAreaIdAndAtivoTrueOrderByNomeAsc(Long areaId);

    List<Categoria> findByAreaIdOrderByNomeAsc(Long areaId);
}
