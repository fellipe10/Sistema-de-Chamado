package br.com.goias.chamados.dominio.usuario;

import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Usado no login: já traz a área junto (evita LazyInitializationException). */
    @EntityGraph(attributePaths = "area")
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Usuario> findByAreaIdAndAtivoTrueOrderByNomeAsc(Long areaId);

    /** Para quem o gestor pode transferir um chamado: ativos da área com o perfil informado. */
    List<Usuario> findByAreaIdAndAtivoTrueAndPerfilInOrderByNomeAsc(Long areaId, Collection<Perfil> perfis);

    /**
     * Administradores ativos, TRAVANDO as linhas até o fim da transação (Etapa 5).
     * Evita que dois admins se desativem ao mesmo tempo e o sistema fique sem nenhum.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Usuario> findByPerfilAndAtivoTrue(Perfil perfil);
}
