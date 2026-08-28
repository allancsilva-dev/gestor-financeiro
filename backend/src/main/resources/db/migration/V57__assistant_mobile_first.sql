-- ADR-0017: persistência auditável do assistente; conteúdo nunca viaja em background_jobs.
ALTER TABLE operacoes_financeiras DROP CONSTRAINT ck_operacoes_origem;
ALTER TABLE operacoes_financeiras ADD CONSTRAINT ck_operacoes_origem
  CHECK (origem IN ('MANUAL','SISTEMA','CSV','OFX','INTEGRACAO','ASSISTENTE'));

CREATE TABLE assistant_conversations (
  id BIGSERIAL PRIMARY KEY, usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
  channel VARCHAR(20) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_assistant_conversations_user ON assistant_conversations(usuario_id, updated_at DESC);

CREATE TABLE assistant_messages (
  id BIGSERIAL PRIMARY KEY, conversation_id BIGINT NOT NULL REFERENCES assistant_conversations(id),
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id), role VARCHAR(16) NOT NULL,
  content VARCHAR(2000) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL, CONSTRAINT ck_assistant_message_role CHECK (role IN ('USER','ASSISTANT'))
);
CREATE INDEX idx_assistant_messages_conversation ON assistant_messages(conversation_id, created_at);
CREATE INDEX idx_assistant_messages_expiry ON assistant_messages(expires_at);

CREATE TABLE assistant_drafts (
  id BIGSERIAL PRIMARY KEY, conversation_id BIGINT REFERENCES assistant_conversations(id),
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id), version BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL, tipo VARCHAR(20), valor NUMERIC(15,2), descricao VARCHAR(500),
  data DATE, carteira_id BIGINT REFERENCES carteiras(id), categoria_id BIGINT REFERENCES categorias(id),
  provider VARCHAR(30) NOT NULL DEFAULT 'DETERMINISTIC', model VARCHAR(80) NOT NULL DEFAULT 'RULE_BASED',
  prompt_version VARCHAR(30) NOT NULL DEFAULT 'deterministic-v1', schema_version VARCHAR(30) NOT NULL DEFAULT 'transaction-draft-v1',
  question_count SMALLINT NOT NULL DEFAULT 0, input_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMP NOT NULL,
  CONSTRAINT ck_assistant_draft_status CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','EXPIRED')),
  CONSTRAINT ck_assistant_question_count CHECK (question_count BETWEEN 0 AND 1)
);
CREATE INDEX idx_assistant_drafts_user ON assistant_drafts(usuario_id, created_at DESC);
CREATE INDEX idx_assistant_drafts_expiry ON assistant_drafts(expires_at);

CREATE TABLE assistant_invocations (
  id BIGSERIAL PRIMARY KEY, usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
  conversation_id BIGINT REFERENCES assistant_conversations(id), provider VARCHAR(30) NOT NULL,
  model VARCHAR(80) NOT NULL, operation VARCHAR(30) NOT NULL, result VARCHAR(30) NOT NULL,
  prompt_version VARCHAR(30) NOT NULL, schema_version VARCHAR(30) NOT NULL,
  cost_usd NUMERIC(12,6), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_assistant_invocations_user ON assistant_invocations(usuario_id, created_at DESC);

CREATE TABLE assistant_confirmations (
  id BIGSERIAL PRIMARY KEY, draft_id BIGINT NOT NULL, usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
  draft_version BIGINT NOT NULL,
  operacao_id BIGINT NOT NULL REFERENCES operacoes_financeiras(id), transacao_id BIGINT NOT NULL REFERENCES transacoes(id),
  snapshot_json TEXT NOT NULL, input_hash VARCHAR(64) NOT NULL, provider VARCHAR(30) NOT NULL,
  model VARCHAR(80) NOT NULL, prompt_version VARCHAR(30) NOT NULL, schema_version VARCHAR(30) NOT NULL,
  corrections_json TEXT NOT NULL DEFAULT '{}', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ux_assistant_confirmation_draft UNIQUE (draft_id)
);
CREATE INDEX idx_assistant_confirmations_user ON assistant_confirmations(usuario_id, created_at DESC);

CREATE TABLE assistant_recommendations (
  id BIGSERIAL PRIMARY KEY, usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
  rule_code VARCHAR(50) NOT NULL, facts_json TEXT NOT NULL,
  period_start DATE NOT NULL, period_end DATE NOT NULL, sources_json TEXT NOT NULL,
  action_type VARCHAR(20) NOT NULL, action_target VARCHAR(180) NOT NULL,
  explanation VARCHAR(1000) NOT NULL, feedback VARCHAR(12),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_assistant_recommendation_action CHECK (action_type IN ('OPEN_SCREEN','OPEN_DRAFT')),
  CONSTRAINT ck_assistant_recommendation_feedback CHECK (feedback IS NULL OR feedback IN ('HELPFUL','NOT_HELPFUL'))
);
CREATE INDEX idx_assistant_recommendations_user ON assistant_recommendations(usuario_id, created_at DESC);

CREATE TABLE assistant_channel_events (
  id BIGSERIAL PRIMARY KEY, usuario_id BIGINT REFERENCES usuarios(id), channel VARCHAR(20) NOT NULL,
  external_id VARCHAR(180) NOT NULL UNIQUE, status VARCHAR(30) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL, payload_ciphertext TEXT NOT NULL, payload_key_version VARCHAR(30) NOT NULL,
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at TIMESTAMP
);

CREATE TABLE assistant_whatsapp_links (
  id BIGSERIAL PRIMARY KEY, usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
  conversation_id BIGINT REFERENCES assistant_conversations(id),
  wa_ciphertext TEXT, wa_key_version VARCHAR(30), wa_hmac VARCHAR(64),
  code_hash VARCHAR(64) NOT NULL, expires_at TIMESTAMP NOT NULL, used_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_assistant_whatsapp_hmac ON assistant_whatsapp_links(wa_hmac) WHERE wa_hmac IS NOT NULL;

CREATE TABLE assistant_usage_daily (
  id BIGSERIAL PRIMARY KEY, usuario_id BIGINT REFERENCES usuarios(id), usage_date DATE NOT NULL,
  external_calls INTEGER NOT NULL DEFAULT 0, cost_usd NUMERIC(12,6) NOT NULL DEFAULT 0,
  CONSTRAINT ux_assistant_usage_user_day UNIQUE NULLS NOT DISTINCT (usuario_id, usage_date)
);

ALTER TABLE background_jobs ADD COLUMN lane VARCHAR(20) NOT NULL DEFAULT 'FINANCIAL';
ALTER TABLE background_jobs ADD CONSTRAINT ck_background_jobs_lane CHECK (lane IN ('FINANCIAL','ASSISTANT'));
CREATE INDEX idx_background_jobs_lane_claim ON background_jobs(lane, status, available_at, id);
