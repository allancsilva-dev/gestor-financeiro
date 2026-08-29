package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.TransacaoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RelatorioService {
    private final java.time.Clock clock;
    private final TransacaoRepository transacaoRepository;

    public RelatorioResponse gerarRelatorio(Long usuarioId, LocalDate inicio, LocalDate fim) {
        if (inicio == null) inicio = LocalDate.now(clock).withDayOfMonth(1);
        if (fim == null) fim = LocalDate.now(clock);

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
            BigDecimal valor = asBigDecimal(row[1]);
            if (valor != null) totalGastos = totalGastos.add(valor);
        }
        for (Object[] row : gastosRaw) {
            BigDecimal valor = asBigDecimal(row[1]);
            int pct = BigDecimal.ZERO.compareTo(totalGastos) == 0 ? 0
                    : valor.multiply(BigDecimal.valueOf(100)).divide(totalGastos, 0, RoundingMode.HALF_UP).intValue();
            gastosPorCategoria.add(new RelatorioCategoriaDto(
                    asLong(row[3]), String.valueOf(row[0]),
                    row[2] != null ? String.valueOf(row[2]) : "#6B7280",
                    asTexto(row[4]), valor, BigDecimal.valueOf(pct)));
        }

        List<Object[]> despesasRaw = transacaoRepository.findMaioresDespesas(
                usuarioId, inicio, fim, PageRequest.of(0, 10));
        List<RelatorioTransacaoDto> maioresDespesas = new ArrayList<>();
        for (Object[] row : despesasRaw) {
            String categoriaNome = row[4] != null ? String.valueOf(row[4]) : null;
            String categoriaCor = row[5] != null ? String.valueOf(row[5]) : "#6B7280";
            maioresDespesas.add(new RelatorioTransacaoDto(
                    asLong(row[0]), String.valueOf(row[1]), asBigDecimal(row[2]),
                    (LocalDate) row[3], categoriaNome, categoriaCor));
        }

        List<RelatorioContaDto> gastosPorConta = calcularGastosPorConta(usuarioId, inicio, fim, totalGastos);

        int totalTransacoes = (int) transacaoRepository
                .countAtivasByUsuarioIdAndPeriodo(usuarioId, inicio, fim);

        return new RelatorioResponse(inicio, fim, totalEntradas, totalSaidas, saldo, totalTransacoes,
                gastosPorCategoria, maioresDespesas, gastosPorConta);
    }

    private List<RelatorioContaDto> calcularGastosPorConta(Long usuarioId, LocalDate inicio, LocalDate fim, BigDecimal totalGastos) {
        List<Object[]> contasRaw = transacaoRepository.sumSaidasAgrupadoPorConta(
                usuarioId, inicio, fim, PageRequest.of(0, 8));
        List<RelatorioContaDto> gastosPorConta = new ArrayList<>();
        for (Object[] row : contasRaw) {
            BigDecimal valor = asBigDecimal(row[2]);
            int pct = BigDecimal.ZERO.compareTo(totalGastos) == 0 ? 0
                    : valor.multiply(BigDecimal.valueOf(100)).divide(totalGastos, 0, RoundingMode.HALF_UP).intValue();
            // Contract V41: toda conta e cartao de credito
            gastosPorConta.add(new RelatorioContaDto(asLong(row[0]), String.valueOf(row[1]),
                    "Cartão de Crédito", valor, BigDecimal.valueOf(pct)));
        }
        return gastosPorConta;
    }

    /**
     * Texto de coluna que pode vir NULL. `String.valueOf(null)` devolve a string
     * "null", que viajava no JSON e chegava à tela: o app mostrava o texto
     * "null" no lugar do ícone da categoria, porque `icone || '🏷️'` não trata
     * string não vazia. Categoria sem ícone tem de chegar como null de verdade.
     */
    private static String asTexto(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimalValue) {
            return bigDecimalValue;
        }
        if (value instanceof Number numberValue) {
            return new BigDecimal(numberValue.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }
}
