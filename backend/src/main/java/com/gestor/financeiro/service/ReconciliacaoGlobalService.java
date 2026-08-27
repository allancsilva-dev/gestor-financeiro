package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse;
import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse.Divergencia;
import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse.Invariante;
import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse.ResumoInvariante;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.projection.CategoriaGastoProjection;
import com.gestor.financeiro.repository.projection.CofreMetaProjection;
import com.gestor.financeiro.repository.projection.LedgerSaldoProjection;
import com.gestor.financeiro.repository.projection.PassivoFaturaProjection;
import com.gestor.financeiro.repository.projection.TransacaoIncompletaProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReconciliacaoGlobalService {
    private final CarteiraRepository carteiraRepository;
    private final MetaRepository metaRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliacaoGlobalResponse reconciliarUsuario(Long usuarioId) {
        Instant executadoEm = clock.instant();
        List<Divergencia> detalhes = new ArrayList<>();
        Map<Invariante, Long> verificacoes = new EnumMap<>(Invariante.class);

        List<LedgerSaldoProjection> ledgers = carteiraRepository.reconciliarSaldosByUsuarioId(usuarioId);
        verificacoes.put(Invariante.SALDO_LEDGER, (long) ledgers.size());
        for (LedgerSaldoProjection item : ledgers) {
            if (different(item.getSaldoMaterializado(), item.getSaldoLedger())) {
                detalhes.add(new Divergencia(Invariante.SALDO_LEDGER, "CONTA_FINANCEIRA",
                        item.getCarteiraId(), money(item.getSaldoLedger()), money(item.getSaldoMaterializado())));
            }
        }

        List<PassivoFaturaProjection> passivos = carteiraRepository.reconciliarPassivosFaturasByUsuarioId(usuarioId);
        verificacoes.put(Invariante.PASSIVO_FATURAS, (long) passivos.size());
        for (PassivoFaturaProjection item : passivos) {
            if (different(item.getSaldoPassivo(), item.getSaldoFaturas())) {
                detalhes.add(new Divergencia(Invariante.PASSIVO_FATURAS, "CARTAO", item.getCartaoId(),
                        money(item.getSaldoFaturas()), money(item.getSaldoPassivo())));
            }
        }

        List<CofreMetaProjection> cofres = metaRepository.reconciliarCofresByUsuarioId(usuarioId);
        verificacoes.put(Invariante.COFRE_META, (long) cofres.size());
        for (CofreMetaProjection item : cofres) {
            boolean ownership = item.getCofreUsuarioId() != null
                    && item.getUsuarioId().equals(item.getCofreUsuarioId());
            boolean subtipo = "COFRE".equals(item.getCofreSubtipo());
            boolean saldo = item.getCofreSaldo() != null
                    && !different(item.getValorReservado(), item.getCofreSaldo());
            if (!ownership || !subtipo || !saldo) {
                String encontrado = item.getCofreId() == null ? "COFRE_AUSENTE"
                        : "usuario=" + item.getCofreUsuarioId() + ",subtipo=" + item.getCofreSubtipo()
                          + ",saldo=" + money(item.getCofreSaldo());
                detalhes.add(new Divergencia(Invariante.COFRE_META, "META", item.getMetaId(),
                        "mesmo_usuario,subtipo=COFRE,saldo=" + money(item.getValorReservado()), encontrado));
            }
        }

        List<CategoriaGastoProjection> gastos = categoriaRepository.reconciliarGastoByUsuarioId(usuarioId);
        verificacoes.put(Invariante.CATEGORIA_VALOR_GASTO, (long) gastos.size());
        for (CategoriaGastoProjection item : gastos) {
            if (different(item.getValorMaterializado(), item.getValorLancado())) {
                detalhes.add(new Divergencia(Invariante.CATEGORIA_VALOR_GASTO, "CATEGORIA",
                        item.getCategoriaId(), money(item.getValorLancado()),
                        money(item.getValorMaterializado())));
            }
        }

        long transacoes = transacaoRepository.countByUsuarioIdAndAtivaTrue(usuarioId);
        verificacoes.put(Invariante.TRANSACAO_INCOMPLETA, transacoes);
        for (TransacaoIncompletaProjection item
                : transacaoRepository.findIncompletasConciliadasByUsuarioId(usuarioId)) {
            boolean compraCartao = item.getContaId() != null;
            detalhes.add(new Divergencia(Invariante.TRANSACAO_INCOMPLETA, "TRANSACAO",
                    item.getTransacaoId(), compraCartao ? "LANCAMENTO_COMPRA" : "CONTA_FINANCEIRA",
                    compraCartao ? "LANCAMENTO_AUSENTE" : "CONTA_FINANCEIRA_AUSENTE"));
        }

        List<ResumoInvariante> resumo = new ArrayList<>();
        long total = 0;
        for (Invariante invariante : Invariante.values()) {
            long checks = verificacoes.getOrDefault(invariante, 0L);
            long divergentes = detalhes.stream().filter(d -> d.invariante() == invariante).count();
            resumo.add(new ResumoInvariante(invariante, checks, checks - divergentes, divergentes));
            total += checks;
        }
        return new ReconciliacaoGlobalResponse(
                detalhes.isEmpty() ? ReconciliacaoGlobalResponse.Status.OK
                        : ReconciliacaoGlobalResponse.Status.DIVERGENTE,
                executadoEm, total, detalhes.size(), List.copyOf(resumo), List.copyOf(detalhes));
    }

    private boolean different(BigDecimal left, BigDecimal right) {
        return value(left).compareTo(value(right)) != 0;
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String money(BigDecimal value) {
        return value == null ? "null" : value.toPlainString();
    }
}
