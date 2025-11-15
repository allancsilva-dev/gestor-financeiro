package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, Long> {
    
    // Busca TODAS as transações do usuário
    List<Transacao> findByUsuarioId(Long usuarioId);
    
    // Busca transações de uma categoria específica
    List<Transacao> findByUsuarioIdAndCategoriaId(Long usuarioId, Long categoriaId);
    
    // Busca transações por tipo (ENTRADA ou SAIDA)
    List<Transacao> findByUsuarioIdAndTipo(Long usuarioId, TipoTransacao tipo);
    
    // Busca transações em um período (entre duas datas)
    List<Transacao> findByUsuarioIdAndDataBetween(Long usuarioId, LocalDate inicio, LocalDate fim);
}