package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class CarteiraService {
    
    @Autowired
    private CarteiraRepository carteiraRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private TransacaoRepository transacaoRepository;
    
    @Autowired
    private CategoriaRepository categoriaRepository;
    
    // Lista carteiras do usuário
    public Page<Carteira> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return carteiraRepository.findByUsuarioId(usuarioId, pageable);
    }
    
    // Busca carteira por ID
    public Carteira buscarPorId(Long id) {
        return carteiraRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));
    }

    // Valida ownership para evitar IDOR em operações por ID.
    public Carteira buscarPorIdDoUsuario(Long id, Long usuarioId) {
        Carteira carteira = carteiraRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

        if (!carteira.getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedAccessException("Acesso negado a esta carteira");
        }

        return carteira;
    }
    
    // Cria nova carteira
    @Transactional
    public Carteira criar(Carteira carteira, Long usuarioId) {
        // O usuário vem do token para evitar IDOR via payload.
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        
        carteira.setUsuario(usuario);
        
        // Se saldo não foi definido, inicia com zero
        if (carteira.getSaldo() == null) {
            carteira.setSaldo(BigDecimal.ZERO);
        }
        
        return carteiraRepository.save(carteira);
    }
    
    // Atualiza carteira
    @Transactional
    public Carteira atualizar(Long id, Carteira carteiraAtualizada, Long usuarioId) {
        Carteira carteira = buscarPorIdDoUsuario(id, usuarioId);
        
        carteira.setNome(carteiraAtualizada.getNome());
        carteira.setTipo(carteiraAtualizada.getTipo());
        carteira.setSaldo(carteiraAtualizada.getSaldo());
        carteira.setBanco(carteiraAtualizada.getBanco());
        
        return carteiraRepository.save(carteira);
    }
    
    // Adiciona dinheiro E cria transação de ENTRADA
    @Transactional
    public Carteira adicionarDinheiro(Long id, BigDecimal valor, Long usuarioId) {
        Carteira carteira = buscarPorIdDoUsuario(id, usuarioId);
        
        // Atualiza o saldo da carteira
        carteira.setSaldo(carteira.getSaldo().add(valor));
        carteiraRepository.save(carteira);
        
        // Cria transação de ENTRADA automaticamente
        criarTransacaoAutomatica(
            carteira,
            valor,
            TipoTransacao.ENTRADA,
            "Depósito na carteira: " + carteira.getNome()
        );
        
        return carteira;
    }
    
    // Remove dinheiro E cria transação de SAÍDA
    @Transactional
    public Carteira removerDinheiro(Long id, BigDecimal valor, Long usuarioId) {
        Carteira carteira = buscarPorIdDoUsuario(id, usuarioId);
        
        if (carteira.getSaldo().compareTo(valor) < 0) {
            throw new BusinessException("Saldo insuficiente");
        }
        
        // Atualiza o saldo da carteira
        carteira.setSaldo(carteira.getSaldo().subtract(valor));
        carteiraRepository.save(carteira);
        
        // Cria transação de SAÍDA automaticamente
        criarTransacaoAutomatica(
            carteira,
            valor,
            TipoTransacao.SAIDA,
            "Retirada da carteira: " + carteira.getNome()
        );
        
        return carteira;
    }
    
    // Método auxiliar para criar transação automática
    private void criarTransacaoAutomatica(Carteira carteira, BigDecimal valor, 
                                         TipoTransacao tipo, String descricao) {
        // Busca ou cria categoria "Transferência"
        Categoria categoria = buscarOuCriarCategoriaTransferencia(carteira.getUsuario());
        
        // Cria a transação
        Transacao transacao = new Transacao();
        transacao.setUsuario(carteira.getUsuario());
        transacao.setConta(null); // Carteira não tem "conta" (cartão)
        transacao.setCategoria(categoria);
        transacao.setDescricao(descricao);
        transacao.setValorTotal(valor);
        transacao.setTipo(tipo);
        transacao.setData(LocalDate.now());
        transacao.setParcelado(false);
        
        transacaoRepository.save(transacao);
    }
    
    // Busca ou cria a categoria "Transferência"
    private Categoria buscarOuCriarCategoriaTransferencia(Usuario usuario) {
        return categoriaRepository.findByUsuarioIdAndNomeIgnoreCase(usuario.getId(), "Transferência")
                .orElseGet(() -> {
                    Categoria novaCategoria = new Categoria();
                    novaCategoria.setUsuario(usuario);
                    novaCategoria.setNome("Transferência");
                    novaCategoria.setCor("#00BCD4");
                    novaCategoria.setIcone("💱");
                    novaCategoria.setValorEsperado(BigDecimal.ZERO);
                    return categoriaRepository.save(novaCategoria);
                });
    }
    
    // Calcula saldo total de todas as carteiras do usuário
    public BigDecimal calcularSaldoTotal(Long usuarioId) {
        return carteiraRepository.sumSaldoByUsuarioId(usuarioId);
    }
    
    @Transactional
    public void deletar(Long id, Long usuarioId) {
        Carteira carteira = buscarPorIdDoUsuario(id, usuarioId);
        carteiraRepository.delete(carteira);
    }
}