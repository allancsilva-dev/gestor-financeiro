-- Buckets de rate limit compartilhados entre instancias da API (PROB-0055).
-- Cada request protegida trava a linha da chave por transacao curta, atualiza
-- attempt_count e evita reset por restart ou bypass por replica diferente.

CREATE TABLE IF NOT EXISTS rate_limit_buckets (
    rate_key VARCHAR(256) PRIMARY KEY,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL,
    CONSTRAINT chk_rate_limit_attempt_count_non_negative CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_rate_limit_buckets_window_start
    ON rate_limit_buckets (window_start);
