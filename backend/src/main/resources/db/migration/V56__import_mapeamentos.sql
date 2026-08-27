-- Mapeamento de colunas por titular. O extrato de cada banco chega com um cabeçalho diferente, e a
-- lista fixa de apelidos do connector nunca cobre todos: aqui o titular diz qual coluna é qual.
CREATE TABLE import_mapeamentos (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuarios (id),
    nome            VARCHAR(80) NOT NULL,
    instituicao     VARCHAR(80),
    delimitador     VARCHAR(1),
    colunas         JSONB NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_import_mapeamentos_nome UNIQUE (usuario_id, nome),
    CONSTRAINT ck_import_mapeamentos_nome CHECK (length(trim(nome)) >= 2),
    CONSTRAINT ck_import_mapeamentos_delimitador CHECK (
        delimitador IS NULL OR delimitador IN (',', ';', '|', E'\t')
    ),
    -- Sem data e valor não há lançamento: mapeamento incompleto não deve nem ser salvo.
    CONSTRAINT ck_import_mapeamentos_colunas CHECK (
        colunas ? 'date' AND colunas ? 'amount'
    )
);

CREATE INDEX ix_import_mapeamentos_titular ON import_mapeamentos (usuario_id);

COMMENT ON TABLE import_mapeamentos IS
    'Perfis de mapeamento de colunas de CSV por titular, usados quando o cabeçalho não é reconhecido.';
