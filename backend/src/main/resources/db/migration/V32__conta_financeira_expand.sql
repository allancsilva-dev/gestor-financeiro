-- =============================================================================
-- V32 — PR-F2-02: expand de carteiras para conta financeira (ADR-0008)
-- =============================================================================
-- Fase EXPAND (ADR-0015): apenas colunas aditivas + backfill deterministico.
-- Nao altera saldo nem movimentos. Guard aborta se o backfill deixar lacuna.
-- Reconciliacao: SUM(saldo) por usuario e invariante do ledger permanecem
-- identicos antes/depois (nenhuma linha de saldo e tocada).
-- =============================================================================

ALTER TABLE carteiras
    ADD COLUMN natureza VARCHAR(10) NOT NULL DEFAULT 'ATIVO',
    ADD COLUMN subtipo VARCHAR(20),
    ADD COLUMN liquidez VARCHAR(10) NOT NULL DEFAULT 'IMEDIATA',
    ADD COLUMN origem_dados VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN estado_conciliacao VARCHAR(20) NOT NULL DEFAULT 'CONCILIADA',
    ADD COLUMN moeda VARCHAR(3) NOT NULL DEFAULT 'BRL';

-- Backfill deterministico do subtipo a partir do tipo legado
UPDATE carteiras
SET subtipo = CASE tipo
                  WHEN 'DINHEIRO'       THEN 'DINHEIRO'
                  WHEN 'CONTA_BANCARIA' THEN 'CORRENTE'
                  WHEN 'POUPANCA'       THEN 'POUPANCA'
              END
WHERE subtipo IS NULL;

-- Guard (ADR-0015): nenhuma carteira pode ficar sem subtipo
DO $$
DECLARE
    lacunas BIGINT;
BEGIN
    SELECT count(*) INTO lacunas FROM carteiras WHERE subtipo IS NULL;
    IF lacunas > 0 THEN
        RAISE EXCEPTION 'V32 abortada: % carteira(s) sem subtipo apos backfill', lacunas;
    END IF;
END $$;

ALTER TABLE carteiras ALTER COLUMN subtipo SET NOT NULL;

-- Dominios (ADR-0008)
ALTER TABLE carteiras
    ADD CONSTRAINT ck_carteiras_natureza
        CHECK (natureza IN ('ATIVO', 'PASSIVO')),
    ADD CONSTRAINT ck_carteiras_subtipo
        CHECK (subtipo IN ('DINHEIRO', 'CORRENTE', 'POUPANCA', 'PAGAMENTO',
                           'COFRE', 'CUSTODIA', 'CARTAO')),
    ADD CONSTRAINT ck_carteiras_liquidez
        CHECK (liquidez IN ('IMEDIATA', 'D1', 'D2', 'CARENCIA', 'BLOQUEADA')),
    ADD CONSTRAINT ck_carteiras_origem_dados
        CHECK (origem_dados IN ('MANUAL', 'CSV', 'OFX', 'INTEGRACAO', 'AJUSTE')),
    ADD CONSTRAINT ck_carteiras_estado_conciliacao
        CHECK (estado_conciliacao IN ('CONCILIADA', 'PENDENTE')),
    ADD CONSTRAINT ck_carteiras_moeda
        CHECK (moeda = 'BRL'),
    -- CARTAO e o unico subtipo PASSIVO nesta fase (ADR-0008)
    ADD CONSTRAINT ck_carteiras_natureza_subtipo
        CHECK ((subtipo = 'CARTAO') = (natureza = 'PASSIVO')),
    -- CUSTODIA nao tem saldo monetario: zero tecnico (ADR-0008/ADR-0011)
    ADD CONSTRAINT ck_carteiras_custodia_saldo_zero
        CHECK (subtipo <> 'CUSTODIA' OR saldo = 0);
