package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Carteira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CarteiraRepository extends JpaRepository<Carteira, Long> {
    
    // Busca carteiras de um usuário
    List<Carteira> findByUsuarioId(Long usuarioId);

    Optional<Carteira> findByIdAndUsuarioId(Long id, Long usuarioId);
}