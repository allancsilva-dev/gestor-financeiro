-- Recorrencia com dois destinos de cobranca: caixa (carteira_id) ou cartao (conta_id).
-- Assinatura cobrada no cartao (Netflix, Spotify) nao tinha caminho: ContaFixa so
-- conhecia carteira. Expand puro e aditivo, sem backfill (ADR-0015).

ALTER TABLE contas_fixas ADD COLUMN conta_id BIGINT REFERENCES contas(id);

-- Execucao automatica passa a aceitar qualquer um dos dois destinos
ALTER TABLE contas_fixas DROP CONSTRAINT ck_contas_fixas_automatica_carteira;
ALTER TABLE contas_fixas ADD CONSTRAINT ck_contas_fixas_destino_automatico
    CHECK (execucao_automatica = FALSE OR carteira_id IS NOT NULL OR conta_id IS NOT NULL);

-- Um destino, nunca dois (mesmo padrao da V55 para destino de importacao)
ALTER TABLE contas_fixas ADD CONSTRAINT ck_contas_fixas_destino_unico
    CHECK (carteira_id IS NULL OR conta_id IS NULL);

-- Cartao cobra; nao se recebe salario no cartao
ALTER TABLE contas_fixas ADD CONSTRAINT ck_contas_fixas_cartao_saida
    CHECK (conta_id IS NULL OR tipo = 'SAIDA');

CREATE INDEX idx_contas_fixas_conta ON contas_fixas(conta_id) WHERE conta_id IS NOT NULL;
