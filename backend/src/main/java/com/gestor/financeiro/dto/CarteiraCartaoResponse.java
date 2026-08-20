package com.gestor.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Visão consolidada de um cartão para a tela Carteira: um request alimenta o
 * carrossel inteiro, sem efeito colateral no banco.
 *
 * `emAberto` e `creditoAFavor` nunca são negativos — o saldo do passivo pareado
 * pode ficar credor (pagamento a maior, estorno) e a UI precisa dos dois lados
 * separados para não pintar crédito de vermelho.
 */
public record CarteiraCartaoResponse(
        Long cartaoId,
        String nome,
        String banco,
        String cor,
        String ultimosDigitos,
        String bandeira,
        Integer diaFechamento,
        Integer diaVencimento,
        BigDecimal limiteTotal,
        BigDecimal limiteDisponivel,
        BigDecimal emAberto,
        BigDecimal creditoAFavor,
        Integer percentualUso,
        LocalDate dataVencimentoAtual,
        Integer diasParaVencimento,
        LocalDate melhorDiaCompra,
        Integer diasParaMelhorDia,
        List<FaturaResumoDto> faturas
) {}
