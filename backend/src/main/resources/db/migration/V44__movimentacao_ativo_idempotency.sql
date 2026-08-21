-- BACKLOG-0081: duplo clique em "adicionar movimentacao" duplicava a posicao.
-- A chave que existia no ledger era derivada do id da movimentacao ja salva
-- ("MOV_ATIVO_" + id), entao dois requests geravam ids diferentes e nunca
-- colidiam. A chave passa a vir do request (header Idempotency-Key) e o indice
-- unico parcial e o que garante a idempotencia sob concorrencia real --
-- mesmo padrao de ux_movimentos_carteira_usuario_idempotency (V11).

ALTER TABLE movimentacoes_ativo ADD COLUMN idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX ux_movimentacoes_ativo_usuario_idempotency
    ON movimentacoes_ativo(usuario_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
