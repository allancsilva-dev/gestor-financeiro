ALTER TABLE import_batches ADD COLUMN declared_opening_balance NUMERIC(19,2);
ALTER TABLE import_batches ADD COLUMN declared_closing_balance NUMERIC(19,2);
ALTER TABLE import_batches ADD COLUMN declared_movement_total NUMERIC(19,2);
ALTER TABLE import_batches ADD COLUMN balance_reconciliation VARCHAR(12) NOT NULL DEFAULT 'UNAVAILABLE';
ALTER TABLE import_batches ADD COLUMN balance_mismatch_acknowledged BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE import_batches ADD CONSTRAINT ck_import_balance_reconciliation
  CHECK (balance_reconciliation IN ('MATCH', 'MISMATCH', 'UNAVAILABLE'));
