-- Rollover de orçamento (ADR-0014). Não confundir com o rollover de fatura da V25: aquele carrega
-- dívida de cartão; este carrega sobra ou excesso de um limite de categoria para o mês seguinte.

ALTER TABLE orcamentos_categorias
    ADD COLUMN IF NOT EXISTS politica_rollover VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE orcamentos_categorias
    ADD CONSTRAINT ck_orcamentos_categorias_politica CHECK (
        politica_rollover IN ('NONE', 'SURPLUS_ONLY', 'DEFICIT_ONLY', 'BOTH')
    );

-- Fechamento é a memória do mês: o que foi planejado, o que veio de trás, o que foi gasto e o que
-- passa adiante — com a política e a versão da regra que valiam na hora. Mês fechado não é
-- reescrito quando a regra muda depois (ADR-0010, ADR-0014).
CREATE TABLE orcamento_fechamentos (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT NOT NULL REFERENCES usuarios (id),
    categoria_id   BIGINT NOT NULL REFERENCES categorias (id),
    mes            SMALLINT NOT NULL,
    ano            SMALLINT NOT NULL,
    base           NUMERIC(12, 2) NOT NULL,
    carry_in       NUMERIC(12, 2) NOT NULL,
    gasto          NUMERIC(12, 2) NOT NULL,
    resultado      NUMERIC(12, 2) NOT NULL,
    carry_out      NUMERIC(12, 2) NOT NULL,
    politica       VARCHAR(16) NOT NULL,
    regra_versao   SMALLINT NOT NULL,
    fechado_em     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_orcamento_fechamentos_competencia UNIQUE (usuario_id, categoria_id, ano, mes),
    CONSTRAINT ck_orcamento_fechamentos_mes CHECK (mes BETWEEN 1 AND 12),
    CONSTRAINT ck_orcamento_fechamentos_ano CHECK (ano BETWEEN 2000 AND 2200),
    CONSTRAINT ck_orcamento_fechamentos_gasto CHECK (gasto >= 0),
    CONSTRAINT ck_orcamento_fechamentos_politica CHECK (
        politica IN ('NONE', 'SURPLUS_ONLY', 'DEFICIT_ONLY', 'BOTH')
    ),
    CONSTRAINT ck_orcamento_fechamentos_regra CHECK (regra_versao >= 1),
    -- Aritmética conferida pelo banco: resultado é sempre base + o que veio menos o que saiu.
    CONSTRAINT ck_orcamento_fechamentos_resultado CHECK (resultado = base + carry_in - gasto),
    -- O que passa adiante nunca inventa valor: é o resultado, zero, ou um dos lados dele.
    CONSTRAINT ck_orcamento_fechamentos_carry_out CHECK (
        (politica = 'NONE' AND carry_out = 0)
        OR (politica = 'BOTH' AND carry_out = resultado)
        OR (politica = 'SURPLUS_ONLY' AND carry_out = GREATEST(resultado, 0))
        OR (politica = 'DEFICIT_ONLY' AND carry_out = LEAST(resultado, 0))
    )
);

CREATE INDEX ix_orcamento_fechamentos_titular_competencia
    ON orcamento_fechamentos (usuario_id, ano, mes);

COMMENT ON TABLE orcamento_fechamentos IS
    'Fechamento mensal de orçamento por categoria: base, carryIn, gasto, resultado e carryOut, com a política e a versão da regra aplicadas.';
