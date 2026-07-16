-- =============================================================================
-- V34 — PR-F2-05: estado de conciliacao explicito em transacoes (ADR-0009/0013)
-- =============================================================================
-- Fase EXPAND (ADR-0015): coluna aditiva + backfill que apenas MARCA o legado
-- orfao (transacao ativa sem carteira que nao e compra de cartao). Nenhum
-- valor, data ou vinculo financeiro e alterado — dado nao some, ganha estado.
-- Transacoes com carteira mas sem movimento sao tratadas pelo
-- LedgerBackfillService (reconciliaveis), nao por esta migration.
-- =============================================================================

ALTER TABLE transacoes
    ADD COLUMN estado_conciliacao VARCHAR(30) NOT NULL DEFAULT 'CONCILIADA';

ALTER TABLE transacoes
    ADD CONSTRAINT chk_transacoes_estado_conciliacao
        CHECK (estado_conciliacao IN ('CONCILIADA', 'PENDENTE_CONCILIACAO'));

-- Backfill: legado orfao (sem caixa e sem fatura) vira PENDENTE_CONCILIACAO
UPDATE transacoes t
SET estado_conciliacao = 'PENDENTE_CONCILIACAO'
WHERE t.ativa = true
  AND t.carteira_id IS NULL
  AND NOT (
        t.tipo = 'SAIDA'
        AND EXISTS (SELECT 1 FROM contas c
                    WHERE c.id = t.conta_id AND c.tipo = 'CREDITO')
      );

-- Guard (ADR-0015): nenhuma orfa pode permanecer marcada como conciliada
DO $$
DECLARE
    orfas_conciliadas BIGINT;
BEGIN
    SELECT count(*) INTO orfas_conciliadas
    FROM transacoes t
    WHERE t.ativa = true
      AND t.carteira_id IS NULL
      AND t.estado_conciliacao = 'CONCILIADA'
      AND NOT (
            t.tipo = 'SAIDA'
            AND EXISTS (SELECT 1 FROM contas c
                        WHERE c.id = t.conta_id AND c.tipo = 'CREDITO')
          );
    IF orfas_conciliadas > 0 THEN
        RAISE EXCEPTION 'V34 abortada: % transacao(oes) orfa(s) ainda conciliada(s)', orfas_conciliadas;
    END IF;
END $$;
