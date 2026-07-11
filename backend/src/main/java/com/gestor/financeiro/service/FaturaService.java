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

    private final FaturaCartaoRepository faturaRepository;
    private final FaturaLancamentoRepository faturaLancamentoRepository;
    private final ContaRepository contaRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CarteiraRepository carteiraRepository;
    private final LedgerService ledgerService;

    public FaturaService(FaturaCartaoRepository faturaRepository,
                         FaturaLancamentoRepository faturaLancamentoRepository,
                         ContaRepository contaRepository,
                         TransacaoRepository transacaoRepository,
                         UsuarioRepository usuarioRepository,
                         CarteiraRepository carteiraRepository,
                         LedgerService ledgerService) {
        this.faturaRepository = faturaRepository;
        this.faturaLancamentoRepository = faturaLancamentoRepository;
        this.contaRepository = contaRepository;
        this.transacaoRepository = transacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.carteiraRepository = carteiraRepository;
        this.ledgerService = ledgerService;
    }

    public FaturaResponse buscarAtual(Long usuarioId, Long contaId) {
        Conta conta = validarContaCredito(usuarioId, contaId);
        YearMonth ym = YearMonth.now();

        Optional<FaturaCartao> existente = faturaRepository.findByContaIdAndMesAndAno(
                contaId, ym.getMonthValue(), ym.getYear());

        if (existente.isPresent()) {
            return toResponse(existente.get(), usuarioId, conta);
        }

        return toResponseVazia(conta, ym.getMonthValue(), ym.getYear());
    }

    public FaturaResponse buscarPorMes(Long usuarioId, Long contaId, Integer mes, Integer ano) {
        Conta conta = validarContaCredito(usuarioId, contaId);

        Optional<FaturaCartao> existente = faturaRepository.findByContaIdAndMesAndAno(contaId, mes, ano);

        if (existente.isPresent()) {
            return toResponse(existente.get(), usuarioId, conta);
        }

        return toResponseVazia(conta, mes, ano);
    }

    @Transactional
    public FaturaResponse criarOuBuscarFatura(Long usuarioId, Long contaId, Integer mes, Integer ano) {
        Conta conta = validarContaCredito(usuarioId, contaId);

        FaturaCartao fatura = criarOuBuscarFaturaEntidade(usuarioId, conta, YearMonth.of(ano, mes));

        return toResponse(fatura, usuarioId, conta);
    }

    @Transactional
    public FaturaResponse pagarFatura(Long usuarioId, Long faturaId, BigDecimal valor, Long carteiraId) {
        FaturaCartao fatura = faturaRepository.findByIdAndUsuarioId(faturaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));

        if (fatura.getStatus() == FaturaStatus.PAGA) {
            throw new BusinessException("Fatura já está paga");
        }

        if (carteiraId == null) {
            throw new BusinessException("Carteira de pagamento é obrigatória");
        }

        Conta conta = fatura.getConta();
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

        if (valor.compareTo(total) != 0) {
            throw new BusinessException("Pagamento parcial de fatura ainda não é suportado");
        }

        Carteira carteira = carteiraRepository.findByIdAndUsuarioId(carteiraId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

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
                "fatura:" + fatura.getId() + ":pagamento",
                null,
                false
        ));

        fatura.setValorPago(valor);
        fatura.setDataPagamento(LocalDate.now());
        fatura.setStatus(FaturaStatus.PAGA);
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
                    usuarioId, transacao.getConta(), YearMonth.now());
            criarLancamento(proxima, transacao, "Estorno: " + transacao.getDescricao(),
                    somaFaturasPagas.negate(), LocalDate.now(), null, null,
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

        BigDecimal restante = transacao.getValorTotal().subtract(somaFaturasPagas);

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

        if (!parcelasEmAberto.isEmpty() && restante.signum() > 0) {
            // Redistribui o restante pelas parcelas ainda não pagas
            BigDecimal valorParcela = restante.divide(
                    BigDecimal.valueOf(parcelasEmAberto.size()), 2, RoundingMode.HALF_UP);
            for (int idx = 0; idx < parcelasEmAberto.size(); idx++) {
                int numero = parcelasEmAberto.get(idx);
                BigDecimal valor = idx == parcelasEmAberto.size() - 1
                        ? restante.subtract(valorParcela.multiply(
                                BigDecimal.valueOf(parcelasEmAberto.size() - 1L)))
                        : valorParcela;
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
        } else if (restante.signum() != 0) {
            // Toda a compra já foi paga (ou o novo valor é menor que o já pago):
            // a diferença entra como ajuste na próxima fatura aberta
            FaturaCartao proxima = faturaDisponivelParaLancamento(
                    usuarioId, transacao.getConta(), YearMonth.now());
            criarLancamento(proxima, transacao, "Ajuste: " + transacao.getDescricao(),
                    restante, LocalDate.now(), null, null, TipoFaturaLancamento.AJUSTE);
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
            Transacao t = lancamento.getTransacao();
            BigDecimal valor = lancamento.getValor() != null ? lancamento.getValor() : BigDecimal.ZERO;
            total = total.add(valor);

            Categoria cat = t.getCategoria();
            lancamentos.add(new FaturaLancamentoDto(
                    t.getId(),
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
        LocalDate hoje = LocalDate.now();
        if (fatura.getDataVencimento() != null && fatura.getDataVencimento().isBefore(hoje)) {
            return FaturaStatus.VENCIDA;
        }
        if (fatura.getDataFechamento() != null && fatura.getDataFechamento().isBefore(hoje)) {
            return FaturaStatus.FECHADA;
        }
        return fatura.getStatus();
    }
}
