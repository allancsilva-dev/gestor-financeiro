-- PROB-0052: o UNIQUE(fatura_id, transacao_id, parcela_numero) criado em V17 nao
-- impede duplicidade para compra a vista, porque no PostgreSQL varios NULL em coluna
-- nullable nao violam unique. Compras a vista usam parcela_numero = NULL, entao o
-- mesmo lancamento podia ser inserido duas vezes, inflando fatura e Conta.valorGasto.
--
-- Correcao: trocar a constraint por um indice unico funcional com COALESCE. Parcelas
-- reais sao numeradas a partir de 1, entao 0 nunca colide com um parcela_numero valido.
-- Lancamentos AJUSTE/ESTORNO (tambem com parcela_numero NULL) nao colidem porque cada
-- mutacao (ressincronizar/cancelar) remove antes todos os lancamentos abertos da
-- transacao, deixando no maximo um lancamento NULL por (fatura, transacao).

-- Remove a unique constraint inline da V17 sem depender do nome auto-gerado.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'fatura_lancamentos'::regclass
      AND contype = 'u';
    IF cname IS NOT NULL THEN
        EXECUTE 'ALTER TABLE fatura_lancamentos DROP CONSTRAINT ' || quote_ident(cname);
    END IF;
END $$;

-- Se existir dado duplicado legado (fatura, transacao, parcela NULL), este CREATE
-- falha de proposito: a duplicidade precisa ser resolvida por um humano, nunca
-- deduplicada silenciosamente (lancamento financeiro).
CREATE UNIQUE INDEX ux_fatura_lancamentos_unico
    ON fatura_lancamentos (fatura_id, transacao_id, COALESCE(parcela_numero, 0));
