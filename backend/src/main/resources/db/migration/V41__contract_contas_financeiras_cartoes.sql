-- =============================================================================
-- V41 — PR-F2-19: CONTRACT definitivo de contas financeiras e cartoes
-- =============================================================================
-- Fase CONTRACT (ADR-0015): remove o modelo legado de contas/carteiras tipadas.
-- Depois desta migration:
--   * contas = exclusivamente configuracao interna de cartao (1:1 com a conta
--     financeira PASSIVO em carteiras, subtipo CARTAO);
--   * carteiras.tipo, contas.tipo, contas.saldo_atual e contas.valor_gasto
--     deixam de existir; o passivo do cartao vive somente no ledger.
--
-- Transacional de ponta a ponta: qualquer guard reprovado aborta e o Flyway
-- reverte tudo. Nao existe undo migration.
--
-- Snapshots e mapeamentos ficam em tabelas temporarias ON COMMIT DROP; o
-- artefato durável pre/post e gerado fora do banco pelos scripts
-- scripts/preflight-v41.sh e scripts/postflight-v41.sh.
-- =============================================================================

-- =============================================================================
-- 1. Snapshot pre-contract: 9 metricas oficiais por usuario (ADR-0013)
-- =============================================================================
CREATE TEMPORARY TABLE tmp_v41_metricas (
    fase       TEXT   NOT NULL,
    usuario_id BIGINT NOT NULL,
    metrica    TEXT   NOT NULL,
    valor      NUMERIC(18, 2) NOT NULL,
    PRIMARY KEY (fase, usuario_id, metrica)
) ON COMMIT DROP;

-- Mesmas formulas do MetricasService (PR-F2-15). Semantica "cartao" via
-- contas.tipo = 'CREDITO' (valido enquanto a coluna existe). A variante
-- pos-drop usa apenas a existencia do vinculo (toda conta restante e cartao).
CREATE FUNCTION pg_temp.v41_snapshot_metricas_legado(p_fase TEXT) RETURNS void AS $$
DECLARE
    v_hoje       DATE := current_date;
    v_inicio_mes DATE := date_trunc('month', current_date)::date;
    v_fim_mes    DATE := (date_trunc('month', current_date) + interval '1 month - 1 day')::date;
    v_horizonte  DATE := (date_trunc('month', current_date) + interval '1 month - 1 day')::date;
    v_inicio_obr DATE := DATE '2000-01-01';
