ALTER TABLE assistant_messages
  ADD COLUMN idempotency_key VARCHAR(100),
  ADD COLUMN request_hash VARCHAR(64),
  ADD COLUMN response_json TEXT;

CREATE UNIQUE INDEX ux_assistant_message_idempotency
  ON assistant_messages(usuario_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

ALTER TABLE assistant_messages ADD CONSTRAINT ck_assistant_message_idempotency
  CHECK ((idempotency_key IS NULL AND request_hash IS NULL AND response_json IS NULL)
      OR (idempotency_key IS NOT NULL AND request_hash IS NOT NULL AND response_json IS NOT NULL));
