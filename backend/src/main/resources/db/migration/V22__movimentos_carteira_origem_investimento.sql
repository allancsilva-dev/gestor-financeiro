-- PROB-0054: compra/venda/dividendo de ativo passam a movimentar o caixa via ledger
-- (LedgerService), registrando MovimentoCarteira com origem = 'INVESTIMENTO'. O CHECK
-- chk_movimentos_carteira_origem criado em V20 restringe origem ao dominio conhecido,
-- entao INVESTIMENTO precisa ser adicionado ao dominio, senao o insert do ledger falha.
--
-- tipo continua usando ENTRADA/SAIDA (ja no dominio da V20), nao precisa alterar.

ALTER TABLE movimentos_carteira
    DROP CONSTRAINT chk_movimentos_carteira_origem;

ALTER TABLE movimentos_carteira
    ADD CONSTRAINT chk_movimentos_carteira_origem CHECK (origem IN (
        'CARTEIRA_AJUSTE', 'TRANSACAO', 'PARCELA', 'CONTA_FIXA',
        'FATURA_CARTAO', 'META', 'TRANSFERENCIA', 'INVESTIMENTO', 'BACKFILL'
    ));