BEGIN
    INSERT INTO tmp_v41_metricas (fase, usuario_id, metrica, valor)
    SELECT p_fase, u.id, m.metrica, m.valor
    FROM usuarios u
    CROSS JOIN LATERAL (
        VALUES
        ('DISPONIVEL_AGORA', (
            SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
            WHERE c.usuario_id = u.id AND c.natureza = 'ATIVO' AND c.liquidez = 'IMEDIATA')),
        ('RESERVADO', (
            SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
            WHERE c.usuario_id = u.id AND c.subtipo = 'COFRE')
          + (SELECT COALESCE(sum(COALESCE(mt.valor_reservado, 0)), 0) FROM metas mt
             WHERE mt.usuario_id = u.id AND mt.modalidade = 'RESERVA_VIRTUAL'
               AND mt.status <> 'ARQUIVADA')),
        ('COMPROMETIDO', (
            SELECT COALESCE(sum(GREATEST(COALESCE(f.valor_total, 0) - COALESCE(f.valor_pago, 0), 0)), 0)
            FROM faturas_cartao f
            WHERE f.usuario_id = u.id AND f.status <> 'PAGA'
              AND f.data_vencimento BETWEEN v_inicio_obr AND v_horizonte
              AND NOT EXISTS (SELECT 1 FROM fatura_lancamentos fl WHERE fl.fatura_origem_id = f.id))
          + (SELECT COALESCE(sum(p.valor), 0)
             FROM parcelas p JOIN transacoes t ON t.id = p.transacao_id
             WHERE t.usuario_id = u.id AND p.status <> 'PAGO'
               AND NOT (t.tipo = 'SAIDA' AND t.conta_id IS NOT NULL
                        AND EXISTS (SELECT 1 FROM contas cc
                                    WHERE cc.id = t.conta_id AND cc.tipo = 'CREDITO'))
               AND p.data_vencimento BETWEEN v_inicio_obr AND v_horizonte)),
        ('DISPONIVEL_PARA_GASTAR',
            (SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
             WHERE c.usuario_id = u.id AND c.natureza = 'ATIVO' AND c.liquidez = 'IMEDIATA')
          - ((SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
              WHERE c.usuario_id = u.id AND c.subtipo = 'COFRE')
             + (SELECT COALESCE(sum(COALESCE(mt.valor_reservado, 0)), 0) FROM metas mt
                WHERE mt.usuario_id = u.id AND mt.modalidade = 'RESERVA_VIRTUAL'
                  AND mt.status <> 'ARQUIVADA'))
          - ((SELECT COALESCE(sum(GREATEST(COALESCE(f.valor_total, 0) - COALESCE(f.valor_pago, 0), 0)), 0)
              FROM faturas_cartao f
              WHERE f.usuario_id = u.id AND f.status <> 'PAGA'
                AND f.data_vencimento BETWEEN v_inicio_obr AND v_horizonte
                AND NOT EXISTS (SELECT 1 FROM fatura_lancamentos fl WHERE fl.fatura_origem_id = f.id))
             + (SELECT COALESCE(sum(p.valor), 0)
                FROM parcelas p JOIN transacoes t ON t.id = p.transacao_id
                WHERE t.usuario_id = u.id AND p.status <> 'PAGO'
                  AND NOT (t.tipo = 'SAIDA' AND t.conta_id IS NOT NULL
                           AND EXISTS (SELECT 1 FROM contas cc
                                       WHERE cc.id = t.conta_id AND cc.tipo = 'CREDITO'))
                  AND p.data_vencimento BETWEEN v_inicio_obr AND v_horizonte))),
        ('INVESTIDO', (
            SELECT COALESCE(sum(a.quantidade * a.valor_atual), 0) FROM ativos a
            WHERE a.usuario_id = u.id AND a.valor_atual IS NOT NULL
              AND a.cotacao_em IS NOT NULL AND a.quantidade > 0)),
        ('DIVIDAS', (
            SELECT COALESCE(sum(GREATEST(c.saldo, 0)), 0) FROM carteiras c
            WHERE c.usuario_id = u.id AND c.natureza = 'PASSIVO')),
        ('RESULTADO_MENSAL', (
            SELECT COALESCE(sum(CASE WHEN t.tipo = 'ENTRADA' THEN t.valor_total ELSE -t.valor_total END), 0)
            FROM transacoes t
            WHERE t.usuario_id = u.id AND t.ativa = true
              AND t.estado_conciliacao = 'CONCILIADA'
              AND t.data BETWEEN v_inicio_mes AND v_fim_mes
              AND (t.tipo = 'ENTRADA'
                   OR NOT (t.conta_id IS NOT NULL
                           AND EXISTS (SELECT 1 FROM contas cc
                                       WHERE cc.id = t.conta_id AND cc.tipo = 'CREDITO'))))
          - (SELECT COALESCE(sum(fl.valor), 0)
             FROM fatura_lancamentos fl JOIN faturas_cartao f ON f.id = fl.fatura_id
             WHERE f.usuario_id = u.id AND fl.tipo IN ('COMPRA', 'AJUSTE', 'ESTORNO')
               AND fl.data_compra BETWEEN v_inicio_mes AND v_fim_mes)),
        ('PATRIMONIO_LIQUIDO', (
            SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
            WHERE c.usuario_id = u.id AND c.natureza = 'ATIVO')
          + (SELECT COALESCE(sum(a.quantidade * a.valor_atual), 0) FROM ativos a
             WHERE a.usuario_id = u.id AND a.valor_atual IS NOT NULL
               AND a.cotacao_em IS NOT NULL AND a.quantidade > 0)
          - (SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
             WHERE c.usuario_id = u.id AND c.natureza = 'PASSIVO')),
        ('VARIACAO_PATRIMONIAL', (
            SELECT COALESCE(sum(mc.valor_assinado), 0)
            FROM movimentos_carteira mc JOIN carteiras c ON c.id = mc.carteira_id
            WHERE mc.usuario_id = u.id AND c.natureza = 'ATIVO'
              AND mc.origem <> 'BACKFILL'
              AND mc.data_movimento >= v_inicio_mes::timestamp
              AND mc.data_movimento < (v_hoje + 1)::timestamp)
          - (SELECT COALESCE(sum(mc.valor_assinado), 0)
             FROM movimentos_carteira mc JOIN carteiras c ON c.id = mc.carteira_id
             WHERE mc.usuario_id = u.id AND c.natureza = 'PASSIVO'
               AND mc.origem <> 'BACKFILL'
               AND mc.data_movimento >= v_inicio_mes::timestamp
               AND mc.data_movimento < (v_hoje + 1)::timestamp)
          + (SELECT COALESCE(sum(CASE ma.tipo WHEN 'COMPRA' THEN COALESCE(ma.valor_total, 0)
                                              WHEN 'VENDA' THEN -COALESCE(ma.valor_total, 0)
                                              ELSE 0 END), 0)
             FROM movimentacoes_ativo ma
             WHERE ma.usuario_id = u.id AND ma.tipo IN ('COMPRA', 'VENDA')
               AND ma.data BETWEEN v_inicio_mes AND v_hoje))
    ) AS m(metrica, valor);
END;
$$ LANGUAGE plpgsql;

