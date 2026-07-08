package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
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

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private MovimentoCarteiraRepository movimentoCarteiraRepository;
    
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

        BigDecimal saldoInicial = carteira.getSaldo() == null ? BigDecimal.ZERO : carteira.getSaldo();
        carteira.setSaldo(BigDecimal.ZERO);

        Carteira salva = carteiraRepository.save(carteira);
        if (saldoInicial.signum() != 0) {
            registrarAjusteManual(
                    salva,
                    saldoInicial.abs(),
                    saldoInicial.signum() > 0
                            ? RegistrarMovimentoCommand.Direcao.ENTRADA
                            : RegistrarMovimentoCommand.Direcao.SAIDA,
                    "Saldo inicial da carteira: " + salva.getNome(),
                    saldoInicial.signum() < 0
            );
        }

        return carteiraRepository.findById(salva.getId()).orElse(salva);
    }
    
    // Atualiza carteira
    @Transactional
    public Carteira atualizar(Long id, Carteira carteiraAtualizada, Long usuarioId) {
        Carteira carteira = buscarPorIdDoUsuario(id, usuarioId);
        BigDecimal saldoAtual = carteira.getSaldo() == null ? BigDecimal.ZERO : carteira.getSaldo();
        BigDecimal novoSaldo = carteiraAtualizada.getSaldo();
        
        carteira.setNome(carteiraAtualizada.getNome());
        carteira.setTipo(carteiraAtualizada.getTipo());
        carteira.setBanco(carteiraAtualizada.getBanco());

        Carteira salva = carteiraRepository.save(carteira);
        if (novoSaldo != null) {
            BigDecimal diferenca = novoSaldo.subtract(saldoAtual);
            if (diferenca.signum() != 0) {
                registrarAjusteManual(
                        salva,
                        diferenca.abs(),
                        diferenca.signum() > 0
                                ? RegistrarMovimentoCommand.Direcao.ENTRADA
                                : RegistrarMovimentoCommand.Direcao.SAIDA,
                        "Ajuste de saldo da carteira: " + salva.getNome(),
                        diferenca.signum() < 0 && novoSaldo.signum() < 0
                );
            }
        }

        return carteiraRepository.findById(id).orElse(salva);
    }
    
    // Adiciona dinheiro E cria transação de ENTRADA
    @Transactional
    public Carteira adicionarDinheiro(Long id, BigDecimal valor, Long usuarioId) {
        Carteira carteira = buscarPorIdDoUsuario(id, usuarioId);

        MovimentoCarteira movimento = registrarAjusteManual(
                carteira,
                valor,
                RegistrarMovimentoCommand.Direcao.ENTRADA,
                "Depósito na carteira: " + carteira.getNome(),
                false
        );
        
        // Cria transação de ENTRADA automaticamente
        criarTransacaoAutomatica(
            carteira,
            valor,
            TipoTransacao.ENTRADA,
            "Depósito na carteira: " + carteira.getNome()
        );
        
        return movimento.getCarteira();
    }
    
    // Remove dinheiro E cria transação de SAÍDA
    @Transactional
    public Carteira removerDinheiro(Long id, BigDecimal valor, Long usuarioId) {
        Carteira carteira = buscarPorIdDoUsuario(id, usuarioId);
        
        if (carteira.getSaldo().compareTo(valor) < 0) {
            throw new BusinessException("Saldo insuficiente");
        }
        
        MovimentoCarteira movimento = registrarAjusteManual(
                carteira,
                valor,
                RegistrarMovimentoCommand.Direcao.SAIDA,
                "Retirada da carteira: " + carteira.getNome(),
                false
        );
        
        // Cria transação de SAÍDA automaticamente
        criarTransacaoAutomatica(
            carteira,
            valor,
            TipoTransacao.SAIDA,
            "Retirada da carteira: " + carteira.getNome()
        );
        
        return movimento.getCarteira();
    }

    private MovimentoCarteira registrarAjusteManual(Carteira carteira,
                                                    BigDecimal valor,
                                                    RegistrarMovimentoCommand.Direcao direcao,
                                                    String descricao,
                                                    boolean permitirSaldoNegativo) {
        return ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                carteira.getUsuario().getId(),
                carteira.getId(),
                TipoMovimentoCarteira.AJUSTE_MANUAL,
                valor,
                direcao,
                OrigemMovimentoCarteira.CARTEIRA_AJUSTE,
                "CARTEIRA",
                carteira.getId(),
                descricao,
                null,
                null,
                permitirSaldoNegativo
        ));
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

        boolean temMovimentos = movimentoCarteiraRepository
                .existsByCarteiraIdAndOrigemAndReferenciaTipo(
                        carteira.getId(),
                        OrigemMovimentoCarteira.CARTEIRA_AJUSTE,
                        "CARTEIRA"
                );

        if (temMovimentos) {
            throw new BusinessException(
                    "Carteira possui histórico financeiro e não pode ser excluída. "
                            + "Considere arquivá-la ou renomeá-la para 'Inativa'.");
        }

        carteiraRepository.delete(carteira);
    }

    @Transactional
    public Carteira ajustarSaldo(Long id, String tipo, BigDecimal valor, String descricao, Long usuarioId) {
        Carteira carteira = buscarPorIdDoUsuario(id, usuarioId);
        RegistrarMovimentoCommand.Direcao direcao;
        try {
            direcao = RegistrarMovimentoCommand.Direcao.valueOf(tipo.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Tipo inválido. Use ENTRADA ou SAIDA");
        }

        registrarAjusteManual(
                carteira,
                valor,
                direcao,
                descricao != null && !descricao.isBlank()
                        ? descricao
                        : "Ajuste manual: " + tipo + " de " + valor,
                false
        );

        return carteiraRepository.findById(id).orElse(carteira);
    }

    public Page<MovimentoCarteira> listarMovimentos(Long carteiraId, Long usuarioId, Pageable pageable) {
        buscarPorIdDoUsuario(carteiraId, usuarioId);
        return movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuarioId, carteiraId, pageable);
    }
}
