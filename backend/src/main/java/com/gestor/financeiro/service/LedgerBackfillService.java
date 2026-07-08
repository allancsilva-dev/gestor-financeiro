package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LedgerBackfillService {

    private static final String REFERENCIA_TIPO_CARTEIRA = "CARTEIRA";
    private static final String IDEMPOTENCY_KEY_PREFIX = "ledger-backfill-carteira-";

    private final CarteiraRepository carteiraRepository;
    private final MovimentoCarteiraRepository movimentoCarteiraRepository;

    public LedgerBackfillService(CarteiraRepository carteiraRepository,
                                 MovimentoCarteiraRepository movimentoCarteiraRepository) {
        this.carteiraRepository = carteiraRepository;
        this.movimentoCarteiraRepository = movimentoCarteiraRepository;
    }

    @Transactional
    public LedgerBackfillResult backfillTodasCarteiras() {
        return backfill(carteiraRepository.findAll());
    }

    @Transactional
    public LedgerBackfillResult backfillUsuario(Long usuarioId) {
        return backfill(carteiraRepository.findByUsuarioId(usuarioId));
    }

    private LedgerBackfillResult backfill(List<Carteira> carteiras) {
        int movimentosCriados = 0;
        int carteirasComBackfillExistente = 0;
        int carteirasSemBackfillNecessario = 0;

        for (Carteira carteira : carteiras) {
            BigDecimal saldoMaterializado = zeroIfNull(carteira.getSaldo());

            if (movimentoCarteiraRepository.existsByCarteiraIdAndOrigemAndReferenciaTipo(
                    carteira.getId(), OrigemMovimentoCarteira.BACKFILL, REFERENCIA_TIPO_CARTEIRA)) {
                carteirasComBackfillExistente++;
                continue;
            }

            BigDecimal saldoLedger = movimentoCarteiraRepository.sumValorAssinadoByUsuarioIdAndCarteiraId(
                    carteira.getUsuario().getId(), carteira.getId());
            BigDecimal valorBackfill = saldoMaterializado.subtract(zeroIfNull(saldoLedger));

            if (valorBackfill.signum() < 0) {
                throw new BusinessException("Backfill bloqueado: carteira com diferença negativa exige decisão manual");
            }
            if (valorBackfill.signum() == 0) {
                carteirasSemBackfillNecessario++;
                continue;
            }

            movimentoCarteiraRepository.save(criarMovimentoBackfill(carteira, valorBackfill, saldoMaterializado));
            movimentosCriados++;
        }

        return new LedgerBackfillResult(
                carteiras.size(),
                movimentosCriados,
                carteirasComBackfillExistente,
                carteirasSemBackfillNecessario
        );
    }

    private static MovimentoCarteira criarMovimentoBackfill(Carteira carteira,
                                                            BigDecimal valorBackfill,
                                                            BigDecimal saldoMaterializado) {
        MovimentoCarteira movimento = new MovimentoCarteira();
        movimento.setUsuario(carteira.getUsuario());
        movimento.setCarteira(carteira);
        movimento.setTipo(TipoMovimentoCarteira.ENTRADA);
        movimento.setValor(valorBackfill);
        movimento.setValorAssinado(valorBackfill);
        movimento.setOrigem(OrigemMovimentoCarteira.BACKFILL);
        movimento.setReferenciaTipo(REFERENCIA_TIPO_CARTEIRA);
        movimento.setReferenciaId(carteira.getId());
        movimento.setDescricao("Backfill inicial da carteira");
        movimento.setDataMovimento(LocalDateTime.now());
        movimento.setSaldoResultante(saldoMaterializado);
        movimento.setIdempotencyKey(IDEMPOTENCY_KEY_PREFIX + carteira.getId());
        return movimento;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