-- Variante canonica (pos-migracao de dados): cartao = conta referenciada.
-- Valida antes e depois dos drops, pois nao le colunas removidas.
CREATE FUNCTION pg_temp.v41_snapshot_metricas_canonico(p_fase TEXT) RETURNS void AS $$
DECLARE
    v_hoje       DATE := current_date;
    v_inicio_mes DATE := date_trunc('month', current_date)::date;
    v_fim_mes    DATE := (date_trunc('month', current_date) + interval '1 month - 1 day')::date;
    v_horizonte  DATE := (date_trunc('month', current_date) + interval '1 month - 1 day')::date;
    v_inicio_obr DATE := DATE '2000-01-01';
BEGIN
    INSERT INTO tmp_v41_metricas (fase, usuario_id, metrica, valor)
    SELECT p_fase, u.id, m.metrica, m.valor
    FROM usuarios u
    CROSS JOIN LATERAL (
        VALUES
        ('DISPONIVEL_AGORA', (
            SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
            WHERE c.usuario_id = u.id AND c.natureza = 'ATIVO' AND c.liquidez = 'IMEDIATA')),
        ('RESERVADO', (
            SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
            WHERE c.usuario_id = u.id AND c.subtipo = 'COFRE')
          + (SELECT COALESCE(sum(COALESCE(mt.valor_reservado, 0)), 0) FROM metas mt
             WHERE mt.usuario_id = u.id AND mt.modalidade = 'RESERVA_VIRTUAL'
               AND mt.status <> 'ARQUIVADA')),
        ('COMPROMETIDO', (
            SELECT COALESCE(sum(GREATEST(COALESCE(f.valor_total, 0) - COALESCE(f.valor_pago, 0), 0)), 0)
            FROM faturas_cartao f
            WHERE f.usuario_id = u.id AND f.status <> 'PAGA'
              AND f.data_vencimento BETWEEN v_inicio_obr AND v_horizonte
              AND NOT EXISTS (SELECT 1 FROM fatura_lancamentos fl WHERE fl.fatura_origem_id = f.id))
          + (SELECT COALESCE(sum(p.valor), 0)
             FROM parcelas p JOIN transacoes t ON t.id = p.transacao_id
             WHERE t.usuario_id = u.id AND p.status <> 'PAGO'
               AND NOT (t.tipo = 'SAIDA' AND t.conta_id IS NOT NULL)
               AND p.data_vencimento BETWEEN v_inicio_obr AND v_horizonte)),
        ('DISPONIVEL_PARA_GASTAR',
            (SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
             WHERE c.usuario_id = u.id AND c.natureza = 'ATIVO' AND c.liquidez = 'IMEDIATA')
          - ((SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
              WHERE c.usuario_id = u.id AND c.subtipo = 'COFRE')
             + (SELECT COALESCE(sum(COALESCE(mt.valor_reservado, 0)), 0) FROM metas mt
                WHERE mt.usuario_id = u.id AND mt.modalidade = 'RESERVA_VIRTUAL'
                  AND mt.status <> 'ARQUIVADA'))
          - ((SELECT COALESCE(sum(GREATEST(COALESCE(f.valor_total, 0) - COALESCE(f.valor_pago, 0), 0)), 0)
              FROM faturas_cartao f
              WHERE f.usuario_id = u.id AND f.status <> 'PAGA'
                AND f.data_vencimento BETWEEN v_inicio_obr AND v_horizonte
                AND NOT EXISTS (SELECT 1 FROM fatura_lancamentos fl WHERE fl.fatura_origem_id = f.id))
             + (SELECT COALESCE(sum(p.valor), 0)
                FROM parcelas p JOIN transacoes t ON t.id = p.transacao_id
                WHERE t.usuario_id = u.id AND p.status <> 'PAGO'
                  AND NOT (t.tipo = 'SAIDA' AND t.conta_id IS NOT NULL)
                  AND p.data_vencimento BETWEEN v_inicio_obr AND v_horizonte))),
        ('INVESTIDO', (
            SELECT COALESCE(sum(a.quantidade * a.valor_atual), 0) FROM ativos a
            WHERE a.usuario_id = u.id AND a.valor_atual IS NOT NULL
              AND a.cotacao_em IS NOT NULL AND a.quantidade > 0)),
        ('DIVIDAS', (
            SELECT COALESCE(sum(GREATEST(c.saldo, 0)), 0) FROM carteiras c
            WHERE c.usuario_id = u.id AND c.natureza = 'PASSIVO')),
        ('RESULTADO_MENSAL', (
            SELECT COALESCE(sum(CASE WHEN t.tipo = 'ENTRADA' THEN t.valor_total ELSE -t.valor_total END), 0)
            FROM transacoes t
            WHERE t.usuario_id = u.id AND t.ativa = true
              AND t.estado_conciliacao = 'CONCILIADA'
              AND t.data BETWEEN v_inicio_mes AND v_fim_mes
              AND (t.tipo = 'ENTRADA' OR t.conta_id IS NULL))
          - (SELECT COALESCE(sum(fl.valor), 0)
             FROM fatura_lancamentos fl JOIN faturas_cartao f ON f.id = fl.fatura_id
             WHERE f.usuario_id = u.id AND fl.tipo IN ('COMPRA', 'AJUSTE', 'ESTORNO')
               AND fl.data_compra BETWEEN v_inicio_mes AND v_fim_mes)),
        ('PATRIMONIO_LIQUIDO', (
            SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
            WHERE c.usuario_id = u.id AND c.natureza = 'ATIVO')
          + (SELECT COALESCE(sum(a.quantidade * a.valor_atual), 0) FROM ativos a
             WHERE a.usuario_id = u.id AND a.valor_atual IS NOT NULL
               AND a.cotacao_em IS NOT NULL AND a.quantidade > 0)
          - (SELECT COALESCE(sum(c.saldo), 0) FROM carteiras c
             WHERE c.usuario_id = u.id AND c.natureza = 'PASSIVO')),
        ('VARIACAO_PATRIMONIAL', (
            SELECT COALESCE(sum(mc.valor_assinado), 0)
            FROM movimentos_carteira mc JOIN carteiras c ON c.id = mc.carteira_id
            WHERE mc.usuario_id = u.id AND c.natureza = 'ATIVO'
              AND mc.origem <> 'BACKFILL'
              AND mc.data_movimento >= v_inicio_mes::timestamp
              AND mc.data_movimento < (v_hoje + 1)::timestamp)
          - (SELECT COALESCE(sum(mc.valor_assinado), 0)
             FROM movimentos_carteira mc JOIN carteiras c ON c.id = mc.carteira_id
             WHERE mc.usuario_id = u.id AND c.natureza = 'PASSIVO'
               AND mc.origem <> 'BACKFILL'
               AND mc.data_movimento >= v_inicio_mes::timestamp
               AND mc.data_movimento < (v_hoje + 1)::timestamp)
          + (SELECT COALESCE(sum(CASE ma.tipo WHEN 'COMPRA' THEN COALESCE(ma.valor_total, 0)
                                              WHEN 'VENDA' THEN -COALESCE(ma.valor_total, 0)
                                              ELSE 0 END), 0)
             FROM movimentacoes_ativo ma
             WHERE ma.usuario_id = u.id AND ma.tipo IN ('COMPRA', 'VENDA')
               AND ma.data BETWEEN v_inicio_mes AND v_hoje))
    ) AS m(metrica, valor);
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.v41_comparar_metricas(p_base TEXT, p_atual TEXT) RETURNS void AS $$
DECLARE
    v_diff RECORD;
