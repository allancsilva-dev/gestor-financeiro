-- Indices de suporte para RelatorioService e ProjecaoService (PROB-0053).
-- As queries agregadas filtram sempre por transacoes ativas; indice parcial
-- WHERE ativa = true e menor e casa exatamente com o predicado.

-- Relatorio: sum/count/maiores despesas/gasto por categoria (usuario_id, tipo, data, ativa=true).
-- Gasto por conta ja usa idx_transacoes_conta (conta_id) para o join.
CREATE INDEX IF NOT EXISTS idx_transacoes_usuario_tipo_data_ativa
    ON transacoes (usuario_id, tipo, data)
    WHERE ativa = true;

-- Projecao: somarPlanejadoNoPeriodo (contas fixas ativas vencendo no periodo).
CREATE INDEX IF NOT EXISTS idx_contas_fixas_usuario_vencimento_ativo
    ON contas_fixas (usuario_id, data_proximo_vencimento)
    WHERE ativo = true;

-- Projecao: somarValorTotalPorStatusNoPeriodo (faturas por status vencendo no periodo).
CREATE INDEX IF NOT EXISTS idx_faturas_usuario_status_vencimento
    ON faturas_cartao (usuario_id, status, data_vencimento);
