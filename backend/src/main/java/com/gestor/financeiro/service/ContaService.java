package com.gestor.financeiro.service;

import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.repository.ContaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
public class ContaService {
    
    @Autowired
    private ContaRepository contaRepository;
    
    // Lista contas ativas do usuário
    public List<Conta> listarPorUsuario(Long usuarioId) {
        return contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
    }
    
    // Cria nova conta
    public Conta criar(Conta conta) {
        // Inicializa valores padrão
        if (conta.getAtivo() == null) conta.setAtivo(true);
        if (conta.getValorGasto() == null) conta.setValorGasto(BigDecimal.ZERO);
        if (conta.getSaldoAtual() == null) conta.setSaldoAtual(BigDecimal.ZERO);
        if (conta.getLimiteTotal() == null) conta.setLimiteTotal(BigDecimal.ZERO);
        
        return contaRepository.save(conta);
    }
    
    // Atualiza conta
    public Conta atualizar(Long id, Conta contaAtualizada) {
        Conta conta = contaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        
        conta.setNome(contaAtualizada.getNome());
        conta.setTipo(contaAtualizada.getTipo());
        conta.setLimiteTotal(contaAtualizada.getLimiteTotal());
        conta.setDiaFechamento(contaAtualizada.getDiaFechamento());
        conta.setDiaVencimento(contaAtualizada.getDiaVencimento());
        conta.setCor(contaAtualizada.getCor());
        
        return contaRepository.save(conta);
    }
    
    // Adiciona gasto na conta (quando faz uma compra)
    public void adicionarGasto(Long contaId, BigDecimal valor) {
        Conta conta = contaRepository.findById(contaId)
            .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        
        // Soma o valor ao gasto atual
        conta.setValorGasto(conta.getValorGasto().add(valor));
        contaRepository.save(conta);
    }
    
    // Remove gasto da conta (quando cancela uma compra)
    public void removerGasto(Long contaId, BigDecimal valor) {
        Conta conta = contaRepository.findById(contaId)
            .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        
        // Subtrai o valor do gasto atual
        conta.setValorGasto(conta.getValorGasto().subtract(valor));
        contaRepository.save(conta);
    }
    
    // Desativa conta
    public void deletar(Long id) {
        Conta conta = contaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        
        conta.setAtivo(false);
        contaRepository.save(conta);
    }
    
    // Busca por ID
    public Conta buscarPorId(Long id) {
        return contaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
    }
}