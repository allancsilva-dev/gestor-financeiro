-- FaturaLancamento e a fonte canonica do cronograma de cartao. Linhas historicas
-- permanecem ate a migration de contrato; novas duplicidades sao bloqueadas.
CREATE OR REPLACE FUNCTION rejeitar_parcela_cartao() RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM transacoes t
        JOIN contas c ON c.id = t.conta_id
        WHERE t.id = NEW.transacao_id AND t.tipo = 'SAIDA' AND c.tipo = 'CREDITO'
    ) THEN
        RAISE EXCEPTION 'Parcela de cartao deve ser persistida em fatura_lancamentos';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_rejeitar_parcela_cartao ON parcelas;
CREATE TRIGGER trg_rejeitar_parcela_cartao
BEFORE INSERT OR UPDATE OF transacao_id ON parcelas
FOR EACH ROW EXECUTE FUNCTION rejeitar_parcela_cartao();

CREATE INDEX IF NOT EXISTS idx_faturas_projecao
    ON faturas_cartao (usuario_id, data_vencimento, status);
