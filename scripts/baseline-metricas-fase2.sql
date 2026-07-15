-- =============================================================================
-- PR-F2-01 — Baseline das 9 métricas oficiais contra o MODELO ATUAL (pré-Fase 2)
-- =============================================================================
-- Somente leitura (SELECT). Não altera dados. Seguro rodar em produção.
--
-- Objetivo (ADR-0013/ADR-0015): snapshot por usuário que serve de referência
-- "antes" para toda reconciliação das migrations da Fase 2. Métricas
-- impossíveis no modelo antigo são declaradas NAO_CALCULAVEL com justificativa;
-- nenhuma fórmula aproximada vira verdade oficial.
--
-- Uso (local ou VPS):
--   psql "$DATABASE_URL" -f scripts/baseline-metricas-fase2.sql
--
-- Guardar a saída junto ao registro do PR-F2-01 (evidência do PROTOCOLO).
-- =============================================================================

\echo '=== A. Métricas calculáveis no modelo atual (por usuário) ==='
\echo ''
\echo '--- A1. DISPONIVEL_AGORA = SUM(carteiras.saldo)'
\echo '    (modelo atual: toda carteira é caixa de liquidez imediata)'
SELECT c.usuario_id,
       count(*)              AS carteiras,
       sum(c.saldo)          AS disponivel_agora
FROM carteiras c
GROUP BY c.usuario_id
ORDER BY c.usuario_id;

\echo ''
\echo '--- A2. RESERVADO = SUM(metas.valor_reservado) (metas nao arquivadas)'
SELECT m.usuario_id,
       count(*)                          AS metas,
       sum(COALESCE(m.valor_reservado,0)) AS reservado
FROM metas m
WHERE m.status <> 'ARQUIVADA'
GROUP BY m.usuario_id
ORDER BY m.usuario_id;

\echo ''
\echo '--- A3. DIVIDAS = SUM(contas.valor_gasto) das contas CREDITO ativas'
\echo '    (passivo de cartão no modelo atual; invariante contra faturas em A6)'
SELECT ct.usuario_id,
       count(*)                        AS cartoes,
       sum(COALESCE(ct.valor_gasto,0)) AS dividas_cartao
FROM contas ct
WHERE ct.tipo = 'CREDITO' AND ct.ativo = true
GROUP BY ct.usuario_id
ORDER BY ct.usuario_id;

\echo ''
\echo '=== B. Métricas NAO_CALCULAVEL no modelo atual (justificativa) ==='
SELECT * FROM (VALUES
  ('COMPROMETIDO',          'NAO_CALCULAVEL', 'nao existe conceito unificado de obrigacao; faturas, parcelas e contas fixas usam politicas divergentes (P1-3)'),
  ('DISPONIVEL_PARA_GASTAR','NAO_CALCULAVEL', 'depende de COMPROMETIDO e de reserva/alocacao formalizada (ADR-0012/0013)'),
  ('INVESTIDO',             'NAO_CALCULAVEL', 'ativos.valor_atual nao tem fonte nem instante de cotacao (ADR-0011); ver referencia bruta em C3'),
  ('RESULTADO_MENSAL',      'NAO_CALCULAVEL', 'agregacao atual mistura politicas (valorEfetivo na data da transacao) e nao exclui transferencias/investimento por marcacao (ADR-0010)'),
  ('PATRIMONIO_LIQUIDO',    'NAO_CALCULAVEL', 'depende de INVESTIDO e de passivo assinado no ledger (ADR-0008/0009)'),
  ('VARIACAO_PATRIMONIAL',  'NAO_CALCULAVEL', 'exige snapshots de patrimonio por periodo, inexistentes no modelo atual (ADR-0013)')
) AS t(metrica, valor, justificativa);

\echo ''
\echo '=== C. Totais brutos de reconciliação (preservados pelas migrations) ==='
\echo ''
\echo '--- C1. Saldo materializado vs ledger, por carteira'
SELECT c.usuario_id, c.id AS carteira_id, c.saldo AS saldo_materializado,
       COALESCE(l.soma, 0) AS soma_ledger,
       c.saldo - COALESCE(l.soma, 0) AS diferenca
FROM carteiras c
LEFT JOIN (SELECT carteira_id, sum(valor_assinado) AS soma
           FROM movimentos_carteira GROUP BY carteira_id) l
       ON l.carteira_id = c.id
ORDER BY c.usuario_id, c.id;

\echo ''
\echo '--- C2. Reservado por meta (base do COFRE por meta, PR-F2-11)'
SELECT m.usuario_id, m.id AS meta_id, m.status,
       COALESCE(m.valor_reservado,0) AS valor_reservado
FROM metas m
ORDER BY m.usuario_id, m.id;

\echo ''
\echo '--- C3. Posições de investimento (referência bruta, sem cotação datada)'
SELECT a.usuario_id, a.id AS ativo_id, a.ticker, a.quantidade,
       a.custo_total,
       a.valor_atual,
       CASE WHEN a.valor_atual IS NULL THEN NULL
            ELSE round(a.quantidade * a.valor_atual, 2) END AS valor_mercado_bruto
FROM ativos a
ORDER BY a.usuario_id, a.id;

\echo ''
\echo '--- C4. Movimentações de ativo sem carteira (virarão snapshot EXTERNO, PR-F2-13)'
SELECT ma.usuario_id,
       count(*) AS movimentacoes_sem_caixa,
       COALESCE(sum(ma.valor_total),0) AS soma_valor
FROM movimentacoes_ativo ma
WHERE NOT EXISTS (
        SELECT 1 FROM movimentos_carteira mc
        WHERE mc.origem = 'INVESTIMENTO'
          AND mc.referencia_tipo = 'ATIVO'
          AND mc.referencia_id = ma.ativo_id)
GROUP BY ma.usuario_id
ORDER BY ma.usuario_id;

\echo ''
\echo '--- C5. Transações órfãs (sem carteira e sem movimento; destino: PENDENTE_CONCILIACAO, PR-F2-05)'
SELECT t.usuario_id,
       count(*) AS transacoes_sem_carteira,
       COALESCE(sum(t.valor_total),0) AS soma_valor
FROM transacoes t
WHERE t.ativa = true
  AND t.carteira_id IS NULL
  AND NOT (t.tipo = 'SAIDA' AND EXISTS
             (SELECT 1 FROM contas c WHERE c.id = t.conta_id AND c.tipo = 'CREDITO'))
GROUP BY t.usuario_id
ORDER BY t.usuario_id;

\echo ''
\echo '--- C6. Invariante do passivo de cartão: valor_gasto == lançamentos de faturas não pagas'
SELECT ct.usuario_id, ct.id AS conta_id,
       COALESCE(ct.valor_gasto,0) AS valor_gasto,
       COALESCE(f.soma,0)         AS soma_faturas_nao_pagas,
       COALESCE(ct.valor_gasto,0) - COALESCE(f.soma,0) AS diferenca
FROM contas ct
LEFT JOIN (SELECT fc.conta_id, sum(fl.valor) AS soma
           FROM faturas_cartao fc
           JOIN fatura_lancamentos fl ON fl.fatura_id = fc.id
           WHERE fc.status <> 'PAGA'
           GROUP BY fc.conta_id) f ON f.conta_id = ct.id
WHERE ct.tipo = 'CREDITO' AND ct.ativo = true
ORDER BY ct.usuario_id, ct.id;
