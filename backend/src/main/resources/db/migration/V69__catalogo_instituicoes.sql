-- Fase 6 / PR-F6-04 — catálogo canônico de instituição, e o que ele conserta na deduplicação.
--
-- Problema concreto: a identidade forte da dedup compara `import_batches.institution_code`, que é
-- texto livre vindo da fonte. O `<FI><ORG>` de um arquivo OFX e o código que um agregador usa para
-- o mesmo banco são strings diferentes. Sem um identificador canônico, o mesmo fato entra duas
-- vezes por rotas diferentes e o titular vê a despesa dobrada.
--
-- O catálogo é global e não guarda PII. Endpoint e segredo do provedor ficam em property; o banco
-- guarda só `config_ref`, o nome do prefixo de configuração. Nenhuma rota de escrita é exposta:
-- não existe autorização por papel no sistema, então o catálogo é populado por migration ou carga
-- operacional. Ele nasce vazio, e enquanto estiver vazio a dedup continua caindo no casamento
-- textual de hoje — degrada, não quebra.

CREATE TABLE open_finance_provedores (
    id         BIGSERIAL PRIMARY KEY,
    codigo     VARCHAR(40) NOT NULL,
    nome       VARCHAR(120) NOT NULL,
    tipo       VARCHAR(12) NOT NULL,
    ativo      BOOLEAN NOT NULL DEFAULT TRUE,
    config_ref VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_open_finance_provedores_codigo UNIQUE (codigo),
    CONSTRAINT ck_open_finance_provedores_tipo CHECK (tipo IN ('AGREGADOR', 'DIRETO', 'FAKE')),
    CONSTRAINT ck_open_finance_provedores_codigo CHECK (codigo ~ '^[A-Z0-9._-]{1,40}$')
);

CREATE TABLE instituicoes_financeiras (
    id              BIGSERIAL PRIMARY KEY,
    provedor_id     BIGINT REFERENCES open_finance_provedores(id),
    codigo          VARCHAR(80) NOT NULL,
    nome            VARCHAR(120) NOT NULL,
    ispb            VARCHAR(8),
    suporta_contas  BOOLEAN NOT NULL DEFAULT TRUE,
    suporta_cartoes BOOLEAN NOT NULL DEFAULT FALSE,
    ativa           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_instituicoes_codigo UNIQUE (codigo),
    CONSTRAINT ck_instituicoes_codigo CHECK (codigo ~ '^[A-Z0-9._-]{1,80}$'),
    CONSTRAINT ck_instituicoes_ispb CHECK (ispb IS NULL OR ispb ~ '^[0-9]{8}$')
);

-- Um banco tem vários nomes conforme quem fala: FITID de OFX, código do agregador, sigla do ISPB.
-- Todos precisam apontar para a mesma linha, senão o catálogo não resolve o problema que motivou
-- sua criação.
CREATE TABLE instituicao_aliases (
    id             BIGSERIAL PRIMARY KEY,
    instituicao_id BIGINT NOT NULL REFERENCES instituicoes_financeiras(id) ON DELETE CASCADE,
    alias          VARCHAR(80) NOT NULL,

    CONSTRAINT ux_instituicao_aliases_alias UNIQUE (alias),
    CONSTRAINT ck_instituicao_aliases_alias CHECK (alias ~ '^[A-Z0-9._-]{1,80}$')
);

CREATE INDEX ix_instituicao_aliases_instituicao ON instituicao_aliases (instituicao_id);

-- Instituição canônica do lote. Nulo enquanto o catálogo não conhecer o código detectado; a dedup
-- trata os dois casos.
ALTER TABLE import_batches ADD COLUMN instituicao_id BIGINT REFERENCES instituicoes_financeiras(id);
CREATE INDEX ix_import_batches_instituicao ON import_batches (instituicao_id)
    WHERE instituicao_id IS NOT NULL;

-- Dois motivos novos de duplicidade. Sem eles a prévia diria só "duplicado", e o titular não teria
-- como distinguir "isto já está lançado" de "isto está esperando sua revisão em outro lote" ou de
-- "você já reverteu isto de propósito".
ALTER TABLE import_records DROP CONSTRAINT IF EXISTS ck_import_records_reason_closed;
ALTER TABLE import_records ADD CONSTRAINT ck_import_records_reason_closed CHECK (
    reason_code IS NULL OR reason_code IN (
        'DATE_MISSING', 'DATE_INVALID', 'DATE_AMBIGUOUS',
        'AMOUNT_MISSING', 'AMOUNT_INVALID', 'AMOUNT_AMBIGUOUS', 'AMOUNT_ROUNDING_REQUIRED',
        'CURRENCY_MISSING', 'CURRENCY_INVALID', 'CURRENCY_UNSUPPORTED',
        'DIRECTION_MISSING', 'DIRECTION_INVALID', 'DIRECTION_CONFLICT',
        'DESCRIPTION_MISSING', 'DESCRIPTION_INVALID', 'EXTERNAL_ID_INVALID',
        'COMMIT_FAILED', 'MULTIPLE_ISSUES',
        'DUPLICATE_PENDING_BATCH', 'DUPLICATE_REVERSED'
    )
);

-- A dedup deixa de olhar só o que foi lançado. Índices parciais acompanham os dois recortes novos:
-- o que foi revertido de propósito, e o que ainda espera revisão.
CREATE INDEX IF NOT EXISTS ix_import_records_reversed_external_id
    ON import_records (external_id)
    WHERE status = 'REVERSED' AND external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_import_records_pendente_external_id
    ON import_records (external_id)
    WHERE status IN ('VALID', 'PENDING_REVIEW', 'APPROVED') AND external_id IS NOT NULL;

COMMENT ON TABLE open_finance_provedores IS
    'Provedores de dados financeiros. Endpoint e segredo ficam em property; aqui so o config_ref.';
COMMENT ON TABLE instituicao_aliases IS
    'Nomes alternativos da mesma instituicao (FITID de OFX, codigo de agregador), para a dedup convergir.';
COMMENT ON COLUMN import_batches.instituicao_id IS
    'Instituicao canonica do lote; nulo quando o catalogo nao conhece o codigo detectado.';