BEGIN
    SELECT COALESCE(b.usuario_id, a.usuario_id), COALESCE(b.metrica, a.metrica),
           b.valor AS valor_base, a.valor AS valor_atual
    INTO v_diff
    FROM (SELECT * FROM tmp_v41_metricas WHERE fase = p_base) b
    FULL OUTER JOIN (SELECT * FROM tmp_v41_metricas WHERE fase = p_atual) a
      ON a.usuario_id = b.usuario_id AND a.metrica = b.metrica
    WHERE a.valor IS DISTINCT FROM b.valor
    LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'V41 abortada: metrica % do usuario % divergiu (% -> %) entre % e %',
            v_diff.metrica, v_diff.usuario_id, v_diff.valor_base, v_diff.valor_atual, p_base, p_atual;
    END IF;
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.v41_snapshot_metricas_legado('PRE');

-- Snapshot de contagens das tabelas financeiras (invariantes de volume)
CREATE TEMPORARY TABLE tmp_v41_contagens ON COMMIT DROP AS
SELECT 'transacoes'          AS tabela, count(*) AS total FROM transacoes
UNION ALL SELECT 'parcelas',            count(*) FROM parcelas
UNION ALL SELECT 'faturas_cartao',      count(*) FROM faturas_cartao
UNION ALL SELECT 'fatura_lancamentos',  count(*) FROM fatura_lancamentos
UNION ALL SELECT 'movimentos_carteira', count(*) FROM movimentos_carteira
UNION ALL SELECT 'operacoes_financeiras', count(*) FROM operacoes_financeiras
UNION ALL SELECT 'metas',               count(*) FROM metas
UNION ALL SELECT 'ativos',              count(*) FROM ativos
UNION ALL SELECT 'movimentacoes_ativo', count(*) FROM movimentacoes_ativo
UNION ALL SELECT 'contas_credito',      count(*) FROM contas WHERE tipo = 'CREDITO'
UNION ALL SELECT 'carteiras',            count(*) FROM carteiras
UNION ALL SELECT 'carteiras_saldo_hash', COALESCE(sum(hashtext(id::text || ':' || saldo::text)), 0) FROM carteiras;

CREATE TEMPORARY TABLE tmp_v41_carteiras_preexistentes ON COMMIT DROP AS
SELECT id, saldo, md5(row_to_json(carteiras)::text) AS hash_linha FROM carteiras;

