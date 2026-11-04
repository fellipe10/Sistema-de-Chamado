package br.com.goias.chamados.dominio.chamado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnexoRepository extends JpaRepository<Anexo, Long> {

    List<Anexo> findByChamadoIdOrderByDataAscIdAsc(Long chamadoId);
}
