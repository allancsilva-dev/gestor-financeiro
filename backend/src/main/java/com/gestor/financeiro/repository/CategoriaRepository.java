package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Categoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    
    // Busca categorias ATIVAS de um usuário
    List<Categoria> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    // Busca categorias ativas com paginação.
    Page<Categoria> findByUsuarioIdAndAtivoTrue(Long usuarioId, Pageable pageable);
    
    // Busca TODAS as categorias do usuário (ativas ou não)
    List<Categoria> findByUsuarioId(Long usuarioId);

    Optional<Categoria> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Confere o gasto materializado da categoria contra a soma das saídas ativas. */
    @org.springframework.data.jpa.repository.Query("""
            select c.id as categoriaId,
                   c.valorGasto as valorMaterializado,
                   coalesce((select sum(t.valorTotal) from Transacao t
                              where t.categoria.id = c.id
                                and t.ativa = true
                                and t.tipo = com.gestor.financeiro.model.enums.TipoTransacao.SAIDA), 0)
                       as valorLancado
              from Categoria c
             where c.usuario.id = :usuarioId
            """)
    List<com.gestor.financeiro.repository.projection.CategoriaGastoProjection>
        reconciliarGastoByUsuarioId(@org.springframework.data.repository.query.Param("usuarioId") Long usuarioId);

    long countByUsuarioIdAndAtivoTrue(Long usuarioId);

    Optional<Categoria> findByUsuarioIdAndNomeIgnoreCase(Long usuarioId, String nome);

    boolean existsByUsuarioIdAndNomeIgnoreCaseAndAtivoTrue(Long usuarioId, String nome);

    boolean existsByUsuarioIdAndNomeIgnoreCaseAndAtivoTrueAndIdNot(Long usuarioId, String nome, Long id);
}