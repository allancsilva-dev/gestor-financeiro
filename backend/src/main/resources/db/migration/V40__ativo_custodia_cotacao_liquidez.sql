-- =============================================================================
-- V40 — PR-F2-14: custodia, cotacao datada e liquidez por ativo (ADR-0011)
-- =============================================================================
-- Aditiva. CUSTODIA e container de posicoes (saldo=0 tecnico, V32); valor
-- investido = quantidade x ultima cotacao valida, com instante explicito.
-- Cotacao legada sem data permanece NULL (sinalizada como desatualizada na
-- leitura); nenhuma linha e alterada.
-- =============================================================================

ALTER TABLE ativos
    ADD COLUMN custodia_id BIGINT REFERENCES carteiras(id),
    ADD COLUMN cotacao_em TIMESTAMP,
    ADD COLUMN liquidez VARCHAR(10) NOT NULL DEFAULT 'IMEDIATA';

ALTER TABLE ativos
    ADD CONSTRAINT ck_ativos_liquidez
        CHECK (liquidez IN ('IMEDIATA', 'D1', 'D2', 'CARENCIA', 'BLOQUEADA'));

CREATE INDEX idx_ativos_custodia ON ativos(custodia_id) WHERE custodia_id IS NOT NULL;
