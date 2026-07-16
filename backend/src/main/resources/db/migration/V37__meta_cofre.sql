-- =============================================================================
-- V37 — PR-F2-11: cofre real por meta (ADR-0012)
-- =============================================================================
-- Uma conta financeira COFRE por meta com reserva. O modelo atual ja e um
-- cofrinho sem conta de destino: RESERVA/RESGATE_META debitavam a carteira e o
-- valor vivia so em metas.valor_reservado. Esta migration cria o destino.
-- Movimentos antigos permanecem intactos (regra de historico, ADR-0009); o
-- cofre nasce com saldo de abertura = valor_reservado atual (origem BACKFILL,
-- nao afeta resultado mensal nem variacao patrimonial economica).
-- Invariante (guard): metas.valor_reservado == saldo do COFRE, por meta.
-- =============================================================================

ALTER TABLE metas
    ADD COLUMN cofre_id BIGINT UNIQUE REFERENCES carteiras(id);

DO $$
DECLARE
    meta_row RECORD;
    novo_cofre_id BIGINT;
BEGIN
    FOR meta_row IN
        SELECT m.id, m.usuario_id, m.nome, COALESCE(m.valor_reservado, 0) AS reservado
        FROM metas m
        WHERE COALESCE(m.valor_reservado, 0) > 0
          AND m.cofre_id IS NULL
    LOOP
        INSERT INTO carteiras (nome, tipo, subtipo, natureza, liquidez, origem_dados,
                               estado_conciliacao, moeda, saldo, usuario_id, version)
        VALUES (left('Cofre: ' || meta_row.nome, 100), 'POUPANCA', 'COFRE', 'ATIVO',
                'IMEDIATA', 'MANUAL', 'CONCILIADA', 'BRL', meta_row.reservado,
                meta_row.usuario_id, 0)
        RETURNING id INTO novo_cofre_id;

        UPDATE metas SET cofre_id = novo_cofre_id WHERE id = meta_row.id;

        INSERT INTO movimentos_carteira (
            usuario_id, carteira_id, tipo, valor, valor_assinado, origem,
            referencia_tipo, referencia_id, descricao, data_movimento,
            saldo_resultante, idempotency_key, moeda, created_at)
        VALUES (
            meta_row.usuario_id, novo_cofre_id, 'ENTRADA', meta_row.reservado,
            meta_row.reservado, 'BACKFILL', 'META', meta_row.id,
            'Saldo de abertura da reserva da meta (migracao)', CURRENT_TIMESTAMP,
            meta_row.reservado, 'cofre-meta-backfill-' || meta_row.id, 'BRL',
            CURRENT_TIMESTAMP);
    END LOOP;
END $$;

-- Guards (ADR-0015)
DO $$
DECLARE
    violacoes BIGINT;
BEGIN
    SELECT count(*) INTO violacoes
    FROM metas m
    LEFT JOIN carteiras c ON c.id = m.cofre_id
    WHERE COALESCE(m.valor_reservado, 0) > 0
      AND (c.id IS NULL OR c.saldo <> COALESCE(m.valor_reservado, 0));
    IF violacoes > 0 THEN
        RAISE EXCEPTION 'V37 abortada: % meta(s) com reserva divergente do cofre', violacoes;
    END IF;
END $$;
