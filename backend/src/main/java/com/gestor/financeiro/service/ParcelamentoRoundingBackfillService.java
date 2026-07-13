package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.FaturaLancamento;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.repository.ParcelaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.service.ParcelamentoRoundingBackfillResult.Alvo;
import com.gestor.financeiro.service.ParcelamentoRoundingBackfillResult.Detalhe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ParcelamentoRoundingBackfillService {

    private final TransacaoRepository transacaoRepository;
    private final ParcelaRepository parcelaRepository;
    private final FaturaLancamentoRepository faturaLancamentoRepository;
    private final FaturaCartaoRepository faturaCartaoRepository;
    private final ContaRepository contaRepository;

    @Transactional(readOnly = true)
    public ParcelamentoRoundingBackfillResult diagnosticarUsuario(Long usuarioId) {
        return reconciliarUsuario(usuarioId, true);
    }

    @Transactional
    public ParcelamentoRoundingBackfillResult corrigirUsuario(Long usuarioId) {
        return reconciliarUsuario(usuarioId, false);
    }

    private ParcelamentoRoundingBackfillResult reconciliarUsuario(Long usuarioId, boolean dryRun) {
        Set<Long> transacoesComResiduoEmParcelas = new LinkedHashSet<>(
                parcelaRepository.findTransacaoIdsComResiduoArredondamentoByUsuarioId(usuarioId));
        Set<Long> transacoesComResiduoEmFaturas = new LinkedHashSet<>(
                faturaLancamentoRepository.findTransacaoIdsComResiduoArredondamentoSeguroByUsuarioId(
                        usuarioId, TipoFaturaLancamento.COMPRA));

        int parcelasCorrigidas = 0;
        int lancamentosCorrigidos = 0;
        List<Detalhe> detalhes = new ArrayList<>();

        for (Long transacaoId : transacoesComResiduoEmParcelas) {
            Transacao transacao = buscarTransacao(usuarioId, transacaoId);
            BigDecimal somaAntes = parcelaRepository.somarValorByTransacaoId(transacaoId);
            BigDecimal diferenca = transacao.getValorTotal().subtract(somaAntes);
            Parcela ultima = parcelaRepository.findTopByTransacaoIdOrderByNumeroParcelaDescIdDesc(transacaoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ultima parcela nao encontrada"));

            detalhes.add(new Detalhe(transacaoId, Alvo.PARCELA, transacao.getValorTotal(), somaAntes,
                    diferenca, ultima.getId()));

            if (!dryRun && diferenca.signum() != 0) {
                ultima.setValor(ultima.getValor().add(diferenca));
                parcelaRepository.save(ultima);
                parcelasCorrigidas++;
            }
        }

        for (Long transacaoId : transacoesComResiduoEmFaturas) {
            Transacao transacao = buscarTransacao(usuarioId, transacaoId);
            BigDecimal somaAntes = faturaLancamentoRepository.somarValorByTransacaoIdAndTipo(
                    transacaoId, TipoFaturaLancamento.COMPRA);
            BigDecimal diferenca = transacao.getValorTotal().subtract(somaAntes);
            FaturaLancamento ultimo = faturaLancamentoRepository
                    .findTopByTransacaoIdAndTipoOrderByParcelaNumeroDescIdDesc(
                            transacaoId, TipoFaturaLancamento.COMPRA)
                    .orElseThrow(() -> new ResourceNotFoundException("Ultimo lancamento de fatura nao encontrado"));

            detalhes.add(new Detalhe(transacaoId, Alvo.FATURA_LANCAMENTO, transacao.getValorTotal(), somaAntes,
                    diferenca, ultimo.getId()));

            if (!dryRun && diferenca.signum() != 0) {
                ultimo.setValor(ultimo.getValor().add(diferenca));
                faturaLancamentoRepository.save(ultimo);

                ultimo.getFatura().setValorTotal(zeroIfNull(ultimo.getFatura().getValorTotal()).add(diferenca));
                faturaCartaoRepository.save(ultimo.getFatura());

                if (ultimo.getFatura().getStatus() != FaturaStatus.PAGA) {
                    Conta conta = ultimo.getFatura().getConta();
                    conta.setValorGasto(zeroIfNull(conta.getValorGasto()).add(diferenca));
                    contaRepository.save(conta);
                }

                lancamentosCorrigidos++;
            }
        }

        return new ParcelamentoRoundingBackfillResult(
                dryRun,
                transacoesComResiduoEmParcelas.size(),
                transacoesComResiduoEmFaturas.size(),
                parcelasCorrigidas,
                lancamentosCorrigidos,
                detalhes
        );
    }

    private Transacao buscarTransacao(Long usuarioId, Long transacaoId) {
        return transacaoRepository.findByIdAndUsuarioId(transacaoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Transacao nao encontrada"));
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
