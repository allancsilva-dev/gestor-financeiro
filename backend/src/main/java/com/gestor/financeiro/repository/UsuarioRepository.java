package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    // Cadastro grava e-mail normalizado, mas contas legadas podem ter maiusculas:
    // a checagem de duplicidade precisa ignorar caixa para nao criar duas contas
    // para o mesmo endereco.
    boolean existsByEmailIgnoreCase(String email);

    // Lista (nao Optional) de proposito: bases legadas podem ter duas contas que
    // so diferem na caixa; quem chama decide o que fazer quando ha mais de uma.
    List<Usuario> findAllByEmailIgnoreCase(String email);

    // Serializa finalizações concorrentes de onboarding (ADR-0002)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id = :id")
    Optional<Usuario> findByIdComLock(@Param("id") Long id);

    @Query("SELECT u.id FROM Usuario u WHERE u.id > :afterId ORDER BY u.id")
    List<Long> findIdsAfter(@Param("afterId") Long afterId, Pageable pageable);
}
