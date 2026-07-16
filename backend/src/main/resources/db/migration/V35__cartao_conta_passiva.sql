-- =============================================================================
-- V35 — PR-F2-06: cartao de credito como conta financeira PASSIVO (ADR-0008/0009)
-- =============================================================================
-- Cria 1 conta financeira CARTAO/PASSIVO por conta CREDITO, ligada por FK unica.
-- Backfill UNICO pelo passivo atual (origem BACKFILL): saldo de abertura no corte
-- da migration — nao reconstroi compras historicas, nao afeta resultado mensal e
-- nao aparece como variacao patrimonial economica (ADR-0013/0015).
-- Convencao: compra +passivo; pagamento/estorno/credito -passivo; saldo negativo
-- representa credito do cliente e e permitido.
-- Reconciliacao transitoria (guards): saldo do passivo == contas.valor_gasto
-- == soma dos lancamentos de faturas nao pagas.
-- =============================================================================

-- Dominio legado ganha CARTAO (exposto apenas na API nova; listagem legada exclui)
ALTER TABLE carteiras DROP CONSTRAINT chk_carteiras_tipo;
ALTER TABLE carteiras
    ADD CONSTRAINT chk_carteiras_tipo
        CHECK (tipo IN ('DINHEIRO', 'CONTA_BANCARIA', 'POUPANCA', 'CARTAO'));

ALTER TABLE contas
    ADD COLUMN conta_financeira_id BIGINT UNIQUE REFERENCES carteiras(id);

-- Backfill idempotente: uma conta financeira passiva por cartao existente
DO $$
DECLARE
    cartao RECORD;
    nova_conta_id BIGINT;
BEGIN
    FOR cartao IN
        SELECT c.id, c.usuario_id, c.nome, c.banco, COALESCE(c.valor_gasto, 0) AS passivo
        FROM contas c
        WHERE c.tipo = 'CREDITO' AND c.conta_financeira_id IS NULL
    LOOP
        INSERT INTO carteiras (nome, tipo, subtipo, natureza, liquidez, origem_dados,
                               estado_conciliacao, moeda, saldo, banco, usuario_id, version)
        VALUES (cartao.nome, 'CARTAO', 'CARTAO', 'PASSIVO', 'IMEDIATA', 'MANUAL',
                'CONCILIADA', 'BRL', cartao.passivo, cartao.banco, cartao.usuario_id, 0)
        RETURNING id INTO nova_conta_id;

        UPDATE contas SET conta_financeira_id = nova_conta_id WHERE id = cartao.id;

        IF cartao.passivo <> 0 THEN
            INSERT INTO movimentos_carteira (
                usuario_id, carteira_id, tipo, valor, valor_assinado, origem,
                referencia_tipo, referencia_id, descricao, data_movimento,
                saldo_resultante, idempotency_key, moeda, created_at)
            VALUES (
                cartao.usuario_id, nova_conta_id, 'ENTRADA', abs(cartao.passivo),
                cartao.passivo, 'BACKFILL', 'CONTA', cartao.id,
                'Saldo de abertura do passivo do cartao', CURRENT_TIMESTAMP,
                cartao.passivo, 'cartao-passivo-backfill-' || cartao.id, 'BRL',
                CURRENT_TIMESTAMP);
        END IF;
    END LOOP;
END $$;

-- Guards (ADR-0015): fail-closed
DO $$
DECLARE
    violacoes BIGINT;
BEGIN
    -- todo cartao tem conta financeira vinculada
    SELECT count(*) INTO violacoes FROM contas
    WHERE tipo = 'CREDITO' AND conta_financeira_id IS NULL;
    IF violacoes > 0 THEN
        RAISE EXCEPTION 'V35 abortada: % cartao(oes) sem conta financeira', violacoes;
    END IF;

    -- passivo materializado == valor_gasto
    SELECT count(*) INTO violacoes
    FROM contas c
    JOIN carteiras cf ON cf.id = c.conta_financeira_id
    WHERE c.tipo = 'CREDITO'
      AND cf.saldo <> COALESCE(c.valor_gasto, 0);
    IF violacoes > 0 THEN
        RAISE EXCEPTION 'V35 abortada: % cartao(oes) com saldo passivo <> valor_gasto', violacoes;
    END IF;

    -- valor_gasto == soma dos lancamentos de faturas nao pagas (invariante do FaturaService)
    SELECT count(*) INTO violacoes
    FROM contas c
    WHERE c.tipo = 'CREDITO'
      AND c.ativo = true
      AND COALESCE(c.valor_gasto, 0) <> COALESCE((
            SELECT sum(fl.valor)
            FROM faturas_cartao fc
            JOIN fatura_lancamentos fl ON fl.fatura_id = fc.id
            WHERE fc.conta_id = c.id AND fc.status <> 'PAGA'), 0);
    IF violacoes > 0 THEN
        RAISE EXCEPTION 'V35 abortada: % cartao(oes) com valor_gasto <> faturas nao pagas (sanear antes)', violacoes;
    END IF;
END $$;
