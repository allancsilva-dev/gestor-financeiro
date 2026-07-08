CREATE TABLE IF NOT EXISTS orcamentos_mensais (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    mes INTEGER NOT NULL,
    ano INTEGER NOT NULL,
    valor_total_planejado NUMERIC(10,2) DEFAULT 0,
    criado_em TIMESTAMP,
    atualizado_em TIMESTAMP,
    UNIQUE(usuario_id, mes, ano)
);

CREATE TABLE IF NOT EXISTS orcamentos_categorias (
    id BIGSERIAL PRIMARY KEY,
    orcamento_id BIGINT NOT NULL REFERENCES orcamentos_mensais(id) ON DELETE CASCADE,
    categoria_id BIGINT NOT NULL REFERENCES categorias(id),
    valor_limite NUMERIC(10,2) NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(orcamento_id, categoria_id)
);

CREATE INDEX IF NOT EXISTS idx_orcamentos_mensais_usuario ON orcamentos_mensais(usuario_id);
CREATE INDEX IF NOT EXISTS idx_orcamentos_categorias_orcamento ON orcamentos_categorias(orcamento_id);