-- =============================================================================
-- 2. Mapeamento e migracao dos registros legados de contas nao-CREDITO
-- =============================================================================
CREATE TEMPORARY TABLE tmp_v41_mapa (
    conta_id            BIGINT PRIMARY KEY,
    usuario_id          BIGINT NOT NULL,
    nome                TEXT,
    banco               TEXT,
    tipo_legado         TEXT NOT NULL,
    subtipo_destino     TEXT NOT NULL,
    carteira_destino_id BIGINT NOT NULL,
    estrategia          TEXT NOT NULL
) ON COMMIT DROP;

DO $$
DECLARE
    r               RECORD;
    v_qtd           BIGINT;
    v_destino       BIGINT;
    v_dono          BIGINT;
    v_subtipo       TEXT;
    v_tipo_carteira TEXT;
BEGIN
    FOR r IN
        SELECT c.id, c.usuario_id, c.nome, c.banco, c.tipo,
               COALESCE(c.saldo_atual, 0) AS saldo_atual,
               COALESCE(c.valor_gasto, 0) AS valor_gasto
        FROM contas c
        WHERE c.tipo <> 'CREDITO'
        ORDER BY c.id
    LOOP
        -- Mapa fixo de subtipos (plano PR-F2-19)
        v_subtipo := CASE r.tipo
            WHEN 'DEBITO'   THEN 'PAGAMENTO'
            WHEN 'DINHEIRO' THEN 'DINHEIRO'
            WHEN 'POUPANCA' THEN 'POUPANCA'
        END;
        IF v_subtipo IS NULL THEN
            RAISE EXCEPTION 'V41 abortada: conta % com tipo legado inesperado %', r.id, r.tipo;
        END IF;

        -- Saldos materializados devem estar zerados; divergencia exige
        -- saneamento versionado separado, nunca correcao aqui.
        IF r.saldo_atual <> 0 THEN
            RAISE EXCEPTION 'V41 abortada: conta % (%) com saldo_atual % <> 0', r.id, r.nome, r.saldo_atual;
        END IF;
        IF r.valor_gasto <> 0 THEN
            RAISE EXCEPTION 'V41 abortada: conta % (%) com valor_gasto % <> 0', r.id, r.nome, r.valor_gasto;
        END IF;

        -- Estrategia 1: transacoes da configuracao apontam para exatamente
        -- uma conta financeira do mesmo usuario
        SELECT count(DISTINCT t.carteira_id), min(t.carteira_id)
        INTO v_qtd, v_destino
        FROM transacoes t
        WHERE t.conta_id = r.id AND t.carteira_id IS NOT NULL;

        IF v_qtd > 1 THEN
            RAISE EXCEPTION 'V41 abortada: conta % (%) referencia % contas financeiras distintas (mapeamento ambiguo)',
                r.id, r.nome, v_qtd;
        ELSIF v_qtd = 1 THEN
            SELECT usuario_id INTO v_dono FROM carteiras WHERE id = v_destino;
            IF v_dono IS DISTINCT FROM r.usuario_id THEN
                RAISE EXCEPTION 'V41 abortada: conta % (usuario %) mapeia carteira % de outro usuario (%)',
                    r.id, r.usuario_id, v_destino, v_dono;
            END IF;
            INSERT INTO tmp_v41_mapa VALUES
                (r.id, r.usuario_id, r.nome, r.banco, r.tipo, v_subtipo, v_destino, 'TRANSACAO');
            CONTINUE;
        END IF;

        -- Estrategia 2: reuso por usuario + nome normalizado + banco + subtipo
        SELECT count(*), min(cf.id)
        INTO v_qtd, v_destino
        FROM carteiras cf
        WHERE cf.usuario_id = r.usuario_id
          AND cf.subtipo = v_subtipo
          AND lower(btrim(cf.nome)) = lower(btrim(r.nome))
          AND COALESCE(lower(btrim(cf.banco)), '') = COALESCE(lower(btrim(r.banco)), '');

        IF v_qtd > 1 THEN
            RAISE EXCEPTION 'V41 abortada: conta % (%) com % candidatas de reuso (mapeamento ambiguo)',
                r.id, r.nome, v_qtd;
        ELSIF v_qtd = 1 THEN
            INSERT INTO tmp_v41_mapa VALUES
                (r.id, r.usuario_id, r.nome, r.banco, r.tipo, v_subtipo, v_destino, 'REUSO_NOME');
            CONTINUE;
        END IF;

        -- Estrategia 3: criar conta financeira ATIVO com saldo e ledger zero.
        -- carteiras.tipo ainda existe neste ponto; valor apenas satisfaz o
        -- CHECK legado e sera dropado no fim desta migration.
        v_tipo_carteira := CASE r.tipo
            WHEN 'DEBITO'   THEN 'CONTA_BANCARIA'
            WHEN 'DINHEIRO' THEN 'DINHEIRO'
            WHEN 'POUPANCA' THEN 'POUPANCA'
        END;
        INSERT INTO carteiras (nome, tipo, saldo, banco, usuario_id, natureza, subtipo,
                               liquidez, origem_dados, estado_conciliacao, moeda, version)
        VALUES (r.nome, v_tipo_carteira, 0, r.banco, r.usuario_id, 'ATIVO', v_subtipo,
                'IMEDIATA', 'MANUAL', 'CONCILIADA', 'BRL', 0)
        RETURNING id INTO v_destino;

        INSERT INTO tmp_v41_mapa VALUES
            (r.id, r.usuario_id, r.nome, r.banco, r.tipo, v_subtipo, v_destino, 'CRIADA');
    END LOOP;

    -- Nenhuma fatura pode referenciar configuracao nao-CREDITO
    SELECT count(*) INTO v_qtd
    FROM faturas_cartao fc JOIN tmp_v41_mapa m ON m.conta_id = fc.conta_id;
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % fatura(s) referenciando conta nao-CREDITO', v_qtd;
    END IF;

    -- Transacao ativa de conta legada sem conta financeira ja deve estar
    -- PENDENTE_CONCILIACAO (V34); nenhuma conta ou lancamento e inventado.
    SELECT count(*) INTO v_qtd
    FROM transacoes t JOIN tmp_v41_mapa m ON m.conta_id = t.conta_id
    WHERE t.ativa = true AND t.carteira_id IS NULL
      AND t.estado_conciliacao <> 'PENDENTE_CONCILIACAO';
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % transacao(oes) de conta legada sem carteira e sem PENDENTE_CONCILIACAO', v_qtd;
    END IF;

    -- Libera FKs: transacoes.carteira_id preservado; conta_id removido apenas
    -- para configuracoes nao-CREDITO.
    UPDATE transacoes t SET conta_id = NULL
    WHERE t.conta_id IN (SELECT conta_id FROM tmp_v41_mapa);

    -- Exclui a configuracao legada somente apos preservar o mapeamento
    DELETE FROM contas WHERE id IN (SELECT conta_id FROM tmp_v41_mapa);
