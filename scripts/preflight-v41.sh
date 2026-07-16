#!/bin/bash
# Gestor Financeiro — PR-F2-19: preflight do contract V41 (somente leitura)
#
# Gera o artefato pre-v41 (CSV por seção + resumo JSON) e retorna exit != 0
# se qualquer invariante bloqueante do contract estiver violada.
#
# Uso:
#   DATABASE_URL='postgresql://user:pass@host:5432/db' scripts/preflight-v41.sh /caminho/seguro/pre-v41
#
# Nunca escreve no banco. Artefatos contêm dados financeiros: manter fora do Git.

set -euo pipefail

DATABASE_URL="${DATABASE_URL:?defina DATABASE_URL (connstring psql)}"
if [ "$#" -ne 1 ]; then
  echo "Uso: $0 DIRETORIO_DE_EVIDENCIA_FORA_DO_REPOSITORIO" >&2
  exit 2
fi
OUTDIR="$1"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
case "$(cd "$(dirname "$OUTDIR")" && pwd -P)/$(basename "$OUTDIR")" in
  "$REPO_ROOT"|"$REPO_ROOT"/*) echo "Diretorio de evidencia deve ficar fora do repositorio" >&2; exit 2 ;;
esac
mkdir -p "$OUTDIR"

PSQL=(psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -X -q)

copy_csv() { # $1=arquivo $2=query
  "${PSQL[@]}" -c "\\copy ($2) TO '$OUTDIR/$1' WITH (FORMAT csv, HEADER true)"
}

# -----------------------------------------------------------------------------
# Snapshot por usuário (plano PR-F2-19 §3)
# -----------------------------------------------------------------------------

# As 9 métricas oficiais (fórmulas do MetricasService; semântica legada com
# contas.tipo, válida pré-V41)
copy_csv "metricas.csv" "
WITH params AS (
  SELECT current_date AS hoje,
         date_trunc('month', current_date)::date AS inicio_mes,
         (date_trunc('month', current_date) + interval '1 month - 1 day')::date AS fim_mes,
         DATE '2000-01-01' AS inicio_obr
)
SELECT u.id AS usuario_id, m.metrica, m.valor
FROM usuarios u CROSS JOIN params p
CROSS JOIN LATERAL (VALUES
  ('DISPONIVEL_AGORA', (SELECT COALESCE(sum(c.saldo),0) FROM carteiras c
     WHERE c.usuario_id=u.id AND c.natureza='ATIVO' AND c.liquidez='IMEDIATA')),
  ('RESERVADO', (SELECT COALESCE(sum(c.saldo),0) FROM carteiras c
     WHERE c.usuario_id=u.id AND c.subtipo='COFRE')
    + (SELECT COALESCE(sum(COALESCE(mt.valor_reservado,0)),0) FROM metas mt
     WHERE mt.usuario_id=u.id AND mt.modalidade='RESERVA_VIRTUAL' AND mt.status<>'ARQUIVADA')),
  ('COMPROMETIDO', (SELECT COALESCE(sum(GREATEST(COALESCE(f.valor_total,0)-COALESCE(f.valor_pago,0),0)),0)
     FROM faturas_cartao f
     WHERE f.usuario_id=u.id AND f.status<>'PAGA'
       AND f.data_vencimento BETWEEN p.inicio_obr AND p.fim_mes
       AND NOT EXISTS (SELECT 1 FROM fatura_lancamentos fl WHERE fl.fatura_origem_id=f.id))
    + (SELECT COALESCE(sum(pa.valor),0)
       FROM parcelas pa JOIN transacoes t ON t.id=pa.transacao_id
       WHERE t.usuario_id=u.id AND pa.status<>'PAGO'
         AND NOT (t.tipo='SAIDA' AND t.conta_id IS NOT NULL
                  AND EXISTS (SELECT 1 FROM contas cc WHERE cc.id=t.conta_id AND cc.tipo='CREDITO'))
         AND pa.data_vencimento BETWEEN p.inicio_obr AND p.fim_mes)),
  ('DISPONIVEL_PARA_GASTAR',
     (SELECT COALESCE(sum(c.saldo),0) FROM carteiras c
      WHERE c.usuario_id=u.id AND c.natureza='ATIVO' AND c.liquidez='IMEDIATA')
     - ((SELECT COALESCE(sum(c.saldo),0) FROM carteiras c
         WHERE c.usuario_id=u.id AND c.subtipo='COFRE')
        + (SELECT COALESCE(sum(COALESCE(mt.valor_reservado,0)),0) FROM metas mt
           WHERE mt.usuario_id=u.id AND mt.modalidade='RESERVA_VIRTUAL' AND mt.status<>'ARQUIVADA'))
     - ((SELECT COALESCE(sum(GREATEST(COALESCE(f.valor_total,0)-COALESCE(f.valor_pago,0),0)),0)
         FROM faturas_cartao f WHERE f.usuario_id=u.id AND f.status<>'PAGA'
           AND f.data_vencimento BETWEEN p.inicio_obr AND p.fim_mes
           AND NOT EXISTS (SELECT 1 FROM fatura_lancamentos fl WHERE fl.fatura_origem_id=f.id))
        + (SELECT COALESCE(sum(pa.valor),0) FROM parcelas pa JOIN transacoes t ON t.id=pa.transacao_id
           WHERE t.usuario_id=u.id AND pa.status<>'PAGO'
             AND NOT (t.tipo='SAIDA' AND t.conta_id IS NOT NULL
                      AND EXISTS (SELECT 1 FROM contas cc WHERE cc.id=t.conta_id AND cc.tipo='CREDITO'))
             AND pa.data_vencimento BETWEEN p.inicio_obr AND p.fim_mes))),
  ('INVESTIDO', (SELECT COALESCE(sum(a.quantidade*a.valor_atual),0) FROM ativos a
     WHERE a.usuario_id=u.id AND a.valor_atual IS NOT NULL AND a.cotacao_em IS NOT NULL AND a.quantidade>0)),
  ('DIVIDAS', (SELECT COALESCE(sum(GREATEST(c.saldo,0)),0) FROM carteiras c
     WHERE c.usuario_id=u.id AND c.natureza='PASSIVO')),
  ('RESULTADO_MENSAL', (SELECT COALESCE(sum(CASE WHEN t.tipo='ENTRADA' THEN t.valor_total ELSE -t.valor_total END),0)
     FROM transacoes t
     WHERE t.usuario_id=u.id AND t.ativa=true AND t.estado_conciliacao='CONCILIADA'
       AND t.data BETWEEN p.inicio_mes AND p.fim_mes
       AND (t.tipo='ENTRADA' OR NOT (t.conta_id IS NOT NULL
            AND EXISTS (SELECT 1 FROM contas cc WHERE cc.id=t.conta_id AND cc.tipo='CREDITO'))))
    - (SELECT COALESCE(sum(fl.valor),0)
       FROM fatura_lancamentos fl JOIN faturas_cartao f ON f.id=fl.fatura_id
       WHERE f.usuario_id=u.id AND fl.tipo IN ('COMPRA','AJUSTE','ESTORNO')
         AND fl.data_compra BETWEEN p.inicio_mes AND p.fim_mes)),
  ('PATRIMONIO_LIQUIDO', (SELECT COALESCE(sum(c.saldo),0) FROM carteiras c
     WHERE c.usuario_id=u.id AND c.natureza='ATIVO')
    + (SELECT COALESCE(sum(a.quantidade*a.valor_atual),0) FROM ativos a
       WHERE a.usuario_id=u.id AND a.valor_atual IS NOT NULL AND a.cotacao_em IS NOT NULL AND a.quantidade>0)
    - (SELECT COALESCE(sum(c.saldo),0) FROM carteiras c
       WHERE c.usuario_id=u.id AND c.natureza='PASSIVO')),
  ('VARIACAO_PATRIMONIAL', (SELECT COALESCE(sum(mc.valor_assinado),0)
     FROM movimentos_carteira mc JOIN carteiras c ON c.id=mc.carteira_id
     WHERE mc.usuario_id=u.id AND c.natureza='ATIVO' AND mc.origem<>'BACKFILL'
       AND mc.data_movimento >= p.inicio_mes::timestamp AND mc.data_movimento < (p.hoje+1)::timestamp)
    - (SELECT COALESCE(sum(mc.valor_assinado),0)
       FROM movimentos_carteira mc JOIN carteiras c ON c.id=mc.carteira_id
       WHERE mc.usuario_id=u.id AND c.natureza='PASSIVO' AND mc.origem<>'BACKFILL'
         AND mc.data_movimento >= p.inicio_mes::timestamp AND mc.data_movimento < (p.hoje+1)::timestamp)
    + (SELECT COALESCE(sum(CASE ma.tipo WHEN 'COMPRA' THEN COALESCE(ma.valor_total,0)
                                        WHEN 'VENDA' THEN -COALESCE(ma.valor_total,0) ELSE 0 END),0)
       FROM movimentacoes_ativo ma
       WHERE ma.usuario_id=u.id AND ma.tipo IN ('COMPRA','VENDA')
         AND ma.data BETWEEN p.inicio_mes AND p.hoje))
) AS m(metrica, valor)
ORDER BY u.id, m.metrica"

copy_csv "contas_financeiras_por_natureza_subtipo.csv" "
SELECT usuario_id, natureza, subtipo, count(*) AS quantidade, sum(saldo) AS soma_saldo
FROM carteiras GROUP BY usuario_id, natureza, subtipo ORDER BY usuario_id, natureza, subtipo"

copy_csv "saldo_vs_ledger_por_conta.csv" "
SELECT cf.usuario_id, cf.id AS conta_financeira_id, cf.nome, cf.natureza, cf.subtipo,
       cf.saldo AS saldo_materializado,
       COALESCE((SELECT sum(mc.valor_assinado) FROM movimentos_carteira mc
                 WHERE mc.carteira_id = cf.id), 0) AS saldo_ledger
FROM carteiras cf ORDER BY cf.usuario_id, cf.id"

copy_csv "saldos_preexistentes.csv" "
SELECT cf.id AS conta_financeira_id, cf.saldo AS saldo_materializado,
       COALESCE((SELECT sum(mc.valor_assinado) FROM movimentos_carteira mc
                 WHERE mc.carteira_id=cf.id),0) AS saldo_ledger
FROM carteiras cf ORDER BY cf.id"

copy_csv "cartoes.csv" "
SELECT c.usuario_id, c.id AS cartao_id, c.nome, c.tipo, c.limite_total,
       c.dia_fechamento, c.dia_vencimento, c.ativo, c.conta_financeira_id,
       cf.nome AS conta_financeira_nome, cf.natureza, cf.subtipo, cf.moeda,
       cf.saldo AS saldo_passivo, COALESCE(c.valor_gasto,0) AS valor_gasto,
       COALESCE((SELECT sum(fl.valor) FROM faturas_cartao fc
                 JOIN fatura_lancamentos fl ON fl.fatura_id = fc.id
                 WHERE fc.conta_id = c.id AND fc.status <> 'PAGA'), 0) AS faturas_nao_pagas
FROM contas c LEFT JOIN carteiras cf ON cf.id = c.conta_financeira_id
WHERE c.tipo = 'CREDITO' ORDER BY c.usuario_id, c.id"

copy_csv "contas_legadas_nao_credito.csv" "
SELECT c.usuario_id, c.id AS conta_id, c.nome, c.banco, c.tipo,
       COALESCE(c.saldo_atual,0) AS saldo_atual, COALESCE(c.valor_gasto,0) AS valor_gasto,
       (SELECT count(*) FROM transacoes t WHERE t.conta_id = c.id) AS transacoes,
       (SELECT count(DISTINCT t.carteira_id) FROM transacoes t
        WHERE t.conta_id = c.id AND t.carteira_id IS NOT NULL) AS carteiras_distintas
FROM contas c WHERE c.tipo <> 'CREDITO' ORDER BY c.usuario_id, c.id"

copy_csv "transacoes_em_contas_nao_credito.csv" "
SELECT t.usuario_id, t.id AS transacao_id, t.conta_id, t.carteira_id, t.tipo,
       t.valor_total, t.data, t.ativa, t.estado_conciliacao
FROM transacoes t JOIN contas c ON c.id = t.conta_id
WHERE c.tipo <> 'CREDITO' ORDER BY t.usuario_id, t.id"

copy_csv "mapeamento_previsto.csv" "
WITH legado AS (
  SELECT c.*, CASE c.tipo WHEN 'DEBITO' THEN 'PAGAMENTO'
                         WHEN 'DINHEIRO' THEN 'DINHEIRO'
                         WHEN 'POUPANCA' THEN 'POUPANCA' END AS subtipo_destino
  FROM contas c WHERE c.tipo <> 'CREDITO'
), refs AS (
  SELECT l.id, count(DISTINCT t.carteira_id) AS qtd, min(t.carteira_id) AS destino
  FROM legado l LEFT JOIN transacoes t ON t.conta_id=l.id AND t.carteira_id IS NOT NULL GROUP BY l.id
), nomes AS (
  SELECT l.id, count(cf.id) AS qtd, min(cf.id) AS destino
  FROM legado l LEFT JOIN carteiras cf ON cf.usuario_id=l.usuario_id
   AND cf.subtipo=l.subtipo_destino
   AND lower(btrim(cf.nome))=lower(btrim(l.nome))
   AND COALESCE(lower(btrim(cf.banco)),'')=COALESCE(lower(btrim(l.banco)),'')
  GROUP BY l.id
)
SELECT l.id AS conta_legada_id, l.usuario_id, l.nome, l.banco, l.tipo AS tipo_legado,
       l.subtipo_destino,
       CASE WHEN r.qtd=1 THEN r.destino WHEN n.qtd=1 THEN n.destino END AS carteira_destino_id,
       CASE WHEN r.qtd=1 THEN 'TRANSACAO' WHEN n.qtd=1 THEN 'REUSO_NOME' ELSE 'CRIADA' END AS estrategia
FROM legado l JOIN refs r ON r.id=l.id JOIN nomes n ON n.id=l.id
ORDER BY l.id"

copy_csv "campos_canonicos_nulos.csv" "
SELECT c.usuario_id, c.id AS cartao_id, c.nome,
       (c.conta_financeira_id IS NULL) AS sem_conta_financeira,
       (c.limite_total IS NULL) AS sem_limite,
       (c.dia_fechamento IS NULL) AS sem_fechamento,
       (c.dia_vencimento IS NULL) AS sem_vencimento,
       (c.ativo IS NULL) AS sem_ativo
FROM contas c
WHERE c.tipo = 'CREDITO'
  AND (c.conta_financeira_id IS NULL OR c.limite_total IS NULL
       OR c.dia_fechamento IS NULL OR c.dia_vencimento IS NULL OR c.ativo IS NULL)
ORDER BY c.usuario_id, c.id"

copy_csv "contagens_tabelas.csv" "
SELECT 'usuarios' AS tabela, count(*) AS total FROM usuarios
UNION ALL SELECT 'contas', count(*) FROM contas
UNION ALL SELECT 'contas_credito', count(*) FROM contas WHERE tipo='CREDITO'
UNION ALL SELECT 'carteiras', count(*) FROM carteiras
UNION ALL SELECT 'transacoes', count(*) FROM transacoes
UNION ALL SELECT 'parcelas', count(*) FROM parcelas
UNION ALL SELECT 'faturas_cartao', count(*) FROM faturas_cartao
UNION ALL SELECT 'fatura_lancamentos', count(*) FROM fatura_lancamentos
UNION ALL SELECT 'movimentos_carteira', count(*) FROM movimentos_carteira
UNION ALL SELECT 'operacoes_financeiras', count(*) FROM operacoes_financeiras
UNION ALL SELECT 'metas', count(*) FROM metas
UNION ALL SELECT 'ativos', count(*) FROM ativos
UNION ALL SELECT 'movimentacoes_ativo', count(*) FROM movimentacoes_ativo
UNION ALL SELECT 'anexos', count(*) FROM anexos"

# -----------------------------------------------------------------------------
# Violações bloqueantes (plano PR-F2-19 §3) — qualquer linha => exit != 0
# -----------------------------------------------------------------------------
VIOLACOES="$OUTDIR/violacoes.csv"
copy_csv "violacoes.csv" "
SELECT 'CARTAO_SEM_CONTA_FINANCEIRA' AS violacao, c.id::text AS ref, c.nome AS detalhe
  FROM contas c WHERE c.tipo='CREDITO' AND c.conta_financeira_id IS NULL
UNION ALL
SELECT 'CARTAO_CONTA_FINANCEIRA_DUPLICADA', cf.id::text,
       'conta financeira referenciada por ' || count(*) || ' cartoes'
  FROM contas c JOIN carteiras cf ON cf.id=c.conta_financeira_id
  WHERE c.tipo='CREDITO' GROUP BY cf.id HAVING count(*) > 1
UNION ALL
SELECT 'CONTA_FINANCEIRA_CARTAO_SEM_CONFIG', cf.id::text, cf.nome
  FROM carteiras cf WHERE cf.subtipo='CARTAO'
    AND NOT EXISTS (SELECT 1 FROM contas c WHERE c.conta_financeira_id=cf.id)
UNION ALL
SELECT 'PAREAMENTO_USUARIO_DIVERGENTE', c.id::text,
       'cartao usuario ' || c.usuario_id || ' x conta financeira usuario ' || cf.usuario_id
  FROM contas c JOIN carteiras cf ON cf.id=c.conta_financeira_id
  WHERE cf.usuario_id <> c.usuario_id
UNION ALL
SELECT 'PAREAMENTO_NATUREZA_OU_MOEDA', c.id::text,
       'natureza=' || cf.natureza || ' subtipo=' || cf.subtipo || ' moeda=' || cf.moeda
  FROM contas c JOIN carteiras cf ON cf.id=c.conta_financeira_id
  WHERE c.tipo='CREDITO' AND (cf.natureza<>'PASSIVO' OR cf.subtipo<>'CARTAO' OR cf.moeda<>'BRL')
UNION ALL
SELECT 'SALDO_MATERIALIZADO_DIFERENTE_LEDGER', cf.id::text,
       'saldo=' || cf.saldo || ' ledger=' || COALESCE((SELECT sum(mc.valor_assinado)
        FROM movimentos_carteira mc WHERE mc.carteira_id=cf.id),0)
  FROM carteiras cf
  WHERE cf.saldo <> COALESCE((SELECT sum(mc.valor_assinado)
        FROM movimentos_carteira mc WHERE mc.carteira_id=cf.id),0)
UNION ALL
SELECT 'PASSIVO_DIFERENTE_VALOR_GASTO', c.id::text,
       'passivo=' || cf.saldo || ' valor_gasto=' || COALESCE(c.valor_gasto,0)
  FROM contas c JOIN carteiras cf ON cf.id=c.conta_financeira_id
  WHERE c.tipo='CREDITO' AND cf.saldo <> COALESCE(c.valor_gasto,0)
UNION ALL
SELECT 'VALOR_GASTO_DIFERENTE_FATURAS', c.id::text,
       'valor_gasto=' || COALESCE(c.valor_gasto,0) || ' faturas=' ||
       COALESCE((SELECT sum(fl.valor) FROM faturas_cartao fc
                 JOIN fatura_lancamentos fl ON fl.fatura_id=fc.id
                 WHERE fc.conta_id=c.id AND fc.status<>'PAGA'),0)
  FROM contas c
  WHERE c.tipo='CREDITO' AND COALESCE(c.valor_gasto,0) <>
        COALESCE((SELECT sum(fl.valor) FROM faturas_cartao fc
                  JOIN fatura_lancamentos fl ON fl.fatura_id=fc.id
                  WHERE fc.conta_id=c.id AND fc.status<>'PAGA'),0)
UNION ALL
SELECT 'SALDO_ATUAL_NAO_ZERO', c.id::text, 'saldo_atual=' || c.saldo_atual
  FROM contas c WHERE COALESCE(c.saldo_atual,0) <> 0
UNION ALL
SELECT 'CAMPO_CANONICO_NULO', c.id::text, c.nome
  FROM contas c
  WHERE c.tipo='CREDITO' AND (c.conta_financeira_id IS NULL OR c.limite_total IS NULL
        OR c.dia_fechamento IS NULL OR c.dia_vencimento IS NULL OR c.ativo IS NULL)
UNION ALL
SELECT 'TRANSACAO_INCOMPLETA_SEM_PENDENTE', t.id::text,
       'ativa sem carteira e sem PENDENTE_CONCILIACAO'
  FROM transacoes t
  WHERE t.ativa=true AND t.carteira_id IS NULL
    AND t.estado_conciliacao <> 'PENDENTE_CONCILIACAO'
    AND NOT (t.tipo='SAIDA' AND EXISTS
             (SELECT 1 FROM contas c WHERE c.id=t.conta_id AND c.tipo='CREDITO'))
UNION ALL
SELECT 'MAPEAMENTO_AMBIGUO_TRANSACOES', c.id::text,
       count(DISTINCT t.carteira_id) || ' contas financeiras distintas nas transacoes'
  FROM contas c JOIN transacoes t ON t.conta_id=c.id AND t.carteira_id IS NOT NULL
  WHERE c.tipo <> 'CREDITO'
  GROUP BY c.id HAVING count(DISTINCT t.carteira_id) > 1
UNION ALL
SELECT 'MAPEAMENTO_AMBIGUO_CANDIDATAS', c.id::text,
       count(cf.id) || ' candidatas por nome/banco/subtipo'
  FROM contas c
  JOIN carteiras cf ON cf.usuario_id=c.usuario_id
   AND cf.subtipo = CASE c.tipo WHEN 'DEBITO' THEN 'PAGAMENTO'
                                 WHEN 'DINHEIRO' THEN 'DINHEIRO'
                                 WHEN 'POUPANCA' THEN 'POUPANCA' END
   AND lower(btrim(cf.nome)) = lower(btrim(c.nome))
   AND COALESCE(lower(btrim(cf.banco)),'') = COALESCE(lower(btrim(c.banco)),'')
  WHERE c.tipo <> 'CREDITO'
    AND NOT EXISTS (SELECT 1 FROM transacoes t
                    WHERE t.conta_id=c.id AND t.carteira_id IS NOT NULL)
  GROUP BY c.id HAVING count(cf.id) > 1
UNION ALL
SELECT 'TRANSACAO_OWNERSHIP_DIVERGENTE', t.id::text, 'usuario da referencia diverge da transacao'
  FROM transacoes t
  LEFT JOIN carteiras cf ON cf.id=t.carteira_id
  LEFT JOIN contas c ON c.id=t.conta_id
  WHERE (t.carteira_id IS NOT NULL AND cf.usuario_id<>t.usuario_id)
     OR (t.conta_id IS NOT NULL AND c.usuario_id<>t.usuario_id)
UNION ALL
SELECT 'FATURA_OWNERSHIP_DIVERGENTE', f.id::text, 'usuario do cartao diverge da fatura'
  FROM faturas_cartao f JOIN contas c ON c.id=f.conta_id
  WHERE f.usuario_id<>c.usuario_id
UNION ALL
SELECT 'LEDGER_OWNERSHIP_DIVERGENTE', mc.id::text, 'usuario da conta/operacao diverge do movimento'
  FROM movimentos_carteira mc JOIN carteiras cf ON cf.id=mc.carteira_id
  LEFT JOIN operacoes_financeiras op ON op.id=mc.operacao_id
  WHERE mc.usuario_id<>cf.usuario_id OR (mc.operacao_id IS NOT NULL AND op.usuario_id<>mc.usuario_id)"

TOTAL_VIOLACOES=$(( $(wc -l < "$VIOLACOES") - 1 ))
PG_VERSION="$("${PSQL[@]}" -Atc 'show server_version')"
COMMIT="$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || echo desconhecido)"
{
  echo "{"
  echo "  \"artefato\": \"pre-v41\","
  echo "  \"gerado_em\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
  echo "  \"data_referencia\": \"$(date +%F)\","
  echo "  \"postgres\": \"$PG_VERSION\","
  echo "  \"commit\": \"$COMMIT\","
  echo "  \"violacoes\": $TOTAL_VIOLACOES"
  echo "}"
} > "$OUTDIR/resumo.json"
(cd "$OUTDIR" && shasum -a 256 ./*.csv resumo.json > checksums.sha256)

echo "Artefato pre-v41 em $OUTDIR"
if [ "$TOTAL_VIOLACOES" -gt 0 ]; then
  echo "PREFLIGHT REPROVADO: $TOTAL_VIOLACOES violacao(oes) — ver $VIOLACOES" >&2
  exit 1
fi
echo "PREFLIGHT OK: zero violacoes"
