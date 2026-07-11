#!/bin/bash
# Gestor Financeiro — Restore drill automatizado em banco descartavel
# Uso: ./scripts/restore-drill-db.sh <ARQUIVO_BACKUP> [DRILL_DATABASE_URL]

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Uso: $0 <backup.sql.gz|backup.sql.gz.gpg> [DRILL_DATABASE_URL]"
  exit 1
fi

BACKUP_FILE="$1"
DRILL_DB_URL="${2:-${RESTORE_DRILL_DATABASE_URL:-}}"

if [ -z "$DRILL_DB_URL" ]; then
  echo "Erro: informe DRILL_DATABASE_URL ou RESTORE_DRILL_DATABASE_URL."
  exit 1
fi

if [[ "$DRILL_DB_URL" =~ prod|production|gestor_financeiro$ ]]; then
  echo "Erro: URL parece ambiente real. Use banco descartavel para restore drill."
  exit 1
fi

echo "Restore drill iniciado: $(date)"
RESTORE_ASSUME_YES=true ./scripts/restore-db.sh "$BACKUP_FILE" "$DRILL_DB_URL"

psql "$DRILL_DB_URL" -v ON_ERROR_STOP=1 -c "select count(*) as usuarios from usuarios;" >/dev/null
psql "$DRILL_DB_URL" -v ON_ERROR_STOP=1 -c "select count(*) as migrations from flyway_schema_history where success = true;" >/dev/null

echo "Restore drill concluido com sucesso: $(date)"
