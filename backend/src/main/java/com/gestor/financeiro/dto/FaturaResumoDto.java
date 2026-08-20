package com.gestor.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fatura sem os lançamentos — o que a tela Carteira precisa para listar
 * "Fatura atual / Próxima fatura". O detalhe continua em
 * GET /api/v1/faturas/cartao/{cartaoId}.
 *
 * `id` nulo significa competência ainda não materializada: as datas são
 * calculadas e os valores são zero, sem criar nada no banco.
 */
public record FaturaResumoDto(
        Long id,
        int mes,
        int ano,
        LocalDate dataFechamento,
        LocalDate dataVencimento,
        BigDecimal valorTotal,
        BigDecimal valorPago,
        BigDecimal saldoRestante,
        String status
) {}
