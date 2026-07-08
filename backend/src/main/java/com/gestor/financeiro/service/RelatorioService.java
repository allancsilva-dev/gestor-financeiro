package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.TransacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RelatorioService {

    @Autowired
    private TransacaoRepository transacaoRepository;

    public RelatorioResponse gerarRelatorio(Long usuarioId, LocalDate inicio, LocalDate fim) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fim == null) fim = LocalDate.now();

        BigDecimal totalEntradas = transacaoRepository
                .sumValorEfetivoByUsuarioIdAndTipoAndDataBetween(usuarioId, TipoTransacao.ENTRADA, inicio, fim);
        BigDecimal totalSaidas = transacaoRepository
                .sumValorEfetivoByUsuarioIdAndTipoAndDataBetween(usuarioId, TipoTransacao.SAIDA, inicio, fim);

        if (totalEntradas == null) totalEntradas = BigDecimal.ZERO;
        if (totalSaidas == null) totalSaidas = BigDecimal.ZERO;

        BigDecimal saldo = totalEntradas.subtract(totalSaidas);

        List<Object[]> gastosRaw = transacaoRepository.sumValorEfetivoAgrupadoPorCategoria(
                usuarioId, TipoTransacao.SAIDA, inicio, fim);
        List<RelatorioCategoriaDto> gastosPorCategoria = new ArrayList<>();
        BigDecimal totalGastos = BigDecimal.ZERO;
        for (Object[] row : gastosRaw) {
            BigDecimal valor = (BigDecimal) row[1];
            if (valor != null) totalGastos = totalGastos.add(valor);
        }
        for (Object[] row : gastosRaw) {
            BigDecimal valor = (BigDecimal) row[1];
            int pct = BigDecimal.ZERO.compareTo(totalGastos) == 0 ? 0
                    : valor.multiply(BigDecimal.valueOf(100)).divide(totalGastos, 0, RoundingMode.HALF_UP).intValue();
            gastosPorCategoria.add(new RelatorioCategoriaDto(
                    null, (String) row[0], (String) row[2], "", valor, BigDecimal.valueOf(pct)));
        }

        List<Transacao> despesas = transacaoRepository
                .findByUsuarioIdAndDataBetween(usuarioId, inicio, fim);
        List<RelatorioTransacaoDto> maioresDespesas = despesas.stream()
                .filter(t -> t.getTipo() == TipoTransacao.SAIDA)
                .sorted((a, b) -> b.getValorTotal().compareTo(a.getValorTotal()))
                .limit(10)
                .map(t -> new RelatorioTransacaoDto(t.getId(), t.getDescricao(), t.getValorTotal(), t.getData(),
                        t.getCategoria() != null ? t.getCategoria().getNome() : null,
                        t.getCategoria() != null ? t.getCategoria().getCor() : "#6B7280"))
                .collect(Collectors.toList());

        List<RelatorioContaDto> gastosPorConta = calcularGastosPorConta(despesas, totalGastos);

        int totalTransacoes = (int) despesas.stream()
                .filter(t -> t.getTipo() == TipoTransacao.SAIDA)
                .count();

        return new RelatorioResponse(inicio, fim, totalEntradas, totalSaidas, saldo, totalTransacoes,
                gastosPorCategoria, maioresDespesas, gastosPorConta);
    }

    private List<RelatorioContaDto> calcularGastosPorConta(List<Transacao> despesas, BigDecimal totalGastos) {
        Map<Long, BigDecimal> porConta = new HashMap<>();
        Map<Long, String> nomeConta = new HashMap<>();
        Map<Long, String> tipoConta = new HashMap<>();

        for (Transacao t : despesas) {
            if (t.getTipo() != TipoTransacao.SAIDA) continue;
            if (t.getConta() == null) continue;
            Long contaId = t.getConta().getId();
            porConta.merge(contaId, t.getValorTotal() != null ? t.getValorTotal() : BigDecimal.ZERO, BigDecimal::add);
            nomeConta.putIfAbsent(contaId, t.getConta().getNome());
            tipoConta.putIfAbsent(contaId, t.getConta().getTipo().getDescricao());
        }

        return porConta.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(8)
                .map(e -> {
                    int pct = BigDecimal.ZERO.compareTo(totalGastos) == 0 ? 0
                            : e.getValue().multiply(BigDecimal.valueOf(100)).divide(totalGastos, 0, RoundingMode.HALF_UP).intValue();
                    return new RelatorioContaDto(e.getKey(), nomeConta.get(e.getKey()),
                            tipoConta.get(e.getKey()), e.getValue(), BigDecimal.valueOf(pct));
                })
                .collect(Collectors.toList());
    }
}
