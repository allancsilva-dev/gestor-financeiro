#!/bin/bash
# Gestor Financeiro — Backup do banco PostgreSQL
# Uso: ./scripts/backup-db.sh [DATABASE_URL_OPCIONAL]
#
# Requer pg_dump instalado (brew install libpq || apt install postgresql-client)
# Usa DATABASE_URL do ambiente ou argumento.
# Criptografia:
# - BACKUP_GPG_RECIPIENT=email@dominio: criptografia assimetrica
# - BACKUP_ENCRYPTION_PASSPHRASE=...: criptografia simetrica AES256
# - ALLOW_UNENCRYPTED_BACKUP=true: permite gerar .sql.gz sem criptografia

set -euo pipefail

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="backups"
BACKUP_FILE="${BACKUP_DIR}/gestor_financeiro_${TIMESTAMP}.sql"

DB_URL="${1:-${DATABASE_URL:-}}"
GPG_RECIPIENT="${BACKUP_GPG_RECIPIENT:-}"
GPG_PASSPHRASE="${BACKUP_ENCRYPTION_PASSPHRASE:-}"
ALLOW_UNENCRYPTED="${ALLOW_UNENCRYPTED_BACKUP:-false}"

if [ -z "$DB_URL" ]; then
  echo "Erro: DATABASE_URL nao definida. Passe como argumento ou defina env var."
  echo "Uso: $0 postgresql://user:pass@host:port/dbname"
  exit 1
fi

if [ -z "$GPG_RECIPIENT" ] && [ -z "$GPG_PASSPHRASE" ] && [ "$ALLOW_UNENCRYPTED" != "true" ]; then
  echo "Erro: backup sem criptografia bloqueado."
  echo "Defina BACKUP_GPG_RECIPIENT ou BACKUP_ENCRYPTION_PASSPHRASE."
  echo "Para ambiente local descartavel: ALLOW_UNENCRYPTED_BACKUP=true."
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

FINAL_FILE="$COMPRESSED"

if [ -n "$GPG_RECIPIENT" ]; then
  FINAL_FILE="${COMPRESSED}.gpg"
  gpg --batch --yes --encrypt --recipient "$GPG_RECIPIENT" --output "$FINAL_FILE" "$COMPRESSED"
  rm -f "$COMPRESSED"
elif [ -n "$GPG_PASSPHRASE" ]; then
  FINAL_FILE="${COMPRESSED}.gpg"
  gpg --batch --yes --pinentry-mode loopback --symmetric --cipher-algo AES256 --passphrase "$GPG_PASSPHRASE" --output "$FINAL_FILE" "$COMPRESSED"
  rm -f "$COMPRESSED"
fi

echo "Backup concluido: ${FINAL_FILE}"
echo "Tamanho: $(du -h "$FINAL_FILE" | cut -f1)"

# Manter apenas ultimos 7 backups
ls -t backups/gestor_financeiro_*.sql.gz backups/gestor_financeiro_*.sql.gz.gpg 2>/dev/null | tail -n +8 | xargs rm -f 2>/dev/null || true
echo "Backups antigos removidos (mantidos ultimos 7)."
