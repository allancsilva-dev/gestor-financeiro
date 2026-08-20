package com.gestor.financeiro.util;

import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.model.enums.FaturaStatus;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Regras de calendário da fatura de cartão, extraídas de FaturaService para
 * serem reusadas por leituras que não podem entrar no fluxo transacional do
 * serviço (os GETs de FaturaService disparam rollover lazy — ver
 * liquidarFaturaAnterior). Comportamento idêntico ao original: FaturaService
 * delega para cá, não duplica.
 */
public final class FaturaDatas {

    private FaturaDatas() {
    }

    /** Dia do mês clampado a 1..lengthOfMonth; nulo significa fim do mês. */
    public static int diaValidoOuFimDoMes(Integer dia, YearMonth mes) {
        if (dia == null) {
            return mes.lengthOfMonth();
        }
        return Math.min(Math.max(dia, 1), mes.lengthOfMonth());
    }

    /** Compra depois do fechamento cai na competência seguinte. */
    public static YearMonth competencia(Conta conta, LocalDate dataCompra) {
        int diaFechamento = diaValidoOuFimDoMes(conta.getDiaFechamento(), YearMonth.from(dataCompra));
        YearMonth competencia = YearMonth.from(dataCompra);
        if (dataCompra.getDayOfMonth() > diaFechamento) {
            competencia = competencia.plusMonths(1);
        }
        return competencia;
    }

    public static LocalDate fechamento(Conta conta, YearMonth competencia) {
        return competencia.atDay(diaValidoOuFimDoMes(conta.getDiaFechamento(), competencia));
    }

    public static LocalDate vencimento(Conta conta, YearMonth competencia) {
        int diaFechamento = diaValidoOuFimDoMes(conta.getDiaFechamento(), competencia);
        int diaVencimento = conta.getDiaVencimento() != null ? conta.getDiaVencimento() : 10;
        YearMonth mesVencimento = diaVencimento <= diaFechamento ? competencia.plusMonths(1) : competencia;
        return mesVencimento.atDay(Math.min(diaVencimento, mesVencimento.lengthOfMonth()));
    }

    /**
     * Melhor dia de compra: o primeiro dia depois do fechamento, porque a
     * compra passa a cair na competência seguinte e ganha o prazo máximo.
     * Devolve a próxima ocorrência a partir de hoje (inclusive).
     */
    public static LocalDate melhorDiaCompra(Conta conta, LocalDate hoje) {
        LocalDate candidato = melhorDiaNoMes(conta, YearMonth.from(hoje));
        if (!candidato.isBefore(hoje)) {
            return candidato;
        }
        return melhorDiaNoMes(conta, YearMonth.from(hoje).plusMonths(1));
    }

    private static LocalDate melhorDiaNoMes(Conta conta, YearMonth mes) {
        int diaFechamento = diaValidoOuFimDoMes(conta.getDiaFechamento(), mes);
        // Fechamento no último dia do mês: o melhor dia é o dia 1 do mês seguinte.
        if (diaFechamento >= mes.lengthOfMonth()) {
            return mes.plusMonths(1).atDay(1);
        }
        return mes.atDay(diaFechamento + 1);
    }

    /**
     * Status derivado da fatura — mesma precedência do FaturaService:
     * PAGA vence tudo, depois VENCIDA, depois FECHADA, senão o persistido.
     */
    public static FaturaStatus statusAtual(FaturaCartao fatura, LocalDate hoje) {
        if (fatura.getStatus() == FaturaStatus.PAGA) {
            return FaturaStatus.PAGA;
        }
        if (fatura.getDataVencimento() != null && fatura.getDataVencimento().isBefore(hoje)) {
            return FaturaStatus.VENCIDA;
        }
        if (fatura.getDataFechamento() != null && fatura.getDataFechamento().isBefore(hoje)) {
            return FaturaStatus.FECHADA;
        }
        return fatura.getStatus();
    }
}
