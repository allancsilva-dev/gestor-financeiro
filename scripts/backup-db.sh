#!/bin/bash
# Gestor Financeiro — Backup do banco PostgreSQL
# Uso: ./scripts/backup-db.sh [DATABASE_URL_OPCIONAL]
#
# Requer pg_dump instalado (brew install libpq || apt install postgresql-client)
# Usa DATABASE_URL do ambiente ou argumento.

set -euo pipefail

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="backups"
BACKUP_FILE="${BACKUP_DIR}/gestor_financeiro_${TIMESTAMP}.sql"

DB_URL="${1:-${DATABASE_URL:-}}"

if [ -z "$DB_URL" ]; then
  echo "Erro: DATABASE_URL nao definida. Passe como argumento ou defina env var."
  echo "Uso: $0 postgresql://user:pass@host:port/dbname"
  exit 1
fi

mkdir -p "$BACKUP_DIR"

echo "Backup iniciado: $(date)"
echo "Destino: $BACKUP_FILE"

pg_dump "$DB_URL" \
  --no-owner \
  --no-acl \
  --format=plain \
  > "$BACKUP_FILE"

COMPRESSED="${BACKUP_FILE}.gz"
gzip -f "$BACKUP_FILE"

echo "Backup concluido: ${COMPRESSED}"
echo "Tamanho: $(du -h "$COMPRESSED" | cut -f1)"

# Manter apenas ultimos 7 backups
ls -t backups/gestor_financeiro_*.sql.gz 2>/dev/null | tail -n +8 | xargs rm -f 2>/dev/null || true
echo "Backups antigos removidos (mantidos ultimos 7)."
