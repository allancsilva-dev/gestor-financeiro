package com.gestor.financeiro.service;

import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Servico canonico de leitura por politica (PR-F2-10, ADR-0010): as tres
 * visoes nomeadas — COMPRA, COMPETENCIA e CAIXA — saem daqui. Nenhum numero
 * exibido mistura politicas sem rotulo. Transferencias internas, reservas de
 * meta, investimento e backfill ficam fora de todas as visoes de consumo.
 */
@Service
public class VisaoFinanceiraService {

    private static final List<TipoFaturaLancamento> TIPOS_CONSUMO_CARTAO = List.of(
            TipoFaturaLancamento.COMPRA,
            TipoFaturaLancamento.AJUSTE,
            TipoFaturaLancamento.ESTORNO);

    private static final List<OrigemMovimentoCarteira> ORIGENS_CAIXA = List.of(
            OrigemMovimentoCarteira.TRANSACAO,
            OrigemMovimentoCarteira.PARCELA,
            OrigemMovimentoCarteira.CONTA_FIXA,
            OrigemMovimentoCarteira.FATURA_CARTAO,
            OrigemMovimentoCarteira.CARTEIRA_AJUSTE);

    private final TransacaoRepository transacaoRepository;
    private final FaturaLancamentoRepository faturaLancamentoRepository;
    private final MovimentoCarteiraRepository movimentoCarteiraRepository;

    public VisaoFinanceiraService(TransacaoRepository transacaoRepository,
                                  FaturaLancamentoRepository faturaLancamentoRepository,
                                  MovimentoCarteiraRepository movimentoCarteiraRepository) {
        this.transacaoRepository = transacaoRepository;
        this.faturaLancamentoRepository = faturaLancamentoRepository;
        this.movimentoCarteiraRepository = movimentoCarteiraRepository;
    }

    public record ResumoVisao(BigDecimal entradas, BigDecimal saidas) {
    }

    public record Visoes(ResumoVisao compra, ResumoVisao competencia, ResumoVisao caixa) {
    }

    public Visoes resumo(Long usuarioId, LocalDate inicio, LocalDate fim) {
        return new Visoes(
                visaoCompra(usuarioId, inicio, fim),
                visaoCompetencia(usuarioId, inicio, fim),
                visaoCaixa(usuarioId, inicio, fim));
    }

    /** COMPRA: valor total na data da compra, qualquer meio de pagamento. */
    private ResumoVisao visaoCompra(Long usuarioId, LocalDate inicio, LocalDate fim) {
        return new ResumoVisao(
                transacaoRepository.sumVisaoCompra(usuarioId, TipoTransacao.ENTRADA, inicio, fim),
                transacaoRepository.sumVisaoCompra(usuarioId, TipoTransacao.SAIDA, inicio, fim));
    }

    /**
     * COMPETENCIA: nao-cartao pela data da transacao + cartao pela data da
     * compra via FaturaLancamento (nunca pela data do pagamento da fatura).
     */
    private ResumoVisao visaoCompetencia(Long usuarioId, LocalDate inicio, LocalDate fim) {
        BigDecimal entradas = transacaoRepository.sumVisaoCompetenciaNaoCartao(
                usuarioId, TipoTransacao.ENTRADA, inicio, fim);
        BigDecimal saidasNaoCartao = transacaoRepository.sumVisaoCompetenciaNaoCartao(
                usuarioId, TipoTransacao.SAIDA, inicio, fim);
        BigDecimal consumoCartao = faturaLancamentoRepository.sumConsumoPorDataCompra(
                usuarioId, TIPOS_CONSUMO_CARTAO, inicio, fim);
        return new ResumoVisao(entradas, saidasNaoCartao.add(consumoCartao));
    }

    /** CAIXA: dinheiro que efetivamente se moveu nas contas ATIVO. */
    private ResumoVisao visaoCaixa(Long usuarioId, LocalDate inicio, LocalDate fim) {
        List<Object[]> rows = movimentoCarteiraRepository.sumVisaoCaixa(
                usuarioId, ORIGENS_CAIXA, inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay().minusNanos(1));
        Object[] row = rows.isEmpty() ? new Object[] {BigDecimal.ZERO, BigDecimal.ZERO} : rows.get(0);
        return new ResumoVisao((BigDecimal) row[0], (BigDecimal) row[1]);
    }
}
