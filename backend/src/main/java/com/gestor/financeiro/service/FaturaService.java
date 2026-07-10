package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.*;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private FaturaCartaoRepository faturaRepository;

    @Autowired
    private FaturaLancamentoRepository faturaLancamentoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private LedgerService ledgerService;

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
        BigDecimal total = calcularTotalLancamentos(fatura);
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
            LocalDate dataReferencia = totalParcelas > 1
                    ? transacao.getData().plusMonths(parcela - 1L)
                    : transacao.getData();
            YearMonth competencia = calcularCompetenciaFatura(transacao.getConta(), dataReferencia);
            FaturaCartao fatura = criarOuBuscarFaturaEntidade(usuarioId, transacao.getConta(), competencia);

            FaturaLancamento lancamento = new FaturaLancamento();
            lancamento.setFatura(fatura);
            lancamento.setTransacao(transacao);
            lancamento.setDescricao(transacao.getDescricao());
            lancamento.setValor(valorParcela);
            lancamento.setDataCompra(transacao.getData());
            lancamento.setParcelaNumero(totalParcelas > 1 ? parcela : null);
            lancamento.setTotalParcelas(totalParcelas > 1 ? totalParcelas : null);
            faturaLancamentoRepository.save(lancamento);

            atualizarTotalFatura(fatura, valorParcela);
        }
    }

    @Transactional
    public void cancelarCompraCartao(Transacao transacao) {
        if (transacao == null || transacao.getId() == null) {
            return;
        }

        List<FaturaLancamento> lancamentos = faturaLancamentoRepository.findByTransacaoId(transacao.getId());
        for (FaturaLancamento lancamento : lancamentos) {
            FaturaCartao fatura = lancamento.getFatura();
            if (fatura.getStatus() == FaturaStatus.PAGA) {
                throw new BusinessException("Não é possível cancelar compra de fatura paga");
            }

            BigDecimal totalAtual = fatura.getValorTotal() != null ? fatura.getValorTotal() : BigDecimal.ZERO;
            BigDecimal novoTotal = totalAtual.subtract(lancamento.getValor());
            fatura.setValorTotal(novoTotal.signum() < 0 ? BigDecimal.ZERO : novoTotal);
            faturaRepository.save(fatura);
        }
        faturaLancamentoRepository.deleteAll(lancamentos);
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
                    lancamento.getTotalParcelas()
            ));
        }

        BigDecimal valorTotal = fatura.getValorTotal() != null
                && fatura.getValorTotal().compareTo(BigDecimal.ZERO) > 0
                ? fatura.getValorTotal() : total;

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

    private void atualizarTotalFatura(FaturaCartao fatura, BigDecimal valor) {
        BigDecimal totalAtual = fatura.getValorTotal() != null ? fatura.getValorTotal() : BigDecimal.ZERO;
        fatura.setValorTotal(totalAtual.add(valor));
        faturaRepository.save(fatura);
    }

    private BigDecimal calcularTotalLancamentos(FaturaCartao fatura) {
        return faturaLancamentoRepository.findByFaturaIdOrderByDataCompraAscIdAsc(fatura.getId())
                .stream()
                .map(FaturaLancamento::getValor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        if (fatura.getDataVencimento() != null && fatura.getDataVencimento().isBefore(LocalDate.now())) {
            return FaturaStatus.VENCIDA;
        }
        return fatura.getStatus();
    }
}
