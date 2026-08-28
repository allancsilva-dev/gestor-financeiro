ALTER TABLE assistant_channel_events ADD COLUMN expires_at TIMESTAMP;
UPDATE assistant_channel_events SET expires_at = received_at + interval '30 days';
ALTER TABLE assistant_channel_events ALTER COLUMN expires_at SET NOT NULL;
CREATE INDEX idx_assistant_channel_events_expiry ON assistant_channel_events(expires_at);
