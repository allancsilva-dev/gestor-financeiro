package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.*;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class FaturaService {

    @Autowired
    private FaturaCartaoRepository faturaRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public FaturaResponse buscarOuCriarAtual(Long usuarioId, Long contaId) {
        Conta conta = validarContaCredito(usuarioId, contaId);
        YearMonth ym = YearMonth.now();

        FaturaCartao fatura = faturaRepository.findByContaIdAndMesAndAno(contaId, ym.getMonthValue(), ym.getYear())
                .orElseGet(() -> criarFaturaVazia(usuarioId, conta, ym.getMonthValue(), ym.getYear()));

        return toResponse(fatura, usuarioId, conta);
    }

    public FaturaResponse buscarPorMes(Long usuarioId, Long contaId, Integer mes, Integer ano) {
        Conta conta = validarContaCredito(usuarioId, contaId);

        FaturaCartao fatura = faturaRepository.findByContaIdAndMesAndAno(contaId, mes, ano)
                .orElseGet(() -> criarFaturaVazia(usuarioId, conta, mes, ano));

        return toResponse(fatura, usuarioId, conta);
    }

    @Transactional
    public FaturaResponse pagarFatura(Long usuarioId, Long faturaId, BigDecimal valor) {
        FaturaCartao fatura = faturaRepository.findByIdAndUsuarioId(faturaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));

        if (fatura.getStatus() == FaturaStatus.PAGA) {
            throw new BusinessException("Fatura já está paga");
        }

        Conta conta = fatura.getConta();

        Transacao pagamento = new Transacao();
        pagamento.setDescricao("Pagamento fatura " + conta.getNome() + " " + fatura.getMes() + "/" + fatura.getAno());
        pagamento.setData(LocalDate.now());
        pagamento.setTipo(TipoTransacao.SAIDA);
        pagamento.setValorTotal(valor);
        pagamento.setParcelado(false);
        pagamento.setCategoria(null);
        pagamento.setUsuario(fatura.getUsuario());
        pagamento.setConta(conta);
        pagamento.setObservacoes("Pagamento de fatura");

        transacaoRepository.save(pagamento);

        fatura.setValorPago(valor);
        fatura.setDataPagamento(LocalDate.now());
        fatura.setStatus(FaturaStatus.PAGA);
        faturaRepository.save(fatura);

        return toResponse(fatura, usuarioId, conta);
    }

    private FaturaCartao criarFaturaVazia(Long usuarioId, Conta conta, Integer mes, Integer ano) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        YearMonth ym = YearMonth.of(ano, mes);
        LocalDate fechamento = conta.getDiaFechamento() != null
                ? ym.atDay(Math.min(conta.getDiaFechamento(), ym.lengthOfMonth()))
                : ym.atDay(ym.lengthOfMonth());
        LocalDate vencimento = conta.getDiaVencimento() != null
                ? ym.plusMonths(1).atDay(Math.min(conta.getDiaVencimento(), ym.plusMonths(1).lengthOfMonth()))
                : ym.plusMonths(1).atDay(10);

        FaturaCartao fatura = new FaturaCartao();
        fatura.setUsuario(usuario);
        fatura.setConta(conta);
        fatura.setMes(mes);
        fatura.setAno(ano);
        fatura.setDataFechamento(fechamento);
        fatura.setDataVencimento(vencimento);
        fatura.setStatus(FaturaStatus.ABERTA);

        return faturaRepository.save(fatura);
    }

    private FaturaResponse toResponse(FaturaCartao fatura, Long usuarioId, Conta conta) {
        YearMonth ym = YearMonth.of(fatura.getAno(), fatura.getMes());
        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();

        List<Transacao> transacoes = transacaoRepository.findByUsuarioIdAndContaIdAndDataBetween(
                usuarioId, conta.getId(), inicio, fim);

        BigDecimal total = BigDecimal.ZERO;
        List<FaturaLancamentoDto> lancamentos = new ArrayList<>();

        for (Transacao t : transacoes) {
            BigDecimal valor = t.getValorTotal() != null ? t.getValorTotal() : BigDecimal.ZERO;
            total = total.add(valor);

            Categoria cat = t.getCategoria();
            lancamentos.add(new FaturaLancamentoDto(
                    t.getId(),
                    t.getDescricao(),
                    valor,
                    t.getData(),
                    cat != null ? cat.getId() : null,
                    cat != null ? cat.getNome() : null,
                    cat != null ? cat.getCor() : "#6B7280",
                    cat != null ? cat.getIcone() : "",
                    t.getTotalParcelas() != null && t.getParcelado() ? t.getTotalParcelas() : null,
                    t.getTotalParcelas()
            ));
        }

        if (fatura.getValorTotal().compareTo(BigDecimal.ZERO) == 0 && total.compareTo(BigDecimal.ZERO) > 0) {
            fatura.setValorTotal(total);
            faturaRepository.save(fatura);
        }

        BigDecimal valorTotal = fatura.getValorTotal().compareTo(BigDecimal.ZERO) > 0
                ? fatura.getValorTotal() : total;

        return new FaturaResponse(
                fatura.getId(),
                conta.getId(),
                conta.getNome(),
                fatura.getMes(),
                fatura.getAno(),
                fatura.getDataFechamento(),
                fatura.getDataVencimento(),
                valorTotal,
                fatura.getValorPago(),
                fatura.getStatus().name(),
                fatura.getDataPagamento(),
                lancamentos
        );
    }

    private Conta validarContaCredito(Long usuarioId, Long contaId) {
        Conta conta = contaRepository.findByIdAndUsuarioId(contaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));

        if (!"CREDITO".equals(conta.getTipo().name())) {
            throw new BusinessException("A conta informada não é um cartão de crédito");
        }

        return conta;
    }
}
