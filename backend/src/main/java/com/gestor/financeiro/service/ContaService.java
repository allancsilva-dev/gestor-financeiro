package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
public class ContaService {
    
    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;
    
    // Lista contas ativas do usuário
    public List<Conta> listarPorUsuario(Long usuarioId) {
        return contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
    }
    
    // Cria nova conta
    public Conta criar(Conta conta, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        conta.setUsuario(usuario);

        // Inicializa valores padrão
        if (conta.getAtivo() == null) conta.setAtivo(true);
        if (conta.getValorGasto() == null) conta.setValorGasto(BigDecimal.ZERO);
        if (conta.getSaldoAtual() == null) conta.setSaldoAtual(BigDecimal.ZERO);
        if (conta.getLimiteTotal() == null) conta.setLimiteTotal(BigDecimal.ZERO);
        
        return contaRepository.save(conta);
    }
    
    // Atualiza conta
    public Conta atualizar(Long id, Conta contaAtualizada, Long usuarioId) {
        Conta conta = buscarPorIdDoUsuario(id, usuarioId);
        
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
            .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
        
        // Soma o valor ao gasto atual
        conta.setValorGasto(conta.getValorGasto().add(valor));
        contaRepository.save(conta);
    }
    
    // Remove gasto da conta (quando cancela uma compra)
    public void removerGasto(Long contaId, BigDecimal valor) {
        Conta conta = contaRepository.findById(contaId)
            .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
        
        // Subtrai o valor do gasto atual
        conta.setValorGasto(conta.getValorGasto().subtract(valor));
        contaRepository.save(conta);
    }
    
    // Desativa conta
    public void deletar(Long id, Long usuarioId) {
        Conta conta = buscarPorIdDoUsuario(id, usuarioId);
        
        conta.setAtivo(false);
        contaRepository.save(conta);
    }
    
    // Busca por ID
    public Conta buscarPorId(Long id) {
        return contaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
    }

    // Valida ownership para evitar IDOR em endpoints por ID.
    public Conta buscarPorIdDoUsuario(Long id, Long usuarioId) {
        Conta conta = contaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));

        if (!conta.getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedAccessException("Acesso negado a esta conta");
        }

        return conta;
    }
}