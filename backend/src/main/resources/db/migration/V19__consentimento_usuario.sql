-- LGPD: registro de consentimento no cadastro (versão da política aceita + quando).
-- Nulo para contas criadas antes deste registro existir.
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS politica_versao VARCHAR(20);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS consentimento_em TIMESTAMP;
