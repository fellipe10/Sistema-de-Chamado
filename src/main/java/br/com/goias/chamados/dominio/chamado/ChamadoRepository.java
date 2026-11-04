package br.com.goias.chamados.dominio.chamado;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChamadoRepository extends JpaRepository<Chamado, Long> {

    /** Usado na consulta pública: traz área, categoria, setor e local juntos. */
    @EntityGraph(attributePaths = {"area", "categoria", "setor", "local", "atendente"})
    Optional<Chamado> findByProtocolo(String protocolo);

    /** Tela de atendimento: chamado com área, categoria, setor, local e atendente. */
    @EntityGraph(attributePaths = {"area", "categoria", "setor", "local", "atendente"})
    Optional<Chamado> findComDetalhesById(Long id);

    /** Fechamento automático: chamados num status, com a área (para o calendário de expediente). */
    @EntityGraph(attributePaths = {"area"})
    List<Chamado> findByStatus(StatusChamado status);

    /** Usado na geração do código para evitar colisão antes de gravar. */
    boolean existsByProtocolo(String protocolo);
}
