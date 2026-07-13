-- BACKLOG-0051: diagnostico read-only de residuo de arredondamento em
-- parcelamentos antigos. Nao executa UPDATE.
--
-- Uso:
--   psql "$DATABASE_URL" -f scripts/diagnose-rounding-residue-backfill.sql

-- 1) Resumo por superficie persistida.
WITH parcelas_divergentes AS (
    SELECT t.id
    FROM transacoes t
    JOIN parcelas p ON p.transacao_id = t.id
    WHERE t.ativa = true
      AND t.parcelado = true
      AND t.total_parcelas > 1
    GROUP BY t.id, t.valor_total
    HAVING SUM(p.valor) <> t.valor_total
),
faturas_divergentes_seguras AS (
    SELECT t.id
    FROM transacoes t
    JOIN fatura_lancamentos fl ON fl.transacao_id = t.id
    WHERE t.ativa = true
      AND t.parcelado = true
      AND t.total_parcelas > 1
      AND fl.tipo = 'COMPRA'
      AND NOT EXISTS (
          SELECT 1
          FROM fatura_lancamentos outro
          WHERE outro.transacao_id = t.id
            AND outro.tipo <> 'COMPRA'
      )
    GROUP BY t.id, t.valor_total
    HAVING SUM(fl.valor) <> t.valor_total
)
SELECT
    (SELECT COUNT(*) FROM parcelas_divergentes) AS transacoes_com_residuo_em_parcelas,
    (SELECT COUNT(*) FROM faturas_divergentes_seguras) AS transacoes_com_residuo_em_faturas_seguras;

-- 2) Detalhe de parcelas legadas divergentes.
SELECT
    t.usuario_id,
    t.id AS transacao_id,
    t.descricao,
    t.valor_total,
    SUM(p.valor) AS soma_parcelas,
    t.valor_total - SUM(p.valor) AS diferenca_para_ultima_parcela,
    MAX(p.numero_parcela) AS ultima_parcela
FROM transacoes t
JOIN parcelas p ON p.transacao_id = t.id
WHERE t.ativa = true
  AND t.parcelado = true
  AND t.total_parcelas > 1
GROUP BY t.usuario_id, t.id, t.descricao, t.valor_total
HAVING SUM(p.valor) <> t.valor_total
ORDER BY t.usuario_id, t.id;

-- 3) Detalhe de lancamentos de fatura divergentes, apenas casos seguros
-- (sem AJUSTE/ESTORNO/CREDITO_ANTERIOR/SALDO_DEVEDOR_ANTERIOR na mesma transacao).
SELECT
    t.usuario_id,
    t.id AS transacao_id,
    t.descricao,
    t.valor_total,
    SUM(fl.valor) AS soma_lancamentos_compra,
    t.valor_total - SUM(fl.valor) AS diferenca_para_ultimo_lancamento,
    MAX(fl.parcela_numero) AS ultima_parcela_fatura
FROM transacoes t
JOIN fatura_lancamentos fl ON fl.transacao_id = t.id
WHERE t.ativa = true
  AND t.parcelado = true
  AND t.total_parcelas > 1
  AND fl.tipo = 'COMPRA'
  AND NOT EXISTS (
      SELECT 1
      FROM fatura_lancamentos outro
      WHERE outro.transacao_id = t.id
        AND outro.tipo <> 'COMPRA'
  )
GROUP BY t.usuario_id, t.id, t.descricao, t.valor_total
HAVING SUM(fl.valor) <> t.valor_total
ORDER BY t.usuario_id, t.id;

-- 4) Casos nao seguros para correcao automatica: compra parcelada com lancamentos
-- nao-COMPRA. Podem ser edicao/cancelamento/rollover, revisar manualmente.
SELECT
    t.usuario_id,
    t.id AS transacao_id,
    t.descricao,
    t.valor_total,
    SUM(CASE WHEN fl.tipo = 'COMPRA' THEN fl.valor ELSE 0 END) AS soma_compra,
    STRING_AGG(DISTINCT fl.tipo, ', ' ORDER BY fl.tipo) AS tipos_lancamento
FROM transacoes t
JOIN fatura_lancamentos fl ON fl.transacao_id = t.id
WHERE t.ativa = true
  AND t.parcelado = true
  AND t.total_parcelas > 1
GROUP BY t.usuario_id, t.id, t.descricao, t.valor_total
HAVING SUM(CASE WHEN fl.tipo = 'COMPRA' THEN fl.valor ELSE 0 END) <> t.valor_total
   AND COUNT(*) FILTER (WHERE fl.tipo <> 'COMPRA') > 0
ORDER BY t.usuario_id, t.id;
