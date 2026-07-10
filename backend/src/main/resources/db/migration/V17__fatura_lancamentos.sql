CREATE TABLE IF NOT EXISTS fatura_lancamentos (
    id BIGSERIAL PRIMARY KEY,
    fatura_id BIGINT NOT NULL REFERENCES faturas_cartao(id),
    transacao_id BIGINT NOT NULL REFERENCES transacoes(id),
    descricao VARCHAR(255) NOT NULL,
    valor NUMERIC(10,2) NOT NULL,
    data_compra DATE NOT NULL,
    parcela_numero INTEGER,
    total_parcelas INTEGER,
    criado_em TIMESTAMP,
    UNIQUE(fatura_id, transacao_id, parcela_numero)
);

CREATE INDEX IF NOT EXISTS idx_fatura_lancamentos_fatura ON fatura_lancamentos(fatura_id);
CREATE INDEX IF NOT EXISTS idx_fatura_lancamentos_transacao ON fatura_lancamentos(transacao_id);
