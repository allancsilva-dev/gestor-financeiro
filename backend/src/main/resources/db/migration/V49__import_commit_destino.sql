-- Destino do lançamento e resultado do commit.
-- Expand puro: colunas nulas, sem reescrever linha existente. A conta fica no lote (um extrato
-- pertence a uma conta) e a categoria no registro (cada linha pode ter a sua).

ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS carteira_id BIGINT;

ALTER TABLE import_batches
    ADD CONSTRAINT fk_import_batches_carteira
    FOREIGN KEY (carteira_id) REFERENCES carteiras (id);

CREATE INDEX IF NOT EXISTS ix_import_batches_carteira
    ON import_batches (carteira_id)
    WHERE carteira_id IS NOT NULL;

-- Sem conta não existe lançamento: o banco recusa o lote entrar em commit sem destino.
ALTER TABLE import_batches
    ADD CONSTRAINT ck_import_batches_destino_no_commit CHECK (
        status NOT IN ('COMMITTING', 'COMMITTED') OR carteira_id IS NOT NULL
    );

ALTER TABLE import_records ADD COLUMN IF NOT EXISTS categoria_id BIGINT;

ALTER TABLE import_records
    ADD CONSTRAINT fk_import_records_categoria
    FOREIGN KEY (categoria_id) REFERENCES categorias (id);

-- Falha ao lançar uma linha precisa de motivo registrado; a lista continua fechada.
ALTER TABLE import_records DROP CONSTRAINT IF EXISTS ck_import_records_reason_closed;
ALTER TABLE import_records ADD CONSTRAINT ck_import_records_reason_closed CHECK (
    reason_code IS NULL OR reason_code IN (
        'DATE_MISSING', 'DATE_INVALID', 'DATE_AMBIGUOUS',
        'AMOUNT_MISSING', 'AMOUNT_INVALID', 'AMOUNT_AMBIGUOUS', 'AMOUNT_ROUNDING_REQUIRED',
        'CURRENCY_MISSING', 'CURRENCY_INVALID', 'CURRENCY_UNSUPPORTED',
        'DIRECTION_MISSING', 'DIRECTION_INVALID', 'DIRECTION_CONFLICT',
        'DESCRIPTION_MISSING', 'DESCRIPTION_INVALID', 'EXTERNAL_ID_INVALID',
        'COMMIT_FAILED', 'MULTIPLE_ISSUES'
    )
);

COMMENT ON COLUMN import_batches.carteira_id IS
    'Conta financeira de destino do lote; obrigatória a partir de COMMITTING.';
COMMENT ON COLUMN import_records.categoria_id IS
    'Categoria escolhida na revisão; opcional, validada por titular no commit.';
