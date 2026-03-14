package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
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
    private TransacaoRepository transacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;
    
    // Lista contas fixas ativas do usuário
    public List<ContaFixa> listarPorUsuario(Long usuarioId) {
        return contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
    }
    
    // Cria nova conta fixa
    public ContaFixa criar(ContaFixa contaFixa, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        contaFixa.setUsuario(usuario);

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
        transacao.setObservacoes("Pagamento automático de conta fixa");
        
        // Salva a transação
        transacaoRepository.save(transacao);
        
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
    
    // Atualiza conta fixa
    public ContaFixa atualizar(Long id, ContaFixa contaAtualizada, Long usuarioId) {
        ContaFixa conta = buscarPorIdDoUsuario(id, usuarioId);
        
        conta.setNome(contaAtualizada.getNome());
        conta.setValorPlanejado(contaAtualizada.getValorPlanejado());
        conta.setDiaVencimento(contaAtualizada.getDiaVencimento());
        conta.setCategoria(contaAtualizada.getCategoria());
        conta.setObservacoes(contaAtualizada.getObservacoes());
        
        // Recalcula próximo vencimento
        calcularProximoVencimento(conta);
        
        return contaFixaRepository.save(conta);
    }
    
    // Desativa conta fixa
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
    
    // ✅ NOVO: Reseta contas pagas quando passa o vencimento
    public void atualizarContasAtrasadas() {
        List<ContaFixa> todasContas = contaFixaRepository.findAll();
        LocalDate hoje = LocalDate.now();
        
        for (ContaFixa conta : todasContas) {
            // Se a conta está PAGA e o vencimento já passou, volta para PENDENTE
            if (conta.getStatus() == StatusPagamento.PAGO 
                && conta.getDataProximoVencimento().isBefore(hoje)) {
                conta.setStatus(StatusPagamento.PENDENTE);
                conta.setValorReal(null);
                contaFixaRepository.save(conta);
            }
            // Se está PENDENTE e atrasou
            else if (conta.getDataProximoVencimento().isBefore(hoje) 
                && conta.getStatus() == StatusPagamento.PENDENTE) {
                conta.setStatus(StatusPagamento.ATRASADO);
                contaFixaRepository.save(conta);
            }
        }
    }
}