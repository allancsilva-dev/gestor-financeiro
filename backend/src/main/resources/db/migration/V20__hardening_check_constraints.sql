-- PROB-0051: invariantes financeiros basicos garantidos no banco.
-- Antes, valores/dias/enums dependiam quase so da validacao Java; imports, scripts
-- ou novas rotas podiam persistir estado invalido mesmo com ddl-auto=validate.
-- Cada CHECK abaixo reflete uma regra que o codigo ja assume verdadeira.
-- Campos que legitimamente podem ser negativos/zero NAO sao restringidos:
--   contas.valor_gasto (cartao pode ficar negativo apos estorno),
--   categorias.valor_gasto, carteiras.saldo, contas.saldo_atual,
--   faturas_cartao.valor_total (rollover/estorno pode zerar ou negativar).

-- transacoes
ALTER TABLE transacoes
    ADD CONSTRAINT chk_transacoes_valor_total_positivo CHECK (valor_total > 0),
    ADD CONSTRAINT chk_transacoes_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    ADD CONSTRAINT chk_transacoes_status CHECK (status IN ('PAGO', 'PENDENTE', 'ATRASADO', 'CANCELADO')),
    ADD CONSTRAINT chk_transacoes_total_parcelas CHECK (total_parcelas IS NULL OR total_parcelas >= 1),
    ADD CONSTRAINT chk_transacoes_valor_parcela CHECK (valor_parcela IS NULL OR valor_parcela > 0);

-- parcelas
ALTER TABLE parcelas
    ADD CONSTRAINT chk_parcelas_numero_positivo CHECK (numero_parcela >= 1),
    ADD CONSTRAINT chk_parcelas_total_positivo CHECK (total_parcelas >= 1),
    ADD CONSTRAINT chk_parcelas_numero_le_total CHECK (numero_parcela <= total_parcelas),
    ADD CONSTRAINT chk_parcelas_valor_positivo CHECK (valor > 0),
    ADD CONSTRAINT chk_parcelas_status CHECK (status IN ('PAGO', 'PENDENTE', 'ATRASADO', 'CANCELADO'));

-- contas
ALTER TABLE contas
    ADD CONSTRAINT chk_contas_tipo CHECK (tipo IN ('CREDITO', 'DEBITO', 'DINHEIRO', 'POUPANCA')),
    ADD CONSTRAINT chk_contas_limite_nao_negativo CHECK (limite_total IS NULL OR limite_total >= 0),
    ADD CONSTRAINT chk_contas_dia_fechamento CHECK (dia_fechamento IS NULL OR dia_fechamento BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_contas_dia_vencimento CHECK (dia_vencimento IS NULL OR dia_vencimento BETWEEN 1 AND 31);

-- carteiras
ALTER TABLE carteiras
    ADD CONSTRAINT chk_carteiras_tipo CHECK (tipo IN ('DINHEIRO', 'CONTA_BANCARIA', 'POUPANCA'));

-- categorias
ALTER TABLE categorias
    ADD CONSTRAINT chk_categorias_valor_esperado_nao_negativo CHECK (valor_esperado IS NULL OR valor_esperado >= 0);

-- contas_fixas
ALTER TABLE contas_fixas
    ADD CONSTRAINT chk_contas_fixas_valor_planejado_positivo CHECK (valor_planejado > 0),
    ADD CONSTRAINT chk_contas_fixas_valor_real CHECK (valor_real IS NULL OR valor_real >= 0),
    ADD CONSTRAINT chk_contas_fixas_dia_vencimento CHECK (dia_vencimento BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_contas_fixas_status CHECK (status IN ('PAGO', 'PENDENTE', 'ATRASADO', 'CANCELADO'));

-- metas
ALTER TABLE metas
    ADD CONSTRAINT chk_metas_valor_total_positivo CHECK (valor_total > 0),
    ADD CONSTRAINT chk_metas_valor_reservado_nao_negativo CHECK (valor_reservado IS NULL OR valor_reservado >= 0),
    ADD CONSTRAINT chk_metas_valor_mensal CHECK (valor_mensal IS NULL OR valor_mensal >= 0);

-- orcamentos_mensais
ALTER TABLE orcamentos_mensais
    ADD CONSTRAINT chk_orcamentos_mensais_mes CHECK (mes BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_orcamentos_mensais_valor CHECK (valor_total_planejado IS NULL OR valor_total_planejado >= 0);

-- orcamentos_categorias
ALTER TABLE orcamentos_categorias
    ADD CONSTRAINT chk_orcamentos_categorias_valor_limite CHECK (valor_limite >= 0);

-- faturas_cartao
ALTER TABLE faturas_cartao
    ADD CONSTRAINT chk_faturas_mes CHECK (mes BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_faturas_valor_pago_nao_negativo CHECK (valor_pago IS NULL OR valor_pago >= 0),
    ADD CONSTRAINT chk_faturas_status CHECK (status IN ('ABERTA', 'FECHADA', 'PAGA', 'VENCIDA'));

-- ativos (quantidade >= 0 tambem serve de backstop para PROB-0054: venda acima da posicao)
ALTER TABLE ativos
    ADD CONSTRAINT chk_ativos_quantidade_nao_negativa CHECK (quantidade >= 0),
    ADD CONSTRAINT chk_ativos_custo_total_nao_negativo CHECK (custo_total IS NULL OR custo_total >= 0),
    ADD CONSTRAINT chk_ativos_valor_atual CHECK (valor_atual IS NULL OR valor_atual >= 0),
    ADD CONSTRAINT chk_ativos_tipo CHECK (tipo IN ('ACAO', 'FII', 'RENDA_FIXA', 'CRIPTO', 'OUTRO'));

-- movimentacoes_ativo (quantidade >= 0: dividendo/bonificacao podem ter quantidade 0)
ALTER TABLE movimentacoes_ativo
    ADD CONSTRAINT chk_mov_ativo_quantidade_nao_negativa CHECK (quantidade >= 0),
    ADD CONSTRAINT chk_mov_ativo_preco_nao_negativo CHECK (preco_unitario >= 0),
    ADD CONSTRAINT chk_mov_ativo_tipo CHECK (tipo IN ('COMPRA', 'VENDA', 'DIVIDENDO', 'BONIFICACAO'));

-- movimentos_meta (tipo string ADICAO/REMOCAO; valor_assinado coerente com o tipo)
ALTER TABLE movimentos_meta
    ADD CONSTRAINT chk_mov_meta_valor_positivo CHECK (valor > 0),
    ADD CONSTRAINT chk_mov_meta_tipo CHECK (tipo IN ('ADICAO', 'REMOCAO')),
    ADD CONSTRAINT chk_mov_meta_coerencia CHECK (
        (tipo = 'ADICAO' AND valor_assinado = valor)
        OR (tipo = 'REMOCAO' AND valor_assinado = -valor)
    );

-- movimentos_carteira: complementa os CHECK de valor ja criados em V11 com os enums
ALTER TABLE movimentos_carteira
    ADD CONSTRAINT chk_movimentos_carteira_tipo CHECK (tipo IN (
        'ENTRADA', 'SAIDA', 'AJUSTE_MANUAL', 'TRANSFERENCIA_ENTRADA',
        'TRANSFERENCIA_SAIDA', 'RESERVA_META', 'RESGATE_META', 'ESTORNO'
    )),
    ADD CONSTRAINT chk_movimentos_carteira_origem CHECK (origem IN (
        'CARTEIRA_AJUSTE', 'TRANSACAO', 'PARCELA', 'CONTA_FIXA',
        'FATURA_CARTAO', 'META', 'TRANSFERENCIA', 'BACKFILL'
    ));
