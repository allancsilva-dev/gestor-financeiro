-- Fase 6 / PR-F6-06 — conta conectada, cursor, log de sincronização e saldo declarado (ADR-0021).
--
-- `usuario_id` aparece em todas, mesmo sendo derivável pela conexão. É redundância deliberada: é o
-- que faz cada entrada do manifesto de exclusão do ADR-0007 ser um DELETE simples por titular, em
-- vez de um subselect encadeado que a próxima tabela quebraria.

CREATE TABLE contas_conectadas (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id),
    conexao_id          BIGINT NOT NULL REFERENCES conexoes_open_finance(id) ON DELETE CASCADE,
    external_account_id VARCHAR(120) NOT NULL,
    tipo                VARCHAR(16) NOT NULL,
    mascara             VARCHAR(8),
    moeda               VARCHAR(3) NOT NULL DEFAULT 'BRL',
    carteira_id         BIGINT REFERENCES carteiras(id),
    conta_id            BIGINT REFERENCES contas(id),
    auto_commit         BOOLEAN NOT NULL DEFAULT FALSE,
    divergente_desde    TIMESTAMPTZ,
    ativa               BOOLEAN NOT NULL DEFAULT TRUE,
    vinculada_em        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_contas_conectadas_tipo CHECK (tipo IN ('CORRENTE', 'POUPANCA', 'CARTAO', 'INVESTIMENTO')),
    CONSTRAINT ck_contas_conectadas_moeda CHECK (moeda ~ '^[A-Z]{3}$'),
    -- Conta ativa precisa de exatamente um destino no ledger. Sem destino, a sincronização não sabe
    -- onde lançar; com dois, lançaria em ambos.
    CONSTRAINT ck_contas_conectadas_destino CHECK (
        NOT ativa
        OR (carteira_id IS NOT NULL AND conta_id IS NULL)
        OR (carteira_id IS NULL AND conta_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_contas_conectadas_conexao_externa
    ON contas_conectadas (conexao_id, external_account_id);
-- Uma carteira nunca recebe duas conexões ativas: duas fontes escrevendo no mesmo caixa
-- produziriam divergência permanente sem culpado identificável.
CREATE UNIQUE INDEX ux_contas_conectadas_carteira
    ON contas_conectadas (carteira_id) WHERE carteira_id IS NOT NULL AND ativa;
CREATE UNIQUE INDEX ux_contas_conectadas_conta
    ON contas_conectadas (conta_id) WHERE conta_id IS NOT NULL AND ativa;
CREATE INDEX ix_contas_conectadas_usuario ON contas_conectadas (usuario_id);

-- Onde a próxima janela começa. Só avança depois que o lote correspondente é criado com sucesso;
-- avançar junto com o fetch perderia a janela em qualquer falha posterior.
CREATE TABLE sync_cursores (
    conta_conectada_id  BIGINT PRIMARY KEY REFERENCES contas_conectadas(id) ON DELETE CASCADE,
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id),
    -- TEXT, e não VARCHAR(n): cursor é opaco e definido pelo parceiro. Um limite arbitrário faria o
    -- job falhar com erro de banco no dia em que o parceiro aumentasse o formato.
    cursor_opaco        TEXT,
    ultima_janela_fim   DATE,
    ultimo_fato_em      TIMESTAMPTZ,
    backfill_concluido  BOOLEAN NOT NULL DEFAULT FALSE,
    backfill_desde      DATE,
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_sync_cursores_usuario ON sync_cursores (usuario_id);

-- Log operacional. Guarda contadores e código de erro; nunca descrição, valor ou identificador de
-- transação. Diagnóstico não precisa de PII.
CREATE TABLE sync_execucoes (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id),
    conta_conectada_id  BIGINT REFERENCES contas_conectadas(id) ON DELETE SET NULL,
    job_key             VARCHAR(180) NOT NULL,
    tipo                VARCHAR(20) NOT NULL,
    janela_inicio       DATE,
    janela_fim          DATE,
    iniciado_em         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalizado_em       TIMESTAMPTZ,
    status              VARCHAR(10) NOT NULL DEFAULT 'OK',
    import_batch_id     BIGINT REFERENCES import_batches(id) ON DELETE SET NULL,
    registros_recebidos INTEGER NOT NULL DEFAULT 0,
    registros_novos     INTEGER NOT NULL DEFAULT 0,
    erro_codigo         VARCHAR(60),
    http_status         SMALLINT,

    CONSTRAINT ck_sync_execucoes_tipo CHECK (tipo IN (
        'BACKFILL', 'INCREMENTAL', 'RECONCILIACAO', 'CONSENT_REFRESH', 'REVOKE'
    )),
    CONSTRAINT ck_sync_execucoes_status CHECK (status IN ('OK', 'VAZIO', 'PARCIAL', 'ERRO')),
    CONSTRAINT ck_sync_execucoes_erro CHECK (
        (status = 'ERRO' AND erro_codigo IS NOT NULL) OR (status <> 'ERRO')
    ),
    CONSTRAINT ck_sync_execucoes_contadores CHECK (
        registros_recebidos >= 0 AND registros_novos >= 0 AND registros_novos <= registros_recebidos
    ),
    CONSTRAINT ck_sync_execucoes_janela CHECK (
        janela_inicio IS NULL OR janela_fim IS NULL OR janela_inicio <= janela_fim
    )
);

-- Terceira camada de reentrância, junto com a job_key da fila e a idempotency_key do lote.
CREATE UNIQUE INDEX ux_sync_execucoes_job_key ON sync_execucoes (job_key);
CREATE INDEX ix_sync_execucoes_conta_iniciado ON sync_execucoes (conta_conectada_id, iniciado_em DESC);
CREATE INDEX ix_sync_execucoes_usuario ON sync_execucoes (usuario_id);

-- Saldo publicado pela instituição. Guarda os DOIS saldos de propósito: a conciliação usa o
-- contábil (ADR-0021), e a diferença para o disponível é justamente o diagnóstico de "há pendente
-- lá que aqui ainda não existe".
CREATE TABLE saldos_declarados_instituicao (
    id                 BIGSERIAL PRIMARY KEY,
    usuario_id         BIGINT NOT NULL REFERENCES usuarios(id),
    conta_conectada_id BIGINT NOT NULL REFERENCES contas_conectadas(id) ON DELETE CASCADE,
    referencia_em      TIMESTAMPTZ NOT NULL,
    saldo_contabil     NUMERIC(19,2),
    saldo_disponivel   NUMERIC(19,2),
    limite_cartao      NUMERIC(19,2),
    capturado_em       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sync_execucao_id   BIGINT REFERENCES sync_execucoes(id) ON DELETE SET NULL
);

CREATE INDEX ix_saldos_declarados_conta_referencia
    ON saldos_declarados_instituicao (conta_conectada_id, referencia_em DESC);
CREATE INDEX ix_saldos_declarados_usuario ON saldos_declarados_instituicao (usuario_id);

COMMENT ON COLUMN contas_conectadas.auto_commit IS
    'Commit automatico e excecao (ADR-0021): nasce falso e so dispara com lote limpo e saldo conciliado.';
COMMENT ON COLUMN sync_cursores.cursor_opaco IS
    'Marcador do parceiro. TEXT porque o formato e dele; so avanca apos o lote ser criado.';
COMMENT ON TABLE sync_execucoes IS
    'Log operacional de sincronizacao. Contadores e codigo de erro; nunca conteudo de transacao.';
COMMENT ON COLUMN saldos_declarados_instituicao.saldo_contabil IS
    'Referencia da conciliacao (ADR-0021). O disponivel fica ao lado so para diagnostico.';
