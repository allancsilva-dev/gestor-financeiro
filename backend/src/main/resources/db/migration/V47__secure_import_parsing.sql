ALTER TABLE import_batches DROP CONSTRAINT ck_import_batches_format;
ALTER TABLE import_batches ADD CONSTRAINT ck_import_batches_format
    CHECK (format IN ('UNKNOWN', 'CSV', 'OFX'));
ALTER TABLE import_batches ADD COLUMN pending_review_records INTEGER NOT NULL DEFAULT 0;
ALTER TABLE import_batches DROP CONSTRAINT ck_import_batches_counts;
ALTER TABLE import_batches ADD CONSTRAINT ck_import_batches_counts CHECK (
    total_records >= 0 AND valid_records >= 0 AND invalid_records >= 0
    AND pending_review_records >= 0 AND duplicate_records >= 0
    AND valid_records + invalid_records + pending_review_records + duplicate_records <= total_records
);
ALTER TABLE import_batches ADD CONSTRAINT ck_import_batches_unknown_lifecycle
    CHECK (format <> 'UNKNOWN' OR status IN ('RECEIVED', 'FAILED'));
ALTER TABLE import_batches DROP CONSTRAINT ck_import_batches_failure;
ALTER TABLE import_batches ADD CONSTRAINT ck_import_batches_failure CHECK (
    (status = 'FAILED' AND failure_code IN (
        'EMPTY_FILE', 'DETECTION_FAILED', 'UNSUPPORTED_FORMAT', 'FORMAT_MISMATCH',
        'CHARSET_UNSUPPORTED', 'STRUCTURE_LIMIT_EXCEEDED', 'HASH_MISMATCH',
        'FILE_LIMIT_EXCEEDED', 'ROW_LIMIT_EXCEEDED', 'PARSE_FAILED',
        'VALIDATION_FAILED', 'COMMIT_FAILED', 'REVERSAL_FAILED', 'UNKNOWN'
    )) OR (status <> 'FAILED' AND failure_code IS NULL)
);
ALTER TABLE import_records ADD CONSTRAINT ck_import_records_reason_closed CHECK (
    reason_code IS NULL OR reason_code IN (
        'DATE_MISSING', 'DATE_INVALID', 'DATE_AMBIGUOUS',
        'AMOUNT_MISSING', 'AMOUNT_INVALID', 'AMOUNT_AMBIGUOUS', 'AMOUNT_ROUNDING_REQUIRED',
        'CURRENCY_MISSING', 'CURRENCY_INVALID',
        'DIRECTION_MISSING', 'DIRECTION_INVALID', 'DIRECTION_CONFLICT',
        'DESCRIPTION_MISSING', 'DESCRIPTION_INVALID', 'EXTERNAL_ID_INVALID', 'MULTIPLE_ISSUES'
    )
);
