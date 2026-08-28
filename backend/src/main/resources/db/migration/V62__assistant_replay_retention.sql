ALTER TABLE assistant_invocations ADD COLUMN expires_at TIMESTAMP;
CREATE INDEX idx_assistant_invocations_expiry ON assistant_invocations(expires_at)
  WHERE expires_at IS NOT NULL;
