CREATE TABLE movimentos_carteira (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    carteira_id BIGINT NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    valor NUMERIC(15,2) NOT NULL,
    valor_assinado NUMERIC(15,2) NOT NULL,
    origem VARCHAR(50) NOT NULL,
    referencia_tipo VARCHAR(50),
    referencia_id BIGINT,
    descricao VARCHAR(500),
    data_movimento TIMESTAMP NOT NULL,
    saldo_resultante NUMERIC(15,2) NOT NULL,
    idempotency_key VARCHAR(100),
    moeda CHAR(3) NOT NULL DEFAULT 'BRL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_movimentos_carteira_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_movimentos_carteira_carteira FOREIGN KEY (carteira_id) REFERENCES carteiras(id),
    CONSTRAINT chk_movimentos_carteira_valor_positivo CHECK (valor > 0),
    CONSTRAINT chk_movimentos_carteira_valor_assinado_nao_zero CHECK (valor_assinado <> 0),
    CONSTRAINT chk_movimentos_carteira_moeda_tres_chars CHECK (char_length(moeda) = 3)
);

CREATE INDEX idx_movimentos_carteira_usuario_carteira_data
    ON movimentos_carteira(usuario_id, carteira_id, data_movimento);

CREATE INDEX idx_movimentos_carteira_referencia
    ON movimentos_carteira(origem, referencia_tipo, referencia_id);

CREATE UNIQUE INDEX ux_movimentos_carteira_usuario_idempotency
    ON movimentos_carteira(usuario_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
