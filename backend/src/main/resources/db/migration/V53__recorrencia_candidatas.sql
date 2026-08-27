-- Padrões de repetição detectados no histórico. São SUGESTÕES: viram recorrência de verdade só
-- quando o titular confirma. O app nunca cria compromisso financeiro sozinho.
CREATE TABLE recorrencia_candidatas (
    id                    BIGSERIAL PRIMARY KEY,
    usuario_id            BIGINT NOT NULL REFERENCES usuarios (id),
    categoria_id          BIGINT REFERENCES categorias (id),
    conta_fixa_id         BIGINT REFERENCES contas_fixas (id),
    descricao_normalizada VARCHAR(200) NOT NULL,
    descricao_exibicao    VARCHAR(200) NOT NULL,
    tipo                  VARCHAR(10) NOT NULL,
    valor_medio           NUMERIC(12, 2) NOT NULL,
    dia_tipico            SMALLINT NOT NULL,
    ocorrencias           SMALLINT NOT NULL,
    primeira_data         DATE NOT NULL,
    ultima_data           DATE NOT NULL,
    status                VARCHAR(12) NOT NULL DEFAULT 'SUGERIDA',
    detectada_em          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decidida_em           TIMESTAMPTZ,

    CONSTRAINT ux_recorrencia_candidatas_titular_descricao UNIQUE (usuario_id, descricao_normalizada, tipo),
    CONSTRAINT ck_recorrencia_candidatas_status CHECK (status IN ('SUGERIDA', 'CONFIRMADA', 'DESCARTADA')),
    CONSTRAINT ck_recorrencia_candidatas_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT ck_recorrencia_candidatas_dia CHECK (dia_tipico BETWEEN 1 AND 31),
    CONSTRAINT ck_recorrencia_candidatas_ocorrencias CHECK (ocorrencias >= 2),
    CONSTRAINT ck_recorrencia_candidatas_valor CHECK (valor_medio > 0),
    CONSTRAINT ck_recorrencia_candidatas_periodo CHECK (ultima_data >= primeira_data),
    -- Confirmada sem recorrência criada é estado impossível: a confirmação é o que a gera.
    CONSTRAINT ck_recorrencia_candidatas_confirmada CHECK (
        status <> 'CONFIRMADA' OR conta_fixa_id IS NOT NULL
    ),
    CONSTRAINT ck_recorrencia_candidatas_decisao CHECK (
        (status = 'SUGERIDA' AND decidida_em IS NULL) OR (status <> 'SUGERIDA' AND decidida_em IS NOT NULL)
    )
);

CREATE INDEX ix_recorrencia_candidatas_sugeridas
    ON recorrencia_candidatas (usuario_id, ultima_data DESC)
    WHERE status = 'SUGERIDA';

COMMENT ON TABLE recorrencia_candidatas IS
    'Padrões de repetição detectados no histórico, aguardando decisão do titular.';
