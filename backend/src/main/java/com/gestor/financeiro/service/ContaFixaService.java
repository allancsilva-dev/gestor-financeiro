package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ContaFixaService {
    
    @Autowired
    private ContaFixaRepository contaFixaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TransacaoService transacaoService;
    
    // Lista contas fixas ativas do usuário
    public Page<ContaFixa> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId, pageable);
    }
    
    // Cria nova conta fixa
    @Transactional
    public ContaFixa criar(ContaFixa contaFixa, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        contaFixa.setUsuario(usuario);

        // Valida ownership da categoria, se informada
        if (contaFixa.getCategoria() != null && contaFixa.getCategoria().getId() != null) {
            Categoria categoria = categoriaRepository.findByIdAndUsuarioId(
                    contaFixa.getCategoria().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
            contaFixa.setCategoria(categoria);
        }

        // Valores padrão
        if (contaFixa.getAtivo() == null) contaFixa.setAtivo(true);
        if (contaFixa.getRecorrente() == null) contaFixa.setRecorrente(true);
        if (contaFixa.getStatus() == null) contaFixa.setStatus(StatusPagamento.PENDENTE);
        
        // Calcula próximo vencimento
        calcularProximoVencimento(contaFixa);
        
        return contaFixaRepository.save(contaFixa);
    }
    
    // Calcula data do próximo vencimento
    private void calcularProximoVencimento(ContaFixa contaFixa) {
        LocalDate hoje = LocalDate.now();
        int diaVencimento = contaFixa.getDiaVencimento();
        
        // Próximo vencimento no mês atual
        LocalDate proximoVencimento = LocalDate.of(
            hoje.getYear(), 
            hoje.getMonthValue(), 
            Math.min(diaVencimento, hoje.lengthOfMonth())
        );
        
        // Se já passou, joga pro próximo mês
        if (proximoVencimento.isBefore(hoje)) {
            proximoVencimento = proximoVencimento.plusMonths(1);
            proximoVencimento = proximoVencimento.withDayOfMonth(
                Math.min(diaVencimento, proximoVencimento.lengthOfMonth())
            );
        }
        
        contaFixa.setDataProximoVencimento(proximoVencimento);
    }
    
    // ✅ CORRIGIDO: Mantém como PAGO e só avança o vencimento
    @Transactional
    public ContaFixa marcarComoPaga(Long id, BigDecimal valorPago, Long usuarioId) {
        ContaFixa conta = buscarPorIdDoUsuario(id, usuarioId);
        
        // ✅ VERIFICA SE JÁ ESTÁ PAGA ESTE MÊS
        if (conta.getStatus() == StatusPagamento.PAGO) {
            throw new BusinessException("Esta conta já foi paga este mês!");
        }
        
        // 1. Cria a transação de saída
        Transacao transacao = new Transacao();
        transacao.setDescricao("Pagamento: " + conta.getNome());
        transacao.setData(LocalDate.now());
        transacao.setTipo(TipoTransacao.SAIDA);
        transacao.setValorTotal(valorPago);
        transacao.setParcelado(false);
        transacao.setCategoria(conta.getCategoria());
        transacao.setUsuario(conta.getUsuario());
        transacao.setContaFixa(conta);
        transacao.setObservacoes("Pagamento automático de conta fixa");

        transacaoService.criar(transacao, conta.getUsuario().getId());
        
        // 2. Marca como PAGA
        conta.setStatus(StatusPagamento.PAGO);
        conta.setValorReal(valorPago);
        
        // 3. ✅ Se é recorrente, apenas AVANÇA o vencimento (mantém como PAGO)
        if (conta.getRecorrente()) {
            LocalDate proximoVencimento = conta.getDataProximoVencimento().plusMonths(1);
            proximoVencimento = proximoVencimento.withDayOfMonth(
                Math.min(conta.getDiaVencimento(), proximoVencimento.lengthOfMonth())
            );
            conta.setDataProximoVencimento(proximoVencimento);
            // ✅ NÃO reseta o status aqui!
        }
        
        return contaFixaRepository.save(conta);
    }

    @Transactional
    public ContaFixa pularMes(Long id, Long usuarioId) {
        ContaFixa conta = buscarPorIdDoUsuario(id, usuarioId);

        if (!conta.getRecorrente()) {
            throw new BusinessException("Apenas contas recorrentes podem pular mês");
        }

        if (conta.getAtivo() == null || !conta.getAtivo()) {
            throw new BusinessException("Conta fixa está inativa");
        }

        LocalDate proximoVencimento = conta.getDataProximoVencimento().plusMonths(1);
        proximoVencimento = proximoVencimento.withDayOfMonth(
                Math.min(conta.getDiaVencimento(), proximoVencimento.lengthOfMonth()));
        conta.setDataProximoVencimento(proximoVencimento);

        if (conta.getStatus() == StatusPagamento.PAGO) {
            conta.setStatus(StatusPagamento.PENDENTE);
        }

        return contaFixaRepository.save(conta);
    }

    @Transactional
    public ContaFixa reativar(Long id, Long usuarioId) {
        ContaFixa conta = buscarPorIdDoUsuario(id, usuarioId);

        if (conta.getAtivo() != null && conta.getAtivo()) {
            throw new BusinessException("Conta fixa já está ativa");
        }

        conta.setAtivo(true);
        conta.setStatus(StatusPagamento.PENDENTE);
        calcularProximoVencimento(conta);

        return contaFixaRepository.save(conta);
    }
    
    // Atualiza conta fixa
    @Transactional
    public ContaFixa atualizar(Long id, ContaFixa contaAtualizada, Long usuarioId) {
        ContaFixa conta = buscarPorIdDoUsuario(id, usuarioId);
        
        conta.setNome(contaAtualizada.getNome());
        conta.setValorPlanejado(contaAtualizada.getValorPlanejado());
        conta.setDiaVencimento(contaAtualizada.getDiaVencimento());
        
        if (contaAtualizada.getCategoria() != null && contaAtualizada.getCategoria().getId() != null) {
            Categoria categoria = categoriaRepository.findByIdAndUsuarioId(
                    contaAtualizada.getCategoria().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
            conta.setCategoria(categoria);
        }
        
        conta.setObservacoes(contaAtualizada.getObservacoes());
        
        // Recalcula próximo vencimento
        calcularProximoVencimento(conta);
        
        return contaFixaRepository.save(conta);
    }
    
    // Desativa conta fixa
    @Transactional
    public void deletar(Long id, Long usuarioId) {
        ContaFixa conta = buscarPorIdDoUsuario(id, usuarioId);
        
        conta.setAtivo(false);
        contaFixaRepository.save(conta);
    }
    
    // Busca por ID
    public ContaFixa buscarPorId(Long id) {
        return contaFixaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conta fixa não encontrada"));
    }

    // Valida ownership para evitar IDOR em operações por ID.
    public ContaFixa buscarPorIdDoUsuario(Long id, Long usuarioId) {
        ContaFixa conta = contaFixaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conta fixa não encontrada"));

        if (!conta.getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedAccessException("Acesso negado a esta conta fixa");
        }

        return conta;
    }
    
    @Transactional
    public void atualizarContasAtrasadas() {
        LocalDate hoje = LocalDate.now();
        contaFixaRepository.resetarContasPagasVencidas(StatusPagamento.PAGO, StatusPagamento.PENDENTE, hoje);
        contaFixaRepository.atualizarStatusContasAtrasadas(StatusPagamento.PENDENTE, StatusPagamento.ATRASADO, hoje);
    }
}