END $$;

-- =============================================================================
-- 2b. Backfill de cartoes legados criados sob o schema V1 (colunas nullable)
-- =============================================================================
-- V1 criou contas.dia_fechamento/dia_vencimento/limite_total/ativo como NULLABLE
-- e V20 so validou "IS NULL OR BETWEEN 1 AND 31"; registros antigos podem chegar
-- aqui com nulo. Os valores abaixo sao exatamente os defaults que o codigo ja
-- aplicava para nulo -- nada e inventado e o comportamento fica preservado:
--   dia_fechamento nulo -> ultimo dia do mes; 31 e equivalente exato porque
--     FaturaDatas.diaValidoOuFimDoMes clampa o dia para lengthOfMonth;
--   dia_vencimento nulo -> 10 (FaturaDatas.vencimento);
--   limite_total / ativo -> DEFAULT declarado em V1 (0 / TRUE).
-- Nenhuma metrica da secao 1 depende dessas colunas. E no-op em base que ja
-- passou pelo guard 3b, entao um flyway repair de checksum e seguro.
-- Depois da migration o usuario pode ajustar os dois dias pelo app.
UPDATE contas
   SET dia_fechamento = COALESCE(dia_fechamento, 31),
       dia_vencimento = COALESCE(dia_vencimento, 10),
       limite_total   = COALESCE(limite_total, 0),
       ativo          = COALESCE(ativo, TRUE)
 WHERE dia_fechamento IS NULL OR dia_vencimento IS NULL
    OR limite_total IS NULL OR ativo IS NULL;

-- =============================================================================
-- 3. Guards antes do drop
-- =============================================================================
DO $$
DECLARE
    v_qtd BIGINT;
