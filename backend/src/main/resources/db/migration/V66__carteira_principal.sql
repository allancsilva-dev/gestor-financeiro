-- Conta principal vira conceito de dominio.
--
-- Ate aqui "Conta Principal" era so o texto default de um campo do onboarding: nao havia
-- coluna, regra nem forma de escolher outra. Quem lancava caia sempre em carteiras[0], que e
-- ordem de insercao, nao decisao do titular.
ALTER TABLE carteiras ADD COLUMN principal BOOLEAN NOT NULL DEFAULT FALSE;

-- No maximo uma principal por titular. Indice parcial e nao UNIQUE simples porque a restricao
-- vale so entre as marcadas: varias contas do mesmo usuario com principal = FALSE convivem.
-- Consequencia para o servico: desmarcar a atual precisa acontecer ANTES de marcar a nova,
-- na mesma transacao.
CREATE UNIQUE INDEX ux_carteiras_principal_usuario ON carteiras (usuario_id) WHERE principal;

-- Backfill deterministico: a conta ATIVO manual de menor id de cada titular e a que o
-- onboarding criou. Subtipos gerenciados (CARTAO, COFRE, CUSTODIA) ficam de fora porque
-- nascem de outro modulo e sao somente leitura. Quem nao tem conta manual fica sem principal
-- ate criar uma -- estado valido, nao erro.
UPDATE carteiras c
   SET principal = TRUE
  FROM (
        SELECT MIN(id) AS id
          FROM carteiras
         WHERE natureza = 'ATIVO'
           AND subtipo IN ('DINHEIRO', 'CORRENTE', 'POUPANCA', 'PAGAMENTO')
         GROUP BY usuario_id
       ) primeira
 WHERE c.id = primeira.id;
