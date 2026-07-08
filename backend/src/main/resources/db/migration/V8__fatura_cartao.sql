CREATE TABLE IF NOT EXISTS faturas_cartao (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    conta_id BIGINT NOT NULL REFERENCES contas(id),
    mes INTEGER NOT NULL,
    ano INTEGER NOT NULL,
    data_fechamento DATE,
    data_vencimento DATE,
    valor_total NUMERIC(10,2) DEFAULT 0,
    valor_pago NUMERIC(10,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
    data_pagamento DATE,
    criado_em TIMESTAMP,
    UNIQUE(conta_id, mes, ano)
);

CREATE INDEX IF NOT EXISTS idx_faturas_usuario ON faturas_cartao(usuario_id);
CREATE INDEX IF NOT EXISTS idx_faturas_conta ON faturas_cartao(conta_id);
