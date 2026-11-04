package br.com.goias.chamados.dominio.cadastro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocalRepository extends JpaRepository<Local, Long> {

    List<Local> findByAtivoTrueOrderByNomeAsc();
}
