package br.com.goias.chamados.dominio.chamado;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChamadoInteracaoRepository extends JpaRepository<ChamadoInteracao, Long> {

    /** Histórico completo (tela de atendimento). */
    @EntityGraph(attributePaths = "autor")
    List<ChamadoInteracao> findByChamadoIdOrderByDataAscIdAsc(Long chamadoId);

    /** Histórico visível ao solicitante (consulta pública). */
    @EntityGraph(attributePaths = "autor")
    List<ChamadoInteracao> findByChamadoIdAndInternoFalseOrderByDataAscIdAsc(Long chamadoId);

    /** Ex.: já houve alguma RESPOSTA_SOLICITANTE neste chamado? (regra do "devolver para a fila") */
    boolean existsByChamadoIdAndTipo(Long chamadoId, TipoInteracao tipo);
}
