CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL
);

CREATE TABLE carteiras (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    tipo VARCHAR(255) NOT NULL,
    saldo DECIMAL(15, 2) NOT NULL DEFAULT 0,
    banco VARCHAR(100),
    usuario_id BIGINT NOT NULL,
    CONSTRAINT fk_carteiras_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE contas (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    nome VARCHAR(255) NOT NULL,
    tipo VARCHAR(255) NOT NULL,
    limite_total DECIMAL(10, 2) DEFAULT 0,
    valor_gasto DECIMAL(10, 2) DEFAULT 0,
    saldo_atual DECIMAL(10, 2) DEFAULT 0,
    dia_fechamento INTEGER,
    dia_vencimento INTEGER,
    ativo BOOLEAN DEFAULT TRUE,
    cor VARCHAR(255),
    CONSTRAINT fk_contas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    nome VARCHAR(255) NOT NULL,
    cor VARCHAR(255),
    icone VARCHAR(255),
    valor_esperado DECIMAL(10, 2) DEFAULT 0,
    valor_gasto DECIMAL(10, 2) DEFAULT 0,
    ativo BOOLEAN DEFAULT TRUE,
    CONSTRAINT fk_categorias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE transacoes (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    conta_id BIGINT,
    categoria_id BIGINT,
    descricao VARCHAR(255) NOT NULL,
    valor_total DECIMAL(10, 2) NOT NULL,
    tipo VARCHAR(255) NOT NULL,
    data DATE NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDENTE',
    parcelado BOOLEAN DEFAULT FALSE,
    total_parcelas INTEGER,
    valor_parcela DECIMAL(10, 2),
    observacoes VARCHAR(255),
    recorrente BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_transacoes_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_transacoes_conta FOREIGN KEY (conta_id) REFERENCES contas(id),
    CONSTRAINT fk_transacoes_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE TABLE parcelas (
    id BIGSERIAL PRIMARY KEY,
    transacao_id BIGINT NOT NULL,
    numero_parcela INTEGER NOT NULL,
    total_parcelas INTEGER NOT NULL,
    valor DECIMAL(10, 2) NOT NULL,
    data_vencimento DATE NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDENTE',
    data_pagamento DATE,
    CONSTRAINT fk_parcelas_transacao FOREIGN KEY (transacao_id) REFERENCES transacoes(id)
);

CREATE TABLE contas_fixas (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    categoria_id BIGINT,
    nome VARCHAR(255) NOT NULL,
    valor_planejado DECIMAL(10, 2) NOT NULL,
    valor_real DECIMAL(10, 2),
    dia_vencimento INTEGER NOT NULL,
    data_proximo_vencimento DATE,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDENTE',
    recorrente BOOLEAN DEFAULT TRUE,
    ativo BOOLEAN DEFAULT TRUE,
    observacoes VARCHAR(500),
    CONSTRAINT fk_contas_fixas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_contas_fixas_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE TABLE metas (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    nome VARCHAR(255) NOT NULL,
    valor_total DECIMAL(10, 2) NOT NULL,
    valor_reservado DECIMAL(10, 2) DEFAULT 0,
    valor_mensal DECIMAL(10, 2),
    data_inicio DATE,
    data_prevista DATE,
    data_conclusao DATE,
    ativa BOOLEAN DEFAULT TRUE,
    cor VARCHAR(255),
    icone VARCHAR(255),
    descricao VARCHAR(500),
    CONSTRAINT fk_metas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    token VARCHAR(500) NOT NULL UNIQUE,
    data_expiracao TIMESTAMP NOT NULL,
    data_criacao TIMESTAMP,
    revogado BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    usuario_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    usado BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_password_reset_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
