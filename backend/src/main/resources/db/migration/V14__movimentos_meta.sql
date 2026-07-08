CREATE TABLE IF NOT EXISTS movimentos_meta (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    meta_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    valor NUMERIC(15, 2) NOT NULL,
    valor_assinado NUMERIC(15, 2) NOT NULL,
    valor_resultante NUMERIC(15, 2) NOT NULL,
    descricao VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_movimentos_meta_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_movimentos_meta_meta FOREIGN KEY (meta_id) REFERENCES metas (id)
);

CREATE INDEX IF NOT EXISTS idx_movimentos_meta_usuario_meta ON movimentos_meta (usuario_id, meta_id);
CREATE INDEX IF NOT EXISTS idx_movimentos_meta_created_at ON movimentos_meta (created_at);
