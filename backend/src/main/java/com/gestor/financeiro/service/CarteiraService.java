package com.gestor.financeiro.service;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CarteiraService {
    
    @Autowired
    private CarteiraRepository carteiraRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    // Lista carteiras do usuário
    public List<Carteira> listarPorUsuario(Long usuarioId) {
        return carteiraRepository.findByUsuarioId(usuarioId);
    }
    
    // Busca carteira por ID
    public Carteira buscarPorId(Long id) {
        return carteiraRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Carteira não encontrada"));
    }
    
    // Cria nova carteira
    public Carteira criar(Carteira carteira) {
        // Verifica se o usuário existe
        Usuario usuario = usuarioRepository.findById(carteira.getUsuario().getId())
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        
        carteira.setUsuario(usuario);
        
        // Se saldo não foi definido, inicia com zero
        if (carteira.getSaldo() == null) {
            carteira.setSaldo(BigDecimal.ZERO);
        }
        
        return carteiraRepository.save(carteira);
    }
    
    // Atualiza carteira
    public Carteira atualizar(Long id, Carteira carteiraAtualizada) {
        Carteira carteira = buscarPorId(id);
        
        carteira.setNome(carteiraAtualizada.getNome());
        carteira.setTipo(carteiraAtualizada.getTipo());
        carteira.setSaldo(carteiraAtualizada.getSaldo());
        carteira.setBanco(carteiraAtualizada.getBanco());
        
        return carteiraRepository.save(carteira);
    }
    
    // Adiciona dinheiro
    public Carteira adicionarDinheiro(Long id, BigDecimal valor) {
        Carteira carteira = buscarPorId(id);
        carteira.setSaldo(carteira.getSaldo().add(valor));
        return carteiraRepository.save(carteira);
    }
    
    // Remove dinheiro
    public Carteira removerDinheiro(Long id, BigDecimal valor) {
        Carteira carteira = buscarPorId(id);
        
        if (carteira.getSaldo().compareTo(valor) < 0) {
            throw new RuntimeException("Saldo insuficiente");
        }
        
        carteira.setSaldo(carteira.getSaldo().subtract(valor));
        return carteiraRepository.save(carteira);
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
}