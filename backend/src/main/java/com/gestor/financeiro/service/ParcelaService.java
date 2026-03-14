package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
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
    public List<Parcela> listarPorTransacao(Long transacaoId, Long usuarioId) {
        return parcelaRepository.findByTransacaoIdAndTransacaoUsuarioId(transacaoId, usuarioId);
    }
    
    // Marca parcela como PAGA
    public Parcela marcarComoPaga(Long parcelaId, Long usuarioId) {
        Parcela parcela = buscarPorIdDoUsuario(parcelaId, usuarioId);
        
        parcela.setStatus(StatusPagamento.PAGO);
        parcela.setDataPagamento(LocalDate.now()); // Data de hoje
        
        return parcelaRepository.save(parcela);
    }
    
    // Marca parcela como PENDENTE (desfazer pagamento)
    public Parcela marcarComoPendente(Long parcelaId, Long usuarioId) {
        Parcela parcela = buscarPorIdDoUsuario(parcelaId, usuarioId);
        
        parcela.setStatus(StatusPagamento.PENDENTE);
        parcela.setDataPagamento(null);
        
        return parcelaRepository.save(parcela);
    }
    
    // Busca parcela por ID
    public Parcela buscarPorId(Long id, Long usuarioId) {
        return buscarPorIdDoUsuario(id, usuarioId);
    }

    // Valida ownership através da transação dona da parcela.
    public Parcela buscarPorIdDoUsuario(Long id, Long usuarioId) {
        Parcela parcela = parcelaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Parcela não encontrada"));

        if (!parcela.getTransacao().getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedAccessException("Acesso negado a esta parcela");
        }

        return parcela;
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