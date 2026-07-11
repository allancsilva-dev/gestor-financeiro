-- =============================================================================
-- BACKLOG-0045 — Levantamento: transações antigas sem MovimentoCarteira
-- =============================================================================
-- Somente leitura (SELECT). Não altera dados. Seguro rodar em produção.
--
-- Objetivo: quantificar a divergência descrita em BACKLOG-0045 — transações
-- criadas antes da correção de BUG-0011/BUG-0012 (2026-07-09) que deveriam
-- ter um movimento no ledger (movimentos_carteira) e não têm.
--
-- Uso (na VPS):
--   psql "$DATABASE_URL" -f scripts/diagnose-ledger-backfill.sql
--   ou:  docker compose -f docker-compose.vps.yml exec -T postgres \
--          psql -U <user> -d <db> < scripts/diagnose-ledger-backfill.sql
--
-- "Transação órfã" = ativa, com carteira_id, NÃO é compra de cartão
--   (conta CREDITO + SAIDA vai para fatura, não gera movimento de carteira),
--   e sem MovimentoCarteira correspondente
--   (origem='TRANSACAO', referencia_tipo='TRANSACAO', referencia_id = transacao.id).
-- =============================================================================

\echo '=== 1. Total de transações órfãs (com carteira, sem movimento) ==='
SELECT count(*) AS transacoes_orfas,
       COALESCE(sum(t.valor_total), 0) AS soma_valor
FROM transacoes t
WHERE t.ativa = true
  AND t.carteira_id IS NOT NULL
  AND NOT (
        t.tipo = 'SAIDA'
        AND EXISTS (SELECT 1 FROM contas c
                    WHERE c.id = t.conta_id AND c.tipo = 'CREDITO')
      )
  AND NOT EXISTS (
        SELECT 1 FROM movimentos_carteira m
        WHERE m.origem = 'TRANSACAO'
          AND m.referencia_tipo = 'TRANSACAO'
          AND m.referencia_id = t.id
      );

\echo ''
\echo '=== 2. Órfãs por carteira/usuário (impacto no saldo) ==='
-- valor com sinal: ENTRADA soma, SAIDA subtrai. É o quanto o ledger "perdeu".
SELECT t.usuario_id,
       t.carteira_id,
       count(*) AS qtd_orfas,
       sum(CASE WHEN t.tipo = 'ENTRADA' THEN t.valor_total
                ELSE -t.valor_total END) AS impacto_saldo_assinado
FROM transacoes t
WHERE t.ativa = true
  AND t.carteira_id IS NOT NULL
  AND NOT (
        t.tipo = 'SAIDA'
        AND EXISTS (SELECT 1 FROM contas c
                    WHERE c.id = t.conta_id AND c.tipo = 'CREDITO')
      )
  AND NOT EXISTS (
        SELECT 1 FROM movimentos_carteira m
        WHERE m.origem = 'TRANSACAO'
          AND m.referencia_tipo = 'TRANSACAO'
          AND m.referencia_id = t.id
      )
GROUP BY t.usuario_id, t.carteira_id
ORDER BY t.usuario_id, t.carteira_id;

\echo ''
\echo '=== 3. Carteiras DIVERGENTES hoje (saldo materializado != soma do ledger) ==='
-- Mesma lógica do LedgerReconciliationService. diferenca != 0 => DIVERGENTE.
SELECT c.usuario_id,
       c.id AS carteira_id,
       c.saldo AS saldo_materializado,
       COALESCE(m.saldo_ledger, 0) AS saldo_ledger,
       c.saldo - COALESCE(m.saldo_ledger, 0) AS diferenca
FROM carteiras c
LEFT JOIN (
    SELECT carteira_id, sum(valor_assinado) AS saldo_ledger
    FROM movimentos_carteira
    GROUP BY carteira_id
) m ON m.carteira_id = c.id
WHERE c.saldo - COALESCE(m.saldo_ledger, 0) <> 0
ORDER BY c.usuario_id, c.id;

\echo ''
\echo '=== 4. (Informativo) Transações ativas SEM carteira_id ==='
-- Fora do escopo de reconciliação de saldo: não estão ligadas a nenhuma carteira
-- (lacuna de produto, não divergência de ledger). Exclui compras de cartão.
SELECT count(*) AS transacoes_sem_carteira
FROM transacoes t
WHERE t.ativa = true
  AND t.carteira_id IS NULL
  AND NOT (
        t.tipo = 'SAIDA'
        AND EXISTS (SELECT 1 FROM contas c
                    WHERE c.id = t.conta_id AND c.tipo = 'CREDITO')
      );

\echo ''
\echo '=== 5. Backfills inicial já aplicados (origem=BACKFILL) ==='
-- Se > 0, o LedgerBackfillService (lump-sum) já rodou nessas carteiras.
SELECT count(*) AS carteiras_com_backfill_inicial
FROM movimentos_carteira
WHERE origem = 'BACKFILL' AND referencia_tipo = 'CARTEIRA';
