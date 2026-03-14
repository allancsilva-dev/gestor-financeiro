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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    public List<Carteira> listarPorUsuario(Long usuarioId) {
        return carteiraRepository.findByUsuarioId(usuarioId);
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
        List<Categoria> categorias = categoriaRepository.findByUsuarioId(usuario.getId());
        
        // Procura categoria existente
        for (Categoria cat : categorias) {
            if ("Transferência".equalsIgnoreCase(cat.getNome()) || 
                "Depósito".equalsIgnoreCase(cat.getNome())) {
                return cat;
            }
        }
        
        // Se não existir, cria uma nova
        Categoria novaCategoria = new Categoria();
        novaCategoria.setUsuario(usuario);
        novaCategoria.setNome("Transferência");
        novaCategoria.setCor("#00BCD4"); // Azul ciano
        novaCategoria.setIcone("💱");
        novaCategoria.setValorEsperado(BigDecimal.ZERO);
        
        return categoriaRepository.save(novaCategoria);
    }
    
    // Calcula saldo total de todas as carteiras do usuário
    public BigDecimal calcularSaldoTotal(Long usuarioId) {
        return carteiraRepository.findByUsuarioId(usuarioId)
            .stream()
            .map(Carteira::getSaldo)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    // Deleta carteira
    public void deletar(Long id) {
        Carteira carteira = buscarPorId(id);
        carteiraRepository.delete(carteira);
    }

    public void deletar(Long id, Long usuarioId) {
        Carteira carteira = buscarPorIdDoUsuario(id, usuarioId);
        carteiraRepository.delete(carteira);
    }
}