BEGIN
    -- 3a. nenhuma conta nao-CREDITO restante
    SELECT count(*) INTO v_qtd FROM contas WHERE tipo <> 'CREDITO';
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % conta(s) nao-CREDITO restante(s)', v_qtd;
    END IF;

    -- 3b. campos canonicos de cartao nao nulos
    SELECT count(*) INTO v_qtd FROM contas
    WHERE conta_financeira_id IS NULL OR limite_total IS NULL
       OR dia_fechamento IS NULL OR dia_vencimento IS NULL OR ativo IS NULL;
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % cartao(oes) com campo canonico nulo', v_qtd;
    END IF;

    -- 3c. pareamento 1:1 com carteiras.subtipo=CARTAO, natureza, dono e moeda
    SELECT count(*) INTO v_qtd
    FROM contas c JOIN carteiras cf ON cf.id = c.conta_financeira_id
    WHERE cf.subtipo <> 'CARTAO' OR cf.natureza <> 'PASSIVO'
       OR cf.usuario_id <> c.usuario_id OR cf.moeda <> 'BRL';
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % pareamento(s) cartao/conta financeira incompativel(is)', v_qtd;
    END IF;

    SELECT count(*) INTO v_qtd
    FROM carteiras cf
    WHERE cf.subtipo = 'CARTAO'
      AND NOT EXISTS (SELECT 1 FROM contas c WHERE c.conta_financeira_id = cf.id);
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % conta(s) financeira(s) CARTAO sem configuracao de cartao', v_qtd;
    END IF;

    SELECT count(*) INTO v_qtd FROM (
        SELECT conta_financeira_id FROM contas
        GROUP BY conta_financeira_id HAVING count(*) <> 1
    ) duplicadas;
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % pareamento(s) de cartao nao sao 1:1', v_qtd;
    END IF;

    -- Ownership deve ser coerente em todas as referencias financeiras. As FKs
    -- garantem existencia; estes guards garantem isolamento de tenant.
    SELECT count(*) INTO v_qtd
    FROM transacoes t
    LEFT JOIN carteiras cf ON cf.id=t.carteira_id
    LEFT JOIN contas c ON c.id=t.conta_id
    WHERE (t.carteira_id IS NOT NULL AND cf.usuario_id <> t.usuario_id)
       OR (t.conta_id IS NOT NULL AND c.usuario_id <> t.usuario_id);
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % transacao(oes) com ownership divergente', v_qtd;
    END IF;

    SELECT count(*) INTO v_qtd
    FROM faturas_cartao f JOIN contas c ON c.id=f.conta_id
    WHERE f.usuario_id <> c.usuario_id;
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % fatura(s) com ownership divergente', v_qtd;
    END IF;

    SELECT count(*) INTO v_qtd
    FROM movimentos_carteira mc
    JOIN carteiras cf ON cf.id=mc.carteira_id
    LEFT JOIN operacoes_financeiras op ON op.id=mc.operacao_id
    WHERE mc.usuario_id <> cf.usuario_id
       OR (mc.operacao_id IS NOT NULL AND op.usuario_id <> mc.usuario_id);
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % movimento(s) de ledger com ownership divergente', v_qtd;
    END IF;

    SELECT count(*) INTO v_qtd
    FROM fatura_pagamentos fp
    JOIN faturas_cartao f ON f.id=fp.fatura_id
    JOIN carteiras cf ON cf.id=fp.carteira_id
    LEFT JOIN operacoes_financeiras op ON op.id=fp.operacao_id
    WHERE fp.usuario_id <> f.usuario_id OR fp.usuario_id <> cf.usuario_id
       OR (fp.operacao_id IS NOT NULL AND op.usuario_id <> fp.usuario_id);
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % pagamento(s) de fatura com ownership divergente', v_qtd;
    END IF;

    SELECT count(*) INTO v_qtd
    FROM operacoes_financeiras op
    JOIN operacoes_financeiras original ON original.id=op.estorno_de_id
    WHERE op.usuario_id <> original.usuario_id;
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % operacao(oes) de estorno com ownership divergente', v_qtd;
    END IF;

    -- 3d. saldo materializado == ledger para toda conta financeira
    SELECT count(*) INTO v_qtd
    FROM carteiras cf
    WHERE cf.saldo <> COALESCE((SELECT sum(mc.valor_assinado)
                                FROM movimentos_carteira mc
                                WHERE mc.carteira_id = cf.id), 0);
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % conta(s) financeira(s) com saldo <> ledger', v_qtd;
    END IF;

    -- 3e. saldo PASSIVO == valor_gasto == faturas nao pagas
    SELECT count(*) INTO v_qtd
    FROM contas c JOIN carteiras cf ON cf.id = c.conta_financeira_id
    WHERE cf.saldo <> COALESCE(c.valor_gasto, 0);
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % cartao(oes) com saldo PASSIVO <> valor_gasto', v_qtd;
    END IF;

    SELECT count(*) INTO v_qtd
    FROM contas c
    WHERE COALESCE(c.valor_gasto, 0) <> COALESCE((
            SELECT sum(fl.valor)
            FROM faturas_cartao fc
            JOIN fatura_lancamentos fl ON fl.fatura_id = fc.id
            WHERE fc.conta_id = c.id AND fc.status <> 'PAGA'), 0);
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % cartao(oes) com valor_gasto <> faturas nao pagas', v_qtd;
    END IF;

    -- 3f. saldo_atual zerado em toda configuracao restante
    SELECT count(*) INTO v_qtd FROM contas WHERE COALESCE(saldo_atual, 0) <> 0;
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % cartao(oes) com saldo_atual <> 0', v_qtd;
    END IF;

    -- 3g. volumes das tabelas financeiras inalterados (contas: so cartoes)
    SELECT count(*) INTO v_qtd
    FROM tmp_v41_contagens s
    JOIN (
        SELECT 'transacoes' AS tabela, count(*) AS total FROM transacoes
        UNION ALL SELECT 'parcelas',            count(*) FROM parcelas
        UNION ALL SELECT 'faturas_cartao',      count(*) FROM faturas_cartao
        UNION ALL SELECT 'fatura_lancamentos',  count(*) FROM fatura_lancamentos
        UNION ALL SELECT 'movimentos_carteira', count(*) FROM movimentos_carteira
        UNION ALL SELECT 'operacoes_financeiras', count(*) FROM operacoes_financeiras
        UNION ALL SELECT 'metas',               count(*) FROM metas
        UNION ALL SELECT 'ativos',              count(*) FROM ativos
        UNION ALL SELECT 'movimentacoes_ativo', count(*) FROM movimentacoes_ativo
        UNION ALL SELECT 'contas_credito',      count(*) FROM contas WHERE tipo = 'CREDITO'
    ) a ON a.tabela = s.tabela AND a.total <> s.total;
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: contagem de tabela financeira alterada pela migracao';
    END IF;

    -- 3h. saldos preexistentes de carteiras intocados (novas carteiras tem saldo 0
    -- e nao alteram o hash das existentes)
    SELECT count(*) INTO v_qtd
    FROM tmp_v41_contagens s
    WHERE s.tabela = 'carteiras_saldo_hash'
      AND s.total <> (SELECT COALESCE(sum(hashtext(id::text || ':' || saldo::text)), 0)
                      FROM carteiras
                      WHERE id NOT IN (SELECT carteira_destino_id FROM tmp_v41_mapa
                                       WHERE estrategia = 'CRIADA'));
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: saldo de conta financeira preexistente alterado';
    END IF;

    SELECT count(*) INTO v_qtd
    FROM tmp_v41_carteiras_preexistentes p
    FULL OUTER JOIN (
        SELECT id, saldo, md5(row_to_json(carteiras)::text) AS hash_linha
        FROM carteiras
        WHERE id NOT IN (SELECT carteira_destino_id FROM tmp_v41_mapa WHERE estrategia = 'CRIADA')
    ) a USING (id)
    WHERE p.id IS NULL OR a.id IS NULL
       OR p.saldo IS DISTINCT FROM a.saldo OR p.hash_linha IS DISTINCT FROM a.hash_linha;
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % conta(s) financeira(s) preexistente(s) alterada(s)', v_qtd;
    END IF;

    SELECT count(*) INTO v_qtd
    FROM tmp_v41_contagens s
    WHERE s.tabela = 'carteiras'
      AND (SELECT count(*) FROM carteiras) <>
          s.total + (SELECT count(*) FROM tmp_v41_mapa WHERE estrategia = 'CRIADA');
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: quantidade de contas financeiras divergiu do previsto';
    END IF;
