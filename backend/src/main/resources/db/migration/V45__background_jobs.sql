-- Fila durável compartilhada por importações, fechamentos, alertas e conectores.
-- PostgreSQL é fonte da verdade; workers podem rodar em múltiplas réplicas.
CREATE TABLE background_jobs (
    id              BIGSERIAL PRIMARY KEY,
    job_key         VARCHAR(180) NOT NULL,
    job_type        VARCHAR(80) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload_version SMALLINT NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority        SMALLINT NOT NULL DEFAULT 0,
    available_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner     VARCHAR(100),
    lease_until     TIMESTAMPTZ,
    attempts        INTEGER NOT NULL DEFAULT 0,
    max_attempts    INTEGER NOT NULL DEFAULT 5,
    last_error      VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMPTZ,

    CONSTRAINT ux_background_jobs_key UNIQUE (job_key),
    CONSTRAINT ck_background_jobs_status CHECK (
        status IN ('PENDING', 'RUNNING', 'RETRY', 'COMPLETED', 'DEAD_LETTER', 'CANCELLED')
    ),
    CONSTRAINT ck_background_jobs_priority CHECK (priority BETWEEN -100 AND 100),
    CONSTRAINT ck_background_jobs_attempts CHECK (attempts >= 0 AND max_attempts BETWEEN 1 AND 100),
    CONSTRAINT ck_background_jobs_payload_version CHECK (payload_version > 0),
    CONSTRAINT ck_background_jobs_lease CHECK (
        (status = 'RUNNING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'RUNNING' AND lease_owner IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT ck_background_jobs_finished CHECK (
        (status IN ('COMPLETED', 'DEAD_LETTER', 'CANCELLED') AND finished_at IS NOT NULL)
        OR (status NOT IN ('COMPLETED', 'DEAD_LETTER', 'CANCELLED') AND finished_at IS NULL)
    )
);

CREATE INDEX ix_background_jobs_claim
    ON background_jobs (priority DESC, available_at, id)
    WHERE status IN ('PENDING', 'RETRY', 'RUNNING');

CREATE INDEX ix_background_jobs_lease
    ON background_jobs (lease_until)
    WHERE status = 'RUNNING';

COMMENT ON TABLE background_jobs IS
    'Fila transacional durável. Claim usa FOR UPDATE SKIP LOCKED e lease recuperável.';
