package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.ReconciliacaoCarteiraResponse;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.projection.LedgerSaldoProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LedgerReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(LedgerReconciliationService.class);

    private final CarteiraRepository carteiraRepository;

    public LedgerReconciliationService(CarteiraRepository carteiraRepository) {
        this.carteiraRepository = carteiraRepository;
    }

    @Transactional(readOnly = true)
    public List<ReconciliacaoCarteiraResponse> reconciliarUsuario(Long usuarioId) {
        return carteiraRepository.reconciliarSaldosByUsuarioId(usuarioId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReconciliacaoCarteiraResponse reconciliarCarteira(Long usuarioId, Long carteiraId) {
        return carteiraRepository.reconciliarSaldoByUsuarioIdAndCarteiraId(usuarioId, carteiraId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));
    }

    private ReconciliacaoCarteiraResponse toResponse(LedgerSaldoProjection projection) {
        BigDecimal saldoMaterializado = zeroIfNull(projection.getSaldoMaterializado());
        BigDecimal saldoLedger = zeroIfNull(projection.getSaldoLedger());
        BigDecimal diferenca = saldoMaterializado.subtract(saldoLedger);
        ReconciliacaoCarteiraResponse.Status status = diferenca.compareTo(BigDecimal.ZERO) == 0
                ? ReconciliacaoCarteiraResponse.Status.OK
                : ReconciliacaoCarteiraResponse.Status.DIVERGENTE;

        if (status == ReconciliacaoCarteiraResponse.Status.DIVERGENTE) {
            log.warn("Divergência de ledger detectada: usuarioId={}, carteiraId={}, diferenca={}",
                    projection.getUsuarioId(), projection.getCarteiraId(), diferenca);
        }

        return new ReconciliacaoCarteiraResponse(
                projection.getCarteiraId(),
                projection.getUsuarioId(),
                saldoMaterializado,
                saldoLedger,
                diferenca,
                status
        );
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
