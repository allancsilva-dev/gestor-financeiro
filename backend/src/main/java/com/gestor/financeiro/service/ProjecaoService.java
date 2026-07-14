package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.ProjecaoMensalDto;
import com.gestor.financeiro.dto.ProjecaoResponse;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.model.ContaFixa;
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

            BigDecimal totalEntradas = somarRecorrenciasNoMes(usuarioId, ym, TipoTransacao.ENTRADA);
            BigDecimal totalContasFixas = somarRecorrenciasNoMes(usuarioId, ym, TipoTransacao.SAIDA);
            BigDecimal totalParcelas = somarParcelasNoMes(usuarioId, inicioMes, fimMes);
            BigDecimal totalFaturas = somarFaturasEmAberto(usuarioId, inicioMes, fimMes);
            BigDecimal totalSaidas = totalContasFixas.add(totalParcelas).add(totalFaturas);
            BigDecimal saldoFinal = saldoAnterior.add(totalEntradas).subtract(totalSaidas);

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
                    totalEntradas,
                    totalSaidas,
                    saldoFinal,
                    realizado
            ));

            saldoAnterior = saldoFinal;
        }

        return new ProjecaoResponse(saldoAtual, meses);
    }

    private BigDecimal somarRecorrenciasNoMes(Long usuarioId, YearMonth mes, TipoTransacao tipo) {
        return contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId).stream()
                .filter(c -> (c.getTipo() == null ? TipoTransacao.SAIDA : c.getTipo()) == tipo)
                .filter(c -> c.getStatus() != StatusPagamento.PAGO && c.getStatus() != StatusPagamento.CANCELADO)
                .filter(c -> ocorreNoMes(c, mes))
                .map(ContaFixa::getValorPlanejado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean ocorreNoMes(ContaFixa conta, YearMonth mes) {
        if (conta.getDataProximoVencimento() == null) return false;
        YearMonth primeiro = YearMonth.from(conta.getDataProximoVencimento());
        if (Boolean.TRUE.equals(conta.getRecorrente())) return !mes.isBefore(primeiro);
        return mes.equals(primeiro);
    }

    private BigDecimal somarParcelasNoMes(Long usuarioId, LocalDate inicio, LocalDate fim) {
        BigDecimal total = parcelaRepository.somarValorNoPeriodo(
                usuarioId, inicio, fim, StatusPagamento.PAGO, TipoTransacao.SAIDA, TipoConta.CREDITO);
        return total != null ? total : BigDecimal.ZERO;
    }

    private BigDecimal somarFaturasEmAberto(Long usuarioId, LocalDate inicio, LocalDate fim) {
        BigDecimal total = faturaCartaoRepository.somarSaldoRestanteNoPeriodo(
                usuarioId, FaturaStatus.PAGA, inicio, fim);
        return total != null ? total : BigDecimal.ZERO;
    }
}
