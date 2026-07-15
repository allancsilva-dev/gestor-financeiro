package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.*;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FaturaService {

    private static final Logger log = LoggerFactory.getLogger(FaturaService.class);

    private final FaturaCartaoRepository faturaRepository;
    private final FaturaLancamentoRepository faturaLancamentoRepository;
    private final ContaRepository contaRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CarteiraRepository carteiraRepository;
    private final MovimentoCarteiraRepository movimentoCarteiraRepository;
    private final LedgerService ledgerService;
    private final java.time.Clock clock;

    public FaturaService(FaturaCartaoRepository faturaRepository,
                         FaturaLancamentoRepository faturaLancamentoRepository,
                         ContaRepository contaRepository,
                         TransacaoRepository transacaoRepository,
                         UsuarioRepository usuarioRepository,
                         CarteiraRepository carteiraRepository,
                         MovimentoCarteiraRepository movimentoCarteiraRepository,
                         LedgerService ledgerService,
                         java.time.Clock clock) {
        this.faturaRepository = faturaRepository;
        this.faturaLancamentoRepository = faturaLancamentoRepository;
        this.contaRepository = contaRepository;
        this.transacaoRepository = transacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.carteiraRepository = carteiraRepository;
        this.movimentoCarteiraRepository = movimentoCarteiraRepository;
        this.ledgerService = ledgerService;
        this.clock = clock;
    }

    // Lazy na leitura (BACKLOG-0054/0059): consultar uma fatura tambem liquida o rollover
    // de credito/saldo devedor da fatura do mes anterior, se ainda nao processado. Por isso
    // este metodo (antes somente leitura) agora e transacional -- ver liquidarFaturaAnterior.
    @Transactional
    public FaturaResponse buscarAtual(Long usuarioId, Long contaId) {
        Conta conta = validarContaCredito(usuarioId, contaId);
        YearMonth ym = YearMonth.now(clock);

        liquidarFaturaAnterior(usuarioId, conta, ym);

        Optional<FaturaCartao> existente = faturaRepository.findByContaIdAndMesAndAno(
                contaId, ym.getMonthValue(), ym.getYear());

        if (existente.isPresent()) {
            return toResponse(existente.get(), usuarioId, conta);
        }

        return toResponseVazia(conta, ym.getMonthValue(), ym.getYear());
    }

    @Transactional
    public FaturaResponse buscarPorMes(Long usuarioId, Long contaId, Integer mes, Integer ano) {
        Conta conta = validarContaCredito(usuarioId, contaId);

        liquidarFaturaAnterior(usuarioId, conta, YearMonth.of(ano, mes));

        Optional<FaturaCartao> existente = faturaRepository.findByContaIdAndMesAndAno(contaId, mes, ano);

        if (existente.isPresent()) {
            return toResponse(existente.get(), usuarioId, conta);
        }

        return toResponseVazia(conta, mes, ano);
    }

    @Transactional
    public FaturaResponse criarOuBuscarFatura(Long usuarioId, Long contaId, Integer mes, Integer ano) {
        Conta conta = validarContaCredito(usuarioId, contaId);
        YearMonth competencia = YearMonth.of(ano, mes);

        liquidarFaturaAnterior(usuarioId, conta, competencia);

        FaturaCartao fatura = criarOuBuscarFaturaEntidade(usuarioId, conta, competencia);

        return toResponse(fatura, usuarioId, conta);
    }

    @Transactional
    public FaturaResponse pagarFatura(Long usuarioId, Long faturaId, BigDecimal valor, Long carteiraId) {
        return pagarFatura(usuarioId, faturaId, valor, carteiraId, null);
    }

    @Transactional
    public FaturaResponse pagarFatura(Long usuarioId, Long faturaId, BigDecimal valor, Long carteiraId,
                                      String idempotencyKey) {
        FaturaCartao fatura = faturaRepository.findWithLockByIdAndUsuarioId(faturaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));
        Conta conta = fatura.getConta();

        if (hasText(idempotencyKey)) {
            String chaveRequest = chaveIdempotenciaPagamento(fatura.getId(), BigDecimal.ZERO, idempotencyKey);
            if (movimentoCarteiraRepository.findByUsuarioIdAndIdempotencyKey(usuarioId, chaveRequest).isPresent()) {
                return toResponse(fatura, usuarioId, conta);
            }
        }

        if (fatura.getStatus() == FaturaStatus.PAGA) {
            throw new BusinessException("Fatura já está paga");
        }

        if (carteiraId == null) {
            throw new BusinessException("Carteira de pagamento é obrigatória");
        }

        List<FaturaLancamento> lancamentosFatura =
                faturaLancamentoRepository.findByFaturaIdOrderByDataCompraAscIdAsc(fatura.getId());
        BigDecimal total = lancamentosFatura.stream()
                .map(FaturaLancamento::getValor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Faturas anteriores ao V17 têm valorTotal mas nenhum lançamento
        if (lancamentosFatura.isEmpty() && fatura.getValorTotal() != null) {
            total = fatura.getValorTotal();
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Fatura sem valor para pagamento");
        }

        BigDecimal valorPagoAtual = fatura.getValorPago() != null ? fatura.getValorPago() : BigDecimal.ZERO;
        BigDecimal saldoRestante = total.subtract(valorPagoAtual);
        if (saldoRestante.compareTo(BigDecimal.ZERO) <= 0) {
            fatura.setStatus(FaturaStatus.PAGA);
            if (fatura.getDataPagamento() == null) {
                fatura.setDataPagamento(LocalDate.now(clock));
            }
            faturaRepository.save(fatura);
            return toResponse(fatura, usuarioId, conta);
        }

        if (valor.compareTo(saldoRestante) > 0) {
            throw new BusinessException("Valor de pagamento maior que o saldo restante da fatura");
        }

        Carteira carteira = carteiraRepository.findByIdAndUsuarioId(carteiraId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

        BigDecimal novoValorPago = valorPagoAtual.add(valor);
        String chaveIdempotencia = chaveIdempotenciaPagamento(fatura.getId(), novoValorPago, idempotencyKey);
        if (hasText(chaveIdempotencia)
                && movimentoCarteiraRepository.findByUsuarioIdAndIdempotencyKey(usuarioId, chaveIdempotencia).isPresent()) {
            return toResponse(fatura, usuarioId, conta);
        }

        ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                usuarioId,
                carteira.getId(),
                TipoMovimentoCarteira.SAIDA,
                valor,
                RegistrarMovimentoCommand.Direcao.SAIDA,
                OrigemMovimentoCarteira.FATURA_CARTAO,
                "FATURA_CARTAO",
                fatura.getId(),
                "Pagamento fatura " + conta.getNome() + " " + fatura.getMes() + "/" + fatura.getAno(),
                chaveIdempotencia,
                null,
                false
        ));

        fatura.setValorPago(novoValorPago);
        if (novoValorPago.compareTo(total) >= 0) {
            fatura.setDataPagamento(LocalDate.now(clock));
            fatura.setStatus(FaturaStatus.PAGA);
        }
        fatura.setValorTotal(total);
        faturaRepository.save(fatura);

        BigDecimal valorGastoAtual = conta.getValorGasto() != null ? conta.getValorGasto() : BigDecimal.ZERO;
        BigDecimal novoValorGasto = valorGastoAtual.subtract(valor);
        conta.setValorGasto(novoValorGasto.signum() < 0 ? BigDecimal.ZERO : novoValorGasto);
        contaRepository.save(conta);

        return toResponse(fatura, usuarioId, conta);
    }

    @Transactional
    public void registrarCompraCartao(Transacao transacao, Long usuarioId) {
        if (!isCompraCartao(transacao)) {
            return;
        }

        if (!faturaLancamentoRepository.findByTransacaoId(transacao.getId()).isEmpty()) {
            return;
        }

        int totalParcelas = Boolean.TRUE.equals(transacao.getParcelado())
                && transacao.getTotalParcelas() != null
                && transacao.getTotalParcelas() > 1
                ? transacao.getTotalParcelas()
                : 1;

        BigDecimal valorParcela = totalParcelas > 1
                ? transacao.getValorTotal().divide(BigDecimal.valueOf(totalParcelas), 2, RoundingMode.HALF_UP)
                : transacao.getValorTotal();

        for (int parcela = 1; parcela <= totalParcelas; parcela++) {
            // Última parcela absorve a diferença de arredondamento para fechar o valor total
            BigDecimal valorLancamento = parcela == totalParcelas
                    ? transacao.getValorTotal().subtract(
                            valorParcela.multiply(BigDecimal.valueOf(totalParcelas - 1L)))
                    : valorParcela;
            LocalDate dataReferencia = totalParcelas > 1
                    ? transacao.getData().plusMonths(parcela - 1L)
                    : transacao.getData();
            YearMonth competencia = calcularCompetenciaFatura(transacao.getConta(), dataReferencia);
            FaturaCartao fatura = faturaDisponivelParaLancamento(usuarioId, transacao.getConta(), competencia);

            criarLancamento(fatura, transacao, transacao.getDescricao(), valorLancamento,
                    transacao.getData(),
                    totalParcelas > 1 ? parcela : null,
                    totalParcelas > 1 ? totalParcelas : null,
                    TipoFaturaLancamento.COMPRA);
        }
    }

    @Transactional
    public void cancelarCompraCartao(Transacao transacao, Long usuarioId) {
        if (transacao == null || transacao.getId() == null) {
            return;
        }

        BigDecimal somaFaturasPagas = BigDecimal.ZERO;
        for (FaturaLancamento lancamento : faturaLancamentoRepository.findByTransacaoId(transacao.getId())) {
            if (lancamento.getFatura().getStatus() == FaturaStatus.PAGA) {
                somaFaturasPagas = somaFaturasPagas.add(lancamento.getValor());
            } else {
                removerLancamentoDeFaturaAberta(lancamento);
            }
        }
        faturaLancamentoRepository.flush();

        // Fatura paga é imutável: a parte já paga vira crédito (estorno) na próxima fatura aberta
        if (somaFaturasPagas.signum() != 0) {
            FaturaCartao proxima = faturaDisponivelParaLancamento(
                    usuarioId, transacao.getConta(), YearMonth.now(clock));
            criarLancamento(proxima, transacao, "Estorno: " + transacao.getDescricao(),
                    somaFaturasPagas.negate(), LocalDate.now(clock), null, null,
                    TipoFaturaLancamento.ESTORNO);
        }
    }

    /**
     * Refaz os lançamentos de uma compra de cartão após edição de valor/data.
     * Lançamentos em faturas abertas são recriados; faturas pagas são imutáveis e a
     * diferença sobre a parte paga entra como lançamento de ajuste na próxima fatura aberta.
     */
    @Transactional
    public void ressincronizarCompraCartao(Transacao transacao, Long usuarioId) {
        if (!isCompraCartao(transacao)) {
            return;
        }

        int totalParcelas = Boolean.TRUE.equals(transacao.getParcelado())
                && transacao.getTotalParcelas() != null
                && transacao.getTotalParcelas() > 1
                ? transacao.getTotalParcelas()
                : 1;

        BigDecimal somaFaturasPagas = BigDecimal.ZERO;
        List<Integer> parcelasPagas = new ArrayList<>();
        boolean compraAVistaPaga = false;

        for (FaturaLancamento lancamento : faturaLancamentoRepository.findByTransacaoId(transacao.getId())) {
            if (lancamento.getFatura().getStatus() == FaturaStatus.PAGA) {
                somaFaturasPagas = somaFaturasPagas.add(lancamento.getValor());
                if (lancamento.getTipo() == TipoFaturaLancamento.COMPRA) {
                    if (lancamento.getParcelaNumero() != null) {
                        parcelasPagas.add(lancamento.getParcelaNumero());
                    } else {
                        compraAVistaPaga = true;
                    }
                }
            } else {
                removerLancamentoDeFaturaAberta(lancamento);
            }
        }
        faturaLancamentoRepository.flush();

        List<Integer> parcelasEmAberto = new ArrayList<>();
        if (totalParcelas > 1) {
            for (int i = 1; i <= totalParcelas; i++) {
                if (!parcelasPagas.contains(i)) {
                    parcelasEmAberto.add(i);
                }
            }
        } else if (!compraAVistaPaga) {
            parcelasEmAberto.add(1);
        }

        List<BigDecimal> cronograma = calcularCronogramaParcelas(transacao.getValorTotal(), totalParcelas);
        if (!parcelasEmAberto.isEmpty()) {
            for (Integer numero : parcelasEmAberto) {
                BigDecimal valor = cronograma.get(numero - 1);
                LocalDate dataReferencia = totalParcelas > 1
                        ? transacao.getData().plusMonths(numero - 1L)
                        : transacao.getData();
                YearMonth competencia = calcularCompetenciaFatura(transacao.getConta(), dataReferencia);
                FaturaCartao fatura = faturaDisponivelParaLancamento(
                        usuarioId, transacao.getConta(), competencia);

                criarLancamento(fatura, transacao, transacao.getDescricao(), valor,
                        transacao.getData(),
                        totalParcelas > 1 ? numero : null,
                        totalParcelas > 1 ? totalParcelas : null,
                        TipoFaturaLancamento.COMPRA);
            }
        }

        BigDecimal valorEsperadoPago = totalParcelas > 1
                ? parcelasPagas.stream()
                        .map(numero -> cronograma.get(numero - 1))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : (compraAVistaPaga ? transacao.getValorTotal() : BigDecimal.ZERO);
        BigDecimal ajustePago = valorEsperadoPago.subtract(somaFaturasPagas);
        if (ajustePago.signum() != 0) {
            // Fatura paga é imutável: diferença do cronograma recalculado vira ajuste em fatura aberta.
            FaturaCartao proxima = faturaDisponivelParaLancamento(
                    usuarioId, transacao.getConta(), YearMonth.now(clock));
            criarLancamento(proxima, transacao, "Ajuste: " + transacao.getDescricao(),
                    ajustePago, LocalDate.now(clock), null, null, TipoFaturaLancamento.AJUSTE);
        }
    }

    private FaturaCartao criarFaturaVazia(Long usuarioId, Conta conta, Integer mes, Integer ano) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        YearMonth ym = YearMonth.of(ano, mes);

        FaturaCartao fatura = new FaturaCartao();
        fatura.setUsuario(usuario);
        fatura.setConta(conta);
        fatura.setMes(mes);
        fatura.setAno(ano);
        fatura.setDataFechamento(calcularDataFechamento(conta, ym));
        fatura.setDataVencimento(calcularDataVencimento(conta, ym));
        fatura.setStatus(FaturaStatus.ABERTA);

        return faturaRepository.save(fatura);
    }

    private FaturaResponse toResponseVazia(Conta conta, Integer mes, Integer ano) {
        YearMonth ym = YearMonth.of(ano, mes);

        return new FaturaResponse(
                null,
                conta.getId(),
                conta.getNome(),
                mes,
                ano,
                calcularDataFechamento(conta, ym),
                calcularDataVencimento(conta, ym),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                FaturaStatus.ABERTA.name(),
                null,
                List.of()
        );
    }

    private FaturaResponse toResponse(FaturaCartao fatura, Long usuarioId, Conta conta) {
        YearMonth ym = YearMonth.of(fatura.getAno(), fatura.getMes());

        BigDecimal total = BigDecimal.ZERO;
        List<FaturaLancamentoDto> lancamentos = new ArrayList<>();

        for (FaturaLancamento lancamento : faturaLancamentoRepository.findByFaturaIdOrderByDataCompraAscIdAsc(fatura.getId())) {
            // Lancamentos de rollover (CREDITO_ANTERIOR/SALDO_DEVEDOR_ANTERIOR) nao tem
            // transacao associada (ver V25) -- id e categoria ficam nulos/default.
            Transacao t = lancamento.getTransacao();
            BigDecimal valor = lancamento.getValor() != null ? lancamento.getValor() : BigDecimal.ZERO;
            total = total.add(valor);

            Categoria cat = t != null ? t.getCategoria() : null;
            lancamentos.add(new FaturaLancamentoDto(
                    t != null ? t.getId() : null,
                    lancamento.getDescricao(),
                    valor,
                    lancamento.getDataCompra(),
                    cat != null ? cat.getId() : null,
                    cat != null ? cat.getNome() : null,
                    cat != null ? cat.getCor() : "#6B7280",
                    cat != null ? cat.getIcone() : "",
                    lancamento.getParcelaNumero(),
                    lancamento.getTotalParcelas(),
                    lancamento.getTipo() != null
                            ? lancamento.getTipo().name()
                            : TipoFaturaLancamento.COMPRA.name()
            ));
        }

        // Soma dos lançamentos é a fonte da verdade (é o que pagarFatura valida);
        // valorTotal persistido só cobre faturas antigas sem lançamentos (pré-V17)
        BigDecimal valorTotal = lancamentos.isEmpty()
                ? (fatura.getValorTotal() != null ? fatura.getValorTotal() : BigDecimal.ZERO)
                : total;

        return new FaturaResponse(
                fatura.getId(),
                conta.getId(),
                conta.getNome(),
                fatura.getMes(),
                fatura.getAno(),
                fatura.getDataFechamento(),
                fatura.getDataVencimento(),
                valorTotal,
                fatura.getValorPago(),
                statusAtual(fatura).name(),
                fatura.getDataPagamento(),
                lancamentos
        );
    }

    private Conta validarContaCredito(Long usuarioId, Long contaId) {
        Conta conta = contaRepository.findByIdAndUsuarioId(contaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));

        if (conta.getTipo() != TipoConta.CREDITO) {
            throw new BusinessException("A conta informada não é um cartão de crédito");
        }

        return conta;
    }

    private FaturaCartao criarOuBuscarFaturaEntidade(Long usuarioId, Conta conta, YearMonth competencia) {
        return faturaRepository.findByContaIdAndMesAndAno(
                        conta.getId(), competencia.getMonthValue(), competencia.getYear())
                .orElseGet(() -> criarFaturaVazia(
                        usuarioId, conta, competencia.getMonthValue(), competencia.getYear()));
    }

    // Compra retroativa não pode cair em fatura já paga: rola para a próxima competência em aberto
    private FaturaCartao faturaDisponivelParaLancamento(Long usuarioId, Conta conta, YearMonth competencia) {
        YearMonth alvo = competencia;
        for (int i = 0; i < 24; i++) {
            FaturaCartao fatura = criarOuBuscarFaturaEntidade(usuarioId, conta, alvo);
            if (fatura.getStatus() != FaturaStatus.PAGA) {
                return fatura;
            }
            alvo = alvo.plusMonths(1);
        }
        throw new BusinessException("Não foi encontrada fatura em aberto para lançar a compra");
    }

    // Profundidade máxima da caminhada retroativa da cadeia de rollover, coerente com o limite
    // de 24 meses já usado por faturaDisponivelParaLancamento (busca para a frente).
    private static final int LIMITE_CADEIA_ROLLOVER_MESES = 24;

    /**
     * Rollover de crédito/saldo devedor de fatura de cartão (BACKLOG-0054/0059).
     * Ver docs/SYSTEM_OVERVIEW.md, seção "Regra de produto: credito de fatura e saldo devedor
     * rolado". Lazy na leitura: chamado a partir de buscarAtual/buscarPorMes/criarOuBuscarFatura
     * ao materializar a competência M, e liquida a fatura de M-1 do mesmo cartão, se fechada e
     * ainda não processada. Idempotente (exists-check + lock pessimista + unique index como
     * backstop). Nunca cria MovimentoCarteira: só move valor entre faturas do mesmo cartão.
     */
    private void liquidarFaturaAnterior(Long usuarioId, Conta conta, YearMonth competenciaAtual) {
        liquidarFaturaAnterior(usuarioId, conta, competenciaAtual, 0);
    }

    // Decisão de produto (2026-07-11): a cadeia deve estar completa ao ler o mês atual, mesmo
    // após vários meses de inatividade -- não basta liquidar M-1 -> M, é preciso garantir que
    // M-1 já tenha herdado tudo de M-2, que M-2 já tenha herdado de M-3, etc. Por isso a
    // caminhada anda para trás recursivamente ANTES de liquidar a competência recebida.
    //
    // Terminação: cada chamada recursiva opera sobre `competenciaAnterior` (competenciaAtual
    // menos 1 mês) e `profundidade + 1`; a competência decresce estritamente a cada nível, então
    // a mesma competência nunca é revisitada na pilha (sem ciclo possível). A recursão para
    // quando (a) a fatura de M-1 não existe no banco (não materializamos fatura retroativa que
    // nunca existiu), (b) o guard de idempotência (`existsByFaturaOrigemId`, verificado logo após
    // o lock) já indica que aquele elo da cadeia foi processado, ou (c) `profundidade` atinge
    // LIMITE_CADEIA_ROLLOVER_MESES, um teto duro independente dos dados. `faturaDisponivelParaLancamento`
    // (usado só para achar o destino, à frente) não chama `liquidarFaturaAnterior`, então não há
    // recursão mútua entre a caminhada para trás e a busca de destino para a frente.
    private void liquidarFaturaAnterior(Long usuarioId, Conta conta, YearMonth competenciaAtual, int profundidade) {
        if (profundidade >= LIMITE_CADEIA_ROLLOVER_MESES) {
            return;
        }

        YearMonth competenciaAnterior = competenciaAtual.minusMonths(1);
        Optional<FaturaCartao> anteriorOpt = faturaRepository.findByContaIdAndMesAndAno(
                conta.getId(), competenciaAnterior.getMonthValue(), competenciaAnterior.getYear());
        if (anteriorOpt.isEmpty()) {
            return; // M-1 nunca existiu como registro: nada a rolar, não materializa fatura retroativa
        }

        // Garante a cadeia completa: liquida M-2 -> M-1 antes de liquidar M-1 -> M, para que o
        // total/valorPago de M-1 já reflitam qualquer crédito/dívida herdado de M-2 nesta mesma leitura.
        liquidarFaturaAnterior(usuarioId, conta, competenciaAnterior, profundidade + 1);

        // Trava de banco: lock pessimista serializa liquidações concorrentes da mesma fatura de
        // origem (duas requisições lendo meses diferentes que dependem da mesma M-1).
        FaturaCartao origem = faturaRepository.findWithLockByIdAndUsuarioId(anteriorOpt.get().getId(), usuarioId)
                .orElse(null);
        if (origem == null) {
            return;
        }

        LocalDate hoje = LocalDate.now(clock);
        if (origem.getDataFechamento() == null || !origem.getDataFechamento().isBefore(hoje)) {
            return; // fatura de origem ainda não fechou
        }
        if (origem.getStatus() == FaturaStatus.PAGA) {
            return; // já quitada (R1 marca PAGA ao rolar; pagamento manual total dispensa rollover)
        }
        if (faturaLancamentoRepository.existsByFaturaOrigemId(origem.getId())) {
            return; // rollover já processado (idempotência em código)
        }

        List<FaturaLancamento> lancamentosOrigem =
                faturaLancamentoRepository.findByFaturaIdOrderByDataCompraAscIdAsc(origem.getId());
        BigDecimal total = lancamentosOrigem.stream()
                .map(FaturaLancamento::getValor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Faturas anteriores ao V17 têm valorTotal mas nenhum lançamento
        if (lancamentosOrigem.isEmpty() && origem.getValorTotal() != null) {
            total = origem.getValorTotal();
        }
        BigDecimal valorPago = origem.getValorPago() != null ? origem.getValorPago() : BigDecimal.ZERO;

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            // R1: total <= 0 (só estorno/crédito) -> credita a próxima fatura em aberto e fecha
            // a origem como PAGA (nada a cobrar), sem exigir ação do usuário.
            FaturaCartao destino = faturaDisponivelParaLancamento(usuarioId, conta, competenciaAtual);
            boolean inserido = inserirRolloverOuNoOp(destino, origem, TipoFaturaLancamento.CREDITO_ANTERIOR,
                    total, "Crédito da fatura anterior");
            if (inserido) {
                origem.setStatus(FaturaStatus.PAGA);
                if (origem.getDataPagamento() == null) {
                    origem.setDataPagamento(origem.getDataFechamento());
                }
                faturaRepository.save(origem);
            }
        } else if (valorPago.compareTo(total) < 0) {
            // R2: fechou com pagamento parcial -> resto vira saldo devedor na próxima fatura.
            // A origem NÃO muda de status além do derivado (fechamento não é bloqueado).
            BigDecimal saldoDevedor = total.subtract(valorPago);
            FaturaCartao destino = faturaDisponivelParaLancamento(usuarioId, conta, competenciaAtual);
            inserirRolloverOuNoOp(destino, origem, TipoFaturaLancamento.SALDO_DEVEDOR_ANTERIOR,
                    saldoDevedor, "Saldo devedor da fatura anterior");
        }
        // total > 0 && valorPago >= total: fatura paga integralmente, nada a fazer.
    }

    // Insere o lançamento de rollover e trata violação da trava de banco
    // (ux_fatura_rollover_origem_tipo) como no-op: só pode ocorrer se outra transação já tiver
    // processado o mesmo rollover na janela entre o exists-check e este insert (o lock
    // pessimista sobre a origem em liquidarFaturaAnterior já deveria ter serializado isso).
    private boolean inserirRolloverOuNoOp(FaturaCartao destino, FaturaCartao origem, TipoFaturaLancamento tipo,
                                          BigDecimal valor, String descricao) {
        try {
            criarLancamentoRollover(destino, origem, descricao, valor, LocalDate.now(clock), tipo);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Rollover duplicado detectado para fatura origem {} (tipo {}); tratado como no-op",
                    origem.getId(), tipo, e);
            return false;
        }
    }

    // Variante de criarLancamento para lançamentos de rollover: sem transação de origem,
    // referenciando faturaOrigem para rastreabilidade (ver V25). saveAndFlush força a
    // constraint ux_fatura_rollover_origem_tipo a ser verificada aqui, onde o catch de
    // DataIntegrityViolationException em inserirRolloverOuNoOp consegue capturá-la.
    private void criarLancamentoRollover(FaturaCartao destino, FaturaCartao origem, String descricao,
                                         BigDecimal valor, LocalDate dataLancamento, TipoFaturaLancamento tipo) {
        FaturaLancamento lancamento = new FaturaLancamento();
        lancamento.setFatura(destino);
        lancamento.setTransacao(null);
        lancamento.setFaturaOrigem(origem);
        lancamento.setDescricao(descricao);
        lancamento.setValor(valor);
        lancamento.setDataCompra(dataLancamento);
        lancamento.setParcelaNumero(null);
        lancamento.setTotalParcelas(null);
        lancamento.setTipo(tipo);
        faturaLancamentoRepository.saveAndFlush(lancamento);

        atualizarTotalFatura(destino, valor);
        ajustarLimiteUtilizado(destino.getConta(), valor);
    }

    private void criarLancamento(FaturaCartao fatura, Transacao transacao, String descricao,
                                 BigDecimal valor, LocalDate dataCompra, Integer parcelaNumero,
                                 Integer totalParcelas, TipoFaturaLancamento tipo) {
        FaturaLancamento lancamento = new FaturaLancamento();
        lancamento.setFatura(fatura);
        lancamento.setTransacao(transacao);
        lancamento.setDescricao(descricao);
        lancamento.setValor(valor);
        lancamento.setDataCompra(dataCompra);
        lancamento.setParcelaNumero(parcelaNumero);
        lancamento.setTotalParcelas(totalParcelas);
        lancamento.setTipo(tipo);
        faturaLancamentoRepository.save(lancamento);

        atualizarTotalFatura(fatura, valor);
        ajustarLimiteUtilizado(fatura.getConta(), valor);
    }

    private void removerLancamentoDeFaturaAberta(FaturaLancamento lancamento) {
        atualizarTotalFatura(lancamento.getFatura(), lancamento.getValor().negate());
        ajustarLimiteUtilizado(lancamento.getFatura().getConta(), lancamento.getValor().negate());
        faturaLancamentoRepository.delete(lancamento);
    }

    // Invariante: valorGasto da conta == soma dos lançamentos em faturas não pagas.
    // Toda criação/remoção de lançamento passa por aqui; pagarFatura libera pelo total da fatura.
    private void ajustarLimiteUtilizado(Conta conta, BigDecimal delta) {
        BigDecimal atual = conta.getValorGasto() != null ? conta.getValorGasto() : BigDecimal.ZERO;
        conta.setValorGasto(atual.add(delta));
        contaRepository.save(conta);
    }

    private void atualizarTotalFatura(FaturaCartao fatura, BigDecimal valor) {
        BigDecimal totalAtual = fatura.getValorTotal() != null ? fatura.getValorTotal() : BigDecimal.ZERO;
        fatura.setValorTotal(totalAtual.add(valor));
        faturaRepository.save(fatura);
    }

    private static List<BigDecimal> calcularCronogramaParcelas(BigDecimal valorTotal, int totalParcelas) {
        if (totalParcelas <= 1) {
            return List.of(valorTotal);
        }

        BigDecimal valorParcela = valorTotal.divide(BigDecimal.valueOf(totalParcelas), 2, RoundingMode.HALF_UP);
        List<BigDecimal> valores = new ArrayList<>(totalParcelas);
        for (int parcela = 1; parcela <= totalParcelas; parcela++) {
            BigDecimal valor = parcela == totalParcelas
                    ? valorTotal.subtract(valorParcela.multiply(BigDecimal.valueOf(totalParcelas - 1L)))
                    : valorParcela;
            valores.add(valor);
        }
        return valores;
    }

    private boolean isCompraCartao(Transacao transacao) {
        return transacao != null
                && transacao.getId() != null
                && transacao.getConta() != null
                && transacao.getConta().getTipo() == TipoConta.CREDITO
                && transacao.getTipo() == TipoTransacao.SAIDA
                && transacao.getAtiva();
    }

    private YearMonth calcularCompetenciaFatura(Conta conta, LocalDate dataCompra) {
        int diaFechamento = diaValidoOuFimDoMes(conta.getDiaFechamento(), YearMonth.from(dataCompra));
        YearMonth competencia = YearMonth.from(dataCompra);
        if (dataCompra.getDayOfMonth() > diaFechamento) {
            competencia = competencia.plusMonths(1);
        }
        return competencia;
    }

    private LocalDate calcularDataFechamento(Conta conta, YearMonth competencia) {
        int dia = diaValidoOuFimDoMes(conta.getDiaFechamento(), competencia);
        return competencia.atDay(dia);
    }

    private LocalDate calcularDataVencimento(Conta conta, YearMonth competencia) {
        int diaFechamento = diaValidoOuFimDoMes(conta.getDiaFechamento(), competencia);
        int diaVencimento = conta.getDiaVencimento() != null ? conta.getDiaVencimento() : 10;
        YearMonth mesVencimento = diaVencimento <= diaFechamento ? competencia.plusMonths(1) : competencia;
        return mesVencimento.atDay(Math.min(diaVencimento, mesVencimento.lengthOfMonth()));
    }

    private int diaValidoOuFimDoMes(Integer dia, YearMonth mes) {
        if (dia == null) {
            return mes.lengthOfMonth();
        }
        return Math.min(Math.max(dia, 1), mes.lengthOfMonth());
    }

    private FaturaStatus statusAtual(FaturaCartao fatura) {
        if (fatura.getStatus() == FaturaStatus.PAGA) {
            return FaturaStatus.PAGA;
        }
        LocalDate hoje = LocalDate.now(clock);
        if (fatura.getDataVencimento() != null && fatura.getDataVencimento().isBefore(hoje)) {
            return FaturaStatus.VENCIDA;
        }
        if (fatura.getDataFechamento() != null && fatura.getDataFechamento().isBefore(hoje)) {
            return FaturaStatus.FECHADA;
        }
        return fatura.getStatus();
    }

    private static String chaveIdempotenciaPagamento(Long faturaId, BigDecimal novoValorPago, String requestKey) {
        if (hasText(requestKey)) {
            return "fatura:" + faturaId + ":pagamento:req:" + requestKey.trim();
        }
        return "fatura:" + faturaId + ":pagamento:valor-pago:" + novoValorPago.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
