package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.CronogramaItemResponse;
import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.model.FaturaLancamento;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.repository.ParcelaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CronogramaService {
    private final TransacaoService transacaoService;
    private final ParcelaRepository parcelaRepository;
    private final FaturaLancamentoRepository faturaLancamentoRepository;

    @Transactional(readOnly = true)
    public List<CronogramaItemResponse> listar(Long transacaoId, Long usuarioId) {
        Transacao transacao = transacaoService.buscarPorIdDoUsuario(transacaoId, usuarioId);
        if (isCompraCartao(transacao)) {
            return faturaLancamentoRepository
                    .findByTransacaoIdAndTipoOrderByParcelaNumeroAscIdAsc(transacaoId, TipoFaturaLancamento.COMPRA)
                    .stream().map(this::fromLancamento).toList();
        }
        return parcelaRepository.findByTransacaoIdAndTransacaoUsuarioId(transacaoId, usuarioId).stream()
                .sorted(Comparator.comparing(Parcela::getNumeroParcela))
                .map(this::fromParcela).toList();
    }

    private CronogramaItemResponse fromLancamento(FaturaLancamento lancamento) {
        FaturaCartao fatura = lancamento.getFatura();
        return new CronogramaItemResponse(lancamento.getId(), CronogramaItemResponse.Origem.CARTAO,
                numero(lancamento.getParcelaNumero()), numero(lancamento.getTotalParcelas()), lancamento.getValor(),
                fatura.getDataVencimento(), statusFatura(fatura));
    }

    private CronogramaItemResponse fromParcela(Parcela parcela) {
        return new CronogramaItemResponse(parcela.getId(), CronogramaItemResponse.Origem.PARCELA,
                parcela.getNumeroParcela(), parcela.getTotalParcelas(), parcela.getValor(),
                parcela.getDataVencimento(), statusParcela(parcela));
    }

    private CronogramaItemResponse.Status statusFatura(FaturaCartao fatura) {
        if (fatura.getStatus() == FaturaStatus.PAGA) return CronogramaItemResponse.Status.PAGO;
        BigDecimal pago = fatura.getValorPago() == null ? BigDecimal.ZERO : fatura.getValorPago();
        if (pago.signum() > 0) return CronogramaItemResponse.Status.PARCIAL;
        if (fatura.getStatus() == FaturaStatus.VENCIDA
                || fatura.getDataVencimento() != null && fatura.getDataVencimento().isBefore(LocalDate.now())) {
            return CronogramaItemResponse.Status.ATRASADO;
        }
        return CronogramaItemResponse.Status.PENDENTE;
    }

    private CronogramaItemResponse.Status statusParcela(Parcela parcela) {
        StatusPagamento status = parcela.getStatus();
        return switch (status) {
            case PAGO -> CronogramaItemResponse.Status.PAGO;
            case ATRASADO -> CronogramaItemResponse.Status.ATRASADO;
            case CANCELADO -> CronogramaItemResponse.Status.CANCELADO;
            case PENDENTE -> CronogramaItemResponse.Status.PENDENTE;
        };
    }

    private boolean isCompraCartao(Transacao transacao) {
        return transacao.getTipo() == TipoTransacao.SAIDA && transacao.getConta() != null
                && transacao.getConta().getTipo() == TipoConta.CREDITO;
    }

    private Integer numero(Integer valor) { return valor == null ? 1 : valor; }
}
