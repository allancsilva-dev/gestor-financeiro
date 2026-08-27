-- Importação de fatura: o lote pode ter como destino um cartão em vez de uma conta de caixa.
-- Compra de cartão nasce na fatura (ADR-0009), então o destino determina o caminho de lançamento.
ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS conta_id BIGINT;

ALTER TABLE import_batches
    ADD CONSTRAINT fk_import_batches_conta FOREIGN KEY (conta_id) REFERENCES contas (id);

CREATE INDEX IF NOT EXISTS ix_import_batches_conta
    ON import_batches (conta_id)
    WHERE conta_id IS NOT NULL;

-- Um destino, nunca dois: extrato de conta e fatura de cartão são lançamentos diferentes.
ALTER TABLE import_batches DROP CONSTRAINT IF EXISTS ck_import_batches_destino_no_commit;
ALTER TABLE import_batches
    ADD CONSTRAINT ck_import_batches_destino_no_commit CHECK (
        (status NOT IN ('COMMITTING', 'COMMITTED')
            OR carteira_id IS NOT NULL OR conta_id IS NOT NULL)
        AND NOT (carteira_id IS NOT NULL AND conta_id IS NOT NULL)
    );

COMMENT ON COLUMN import_batches.conta_id IS
    'Cartão de destino quando o lote é uma fatura; exclusivo com carteira_id.';
