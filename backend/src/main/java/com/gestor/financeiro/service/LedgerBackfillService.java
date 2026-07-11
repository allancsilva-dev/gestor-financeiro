package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.service.LedgerOrfaBackfillResult.CarteiraOrfaDetalhe;
import com.gestor.financeiro.service.LedgerOrfaBackfillResult.CarteiraOrfaDetalhe.Classificacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LedgerBackfillService {

    private static final String REFERENCIA_TIPO_CARTEIRA = "CARTEIRA";
    private static final String REFERENCIA_TIPO_TRANSACAO = "TRANSACAO";
    private static final String IDEMPOTENCY_KEY_PREFIX = "ledger-backfill-carteira-";
    private static final String IDEMPOTENCY_KEY_ORFA_PREFIX = "ledger-backfill-transacao-";

    private final CarteiraRepository carteiraRepository;
    private final MovimentoCarteiraRepository movimentoCarteiraRepository;
    private final TransacaoRepository transacaoRepository;

    public LedgerBackfillService(CarteiraRepository carteiraRepository,
                                 MovimentoCarteiraRepository movimentoCarteiraRepository,
                                 TransacaoRepository transacaoRepository) {
        this.carteiraRepository = carteiraRepository;
        this.movimentoCarteiraRepository = movimentoCarteiraRepository;
        this.transacaoRepository = transacaoRepository;
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

    /**
     * Reconcilia transações órfãs do ledger (BACKLOG-0045) para um usuário.
     *
     * <p>Agrupa as órfãs por carteira. Uma carteira só é tocada quando o impacto
     * assinado das suas órfãs explica exatamente a divergência atual
     * ({@code saldoMaterializado - saldoLedger == impactoOrfas}); nesse caso cria um
     * movimento {@code TRANSACAO} por órfã <b>sem alterar o saldo materializado</b>
     * (o saldo já reflete essas transações — apenas o ledger não), fazendo o ledger
     * convergir para o saldo. Carteiras cuja divergência não é explicada pelas órfãs
     * ficam intactas e são reportadas como {@code REVISAO_MANUAL}.
     *
     * @param dryRun quando {@code true}, apenas classifica e reporta sem persistir.
     */
    @Transactional
    public LedgerOrfaBackfillResult reconciliarTransacoesOrfasUsuario(Long usuarioId, boolean dryRun) {
        List<Transacao> orfas = transacaoRepository.findOrfasSemMovimentoByUsuarioId(usuarioId);

        Map<Long, List<Transacao>> porCarteira = new LinkedHashMap<>();
        for (Transacao t : orfas) {
            porCarteira.computeIfAbsent(t.getCarteira().getId(), k -> new ArrayList<>()).add(t);
        }

        int movimentosCriados = 0;
        int reconciliaveis = 0;
        int revisaoManual = 0;
        List<CarteiraOrfaDetalhe> detalhes = new ArrayList<>();

        for (List<Transacao> lista : porCarteira.values()) {
            Carteira carteira = lista.get(0).getCarteira();
            BigDecimal saldoMaterializado = zeroIfNull(carteira.getSaldo());
            BigDecimal saldoLedger = zeroIfNull(
                    movimentoCarteiraRepository.sumValorAssinadoByUsuarioIdAndCarteiraId(usuarioId, carteira.getId()));
            BigDecimal impactoOrfas = lista.stream()
                    .map(LedgerBackfillService::valorAssinadoOrfa)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            boolean reconciliavel = saldoMaterializado.subtract(saldoLedger).compareTo(impactoOrfas) == 0;

            if (reconciliavel) {
                reconciliaveis++;
                if (!dryRun) {
                    for (Transacao t : lista) {
                        movimentoCarteiraRepository.save(criarMovimentoOrfa(carteira, t, saldoMaterializado));
                        movimentosCriados++;
                    }
                }
            } else {
                revisaoManual++;
            }

            detalhes.add(new CarteiraOrfaDetalhe(
                    carteira.getId(),
                    lista.size(),
                    saldoMaterializado,
                    saldoLedger,
                    impactoOrfas,
                    reconciliavel ? Classificacao.RECONCILIAVEL : Classificacao.REVISAO_MANUAL
            ));
        }

        return new LedgerOrfaBackfillResult(
                dryRun,
                orfas.size(),
                porCarteira.size(),
                reconciliaveis,
                revisaoManual,
                movimentosCriados,
                detalhes
        );
    }

    private static BigDecimal valorAssinadoOrfa(Transacao transacao) {
        BigDecimal valor = transacao.getValorTotal();
        return transacao.getTipo() == TipoTransacao.ENTRADA ? valor : valor.negate();
    }

    private static MovimentoCarteira criarMovimentoOrfa(Carteira carteira,
                                                        Transacao transacao,
                                                        BigDecimal saldoMaterializado) {
        boolean entrada = transacao.getTipo() == TipoTransacao.ENTRADA;
        BigDecimal valor = transacao.getValorTotal();

        MovimentoCarteira movimento = new MovimentoCarteira();
        movimento.setUsuario(carteira.getUsuario());
        movimento.setCarteira(carteira);
        movimento.setTipo(entrada ? TipoMovimentoCarteira.ENTRADA : TipoMovimentoCarteira.SAIDA);
        movimento.setValor(valor);
        movimento.setValorAssinado(entrada ? valor : valor.negate());
        movimento.setOrigem(OrigemMovimentoCarteira.TRANSACAO);
        movimento.setReferenciaTipo(REFERENCIA_TIPO_TRANSACAO);
        movimento.setReferenciaId(transacao.getId());
        movimento.setDescricao(truncar("Backfill retroativo (BACKLOG-0045): "
                + (transacao.getDescricao() == null ? "" : transacao.getDescricao()), 500));
        movimento.setDataMovimento(transacao.getData() == null
                ? LocalDateTime.now()
                : transacao.getData().atStartOfDay());
        // Movimento-only: o saldo materializado já reflete esta transação; usa o saldo
        // final como saldoResultante (coluna NOT NULL), coerente com o backfill lump-sum.
        movimento.setSaldoResultante(saldoMaterializado);
        movimento.setIdempotencyKey(IDEMPOTENCY_KEY_ORFA_PREFIX + transacao.getId());
        return movimento;
    }

    private static String truncar(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
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
