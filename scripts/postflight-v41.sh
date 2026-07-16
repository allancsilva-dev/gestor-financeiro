#!/bin/bash
# Gestor Financeiro — PR-F2-19: postflight do contract V41 (somente leitura)
#
# Gera o artefato post-v41 no mesmo formato do preflight para comparação das
# 9 métricas e das invariantes canônicas. Exit != 0 se alguma invariante
# canônica estiver violada ou se o schema legado ainda existir.
#
# Uso:
#   DATABASE_URL='postgresql://user:pass@host:5432/db' scripts/postflight-v41.sh PRE_DIR POST_DIR
#
# Nunca escreve no banco. Artefatos contêm dados financeiros: manter fora do Git.

set -euo pipefail

DATABASE_URL="${DATABASE_URL:?defina DATABASE_URL (connstring psql)}"
if [ "$#" -ne 2 ]; then
  echo "Uso: $0 DIRETORIO_PRE_V41 DIRETORIO_POST_V41" >&2
  exit 2
fi
PREDIR="$1"
OUTDIR="$2"
test -f "$PREDIR/metricas.csv" -a -f "$PREDIR/contagens_tabelas.csv" \
  -a -f "$PREDIR/saldos_preexistentes.csv" -a -f "$PREDIR/mapeamento_previsto.csv" || {
  echo "Diretorio de preflight incompleto: $PREDIR" >&2; exit 2;
}
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
case "$(cd "$(dirname "$OUTDIR")" && pwd -P)/$(basename "$OUTDIR")" in
  "$REPO_ROOT"|"$REPO_ROOT"/*) echo "Diretorio de evidencia deve ficar fora do repositorio" >&2; exit 2 ;;
esac
mkdir -p "$OUTDIR"
DATA_REFERENCIA="$(jq -r '.data_referencia' "$PREDIR/resumo.json")"
if [ "$DATA_REFERENCIA" != "$(date +%F)" ]; then
  echo "Postflight deve usar a mesma data de referencia do preflight ($DATA_REFERENCIA)" >&2
  exit 2
fi

PSQL=(psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -X -q)

copy_csv() { # $1=arquivo $2=query
  "${PSQL[@]}" -c "\\copy ($2) TO '$OUTDIR/$1' WITH (FORMAT csv, HEADER true)"
}

# As 9 métricas oficiais na semântica canônica pós-V41 (cartão = conta
# referenciada; colunas tipo/saldo_atual/valor_gasto não existem mais)
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
         AND NOT (t.tipo='SAIDA' AND t.conta_id IS NOT NULL)
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
             AND NOT (t.tipo='SAIDA' AND t.conta_id IS NOT NULL)
             AND pa.data_vencimento BETWEEN p.inicio_obr AND p.fim_mes))),
  ('INVESTIDO', (SELECT COALESCE(sum(a.quantidade*a.valor_atual),0) FROM ativos a
     WHERE a.usuario_id=u.id AND a.valor_atual IS NOT NULL AND a.cotacao_em IS NOT NULL AND a.quantidade>0)),
  ('DIVIDAS', (SELECT COALESCE(sum(GREATEST(c.saldo,0)),0) FROM carteiras c
     WHERE c.usuario_id=u.id AND c.natureza='PASSIVO')),
  ('RESULTADO_MENSAL', (SELECT COALESCE(sum(CASE WHEN t.tipo='ENTRADA' THEN t.valor_total ELSE -t.valor_total END),0)
     FROM transacoes t
     WHERE t.usuario_id=u.id AND t.ativa=true AND t.estado_conciliacao='CONCILIADA'
       AND t.data BETWEEN p.inicio_mes AND p.fim_mes
       AND (t.tipo='ENTRADA' OR t.conta_id IS NULL))
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
SELECT c.usuario_id, c.id AS cartao_id, c.nome, c.limite_total,
       c.dia_fechamento, c.dia_vencimento, c.ativo, c.conta_financeira_id,
       cf.nome AS conta_financeira_nome, cf.natureza, cf.subtipo, cf.moeda,
       cf.saldo AS saldo_passivo,
       COALESCE((SELECT sum(fl.valor) FROM faturas_cartao fc
                 JOIN fatura_lancamentos fl ON fl.fatura_id = fc.id
                 WHERE fc.conta_id = c.id AND fc.status <> 'PAGA'), 0) AS faturas_nao_pagas
FROM contas c JOIN carteiras cf ON cf.id = c.conta_financeira_id
ORDER BY c.usuario_id, c.id"

copy_csv "contagens_tabelas.csv" "
SELECT 'usuarios' AS tabela, count(*) AS total FROM usuarios
UNION ALL SELECT 'contas', count(*) FROM contas
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
# Violações canônicas pós-V41 — qualquer linha => exit != 0
# -----------------------------------------------------------------------------
VIOLACOES="$OUTDIR/violacoes.csv"
copy_csv "violacoes.csv" "
SELECT 'COLUNA_LEGADA_AINDA_EXISTE' AS violacao, table_name || '.' || column_name AS ref, '' AS detalhe
  FROM information_schema.columns
  WHERE (table_name='carteiras' AND column_name='tipo')
     OR (table_name='contas' AND column_name IN ('tipo','saldo_atual','valor_gasto'))
UNION ALL
SELECT 'V41_NAO_APLICADA_OU_DUPLICADA', version, 'aplicacoes=' || count(*)::text
  FROM flyway_schema_history WHERE version='41' AND success=true
  GROUP BY version HAVING count(*) <> 1
UNION ALL
SELECT 'CARTAO_SEM_CONTA_FINANCEIRA', c.id::text, c.nome
  FROM contas c WHERE c.conta_financeira_id IS NULL
UNION ALL
SELECT 'CONTA_FINANCEIRA_CARTAO_SEM_CONFIG', cf.id::text, cf.nome
  FROM carteiras cf WHERE cf.subtipo='CARTAO'
    AND NOT EXISTS (SELECT 1 FROM contas c WHERE c.conta_financeira_id=cf.id)
UNION ALL
SELECT 'PAREAMENTO_INCOMPATIVEL', c.id::text,
       'usuario/natureza/subtipo/moeda divergente'
  FROM contas c JOIN carteiras cf ON cf.id=c.conta_financeira_id
  WHERE cf.usuario_id<>c.usuario_id OR cf.natureza<>'PASSIVO'
     OR cf.subtipo<>'CARTAO' OR cf.moeda<>'BRL'
UNION ALL
SELECT 'SALDO_MATERIALIZADO_DIFERENTE_LEDGER', cf.id::text,
       'saldo=' || cf.saldo || ' ledger=' || COALESCE((SELECT sum(mc.valor_assinado)
        FROM movimentos_carteira mc WHERE mc.carteira_id=cf.id),0)
  FROM carteiras cf
  WHERE cf.saldo <> COALESCE((SELECT sum(mc.valor_assinado)
        FROM movimentos_carteira mc WHERE mc.carteira_id=cf.id),0)
UNION ALL
SELECT 'PASSIVO_DIFERENTE_FATURAS', c.id::text,
       'passivo=' || cf.saldo || ' faturas=' ||
       COALESCE((SELECT sum(fl.valor) FROM faturas_cartao fc
                 JOIN fatura_lancamentos fl ON fl.fatura_id=fc.id
                 WHERE fc.conta_id=c.id AND fc.status<>'PAGA'),0)
  FROM contas c JOIN carteiras cf ON cf.id=c.conta_financeira_id
  WHERE cf.saldo <> COALESCE((SELECT sum(fl.valor) FROM faturas_cartao fc
                 JOIN fatura_lancamentos fl ON fl.fatura_id=fc.id
                 WHERE fc.conta_id=c.id AND fc.status<>'PAGA'),0)
UNION ALL
SELECT 'TRANSACAO_INCOMPLETA_SEM_PENDENTE', t.id::text,
       'ativa sem carteira e sem PENDENTE_CONCILIACAO'
  FROM transacoes t
  WHERE t.ativa=true AND t.carteira_id IS NULL
    AND t.estado_conciliacao <> 'PENDENTE_CONCILIACAO'
    AND NOT (t.tipo='SAIDA' AND t.conta_id IS NOT NULL)
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
COMPARACAO_ERROS=0
if ! diff -u "$PREDIR/metricas.csv" "$OUTDIR/metricas.csv" > "$OUTDIR/diff-metricas.txt"; then
  echo "Metricas divergiram do preflight" >&2
  COMPARACAO_ERROS=$((COMPARACAO_ERROS + 1))
fi

# Tabelas invariantes devem manter contagem exata. Contas/carteiras sao
# comparadas separadamente conforme o mapeamento previsto.
awk -F, 'NR>1 && $1!="contas" && $1!="contas_credito" && $1!="carteiras" {print}' \
  "$PREDIR/contagens_tabelas.csv" | sort > "$OUTDIR/contagens-pre-invariantes.txt"
awk -F, 'NR>1 && $1!="contas" && $1!="carteiras" {print}' \
  "$OUTDIR/contagens_tabelas.csv" | sort > "$OUTDIR/contagens-post-invariantes.txt"
if ! diff -u "$OUTDIR/contagens-pre-invariantes.txt" "$OUTDIR/contagens-post-invariantes.txt" \
     > "$OUTDIR/diff-contagens.txt"; then
  echo "Contagens invariantes divergiram" >&2
  COMPARACAO_ERROS=$((COMPARACAO_ERROS + 1))
fi

PRE_CARTOES="$(awk -F, '$1=="contas_credito" {print $2}' "$PREDIR/contagens_tabelas.csv")"
PRE_CARTEIRAS="$(awk -F, '$1=="carteiras" {print $2}' "$PREDIR/contagens_tabelas.csv")"
POST_CARTOES="$(awk -F, '$1=="contas" {print $2}' "$OUTDIR/contagens_tabelas.csv")"
POST_CARTEIRAS="$(awk -F, '$1=="carteiras" {print $2}' "$OUTDIR/contagens_tabelas.csv")"
CRIADAS="$(awk -F, 'NR>1 && $NF=="CRIADA" {n++} END {print n+0}' "$PREDIR/mapeamento_previsto.csv")"
if [ "$POST_CARTOES" -ne "$PRE_CARTOES" ] || [ "$POST_CARTEIRAS" -ne $((PRE_CARTEIRAS + CRIADAS)) ]; then
  echo "Resultado do mapeamento de contas divergiu do previsto" >&2
  COMPARACAO_ERROS=$((COMPARACAO_ERROS + 1))
fi

# Toda conta financeira preexistente deve conservar saldo materializado e
# saldo de ledger; linhas novas sao ignoradas nesta comparacao.
tail -n +2 "$PREDIR/saldos_preexistentes.csv" > "$OUTDIR/saldos-pre.txt"
awk -F, 'NR==FNR {ids[$1]=1; next} FNR>1 && ($1 in ids) {print}' \
  "$OUTDIR/saldos-pre.txt" "$OUTDIR/saldos_preexistentes.csv" > "$OUTDIR/saldos-post-preexistentes.txt"
if ! diff -u "$OUTDIR/saldos-pre.txt" "$OUTDIR/saldos-post-preexistentes.txt" > "$OUTDIR/diff-saldos.txt"; then
  echo "Saldos preexistentes divergiram" >&2
  COMPARACAO_ERROS=$((COMPARACAO_ERROS + 1))
fi

PG_VERSION="$("${PSQL[@]}" -Atc 'show server_version')"
COMMIT="$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || echo desconhecido)"
{
  echo "{"
  echo "  \"artefato\": \"post-v41\","
  echo "  \"gerado_em\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
  echo "  \"postgres\": \"$PG_VERSION\","
  echo "  \"commit\": \"$COMMIT\","
  echo "  \"violacoes\": $TOTAL_VIOLACOES,"
  echo "  \"divergencias_pre_pos\": $COMPARACAO_ERROS"
  echo "}"
} > "$OUTDIR/resumo.json"
(cd "$OUTDIR" && shasum -a 256 ./*.csv resumo.json > checksums.sha256)

echo "Artefato post-v41 em $OUTDIR"
if [ "$TOTAL_VIOLACOES" -gt 0 ] || [ "$COMPARACAO_ERROS" -gt 0 ]; then
  echo "POSTFLIGHT REPROVADO: $TOTAL_VIOLACOES violacao(oes) — ver $VIOLACOES" >&2
  exit 1
fi
echo "POSTFLIGHT OK: zero violacoes"
