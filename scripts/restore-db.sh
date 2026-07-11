#!/bin/bash
# Gestor Financeiro — Restore do banco PostgreSQL
# Uso: ./scripts/restore-db.sh <ARQUIVO_BACKUP> [DATABASE_URL]
#
# ATENCAO: Sobrescreve dados existentes!
# Aceita .sql.gz e .sql.gz.gpg.
# Para restore nao interativo, defina RESTORE_ASSUME_YES=true.

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Uso: $0 <backup.sql.gz> [DATABASE_URL]"
  echo "Exemplo: $0 backups/gestor_financeiro_20260101_120000.sql.gz postgresql://user:pass@host:5432/db"
  exit 1
fi

BACKUP_FILE="$1"
DB_URL="${2:-${DATABASE_URL:-}}"
GPG_PASSPHRASE="${BACKUP_ENCRYPTION_PASSPHRASE:-}"
ASSUME_YES="${RESTORE_ASSUME_YES:-false}"
TMP_FILE=""

cleanup() {
  if [ -n "$TMP_FILE" ] && [ -f "$TMP_FILE" ]; then
    rm -f "$TMP_FILE"
  fi
}
trap cleanup EXIT

if [ ! -f "$BACKUP_FILE" ]; then
  echo "Erro: arquivo nao encontrado: $BACKUP_FILE"
  exit 1
fi

if [ -z "$DB_URL" ]; then
  echo "Erro: DATABASE_URL nao definida."
  exit 1
fi

echo "ATENCAO: Isso sobrescreve todos os dados em $(echo "$DB_URL" | sed 's/\/\/.*@/\/\/***@/')."
if [ "$ASSUME_YES" = "true" ]; then
  CONFIRM="CONFIRMO"
else
  read -rp "Digite 'CONFIRMO' para continuar: " CONFIRM
fi

if [ "$CONFIRM" != "CONFIRMO" ]; then
  echo "Restore cancelado."
  exit 0
fi

echo "Restore iniciado: $(date)"

RESTORE_FILE="$BACKUP_FILE"
if [[ "$BACKUP_FILE" == *.gpg ]]; then
  TMP_FILE="$(mktemp /tmp/gestor_restore_XXXXXX.sql.gz)"
  if [ -n "$GPG_PASSPHRASE" ]; then
    gpg --batch --yes --pinentry-mode loopback --decrypt --passphrase "$GPG_PASSPHRASE" --output "$TMP_FILE" "$BACKUP_FILE"
  else
    gpg --batch --yes --decrypt --output "$TMP_FILE" "$BACKUP_FILE"
  fi
  RESTORE_FILE="$TMP_FILE"
fi

gunzip -c "$RESTORE_FILE" | psql "$DB_URL"

echo "Restore concluido: $(date)"
echo "Execute Flyway validate: ./mvnw flyway:validate -f backend/pom.xml"
