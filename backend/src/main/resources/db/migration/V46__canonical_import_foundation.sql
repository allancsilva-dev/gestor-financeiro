CREATE TABLE import_batches (
    id                BIGSERIAL PRIMARY KEY,
    usuario_id        BIGINT NOT NULL REFERENCES usuarios(id),
    format            VARCHAR(12) NOT NULL,
    institution_code  VARCHAR(80),
    file_sha256       VARCHAR(64) NOT NULL,
    idempotency_key   VARCHAR(100),
    status            VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
    total_records     INTEGER NOT NULL DEFAULT 0,
    valid_records     INTEGER NOT NULL DEFAULT 0,
    invalid_records   INTEGER NOT NULL DEFAULT 0,
    duplicate_records INTEGER NOT NULL DEFAULT 0,
    failure_code      VARCHAR(80),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_import_batches_format CHECK (format IN ('CSV', 'OFX')),
    CONSTRAINT ck_import_batches_status CHECK (status IN (
        'RECEIVED', 'PARSED', 'PENDING_REVIEW', 'READY_TO_COMMIT',
        'COMMITTING', 'COMMITTED', 'FAILED', 'REVERSED'
    )),
    CONSTRAINT ck_import_batches_sha256 CHECK (file_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_import_batches_idempotency CHECK (
        idempotency_key IS NULL OR idempotency_key ~ '^[A-Za-z0-9._:-]{1,100}$'
    ),
    CONSTRAINT ck_import_batches_counts CHECK (
        total_records >= 0 AND valid_records >= 0 AND invalid_records >= 0
        AND duplicate_records >= 0
        AND valid_records + invalid_records + duplicate_records <= total_records
    ),
    CONSTRAINT ck_import_batches_failure CHECK (
        (status = 'FAILED' AND failure_code IN (
            'DETECTION_FAILED', 'UNSUPPORTED_FORMAT', 'FILE_LIMIT_EXCEEDED',
            'ROW_LIMIT_EXCEEDED', 'PARSE_FAILED', 'VALIDATION_FAILED',
            'COMMIT_FAILED', 'REVERSAL_FAILED', 'UNKNOWN'
        ))
        OR (status <> 'FAILED' AND failure_code IS NULL)
    )
);

CREATE UNIQUE INDEX ux_import_batches_user_idempotency
    ON import_batches (usuario_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_import_batches_user_status_created
    ON import_batches (usuario_id, status, created_at DESC, id);
CREATE INDEX ix_import_batches_user_file_sha
    ON import_batches (usuario_id, file_sha256);

CREATE TABLE import_records (
    id                     BIGSERIAL PRIMARY KEY,
    batch_id               BIGINT NOT NULL REFERENCES import_batches(id) ON DELETE CASCADE,
    source_line            INTEGER NOT NULL,
    external_id            VARCHAR(180),
    record_fingerprint     VARCHAR(64) NOT NULL,
    occurred_on            DATE,
    normalized_description VARCHAR(500),
    amount                 NUMERIC(19,2),
    currency               VARCHAR(3),
    direction              VARCHAR(10),
    status                 VARCHAR(24) NOT NULL,
    reason_code            VARCHAR(80),
    transacao_id           BIGINT REFERENCES transacoes(id),
    version                BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ux_import_records_batch_line UNIQUE (batch_id, source_line),
    CONSTRAINT ck_import_records_source_line CHECK (source_line > 0),
    CONSTRAINT ck_import_records_fingerprint CHECK (record_fingerprint ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_import_records_currency CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_import_records_direction CHECK (direction IS NULL OR direction IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT ck_import_records_status CHECK (status IN (
        'VALID', 'INVALID', 'DUPLICATE', 'PENDING_REVIEW', 'APPROVED', 'COMMITTED', 'REVERSED'
    )),
    CONSTRAINT ck_import_records_reason CHECK (
        reason_code IS NULL OR reason_code ~ '^[A-Z0-9_]{1,80}$'
    ),
    CONSTRAINT ck_import_records_committed_link CHECK (
        status NOT IN ('COMMITTED', 'REVERSED') OR transacao_id IS NOT NULL
    )
);

CREATE INDEX ix_import_records_batch_status_line
    ON import_records (batch_id, status, source_line);
CREATE INDEX ix_import_records_fingerprint
    ON import_records (record_fingerprint);
CREATE INDEX ix_import_records_external_id
    ON import_records (external_id)
    WHERE external_id IS NOT NULL;
CREATE INDEX ix_import_records_transaction
    ON import_records (transacao_id)
    WHERE transacao_id IS NOT NULL;

COMMENT ON TABLE import_batches IS
    'Lifecycle auditável de importação; arquivo bruto não é persistido.';
COMMENT ON TABLE import_records IS
    'Staging canônico por registro, independente do formato de origem.';
