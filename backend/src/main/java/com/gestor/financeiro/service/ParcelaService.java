package com.gestor.financeiro.service;

import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.repository.ParcelaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
public class ParcelaService {
    
    @Autowired
    private ParcelaRepository parcelaRepository;
    
    // Busca todas as parcelas de uma transação
    public List<Parcela> listarPorTransacao(Long transacaoId) {
        return parcelaRepository.findByTransacaoId(transacaoId);
    }
    
    // Marca parcela como PAGA
    public Parcela marcarComoPaga(Long parcelaId) {
        Parcela parcela = parcelaRepository.findById(parcelaId)
            .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));
        
        parcela.setStatus(StatusPagamento.PAGO);
        parcela.setDataPagamento(LocalDate.now()); // Data de hoje
        
        return parcelaRepository.save(parcela);
    }
    
    // Marca parcela como PENDENTE (desfazer pagamento)
    public Parcela marcarComoPendente(Long parcelaId) {
        Parcela parcela = parcelaRepository.findById(parcelaId)
            .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));
        
        parcela.setStatus(StatusPagamento.PENDENTE);
        parcela.setDataPagamento(null);
        
        return parcelaRepository.save(parcela);
    }
    
    // Busca parcela por ID
    public Parcela buscarPorId(Long id) {
        return parcelaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));
    }
    
    // Atualiza status de parcelas atrasadas (executa todo dia)
    public void atualizarParcelasAtrasadas() {
        List<Parcela> todasParcelas = parcelaRepository.findAll();
        LocalDate hoje = LocalDate.now();
        
        for (Parcela parcela : todasParcelas) {
            // Se passou da data de vencimento e ainda está PENDENTE
            if (parcela.getDataVencimento().isBefore(hoje) 
                && parcela.getStatus() == StatusPagamento.PENDENTE) {
                parcela.setStatus(StatusPagamento.ATRASADO);
                parcelaRepository.save(parcela);
            }
        }
    }
}