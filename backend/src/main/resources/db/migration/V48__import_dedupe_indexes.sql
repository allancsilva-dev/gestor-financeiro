-- Deduplicação consulta sempre o mesmo recorte: registros já lançados do titular, por impressão
-- digital ou por id externo. Índice parcial mantém a estrutura pequena mesmo com lote grande em
-- revisão, que é a maior parte da tabela.
CREATE INDEX IF NOT EXISTS ix_import_records_committed_fingerprint
    ON import_records (record_fingerprint)
    WHERE status = 'COMMITTED';

CREATE INDEX IF NOT EXISTS ix_import_records_committed_external_id
    ON import_records (external_id)
    WHERE status = 'COMMITTED' AND external_id IS NOT NULL;

COMMENT ON INDEX ix_import_records_committed_fingerprint IS
    'Dedupe heurístico: impressão digital de registro já lançado.';
COMMENT ON INDEX ix_import_records_committed_external_id IS
    'Dedupe por identidade forte: id externo (FITID) já lançado.';
