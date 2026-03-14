package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.TipoTransacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // Importar
import org.springframework.data.repository.query.Param; // Importar
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, Long> {
    
    // Busca TODAS as transações do usuário
    @EntityGraph(attributePaths = {"categoria", "conta"})
    List<Transacao> findByUsuarioId(Long usuarioId);

    // Busca transações paginadas do usuário.
    @EntityGraph(attributePaths = {"categoria", "conta"})
    Page<Transacao> findByUsuarioId(Long usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = {"categoria", "conta"})
    Optional<Transacao> findByIdAndUsuarioId(Long id, Long usuarioId);
    
    // Busca transações de uma categoria específica
    @EntityGraph(attributePaths = {"categoria", "conta"})
    List<Transacao> findByUsuarioIdAndCategoriaId(Long usuarioId, Long categoriaId);
    
    // Busca transações por tipo (ENTRADA ou SAIDA)
    @EntityGraph(attributePaths = {"categoria", "conta"})
    List<Transacao> findByUsuarioIdAndTipo(Long usuarioId, TipoTransacao tipo);
    
    // Busca transações em um período (entre duas datas)
    @EntityGraph(attributePaths = {"categoria", "conta"})
    List<Transacao> findByUsuarioIdAndDataBetween(Long usuarioId, LocalDate inicio, LocalDate fim);

    // Busca transações em período com paginação.
    @EntityGraph(attributePaths = {"categoria", "conta"})
    Page<Transacao> findByUsuarioIdAndDataBetween(Long usuarioId, LocalDate inicio, LocalDate fim, Pageable pageable);

    // --- ADIÇÃO NECESSÁRIA ---
    // Este novo método força o JPA a carregar a Categoria junto com a Transação
    @Query("SELECT t FROM Transacao t JOIN FETCH t.categoria " +
           "WHERE t.usuario.id = :usuarioId " +
           "AND t.data BETWEEN :inicio AND :fim")
    List<Transacao> findByUsuarioIdAndDataBetweenWithCategoria(
            @Param("usuarioId") Long usuarioId, 
            @Param("inicio") LocalDate inicio, 
            @Param("fim") LocalDate fim);
    // --- FIM DA ADIÇÃO ---
}