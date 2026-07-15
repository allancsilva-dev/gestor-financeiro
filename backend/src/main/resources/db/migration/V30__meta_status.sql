-- Ciclo de vida de metas (ADR-0004 / PROB-0077): status canonico ATIVA | CONCLUIDA | ARQUIVADA.
-- O boolean `ativa` permanece sincronizado por compatibilidade com clientes publicados.

ALTER TABLE metas
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ATIVA';

-- Backfill pelos dados atuais:
--   data_conclusao presente          -> CONCLUIDA (meta atingiu 100%)
--   ativa = FALSE sem data_conclusao -> ARQUIVADA (exclusao logica antiga)
--   demais                           -> ATIVA
UPDATE metas
SET status = CASE
    WHEN data_conclusao IS NOT NULL THEN 'CONCLUIDA'
    WHEN ativa = FALSE THEN 'ARQUIVADA'
    ELSE 'ATIVA'
END;

-- `ativa` legada pode ser NULL; sincroniza com o status canonico.
UPDATE metas SET ativa = (status = 'ATIVA') WHERE ativa IS NULL;

ALTER TABLE metas
    ADD CONSTRAINT ck_metas_status CHECK (status IN ('ATIVA', 'CONCLUIDA', 'ARQUIVADA'));

CREATE INDEX idx_metas_usuario_status ON metas(usuario_id, status);
