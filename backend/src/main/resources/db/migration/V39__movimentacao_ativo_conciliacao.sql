-- =============================================================================
-- V39 — PR-F2-13: conciliacao explicita de movimentacoes de investimento
-- =============================================================================
-- (ADR-0011) Movimentacao com movimento de caixa vinculado (origem
-- INVESTIMENTO, chave 'MOV_ATIVO_<id>') e CONCILIADA; sem historico de caixa
-- vira snapshot EXTERNO, explicitamente nao conciliado. A migration nao cria
-- nem altera movimento de caixa (delta de caixa zero por construcao); apenas
-- marca estado — dado nao some, ganha rotulo.
-- =============================================================================

ALTER TABLE movimentacoes_ativo
    ADD COLUMN conciliacao VARCHAR(20) NOT NULL DEFAULT 'EXTERNO';

ALTER TABLE movimentacoes_ativo
    ADD CONSTRAINT ck_mov_ativo_conciliacao
        CHECK (conciliacao IN ('CONCILIADA', 'EXTERNO'));

-- Legado com caixa vinculado e conciliado
UPDATE movimentacoes_ativo ma
SET conciliacao = 'CONCILIADA'
WHERE EXISTS (
    SELECT 1 FROM movimentos_carteira mc
    WHERE mc.usuario_id = ma.usuario_id
      AND mc.origem = 'INVESTIMENTO'
      AND mc.idempotency_key = 'MOV_ATIVO_' || ma.id
);

-- Guard (ADR-0015): CONCILIADA sem movimento correspondente e violacao
DO $$
DECLARE
    violacoes BIGINT;
BEGIN
    SELECT count(*) INTO violacoes
    FROM movimentacoes_ativo ma
    WHERE ma.conciliacao = 'CONCILIADA'
      AND NOT EXISTS (
        SELECT 1 FROM movimentos_carteira mc
        WHERE mc.usuario_id = ma.usuario_id
          AND mc.origem = 'INVESTIMENTO'
          AND mc.idempotency_key = 'MOV_ATIVO_' || ma.id);
    IF violacoes > 0 THEN
        RAISE EXCEPTION 'V39 abortada: % movimentacao(oes) conciliada(s) sem caixa', violacoes;
    END IF;
END $$;
