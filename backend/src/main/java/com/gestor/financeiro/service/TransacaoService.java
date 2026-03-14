package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.ParcelaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private CategoriaRepository categoriaRepository;
    
    @Autowired
    private ContaRepository contaRepository;
    
    @Autowired
    private ContaService contaService;

    @Autowired
    private UsuarioRepository usuarioRepository;
    
    // Lista transações do usuário
    public Page<Transacao> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return transacaoRepository.findByUsuarioId(usuarioId, pageable);
    }
    
    // Lista transações de um período
    public Page<Transacao> listarPorPeriodo(Long usuarioId, LocalDate inicio, LocalDate fim, Pageable pageable) {
        return transacaoRepository.findByUsuarioIdAndDataBetween(usuarioId, inicio, fim, pageable);
    }
    
    // Cria transação (com ou sem parcelamento)
    @Transactional
    public Transacao criar(Transacao transacao, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        transacao.setUsuario(usuario);
        
        // ✅ BUSCA A CATEGORIA DO BANCO (com dados completos)
        if (transacao.getCategoria() != null && transacao.getCategoria().getId() != null) {
            Categoria categoria = categoriaRepository.findById(transacao.getCategoria().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
            
            // Atualiza o valorGasto da categoria
            categoria.setValorGasto(
                categoria.getValorGasto().add(transacao.getValorTotal())
            );
            categoriaRepository.save(categoria);
            
            // Associa a categoria completa à transação
            transacao.setCategoria(categoria);
        }
        
        // ✅ BUSCA A CONTA DO BANCO (com dados completos)
        if (transacao.getConta() != null && transacao.getConta().getId() != null) {
            Conta conta = contaRepository.findById(transacao.getConta().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
            
            // Adiciona gasto na conta
            contaService.adicionarGasto(conta.getId(), transacao.getValorTotal());
            
            // Associa a conta completa à transação
            transacao.setConta(conta);
        }
        
        // Se for parcelado, cria as parcelas automaticamente
        if (transacao.getParcelado() && transacao.getTotalParcelas() > 1) {
            criarParcelas(transacao);
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
    public Transacao atualizar(Long id, Transacao transacaoAtualizada, Long usuarioId) {
        Transacao transacao = buscarPorIdDoUsuario(id, usuarioId);
        
        transacao.setDescricao(transacaoAtualizada.getDescricao());
        transacao.setValorTotal(transacaoAtualizada.getValorTotal());
        transacao.setData(transacaoAtualizada.getData());
        transacao.setObservacoes(transacaoAtualizada.getObservacoes());
        
        return transacaoRepository.save(transacao);
    }
    
    // Deleta transação
    @Transactional
    public void deletar(Long id, Long usuarioId) {
        Transacao transacao = buscarPorIdDoUsuario(id, usuarioId);
        
        // Remove gasto da conta
        if (transacao.getConta() != null) {
            contaService.removerGasto(
                transacao.getConta().getId(), 
                transacao.getValorTotal()
            );
        }
        
        // Remove gasto da categoria
        if (transacao.getCategoria() != null) {
            Categoria categoria = categoriaRepository.findById(transacao.getCategoria().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
            
            categoria.setValorGasto(
                categoria.getValorGasto().subtract(transacao.getValorTotal())
            );
            categoriaRepository.save(categoria);
        }
        
        transacaoRepository.delete(transacao);
    }
    
    // Busca por ID
    public Transacao buscarPorId(Long id) {
        return transacaoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));
    }

    // Valida ownership para evitar IDOR em endpoints por ID.
    public Transacao buscarPorIdDoUsuario(Long id, Long usuarioId) {
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        if (!transacao.getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedAccessException("Acesso negado a esta transação");
        }

        return transacao;
    }
}