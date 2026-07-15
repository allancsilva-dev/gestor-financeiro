-- =============================================================================
-- V33 — PR-F2-03: operacao financeira como agrupador do ledger (ADR-0009)
-- =============================================================================
-- Fase EXPAND (ADR-0015): tabela nova + FKs aditivas (nullable para legado).
-- Nao altera nenhuma linha existente; preencher operacao_id em registro legado
-- e enriquecimento de metadado permitido (conteudo financeiro intocavel).
-- =============================================================================

CREATE TABLE operacoes_financeiras (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    tipo VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
    politica VARCHAR(20) NOT NULL DEFAULT 'CAIXA',
    origem VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    data_operacao TIMESTAMP NOT NULL,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR(100),
    request_hash VARCHAR(64),
    estorno_de_id BIGINT REFERENCES operacoes_financeiras(id),
    descricao VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT ck_operacoes_tipo CHECK (tipo IN (
        'TRANSACAO', 'TRANSFERENCIA', 'RESERVA_META', 'RESGATE_META',
        'COMPRA_CARTAO', 'PAGAMENTO_FATURA', 'INVESTIMENTO', 'AJUSTE', 'ESTORNO')),
    CONSTRAINT ck_operacoes_status CHECK (status IN ('CONFIRMADA', 'ESTORNADA')),
    CONSTRAINT ck_operacoes_politica CHECK (politica IN ('CAIXA', 'COMPETENCIA')),
    CONSTRAINT ck_operacoes_origem CHECK (origem IN ('MANUAL', 'SISTEMA', 'CSV', 'OFX', 'INTEGRACAO')),
    -- estorno sempre referencia a operacao original (ADR-0009)
    CONSTRAINT ck_operacoes_estorno_ref CHECK (tipo <> 'ESTORNO' OR estorno_de_id IS NOT NULL)
);

-- Idempotencia na OPERACAO: uma chave por usuario (ADR-0009); payload igual
-- retorna a original, payload diferente retorna 409 (comparacao por request_hash).
CREATE UNIQUE INDEX ux_operacoes_usuario_idempotency
    ON operacoes_financeiras(usuario_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_operacoes_usuario ON operacoes_financeiras(usuario_id);
CREATE INDEX idx_operacoes_estorno_de ON operacoes_financeiras(estorno_de_id)
    WHERE estorno_de_id IS NOT NULL;

-- Lancamentos 1..N por operacao: FKs aditivas, nullable para o legado
ALTER TABLE movimentos_carteira
    ADD COLUMN operacao_id BIGINT REFERENCES operacoes_financeiras(id);
ALTER TABLE fatura_lancamentos
    ADD COLUMN operacao_id BIGINT REFERENCES operacoes_financeiras(id);
ALTER TABLE movimentacoes_ativo
    ADD COLUMN operacao_id BIGINT REFERENCES operacoes_financeiras(id);
ALTER TABLE movimentos_meta
    ADD COLUMN operacao_id BIGINT REFERENCES operacoes_financeiras(id);

CREATE INDEX idx_movimentos_carteira_operacao ON movimentos_carteira(operacao_id)
    WHERE operacao_id IS NOT NULL;
CREATE INDEX idx_fatura_lancamentos_operacao ON fatura_lancamentos(operacao_id)
    WHERE operacao_id IS NOT NULL;
CREATE INDEX idx_movimentacoes_ativo_operacao ON movimentacoes_ativo(operacao_id)
    WHERE operacao_id IS NOT NULL;
CREATE INDEX idx_movimentos_meta_operacao ON movimentos_meta(operacao_id)
    WHERE operacao_id IS NOT NULL;

-- Historico explicito de pagamentos de fatura (usado a partir do PR-F2-08;
-- entidade JPA entra junto com o fluxo de pagamento)
CREATE TABLE fatura_pagamentos (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    fatura_id BIGINT NOT NULL REFERENCES faturas_cartao(id),
    carteira_id BIGINT NOT NULL REFERENCES carteiras(id),
    operacao_id BIGINT REFERENCES operacoes_financeiras(id),
    valor NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    data_pagamento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fatura_pagamentos_fatura ON fatura_pagamentos(fatura_id);
CREATE INDEX idx_fatura_pagamentos_usuario ON fatura_pagamentos(usuario_id);
