package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.ProjecaoMensalDto;
import com.gestor.financeiro.dto.ProjecaoResponse;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class ProjecaoService {

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private ContaFixaRepository contaFixaRepository;

    @Autowired
    private ParcelaRepository parcelaRepository;

    public ProjecaoResponse projetar(Long usuarioId, int mesesProjecao) {
        BigDecimal saldoAtual = carteiraRepository.sumSaldoByUsuarioId(usuarioId);

        if (saldoAtual == null) saldoAtual = BigDecimal.ZERO;

        YearMonth ymAtual = YearMonth.now();
        List<ProjecaoMensalDto> meses = new ArrayList<>();
        BigDecimal saldoAnterior = saldoAtual;

        for (int i = 0; i < mesesProjecao; i++) {
            YearMonth ym = ymAtual.plusMonths(i);
            LocalDate inicioMes = ym.atDay(1);
            LocalDate fimMes = ym.atEndOfMonth();

            BigDecimal totalContasFixas = BigDecimal.ZERO;
            List<ContaFixa> contas = contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
            for (ContaFixa cf : contas) {
                if (cf.getDataProximoVencimento() != null &&
                        !cf.getDataProximoVencimento().isBefore(inicioMes) &&
                        !cf.getDataProximoVencimento().isAfter(fimMes) &&
                        cf.getStatus() != StatusPagamento.PAGO &&
                        cf.getStatus() != StatusPagamento.CANCELADO) {
                    totalContasFixas = totalContasFixas.add(
                            cf.getValorPlanejado() != null ? cf.getValorPlanejado() : BigDecimal.ZERO);
                }
            }

            BigDecimal totalParcelas = BigDecimal.ZERO;
            List<Parcela> parcelas = parcelaRepository.findFuturasByUsuarioId(
                    usuarioId, inicioMes, StatusPagamento.PAGO);
            for (Parcela p : parcelas) {
                if (p.getDataVencimento() != null &&
                        !p.getDataVencimento().isBefore(inicioMes) &&
                        !p.getDataVencimento().isAfter(fimMes)) {
                    totalParcelas = totalParcelas.add(
                            p.getValor() != null ? p.getValor() : BigDecimal.ZERO);
                }
            }

            BigDecimal totalSaidas = totalContasFixas.add(totalParcelas);
            BigDecimal saldoFinal = saldoAnterior.subtract(totalSaidas);

            String periodo = ym.getMonth().getDisplayName(TextStyle.SHORT, new Locale("pt", "BR")) + " " + ym.getYear();

            meses.add(new ProjecaoMensalDto(
                    periodo,
                    ym.getMonthValue(),
                    ym.getYear(),
                    saldoAnterior,
                    totalContasFixas,
                    totalParcelas,
                    totalSaidas,
                    saldoFinal
            ));

            saldoAnterior = saldoFinal;
        }

        return new ProjecaoResponse(saldoAtual, meses);
    }
}
