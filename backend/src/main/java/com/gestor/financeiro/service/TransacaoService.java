package com.gestor.financeiro.service;

import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.repository.ParcelaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransacaoService {
    
    @Autowired
    private TransacaoRepository transacaoRepository;
    
    @Autowired
    private ParcelaRepository parcelaRepository;
    
    @Autowired
    private CategoriaService categoriaService;
    
    @Autowired
    private ContaService contaService;
    
    // Lista transações do usuário
    public List<Transacao> listarPorUsuario(Long usuarioId) {
        return transacaoRepository.findByUsuarioId(usuarioId);
    }
    
    // Lista transações de um período
    public List<Transacao> listarPorPeriodo(Long usuarioId, LocalDate inicio, LocalDate fim) {
        return transacaoRepository.findByUsuarioIdAndDataBetween(usuarioId, inicio, fim);
    }
    
    // Cria transação (com ou sem parcelamento)
    @Transactional // Garante que tudo seja salvo ou nada (segurança)
    public Transacao criar(Transacao transacao) {
        
        // Se for parcelado, cria as parcelas automaticamente
        if (transacao.getParcelado() && transacao.getTotalParcelas() > 1) {
            criarParcelas(transacao);
        }
        
        // Atualiza o gasto da categoria
        if (transacao.getCategoria() != null) {
            var categoria = transacao.getCategoria();
            categoria.setValorGasto(
                categoria.getValorGasto().add(transacao.getValorTotal())
            );
            categoriaService.atualizar(categoria.getId(), categoria);
        }
        
        // Atualiza o gasto da conta
        if (transacao.getConta() != null) {
            contaService.adicionarGasto(
                transacao.getConta().getId(), 
                transacao.getValorTotal()
            );
        }
        
        return transacaoRepository.save(transacao);
    }
    
    // Cria as parcelas automaticamente
    private void criarParcelas(Transacao transacao) {
        List<Parcela> parcelas = new ArrayList<>();
        
        // Calcula o valor de cada parcela
        BigDecimal valorParcela = transacao.getValorTotal()
            .divide(BigDecimal.valueOf(transacao.getTotalParcelas()), 2, RoundingMode.HALF_UP);
        
        transacao.setValorParcela(valorParcela);
        
        // Cria cada parcela
        for (int i = 1; i <= transacao.getTotalParcelas(); i++) {
            Parcela parcela = new Parcela();
            parcela.setTransacao(transacao);
            parcela.setNumeroParcela(i);
            parcela.setTotalParcelas(transacao.getTotalParcelas());
            parcela.setValor(valorParcela);
            
            // Define data de vencimento (soma i meses)
            parcela.setDataVencimento(transacao.getData().plusMonths(i));
            parcela.setStatus(StatusPagamento.PENDENTE);
            
            parcelas.add(parcela);
        }
        
        transacao.setParcelas(parcelas);
    }
    
    // Atualiza transação
    @Transactional
    public Transacao atualizar(Long id, Transacao transacaoAtualizada) {
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        
        transacao.setDescricao(transacaoAtualizada.getDescricao());
        transacao.setValorTotal(transacaoAtualizada.getValorTotal());
        transacao.setData(transacaoAtualizada.getData());
        transacao.setObservacoes(transacaoAtualizada.getObservacoes());
        
        return transacaoRepository.save(transacao);
    }
    
    // Deleta transação
    @Transactional
    public void deletar(Long id) {
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        
        // Remove gasto da conta
        if (transacao.getConta() != null) {
            contaService.removerGasto(
                transacao.getConta().getId(), 
                transacao.getValorTotal()
            );
        }
        
        // Remove gasto da categoria
        if (transacao.getCategoria() != null) {
            var categoria = transacao.getCategoria();
            categoria.setValorGasto(
                categoria.getValorGasto().subtract(transacao.getValorTotal())
            );
            categoriaService.atualizar(categoria.getId(), categoria);
        }
        
        transacaoRepository.delete(transacao);
    }
    
    // Busca por ID
    public Transacao buscarPorId(Long id) {
        return transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
    }
}