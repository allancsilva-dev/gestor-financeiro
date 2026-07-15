-- Auditoria read-only: rendas potencialmente gravadas como SAIDA (PROB-0075 / P0-1).
-- NAO executa UPDATE. Produz candidatos para correcao assistida individual, com backup
-- e plano de reconciliacao proprios (ver ADR-0002 e PROBLEM_LEDGER PROB-0075).
--
-- Uso: psql "$DATABASE_URL" -f scripts/audit-renda-onboarding.sql

-- 1) Contas fixas suspeitas de serem renda gravada como SAIDA.
--    Heuristica apenas sinaliza; nao ha proveniencia confiavel de onboarding.
SELECT cf.id,
       cf.usuario_id,
       cf.nome,
       cf.tipo,
       cf.valor_planejado,
       cf.recorrente,
       cf.ativo,
       cat.nome AS categoria
FROM contas_fixas cf
LEFT JOIN categorias cat ON cat.id = cf.categoria_id
WHERE cf.tipo = 'SAIDA'
  AND lower(cf.nome) ~ '(sal[aá]rio|renda|provento|pagamento mensal|freela|pr[oó]-labore)'
ORDER BY cf.usuario_id, cf.id;

-- 2) Execucoes de recorrencia geradas a partir das contas fixas suspeitas
--    (o scheduler pode ter materializado saidas erradas).
SELECT er.id,
       er.conta_fixa_id,
       er.usuario_id,
       er.status,
       er.transacao_id
FROM execucoes_recorrencia er
JOIN contas_fixas cf ON cf.id = er.conta_fixa_id
WHERE cf.tipo = 'SAIDA'
  AND lower(cf.nome) ~ '(sal[aá]rio|renda|provento|pagamento mensal|freela|pr[oó]-labore)'
ORDER BY er.conta_fixa_id, er.id;

-- 3) Transacoes vinculadas a essas execucoes, com impacto em carteira/ledger.
SELECT t.id,
       t.usuario_id,
       t.tipo,
       t.valor,
       t.data,
       t.carteira_id,
       er.conta_fixa_id
FROM transacoes t
JOIN execucoes_recorrencia er ON er.transacao_id = t.id
JOIN contas_fixas cf ON cf.id = er.conta_fixa_id
WHERE cf.tipo = 'SAIDA'
  AND lower(cf.nome) ~ '(sal[aá]rio|renda|provento|pagamento mensal|freela|pr[oó]-labore)'
ORDER BY t.usuario_id, t.data;

-- 4) Movimentos de carteira ligados a essas transacoes (efeito no saldo materializado).
SELECT mc.id,
       mc.carteira_id,
       mc.tipo,
       mc.valor,
       mc.valor_assinado,
       mc.origem,
       mc.referencia_id AS transacao_id
FROM movimentos_carteira mc
JOIN execucoes_recorrencia er ON er.transacao_id = mc.referencia_id
JOIN contas_fixas cf ON cf.id = er.conta_fixa_id
WHERE mc.referencia_tipo = 'TRANSACAO'
  AND cf.tipo = 'SAIDA'
  AND lower(cf.nome) ~ '(sal[aá]rio|renda|provento|pagamento mensal|freela|pr[oó]-labore)'
ORDER BY mc.carteira_id, mc.id;

-- 5) Resumo por usuario para priorizar contato/correcao.
SELECT cf.usuario_id,
       count(DISTINCT cf.id)              AS contas_fixas_suspeitas,
       count(DISTINCT er.id)              AS execucoes,
       count(DISTINCT er.transacao_id)    AS transacoes_geradas,
       coalesce(sum(t.valor), 0)          AS valor_total_transacoes
FROM contas_fixas cf
LEFT JOIN execucoes_recorrencia er ON er.conta_fixa_id = cf.id
LEFT JOIN transacoes t ON t.id = er.transacao_id
WHERE cf.tipo = 'SAIDA'
  AND lower(cf.nome) ~ '(sal[aá]rio|renda|provento|pagamento mensal|freela|pr[oó]-labore)'
GROUP BY cf.usuario_id
ORDER BY valor_total_transacoes DESC;
