-- Frequencia de recorrencia (BACKLOG-0120). Ate aqui o motor so sabia plusMonths(1):
-- assinatura anual (Amazon Prime) ou semanal nao tinha caminho de cadastro nenhum.
--
-- Expand puro e aditivo (ADR-0015): o default MENSAL preserva exatamente o
-- comportamento atual e nenhuma linha existente muda de significado. Sem backfill e
-- sem data-migration, entao o protocolo de snapshot/guard/restore drill nao se aplica.

ALTER TABLE contas_fixas
    ADD COLUMN frequencia  VARCHAR(20) NOT NULL DEFAULT 'MENSAL',
    ADD COLUMN data_ancora DATE;

ALTER TABLE contas_fixas ADD CONSTRAINT ck_contas_fixas_frequencia
    CHECK (frequencia IN ('SEMANAL','QUINZENAL','MENSAL','BIMESTRAL',
                          'TRIMESTRAL','SEMESTRAL','ANUAL'));

-- Frequencia sub-mensal nao cabe em dia_vencimento (1..31, chk da V20): precisa de uma
-- data de origem para fixar o dia da semana e a paridade da quinzena.
ALTER TABLE contas_fixas ADD CONSTRAINT ck_contas_fixas_ancora_sub_mensal
    CHECK (frequencia NOT IN ('SEMANAL','QUINZENAL') OR data_ancora IS NOT NULL);

-- Ancora so existe onde e usada; sobra de formulario nao pode virar verdade acidental.
ALTER TABLE contas_fixas ADD CONSTRAINT ck_contas_fixas_ancora_apenas_sub_mensal
    CHECK (frequencia IN ('SEMANAL','QUINZENAL') OR data_ancora IS NULL);

-- Sem indice novo de proposito: a query quente do motor e findIdsAutomaticasVencidas,
-- que filtra por data_proximo_vencimento e ja tem o indice parcial da V29. Um indice em
-- frequencia so pagaria custo de escrita — diferente da V67, onde conta_id virou chave
-- de juncao.

COMMENT ON COLUMN contas_fixas.frequencia IS
    'Periodicidade da ocorrencia. MENSAL preserva o comportamento anterior a V72.';
COMMENT ON COLUMN contas_fixas.data_ancora IS
    'Primeira ocorrencia de recorrencia sub-mensal; fixa dia da semana e paridade da quinzena. NULL em MENSAL+, onde a serie sai de dia_vencimento.';