END $$;

-- 3i. as 9 metricas canonicas antes dos drops nao podem divergir do legado
SELECT pg_temp.v41_snapshot_metricas_canonico('POS_MIGRACAO');
SELECT pg_temp.v41_comparar_metricas('PRE', 'POS_MIGRACAO');

-- =============================================================================
-- 4. Drops e constraints finais
-- =============================================================================
ALTER TABLE carteiras DROP CONSTRAINT chk_carteiras_tipo;
ALTER TABLE carteiras DROP COLUMN tipo;

ALTER TABLE contas DROP CONSTRAINT chk_contas_tipo;
ALTER TABLE contas DROP COLUMN tipo;
ALTER TABLE contas DROP COLUMN saldo_atual;
ALTER TABLE contas DROP COLUMN valor_gasto;

ALTER TABLE contas
    ALTER COLUMN conta_financeira_id SET NOT NULL,
    ALTER COLUMN limite_total SET NOT NULL,
    ALTER COLUMN dia_fechamento SET NOT NULL,
    ALTER COLUMN dia_vencimento SET NOT NULL,
    ALTER COLUMN ativo SET NOT NULL;

-- Trigger V26 lia contas.tipo; recriado com a semantica canonica: toda conta
-- referenciada por transacao e cartao.
CREATE OR REPLACE FUNCTION rejeitar_parcela_cartao() RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM transacoes t
        WHERE t.id = NEW.transacao_id AND t.tipo = 'SAIDA' AND t.conta_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Parcela de cartao deve ser persistida em fatura_lancamentos';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 5. Recalculo final das invariantes canonicas antes do commit
-- =============================================================================
SELECT pg_temp.v41_snapshot_metricas_canonico('POS_DROP');
SELECT pg_temp.v41_comparar_metricas('PRE', 'POS_DROP');

DO $$
DECLARE
    v_qtd BIGINT;
BEGIN
    SELECT count(*) INTO v_qtd
    FROM carteiras cf
    WHERE cf.saldo <> COALESCE((SELECT sum(mc.valor_assinado)
                                FROM movimentos_carteira mc
                                WHERE mc.carteira_id = cf.id), 0);
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % conta(s) financeira(s) com saldo <> ledger apos drops', v_qtd;
    END IF;

    SELECT count(*) INTO v_qtd
    FROM contas c JOIN carteiras cf ON cf.id = c.conta_financeira_id
    WHERE cf.saldo <> COALESCE((
            SELECT sum(fl.valor)
            FROM faturas_cartao fc
            JOIN fatura_lancamentos fl ON fl.fatura_id = fc.id
            WHERE fc.conta_id = c.id AND fc.status <> 'PAGA'), 0);
    IF v_qtd > 0 THEN
        RAISE EXCEPTION 'V41 abortada: % cartao(oes) com passivo <> faturas nao pagas apos drops', v_qtd;
    END IF;
END $$;
