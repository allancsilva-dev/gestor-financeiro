CREATE TABLE ativos (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    ticker VARCHAR(20) NOT NULL,
    nome VARCHAR(255) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    quantidade NUMERIC(18,8) NOT NULL DEFAULT 0,
    valor_atual NUMERIC(10,2),
    custo_total NUMERIC(18,2) DEFAULT 0,
    version BIGINT DEFAULT 0
);

CREATE TABLE movimentacoes_ativo (
    id BIGSERIAL PRIMARY KEY,
    ativo_id BIGINT NOT NULL REFERENCES ativos(id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo VARCHAR(20) NOT NULL,
    data DATE NOT NULL,
    quantidade NUMERIC(18,8) NOT NULL,
    preco_unitario NUMERIC(10,2) NOT NULL,
    valor_total NUMERIC(10,2)
);

CREATE INDEX idx_ativos_usuario ON ativos(usuario_id);
CREATE INDEX idx_movimentacoes_ativo ON movimentacoes_ativo(ativo_id);
CREATE INDEX idx_movimentacoes_usuario ON movimentacoes_ativo(usuario_id);
