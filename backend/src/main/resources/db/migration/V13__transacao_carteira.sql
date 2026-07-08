ALTER TABLE transacoes
    ADD COLUMN IF NOT EXISTS carteira_id BIGINT,
    ADD COLUMN IF NOT EXISTS ativa BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT fk_transacoes_carteira
        FOREIGN KEY (carteira_id) REFERENCES carteiras (id)
        ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_transacoes_carteira_id ON transacoes (carteira_id);
