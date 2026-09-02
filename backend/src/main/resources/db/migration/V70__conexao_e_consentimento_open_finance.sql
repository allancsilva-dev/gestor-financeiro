-- Fase 6 / PR-F6-05 — conexão, credencial e consentimento (ADR-0020).
--
-- Três tabelas separadas de propósito, porque têm ciclos de vida diferentes:
--   * a conexão é o vínculo e sobrevive à revogação, como histórico;
--   * a credencial é o segredo e é a primeira coisa que a revogação apaga;
--   * o consentimento é a prova de conformidade e é append-only.
--
-- Juntar credencial e conexão numa linha só faria "apagar o segredo" virar UPDATE com colunas nulas
-- numa linha que também guarda histórico, faria a rotação de chave reescrever a tabela inteira, e
-- faria qualquer SELECT descuidado de conexão carregar material cifrado para log e resposta.

CREATE TABLE conexoes_open_finance (
    id                     BIGSERIAL PRIMARY KEY,
    usuario_id             BIGINT NOT NULL REFERENCES usuarios(id),
    provedor_id            BIGINT NOT NULL REFERENCES open_finance_provedores(id),
    instituicao_id         BIGINT REFERENCES instituicoes_financeiras(id),
    apelido                VARCHAR(60),
    status                 VARCHAR(16) NOT NULL DEFAULT 'PENDENTE',
    external_connection_id VARCHAR(120),
    ultima_sync_em         TIMESTAMPTZ,
    ultimo_erro_codigo     VARCHAR(60),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_conexoes_of_status CHECK (status IN (
        'PENDENTE', 'ATIVA', 'EXPIRADA', 'REVOGADA', 'ERRO', 'DESVINCULADA'
    ))
);

CREATE UNIQUE INDEX ux_conexoes_of_usuario_provedor_externo
    ON conexoes_open_finance (usuario_id, provedor_id, external_connection_id)
    WHERE external_connection_id IS NOT NULL;
CREATE INDEX ix_conexoes_of_usuario_status ON conexoes_open_finance (usuario_id, status);

-- Segredo do titular. `usuario_id` é redundante com a conexão e existe assim mesmo: é o que torna
-- cada entrada do manifesto de exclusão do ADR-0007 um DELETE simples por titular.
CREATE TABLE conexao_credenciais (
    conexao_id            BIGINT PRIMARY KEY REFERENCES conexoes_open_finance(id) ON DELETE CASCADE,
    usuario_id            BIGINT NOT NULL REFERENCES usuarios(id),
    access_token_cifrado  TEXT,
    refresh_token_cifrado TEXT,
    token_expira_em       TIMESTAMPTZ,
    key_version           VARCHAR(10) NOT NULL DEFAULT 'v1',
    token_hmac            VARCHAR(64),
    rotacionado_em        TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_conexao_credenciais_hmac CHECK (token_hmac IS NULL OR token_hmac ~ '^[a-f0-9]{64}$')
);

CREATE INDEX ix_conexao_credenciais_usuario ON conexao_credenciais (usuario_id);
CREATE INDEX ix_conexao_credenciais_hmac ON conexao_credenciais (token_hmac)
    WHERE token_hmac IS NOT NULL;

-- Append-only: revogar muda status, nunca apaga linha. A linha É a prova de conformidade.
CREATE TABLE consentimentos_open_finance (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id),
    conexao_id          BIGINT NOT NULL REFERENCES conexoes_open_finance(id) ON DELETE CASCADE,
    external_consent_id VARCHAR(120),
    escopos             VARCHAR(300) NOT NULL,
    status              VARCHAR(12) NOT NULL DEFAULT 'AGUARDANDO',
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    concedido_em        TIMESTAMPTZ,
    expira_em           TIMESTAMPTZ NOT NULL,
    renovado_de_id      BIGINT REFERENCES consentimentos_open_finance(id),
    revogado_em         TIMESTAMPTZ,
    revogado_por        VARCHAR(12),
    politica_versao     VARCHAR(20),
    evidencia_hash      VARCHAR(64),

    CONSTRAINT ck_consentimentos_of_status CHECK (status IN (
        'AGUARDANDO', 'ATIVO', 'EXPIRADO', 'REVOGADO', 'RECUSADO'
    )),
    CONSTRAINT ck_consentimentos_of_revogado_por CHECK (
        revogado_por IS NULL OR revogado_por IN ('TITULAR', 'INSTITUICAO', 'SISTEMA', 'EXPIRACAO')
    ),
    -- Revogado sem carimbo de quando e por quem seria prova incompleta.
    CONSTRAINT ck_consentimentos_of_revogacao CHECK (
        (status = 'REVOGADO' AND revogado_em IS NOT NULL AND revogado_por IS NOT NULL)
        OR (status <> 'REVOGADO' AND revogado_em IS NULL AND revogado_por IS NULL)
    ),
    CONSTRAINT ck_consentimentos_of_concessao CHECK (
        status <> 'ATIVO' OR concedido_em IS NOT NULL
    ),
    -- Lista fechada de escopos, em maiúsculas e separados por vírgula, sem espaço.
    CONSTRAINT ck_consentimentos_of_escopos CHECK (
        escopos ~ '^(ACCOUNTS|TRANSACTIONS|BALANCES|CREDIT_CARDS)(,(ACCOUNTS|TRANSACTIONS|BALANCES|CREDIT_CARDS))*$'
    ),
    CONSTRAINT ck_consentimentos_of_evidencia CHECK (
        evidencia_hash IS NULL OR evidencia_hash ~ '^[a-f0-9]{64}$'
    )
);

-- Serializa a renovação: duas concorrentes não podem produzir dois consentimentos ativos para a
-- mesma conexão, o que deixaria a revogação de um sem efeito prático.
CREATE UNIQUE INDEX ux_consentimentos_of_ativo_por_conexao
    ON consentimentos_open_finance (conexao_id)
    WHERE status = 'ATIVO';
CREATE INDEX ix_consentimentos_of_expiracao ON consentimentos_open_finance (status, expira_em);
CREATE INDEX ix_consentimentos_of_usuario ON consentimentos_open_finance (usuario_id);

COMMENT ON TABLE conexao_credenciais IS
    'Segredo de terceiro cifrado (AES-GCM). Tabela separada para revogacao e rotacao nao tocarem historico.';
COMMENT ON TABLE consentimentos_open_finance IS
    'Append-only: revogar muda status, nunca apaga linha. Revogar nao e excluir (ADR-0020).';
COMMENT ON COLUMN consentimentos_open_finance.evidencia_hash IS
    'SHA-256 do texto de consentimento exibido ao titular, para provar depois o que foi consentido.';
