-- Fase 6 / PR-F6-03 — abre o lote de importação para o conector regulado (ADR-0019).
--
-- Duas coisas distintas que não podem virar uma só: `format` diz como o conteúdo é lido,
-- `origin` diz de onde ele veio. Inferir proveniência a partir do formato funcionaria hoje,
-- com um único formato de conector, e passaria a mentir no dia em que houver dois.
--
-- `instituicao_id` não entra aqui: a chave estrangeira aponta para `instituicoes_financeiras`,
-- que nasce na V69. Coluna e FK entram juntas lá, para não existir janela com coluna solta.

ALTER TABLE import_batches DROP CONSTRAINT ck_import_batches_format;
ALTER TABLE import_batches ADD CONSTRAINT ck_import_batches_format
    CHECK (format IN ('UNKNOWN', 'CSV', 'OFX', 'OPEN_FINANCE'));

ALTER TABLE import_batches ADD COLUMN origin VARCHAR(12) NOT NULL DEFAULT 'UPLOAD';
ALTER TABLE import_batches ADD CONSTRAINT ck_import_batches_origin
    CHECK (origin IN ('UPLOAD', 'CONNECTOR'));

-- Lote de conector nunca pode se apresentar como envio manual. A recíproca não é exigida:
-- um conector futuro pode entregar CSV ou OFX de verdade, e aí `origin` é o que distingue.
ALTER TABLE import_batches ADD CONSTRAINT ck_import_batches_origin_formato
    CHECK (format <> 'OPEN_FINANCE' OR origin = 'CONNECTOR');

COMMENT ON COLUMN import_batches.origin IS
    'Proveniencia do lote: UPLOAD (titular enviou arquivo) ou CONNECTOR (sincronizacao automatica).';
