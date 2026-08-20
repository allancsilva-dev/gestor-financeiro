package com.gestor.financeiro;

import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.util.FaturaDatas;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Calendário da fatura. Casos de borda que o cliente não pode recalcular
 * sozinho: mês curto, fechamento no último dia e virada de ano.
 */
class FaturaDatasTest {

    private Conta cartao(Integer diaFechamento, Integer diaVencimento) {
        Conta c = new Conta();
        c.setDiaFechamento(diaFechamento);
        c.setDiaVencimento(diaVencimento);
        return c;
    }

    @Test
    void melhorDiaEODiaSeguinteAoFechamento() {
        // Fecha dia 19 -> comprar dia 20 joga a compra para a competência seguinte.
        assertEquals(LocalDate.of(2026, 8, 20),
                FaturaDatas.melhorDiaCompra(cartao(19, 27), LocalDate.of(2026, 8, 5)));
    }

    @Test
    void melhorDiaJaPassadoPulaParaOMesSeguinte() {
        assertEquals(LocalDate.of(2026, 9, 20),
                FaturaDatas.melhorDiaCompra(cartao(19, 27), LocalDate.of(2026, 8, 21)));
    }

    @Test
    void melhorDiaNoProprioDiaContaComoHoje() {
        assertEquals(LocalDate.of(2026, 8, 20),
                FaturaDatas.melhorDiaCompra(cartao(19, 27), LocalDate.of(2026, 8, 20)));
    }

    @Test
    void fechamentoDia31EmFevereiroCaiNoDia1DeMarco() {
        // Fevereiro tem 28 dias: o fechamento clampa para 28 (último dia), então
        // o melhor dia é o primeiro dia do mês seguinte, não "29 de fevereiro".
        assertEquals(LocalDate.of(2026, 3, 1),
                FaturaDatas.melhorDiaCompra(cartao(31, 10), LocalDate.of(2026, 2, 10)));
    }

    @Test
    void fechamentoDia31EmDezembroViraOAno() {
        assertEquals(LocalDate.of(2027, 1, 1),
                FaturaDatas.melhorDiaCompra(cartao(31, 10), LocalDate.of(2026, 12, 5)));
    }

    @Test
    void fechamentoNuloUsaFimDoMes() {
        assertEquals(LocalDate.of(2026, 9, 1),
                FaturaDatas.melhorDiaCompra(cartao(null, 10), LocalDate.of(2026, 8, 5)));
    }

    @Test
    void vencimentoAntesDoFechamentoCaiNoMesSeguinte() {
        // Fecha 19, vence 27: vencimento é no próprio mês da competência.
        assertEquals(LocalDate.of(2026, 8, 27),
                FaturaDatas.vencimento(cartao(19, 27), YearMonth.of(2026, 8)));
        // Fecha 27, vence 10: vencimento vai para o mês seguinte.
        assertEquals(LocalDate.of(2026, 9, 10),
                FaturaDatas.vencimento(cartao(27, 10), YearMonth.of(2026, 8)));
    }

    @Test
    void diaDeVencimentoAlemDoFimDoMesClampa() {
        assertEquals(LocalDate.of(2026, 2, 28),
                FaturaDatas.vencimento(cartao(10, 31), YearMonth.of(2026, 2)));
    }

    @Test
    void fechamentoClampaAoTamanhoDoMes() {
        assertEquals(LocalDate.of(2026, 2, 28),
                FaturaDatas.fechamento(cartao(31, 10), YearMonth.of(2026, 2)));
    }

    @Test
    void competenciaAvancaDepoisDoFechamento() {
        assertEquals(YearMonth.of(2026, 8),
                FaturaDatas.competencia(cartao(19, 27), LocalDate.of(2026, 8, 19)));
        assertEquals(YearMonth.of(2026, 9),
                FaturaDatas.competencia(cartao(19, 27), LocalDate.of(2026, 8, 20)));
    }
}
