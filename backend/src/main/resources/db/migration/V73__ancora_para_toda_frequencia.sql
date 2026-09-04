-- Ancora deixa de ser exclusiva de recorrencia sub-mensal.
--
-- A V72 amarrou data_ancora a SEMANAL/QUINZENAL. Na pratica isso impediu escolher o MES
-- de uma cobranca anual: sem ancora, a serie sai de dia_vencimento no mes corrente, entao
-- "Amazon Prime todo 15 de marco" cadastrado em setembro caia em 15 de setembro.
--
-- O mesmo buraco fazia ContaFixaService.atualizar antecipar o aniversario: como o proximo
-- vencimento era recalculado a partir de hoje, editar o valor de uma anual em setembro
-- movia a cobranca de marco para setembro.
--
-- Com a ancora valendo para qualquer frequencia, a serie deixa de depender de "hoje".
-- Expand puro e aditivo (ADR-0015): nenhuma linha existente muda de significado, porque
-- hoje data_ancora e NULL em toda frequencia que nao seja SEMANAL/QUINZENAL, e MENSAL sem
-- ancora continua com o comportamento anterior.

ALTER TABLE contas_fixas DROP CONSTRAINT ck_contas_fixas_ancora_apenas_sub_mensal;

-- Sub-mensal continua exigindo ancora (dia do mes nao descreve "toda terca"); as demais
-- frequencias passam a aceitar ancora opcional. MENSAL nao usa: todo mes tem ocorrencia,
-- entao o mes de partida e irrelevante e a ancora so criaria dado redundante.
ALTER TABLE contas_fixas ADD CONSTRAINT ck_contas_fixas_ancora_por_frequencia
    CHECK (frequencia <> 'MENSAL' OR data_ancora IS NULL);

COMMENT ON COLUMN contas_fixas.data_ancora IS
    'Primeira ocorrencia da serie. Obrigatoria em SEMANAL/QUINZENAL (fixa dia da semana e paridade) e opcional de BIMESTRAL a ANUAL (fixa o mes do aniversario). NULL em MENSAL, onde a serie sai de dia_vencimento.';
