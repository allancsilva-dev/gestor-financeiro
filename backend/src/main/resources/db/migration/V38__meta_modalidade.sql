-- =============================================================================
-- V38 — PR-F2-12: modalidade da meta (ADR-0012)
-- =============================================================================
-- COFRE_REAL (default, comportamento existente) ou RESERVA_VIRTUAL (alocacao
-- explicita sobre conta de caixa, sem lancamento). Exatamente uma por meta.
-- Aditiva: backfill trivial via DEFAULT; nenhuma linha muda de semantica.
-- =============================================================================

ALTER TABLE metas
    ADD COLUMN modalidade VARCHAR(20) NOT NULL DEFAULT 'COFRE_REAL',
    ADD COLUMN carteira_alocada_id BIGINT REFERENCES carteiras(id);

ALTER TABLE metas
    ADD CONSTRAINT ck_metas_modalidade
        CHECK (modalidade IN ('COFRE_REAL', 'RESERVA_VIRTUAL')),
    -- reserva virtual nunca tem cofre; cofre real nunca tem carteira alocada
    ADD CONSTRAINT ck_metas_modalidade_coerente
        CHECK (
            (modalidade = 'RESERVA_VIRTUAL' AND cofre_id IS NULL)
            OR (modalidade = 'COFRE_REAL' AND carteira_alocada_id IS NULL)
        );
