ALTER TABLE contas_fixas
    ADD COLUMN tipo VARCHAR(10) NOT NULL DEFAULT 'SAIDA',
    ADD COLUMN execucao_automatica BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN carteira_id BIGINT REFERENCES carteiras(id);

ALTER TABLE contas_fixas
    ADD CONSTRAINT ck_contas_fixas_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    ADD CONSTRAINT ck_contas_fixas_automatica_carteira CHECK (execucao_automatica = FALSE OR carteira_id IS NOT NULL);

CREATE INDEX idx_contas_fixas_execucao_vencimento
    ON contas_fixas(data_proximo_vencimento)
    WHERE ativo = TRUE AND execucao_automatica = TRUE;

CREATE TABLE execucoes_recorrencia (
    id BIGSERIAL PRIMARY KEY,
    conta_fixa_id BIGINT NOT NULL REFERENCES contas_fixas(id),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    data_vencimento DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    tentado_em TIMESTAMP NOT NULL,
    mensagem_falha VARCHAR(500),
    transacao_id BIGINT REFERENCES transacoes(id),
    CONSTRAINT ux_execucao_recorrencia_vencimento UNIQUE (conta_fixa_id, data_vencimento),
    CONSTRAINT ck_execucao_recorrencia_status CHECK (status IN ('REALIZADA', 'PULADA', 'FALHA_SALDO'))
);

CREATE INDEX idx_execucoes_recorrencia_usuario_status
    ON execucoes_recorrencia(usuario_id, status, data_vencimento);
