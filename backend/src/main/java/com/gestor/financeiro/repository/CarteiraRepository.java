package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Carteira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CarteiraRepository extends JpaRepository<Carteira, Long> {
    
    // Busca carteiras de um usuário
    List<Carteira> findByUsuarioId(Long usuarioId);
}