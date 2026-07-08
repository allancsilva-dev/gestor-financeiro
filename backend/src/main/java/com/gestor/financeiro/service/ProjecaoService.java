package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.ProjecaoMensalDto;
import com.gestor.financeiro.dto.ProjecaoResponse;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ProjecaoService {

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private ContaFixaRepository contaFixaRepository;

    @Autowired
    private ParcelaRepository parcelaRepository;

    @Autowired
    private FaturaCartaoRepository faturaCartaoRepository;

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
        BigDecimal total = BigDecimal.ZERO;
        List<ContaFixa> contas = contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        for (ContaFixa cf : contas) {
            if (cf.getDataProximoVencimento() != null &&
                    !cf.getDataProximoVencimento().isBefore(inicio) &&
                    !cf.getDataProximoVencimento().isAfter(fim) &&
                    cf.getStatus() != StatusPagamento.PAGO &&
                    cf.getStatus() != StatusPagamento.CANCELADO) {
                total = total.add(cf.getValorPlanejado() != null ? cf.getValorPlanejado() : BigDecimal.ZERO);
            }
        }
        return total;
    }

    private BigDecimal somarParcelasNoMes(Long usuarioId, LocalDate inicio, LocalDate fim) {
        BigDecimal total = BigDecimal.ZERO;
        List<Parcela> parcelas = parcelaRepository.findFuturasByUsuarioId(usuarioId, inicio, StatusPagamento.PAGO);
        for (Parcela p : parcelas) {
            if (p.getDataVencimento() != null &&
                    !p.getDataVencimento().isBefore(inicio) &&
                    !p.getDataVencimento().isAfter(fim)) {
                total = total.add(p.getValor() != null ? p.getValor() : BigDecimal.ZERO);
            }
        }
        return total;
    }

    private BigDecimal somarFaturasEmAberto(Long usuarioId, LocalDate inicio, LocalDate fim) {
        BigDecimal total = BigDecimal.ZERO;
        List<FaturaCartao> faturas = faturaCartaoRepository.findByUsuarioId(usuarioId);
        for (FaturaCartao f : faturas) {
            if (f.getStatus() == FaturaStatus.ABERTA &&
                    f.getDataVencimento() != null &&
                    !f.getDataVencimento().isBefore(inicio) &&
                    !f.getDataVencimento().isAfter(fim)) {
                total = total.add(f.getValorTotal() != null ? f.getValorTotal() : BigDecimal.ZERO);
            }
        }
        return total;
    }
}
