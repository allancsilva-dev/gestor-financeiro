package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.ProjecaoMensalDto;
import com.gestor.financeiro.dto.ProjecaoResponse;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjecaoService {
    private final CarteiraRepository carteiraRepository;
    private final ContaFixaRepository contaFixaRepository;
    private final ParcelaRepository parcelaRepository;
    private final FaturaCartaoRepository faturaCartaoRepository;

    public ProjecaoResponse projetar(Long usuarioId, int mesesProjecao) {
        BigDecimal saldoAtual = carteiraRepository.sumSaldoByUsuarioId(usuarioId);
        if (saldoAtual == null) saldoAtual = BigDecimal.ZERO;

        YearMonth ymAtual = YearMonth.now();
        List<ProjecaoMensalDto> meses = new ArrayList<>();
        BigDecimal saldoAnterior = saldoAtual;

        LocalDate hoje = LocalDate.now();

        for (int i = 0; i < mesesProjecao; i++) {
            YearMonth ym = ymAtual.plusMonths(i);
            LocalDate inicioMes = ym.atDay(1);
            LocalDate fimMes = ym.atEndOfMonth();

            BigDecimal totalContasFixas = somarContasFixasNoMes(usuarioId, inicioMes, fimMes);
            BigDecimal totalParcelas = somarParcelasNoMes(usuarioId, inicioMes, fimMes);
            BigDecimal totalFaturas = somarFaturasEmAberto(usuarioId, inicioMes, fimMes);
            BigDecimal totalSaidas = totalContasFixas.add(totalParcelas).add(totalFaturas);
            BigDecimal saldoFinal = saldoAnterior.subtract(totalSaidas);

            boolean realizado = i == 0
                    || fimMes.isBefore(hoje)
                    || fimMes.isEqual(hoje);

            String periodo = ym.getMonth().getDisplayName(
                    java.time.format.TextStyle.SHORT, new Locale("pt", "BR")) + " " + ym.getYear();

            meses.add(new ProjecaoMensalDto(
                    periodo,
                    ym.getMonthValue(),
                    ym.getYear(),
                    saldoAnterior,
                    totalContasFixas,
                    totalParcelas,
                    totalFaturas,
                    totalSaidas,
                    saldoFinal,
                    realizado
            ));

            saldoAnterior = saldoFinal;
        }

        return new ProjecaoResponse(saldoAtual, meses);
    }

    private BigDecimal somarContasFixasNoMes(Long usuarioId, LocalDate inicio, LocalDate fim) {
        BigDecimal total = contaFixaRepository.somarPlanejadoNoPeriodo(
                usuarioId, inicio, fim, StatusPagamento.PAGO, StatusPagamento.CANCELADO);
        return total != null ? total : BigDecimal.ZERO;
    }

    private BigDecimal somarParcelasNoMes(Long usuarioId, LocalDate inicio, LocalDate fim) {
        BigDecimal total = parcelaRepository.somarValorNoPeriodo(
                usuarioId, inicio, fim, StatusPagamento.PAGO);
        return total != null ? total : BigDecimal.ZERO;
    }

    private BigDecimal somarFaturasEmAberto(Long usuarioId, LocalDate inicio, LocalDate fim) {
        BigDecimal total = faturaCartaoRepository.somarValorTotalPorStatusNoPeriodo(
                usuarioId, FaturaStatus.ABERTA, inicio, fim);
        return total != null ? total : BigDecimal.ZERO;
    }
}
