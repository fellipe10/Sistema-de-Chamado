package br.com.goias.chamados.dominio.cadastro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SetorRepository extends JpaRepository<Setor, Long> {

    List<Setor> findByAtivoTrueOrderByNomeAsc();
}
