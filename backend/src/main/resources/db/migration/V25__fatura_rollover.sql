-- BACKLOG-0054/BACKLOG-0059 (PROB-0050): rollover de credito/saldo devedor de fatura de
-- cartao. Ver docs/SYSTEM_OVERVIEW.md, secao "Regra de produto: credito de fatura e saldo
-- devedor rolado". Lancamentos de rollover (tipos CREDITO_ANTERIOR/SALDO_DEVEDOR_ANTERIOR)
-- nao tem transacao de origem -- sao gerados pelo proprio FaturaService a partir da fatura
-- anterior, entao transacao_id precisa aceitar NULL.
ALTER TABLE fatura_lancamentos ALTER COLUMN transacao_id DROP NOT NULL;

-- Rastreabilidade: de qual fatura veio o credito/divida rolado.
ALTER TABLE fatura_lancamentos ADD COLUMN fatura_origem_id BIGINT NULL
    REFERENCES faturas_cartao(id);

CREATE INDEX IF NOT EXISTS idx_fatura_lancamentos_fatura_origem
    ON fatura_lancamentos(fatura_origem_id);

-- Trava de banco / idempotencia: no maximo um lancamento de rollover por fatura de origem
-- e por tipo. Na pratica R1 (credito, total <= 0) e R2 (saldo devedor, total > 0) sao
-- mutuamente exclusivos para a mesma fatura de origem, entao isso garante no maximo UM
-- rollover por fatura de origem. E o ultimo backstop; o lock pessimista tomado em
-- FaturaCartaoRepository.findWithLockByIdAndUsuarioId sobre a fatura de origem antes de
-- gerar o rollover ja deveria serializar qualquer tentativa concorrente.
CREATE UNIQUE INDEX ux_fatura_rollover_origem_tipo
    ON fatura_lancamentos (fatura_origem_id, tipo)
    WHERE fatura_origem_id IS NOT NULL;

-- Nao ha CHECK constraint restringindo os valores de fatura_lancamentos.tipo: a coluna foi
-- criada em V18__fatura_lancamento_tipo.sql sem CHECK, e V20__hardening_check_constraints.sql
-- (hardening geral de CHECKs) nao cobriu esta tabela. Os novos valores de enum
-- CREDITO_ANTERIOR/SALDO_DEVEDOR_ANTERIOR (adicionados em TipoFaturaLancamento) portanto nao
-- exigem nenhuma alteracao de schema aqui -- confirmado por leitura de V8, V17, V18, V20 e V21.

-- Nota sobre a unique constraint antiga: ux_fatura_lancamentos_unico (criada em
-- V21__fatura_lancamentos_unique_null_safe.sql) e um indice unico funcional sobre
-- (fatura_id, transacao_id, COALESCE(parcela_numero, 0)) -- SEM aplicar COALESCE em
-- transacao_id. No Postgres, valores NULL em coluna indexada sao considerados distintos
-- entre si por padrao, entao duas linhas com o mesmo fatura_id e transacao_id NULL NAO
-- violam esse indice. Lancamentos de rollover (transacao_id sempre NULL) portanto nunca
-- colidem com ux_fatura_lancamentos_unico; a idempotencia deles e garantida exclusivamente
-- pelo indice ux_fatura_rollover_origem_tipo criado acima.
