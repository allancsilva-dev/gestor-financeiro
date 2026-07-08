CREATE TABLE anexos (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    tipo VARCHAR(100) NOT NULL,
    tamanho BIGINT NOT NULL,
    caminho TEXT NOT NULL,
    transacao_id BIGINT NOT NULL REFERENCES transacoes(id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    data_upload TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_anexos_transacao ON anexos(transacao_id);
CREATE INDEX idx_anexos_usuario ON anexos(usuario_id);
