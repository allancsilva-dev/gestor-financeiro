ALTER TABLE assistant_whatsapp_links
  ADD COLUMN code_ciphertext TEXT,
  ADD COLUMN idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX ux_assistant_whatsapp_link_idempotency
  ON assistant_whatsapp_links(usuario_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

ALTER TABLE assistant_whatsapp_links ADD CONSTRAINT ck_assistant_whatsapp_link_replay
  CHECK ((idempotency_key IS NULL AND code_ciphertext IS NULL)
      OR (idempotency_key IS NOT NULL AND code_ciphertext IS NOT NULL));
