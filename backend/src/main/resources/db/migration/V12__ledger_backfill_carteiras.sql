DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT c.id,
                   c.saldo - COALESCE(SUM(m.valor_assinado), 0) AS valor_backfill
            FROM carteiras c
            LEFT JOIN movimentos_carteira m
                ON m.carteira_id = c.id
               AND m.usuario_id = c.usuario_id
            WHERE NOT EXISTS (
                SELECT 1
                FROM movimentos_carteira backfill
                WHERE backfill.carteira_id = c.id
                  AND backfill.origem = 'BACKFILL'
                  AND backfill.referencia_tipo = 'CARTEIRA'
            )
            GROUP BY c.id, c.saldo
        ) pendentes
        WHERE pendentes.valor_backfill < 0
    ) THEN
        RAISE EXCEPTION 'Backfill do Ledger bloqueado: existem carteiras com diferença negativa';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_movimentos_carteira_backfill_carteira
    ON movimentos_carteira(carteira_id)
    WHERE origem = 'BACKFILL'
      AND referencia_tipo = 'CARTEIRA';

INSERT INTO movimentos_carteira (
    usuario_id,
    carteira_id,
    tipo,
    valor,
    valor_assinado,
    origem,
    referencia_tipo,
    referencia_id,
    descricao,
    data_movimento,
    saldo_resultante,
    idempotency_key,
    moeda,
    created_at
)
WITH saldos AS (
    SELECT c.id AS carteira_id,
           c.usuario_id,
           c.saldo AS saldo_materializado,
           c.saldo - COALESCE(SUM(m.valor_assinado), 0) AS valor_backfill
    FROM carteiras c
    LEFT JOIN movimentos_carteira m
        ON m.carteira_id = c.id
       AND m.usuario_id = c.usuario_id
    WHERE NOT EXISTS (
        SELECT 1
        FROM movimentos_carteira backfill
        WHERE backfill.carteira_id = c.id
          AND backfill.origem = 'BACKFILL'
          AND backfill.referencia_tipo = 'CARTEIRA'
    )
    GROUP BY c.id, c.usuario_id, c.saldo
)
SELECT
    s.usuario_id,
    s.carteira_id,
    'ENTRADA',
    s.valor_backfill,
    s.valor_backfill,
    'BACKFILL',
    'CARTEIRA',
    s.carteira_id,
    'Backfill inicial da carteira',
    CURRENT_TIMESTAMP,
    s.saldo_materializado,
    'ledger-backfill-carteira-' || s.carteira_id,
    'BRL',
    CURRENT_TIMESTAMP
FROM saldos s
WHERE s.valor_backfill > 0;
