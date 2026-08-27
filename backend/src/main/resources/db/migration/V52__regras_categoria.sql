-- Regras de categorização do titular. Vêm antes das heurísticas: quem já disse "mercado da esquina
-- é Alimentação" não deve precisar repetir a cada lançamento.
--
-- Casamento é por texto normalizado — CONTEM, COMECA_COM ou IGUAL. Deliberadamente NÃO existe
-- expressão regular: regex vinda do usuário roda no worker e no request, e Java não tem engine com
-- garantia linear, então uma regra mal escrita viraria negação de serviço.
CREATE TABLE regras_categoria (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuarios (id),
    categoria_id    BIGINT NOT NULL REFERENCES categorias (id),
    padrao          VARCHAR(120) NOT NULL,
    tipo_casamento  VARCHAR(16) NOT NULL,
    tipo_transacao  VARCHAR(10),
    prioridade      SMALLINT NOT NULL DEFAULT 100,
    ativa           BOOLEAN NOT NULL DEFAULT TRUE,
    criada_em       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizada_em   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_regras_categoria_padrao UNIQUE (usuario_id, padrao, tipo_casamento, tipo_transacao),
    CONSTRAINT ck_regras_categoria_casamento CHECK (tipo_casamento IN ('IGUAL', 'COMECA_COM', 'CONTEM')),
    CONSTRAINT ck_regras_categoria_tipo CHECK (tipo_transacao IS NULL OR tipo_transacao IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT ck_regras_categoria_prioridade CHECK (prioridade BETWEEN 1 AND 1000),
    -- Padrão já chega normalizado (minúsculo, sem acento, espaço condensado) e não pode ser vazio.
    CONSTRAINT ck_regras_categoria_padrao CHECK (padrao = lower(padrao) AND length(trim(padrao)) >= 2)
);

CREATE INDEX ix_regras_categoria_titular
    ON regras_categoria (usuario_id, prioridade)
    WHERE ativa;

COMMENT ON TABLE regras_categoria IS
    'Regras determinísticas de categorização por titular, avaliadas antes das heurísticas de sugestão.';
