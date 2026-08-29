-- Assistente passa a registrar compra parcelada no cartao. Parcela existe somente
-- com cartao (mesma regra do formulario manual); fora dele o lancamento e a vista.
ALTER TABLE assistant_drafts
  ADD COLUMN conta_id BIGINT REFERENCES contas(id),
  ADD COLUMN parcelas INTEGER;

-- Rascunho pode registrar "em 3x" antes de saber o cartao: a cobranca do cartao e do
-- confirm, que recusa rascunho incompleto. So a transacao exige o par cartao+parcelas.
ALTER TABLE assistant_drafts
  ADD CONSTRAINT ck_assistant_draft_parcelas CHECK (parcelas IS NULL OR parcelas BETWEEN 2 AND 48);
