package com.gestor.financeiro.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado do backfill/diagnóstico de transações órfãs do ledger (BACKLOG-0045).
 *
 * <p>Uma carteira só é reconciliada automaticamente quando o impacto assinado das
 * suas órfãs explica exatamente a divergência atual ({@code saldoMaterializado -
 * saldoLedger == impactoOrfas}). Nesse caso, criar um movimento por órfã (sem tocar
 * no saldo materializado) faz o ledger convergir para o saldo — reconciliação OK.
 * Qualquer outra situação é marcada como {@code REVISAO_MANUAL} e não é alterada.
 */
public record LedgerOrfaBackfillResult(
        boolean dryRun,
        int transacoesOrfas,
        int carteirasAfetadas,
        int carteirasReconciliaveis,
        int carteirasRevisaoManual,
        int movimentosCriados,
        List<CarteiraOrfaDetalhe> detalhes
) {
    public record CarteiraOrfaDetalhe(
            Long carteiraId,
            int quantidadeOrfas,
            BigDecimal saldoMaterializado,
            BigDecimal saldoLedger,
            BigDecimal impactoOrfas,
            Classificacao classificacao
    ) {
        public enum Classificacao {
            /** Divergência atual explicada pelas órfãs; backfill movimento-only reconcilia. */
            RECONCILIAVEL,
            /** Divergência não explicada pelas órfãs; exige decisão manual. */
            REVISAO_MANUAL
        }
    }
}
