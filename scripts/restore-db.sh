#!/bin/bash
# Gestor Financeiro — Restore do banco PostgreSQL
# Uso: ./scripts/restore-db.sh <ARQUIVO_BACKUP> [DATABASE_URL]
#
# ATENCAO: Sobrescreve dados existentes!

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Uso: $0 <backup.sql.gz> [DATABASE_URL]"
  echo "Exemplo: $0 backups/gestor_financeiro_20260101_120000.sql.gz postgresql://user:pass@host:5432/db"
  exit 1
fi

BACKUP_FILE="$1"
DB_URL="${2:-${DATABASE_URL:-}}"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "Erro: arquivo nao encontrado: $BACKUP_FILE"
  exit 1
fi

if [ -z "$DB_URL" ]; then
  echo "Erro: DATABASE_URL nao definida."
  exit 1
fi

echo "ATENCAO: Isso sobrescreve todos os dados em $(echo "$DB_URL" | sed 's/\/\/.*@/\/\/***@/')."
read -rp "Digite 'CONFIRMO' para continuar: " CONFIRM

if [ "$CONFIRM" != "CONFIRMO" ]; then
  echo "Restore cancelado."
  exit 0
fi

echo "Restore iniciado: $(date)"

gunzip -c "$BACKUP_FILE" | psql "$DB_URL"

echo "Restore concluido: $(date)"
echo "Execute Flyway validate: ./mvnw flyway:validate -f backend/pom.xml"
