-- RELEASE B: mover para db/migration somente depois do dry-run card-schedule na VPS.
-- Falha antes de apagar quando a equivalencia financeira nao estiver comprovada.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM transacoes t
        JOIN contas c ON c.id = t.conta_id AND c.tipo = 'CREDITO'
        WHERE t.tipo = 'SAIDA'
          AND EXISTS (SELECT 1 FROM parcelas p WHERE p.transacao_id = t.id)
          AND (
            (SELECT COUNT(*) FROM parcelas p WHERE p.transacao_id = t.id)
              <> (SELECT COUNT(*) FROM fatura_lancamentos fl WHERE fl.transacao_id = t.id AND fl.tipo = 'COMPRA')
            OR (SELECT COALESCE(SUM(p.valor), 0) FROM parcelas p WHERE p.transacao_id = t.id)
              <> (SELECT COALESCE(SUM(fl.valor), 0) FROM fatura_lancamentos fl WHERE fl.transacao_id = t.id AND fl.tipo = 'COMPRA')
            OR EXISTS (
              SELECT 1 FROM parcelas p WHERE p.transacao_id = t.id
              AND NOT EXISTS (
                SELECT 1 FROM fatura_lancamentos fl
                WHERE fl.transacao_id = t.id AND fl.tipo = 'COMPRA'
                  AND COALESCE(fl.parcela_numero, 1) = p.numero_parcela
                  AND COALESCE(fl.total_parcelas, 1) = p.total_parcelas
                  AND fl.valor = p.valor
              )
            )
          )
    ) THEN
        RAISE EXCEPTION 'Divergencia entre parcelas e fatura_lancamentos; contrato abortado';
    END IF;
END $$;

DELETE FROM parcelas p
USING transacoes t, contas c
WHERE p.transacao_id = t.id
  AND t.conta_id = c.id
  AND t.tipo = 'SAIDA'
  AND c.tipo = 'CREDITO';
