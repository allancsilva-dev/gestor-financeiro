package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.exception.CardParcelDeprecatedException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ParcelaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParcelaService {
    private final java.time.Clock clock;
    private final ParcelaRepository parcelaRepository;
    private final LedgerService ledgerService;
    private final CarteiraRepository carteiraRepository;

    public Page<Parcela> listarPorTransacao(Long transacaoId, Long usuarioId, Pageable pageable) {
        return parcelaRepository.findByTransacaoIdAndTransacaoUsuarioId(transacaoId, usuarioId, pageable);
    }

    @Transactional
    public Parcela marcarComoPaga(Long parcelaId, Long usuarioId) {
        Parcela parcela = buscarPorIdDoUsuario(parcelaId, usuarioId);
        rejeitarCartao(parcela);

        // Idempotência: parcela já paga não gera novo débito na carteira em re-submit.
        // Concorrência real é coberta por @Version em Parcela (OptimisticLock -> 409).
        if (parcela.getStatus() == StatusPagamento.PAGO) {
            return parcela;
        }

        parcela.setStatus(StatusPagamento.PAGO);
        parcela.setDataPagamento(LocalDate.now(clock));

        Parcela salva = parcelaRepository.save(parcela);

        registrarMovimentoPagamento(salva, usuarioId, TipoMovimentoCarteira.SAIDA);

        return salva;
    }

    @Transactional
    public Parcela marcarComoPendente(Long parcelaId, Long usuarioId) {
        Parcela parcela = buscarPorIdDoUsuario(parcelaId, usuarioId);
        rejeitarCartao(parcela);

        if (parcela.getStatus() != StatusPagamento.PAGO) {
            return parcela;
        }

        parcela.setStatus(StatusPagamento.PENDENTE);
        parcela.setDataPagamento(null);

        Parcela salva = parcelaRepository.save(parcela);

        registrarMovimentoPagamento(salva, usuarioId, TipoMovimentoCarteira.ESTORNO);

        return salva;
    }

    public Parcela buscarPorId(Long id, Long usuarioId) {
        return buscarPorIdDoUsuario(id, usuarioId);
    }

    public Parcela buscarPorIdDoUsuario(Long id, Long usuarioId) {
        Parcela parcela = parcelaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Parcela não encontrada"));

        if (!parcela.getTransacao().getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedAccessException("Acesso negado a esta parcela");
        }

        return parcela;
    }

    @Transactional
    public void atualizarParcelasAtrasadas() {
        parcelaRepository.atualizarStatusParcelasAtrasadas(
            StatusPagamento.PENDENTE, StatusPagamento.ATRASADO, LocalDate.now(clock));
    }

    private void registrarMovimentoPagamento(Parcela parcela, Long usuarioId, TipoMovimentoCarteira tipoMovimento) {
        if (parcela.getTransacao().getCarteira() == null
                || parcela.getTransacao().getCarteira().getId() == null) {
            return;
        }

        Long carteiraId = parcela.getTransacao().getCarteira().getId();

        Carteira carteira = carteiraRepository.findByIdAndUsuarioIdForUpdate(carteiraId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

        RegistrarMovimentoCommand.Direcao direcao = tipoMovimento == TipoMovimentoCarteira.ESTORNO
                ? RegistrarMovimentoCommand.Direcao.ENTRADA
                : RegistrarMovimentoCommand.Direcao.SAIDA;

        ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                usuarioId,
                carteira.getId(),
                tipoMovimento,
                parcela.getValor(),
                direcao,
                OrigemMovimentoCarteira.PARCELA,
                "PARCELA",
                parcela.getId(),
                "Parcela " + parcela.getNumeroParcela() + "/" + parcela.getTotalParcelas()
                        + " - " + parcela.getTransacao().getDescricao(),
                null,
                LocalDateTime.now(clock),
                false
        ));
    }

    private void rejeitarCartao(Parcela parcela) {
        if (parcela.getTransacao().getTipo() == TipoTransacao.SAIDA
                && parcela.getTransacao().getConta() != null
                && parcela.getTransacao().getConta().getTipo() == TipoConta.CREDITO) {
            throw new CardParcelDeprecatedException(parcela.getTransacao().getId());
        }
    }
}
