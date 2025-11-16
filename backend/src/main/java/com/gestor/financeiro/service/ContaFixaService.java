package com.gestor.financeiro.service;

import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
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
    
    // Lista contas fixas ativas do usuário
    public List<ContaFixa> listarPorUsuario(Long usuarioId) {
        return contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
    }
    
    // Cria nova conta fixa
    public ContaFixa criar(ContaFixa contaFixa) {
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
            Math.min(diaVencimento, hoje.lengthOfMonth()) // Ajusta para meses com menos dias
        );
        
        // Se já passou, joga pro próximo mês
        if (proximoVencimento.isBefore(hoje)) {
            proximoVencimento = proximoVencimento.plusMonths(1);
            // Ajusta novamente para o dia correto no próximo mês
            proximoVencimento = proximoVencimento.withDayOfMonth(
                Math.min(diaVencimento, proximoVencimento.lengthOfMonth())
            );
        }
        
        contaFixa.setDataProximoVencimento(proximoVencimento);
    }
    
    // ✅ NOVO: Marca conta como paga E cria transação automática
    @Transactional
    public ContaFixa marcarComoPaga(Long id, BigDecimal valorPago) {
        ContaFixa conta = contaFixaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conta fixa não encontrada"));
        
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
        
        // 2. Atualiza o status da conta fixa
        conta.setStatus(StatusPagamento.PAGO);
        conta.setValorReal(valorPago);
        
        // 3. Se é recorrente, gera próximo vencimento e reseta status
        if (conta.getRecorrente()) {
            LocalDate proximoVencimento = conta.getDataProximoVencimento().plusMonths(1);
            // Ajusta para o dia correto no próximo mês
            proximoVencimento = proximoVencimento.withDayOfMonth(
                Math.min(conta.getDiaVencimento(), proximoVencimento.lengthOfMonth())
            );
            conta.setDataProximoVencimento(proximoVencimento);
            conta.setStatus(StatusPagamento.PENDENTE); // Reseta para próximo mês
            conta.setValorReal(null); // Limpa valor real
        }
        
        return contaFixaRepository.save(conta);
    }
    
    // Atualiza conta fixa
    public ContaFixa atualizar(Long id, ContaFixa contaAtualizada) {
        ContaFixa conta = contaFixaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conta fixa não encontrada"));
        
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
    public void deletar(Long id) {
        ContaFixa conta = contaFixaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conta fixa não encontrada"));
        
        conta.setAtivo(false);
        contaFixaRepository.save(conta);
    }
    
    // Busca por ID
    public ContaFixa buscarPorId(Long id) {
        return contaFixaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conta fixa não encontrada"));
    }
    
    // Verifica contas atrasadas
    public void atualizarContasAtrasadas() {
        List<ContaFixa> todasContas = contaFixaRepository.findAll();
        LocalDate hoje = LocalDate.now();
        
        for (ContaFixa conta : todasContas) {
            if (conta.getDataProximoVencimento().isBefore(hoje) 
                && conta.getStatus() == StatusPagamento.PENDENTE) {
                conta.setStatus(StatusPagamento.ATRASADO);
                contaFixaRepository.save(conta);
            }
        }
    }
}