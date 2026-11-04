package br.com.goias.chamados.dominio.cadastro;

import br.com.goias.chamados.dominio.chamado.Prioridade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaConfigRepository extends JpaRepository<SlaConfig, Long> {

    Optional<SlaConfig> findByAreaIdAndPrioridade(Long areaId, Prioridade prioridade);

    List<SlaConfig> findByAreaId(Long areaId);
}
