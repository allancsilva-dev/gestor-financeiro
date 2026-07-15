#!/bin/bash
# Gestor Financeiro — Restore drill automatizado em PostgreSQL limpo (ADR-0006)
# Uso: ./scripts/restore-drill-db.sh <ARQUIVO_BACKUP> [DRILL_DATABASE_URL]
#
# Sem DRILL_DATABASE_URL: sobe um postgres:16-alpine efêmero via docker e derruba no fim.
# Valida: checksums do manifesto (via restore-db.sh), migrations Flyway, contagens de
# tabelas-chave e presença dos anexos extraídos do bundle.

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Uso: $0 <gf_backup_*.tar.gpg|backup.sql.gz> [DRILL_DATABASE_URL]"
  exit 1
fi

BACKUP_FILE="$1"
DRILL_DB_URL="${2:-${RESTORE_DRILL_DATABASE_URL:-}}"
CONTAINER=""
UPLOADS_TMP="$(mktemp -d /tmp/gf_drill_uploads_XXXXXX)"

cleanup() {
  [ -n "$CONTAINER" ] && docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$UPLOADS_TMP"
}
trap cleanup EXIT

if [ -z "$DRILL_DB_URL" ]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "Erro: informe DRILL_DATABASE_URL ou tenha docker disponível para o banco efêmero." >&2
    exit 1
  fi
  CONTAINER="gf-restore-drill-$$"
  docker run -d --name "$CONTAINER" -e POSTGRES_DB=gf_drill -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD=postgres -p 127.0.0.1::5432 postgres:16-alpine >/dev/null
  for _ in $(seq 1 40); do
    docker exec "$CONTAINER" pg_isready -U postgres -d gf_drill >/dev/null 2>&1 && break
    sleep 1
  done
  PORT="$(docker port "$CONTAINER" 5432/tcp | sed 's/.*://')"
  DRILL_DB_URL="postgresql://postgres:postgres@127.0.0.1:${PORT}/gf_drill"
  echo "PostgreSQL efêmero: $DRILL_DB_URL"
fi

if [[ "$DRILL_DB_URL" =~ prod|production|gestor_financeiro$ ]]; then
  echo "Erro: URL parece ambiente real. Use banco descartavel para restore drill." >&2
  exit 1
fi

echo "Restore drill iniciado: $(date)"
RESTORE_ASSUME_YES=true RESTORE_UPLOADS_DIR="$UPLOADS_TMP" \
  "$(dirname "$0")/restore-db.sh" "$BACKUP_FILE" "$DRILL_DB_URL"

# ── Validações ──
USUARIOS=$(psql "$DRILL_DB_URL" -v ON_ERROR_STOP=1 -tAc "select count(*) from usuarios;")
MIGRATIONS=$(psql "$DRILL_DB_URL" -v ON_ERROR_STOP=1 -tAc "select count(*) from flyway_schema_history where success = true;")
TRANSACOES=$(psql "$DRILL_DB_URL" -v ON_ERROR_STOP=1 -tAc "select count(*) from transacoes;")
ANEXOS_DB=$(psql "$DRILL_DB_URL" -v ON_ERROR_STOP=1 -tAc "select count(*) from anexos;")
ANEXOS_ARQUIVOS=$(find "$UPLOADS_TMP" -type f | wc -l | tr -d ' ')

echo "usuarios=$USUARIOS migrations=$MIGRATIONS transacoes=$TRANSACOES anexos_db=$ANEXOS_DB anexos_arquivos=$ANEXOS_ARQUIVOS"

if [ "$MIGRATIONS" -lt 1 ]; then
  echo "Erro: flyway_schema_history vazia — restore inválido." >&2
  exit 1
fi
if [ "$ANEXOS_DB" -gt 0 ] && [ "$ANEXOS_ARQUIVOS" -lt "$ANEXOS_DB" ]; then
  echo "Erro: banco referencia $ANEXOS_DB anexo(s) mas o bundle trouxe $ANEXOS_ARQUIVOS arquivo(s)." >&2
  exit 1
fi

echo "Restore drill concluido com sucesso: $(date)"
