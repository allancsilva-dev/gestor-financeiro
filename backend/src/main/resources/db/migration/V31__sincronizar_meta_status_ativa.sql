-- Corrige divergências deixadas por clientes/versões anteriores e torna a compatibilidade
-- entre o status canônico e o boolean legado uma invariável do banco.
UPDATE metas SET ativa = (status = 'ATIVA');

ALTER TABLE metas
    ALTER COLUMN ativa SET NOT NULL;

ALTER TABLE metas
    ADD CONSTRAINT ck_metas_status_ativa
    CHECK (ativa = (status = 'ATIVA'));
