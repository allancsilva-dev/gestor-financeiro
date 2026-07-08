ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS conta_fixa_id BIGINT REFERENCES contas_fixas(id);

CREATE INDEX IF NOT EXISTS idx_transacoes_conta_fixa ON transacoes(conta_fixa_id);
