#!/bin/bash
# Gestor Financeiro — Restore do banco PostgreSQL (ADR-0006)
# Uso: ./scripts/restore-db.sh <ARQUIVO_BACKUP> [DATABASE_URL]
#
# ATENCAO: Sobrescreve dados existentes!
# Formatos aceitos:
#   - gf_backup_*.tar[.gpg]  bundle canônico: manifest.txt + db.dump (-Fc) + uploads.tar.gz
#   - *.sql.gz[.gpg]         legado (dump plain)
# GPG do bundle é assimétrico: o restore exige a CHAVE PRIVADA (off-host, custódia do
# responsável) importada no keyring local.
# Env obrigatória: RESTORE_TARGET_MARKER deve coincidir com a configuração server-side
# `nexos.restore_target`. Por padrão o banco também precisa estar vazio.
# Env opcionais: RESTORE_ASSUME_YES=true; RESTORE_UPLOADS_DIR=<dir>;
# RESTORE_ALLOW_NONEMPTY=true (recuperação excepcional, além do marcador).

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Uso: $0 <gf_backup_*.tar.gpg|backup.sql.gz> [DATABASE_URL]"
  exit 1
fi

BACKUP_FILE="$1"
DB_URL="${2:-${DATABASE_URL:-}}"
GPG_PASSPHRASE="${BACKUP_ENCRYPTION_PASSPHRASE:-}"
ASSUME_YES="${RESTORE_ASSUME_YES:-false}"
RESTORE_UPLOADS_DIR="${RESTORE_UPLOADS_DIR:-}"
EXPECTED_TARGET_MARKER="${RESTORE_TARGET_MARKER:-}"
ALLOW_NONEMPTY="${RESTORE_ALLOW_NONEMPTY:-false}"
TMP_DIR=""

cleanup() {
  [ -n "$TMP_DIR" ] && rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if [ ! -f "$BACKUP_FILE" ]; then
  echo "Erro: arquivo nao encontrado: $BACKUP_FILE" >&2
  exit 1
fi
if [ -z "$DB_URL" ]; then
  echo "Erro: DATABASE_URL nao definida." >&2
  exit 1
fi
if [ -z "$EXPECTED_TARGET_MARKER" ]; then
  echo "Erro: RESTORE_TARGET_MARKER obrigatório; restore sem identidade server-side é proibido." >&2
  exit 1
fi

ACTUAL_TARGET_MARKER="$(psql "$DB_URL" -v ON_ERROR_STOP=1 -tAc \
  "select current_setting('nexos.restore_target', true)" | tr -d '[:space:]')"
if [ -z "$ACTUAL_TARGET_MARKER" ] || [ "$ACTUAL_TARGET_MARKER" != "$EXPECTED_TARGET_MARKER" ]; then
  echo "Erro: marcador do alvo ausente ou divergente; restore bloqueado." >&2
  exit 1
fi

USER_RELATIONS="$(psql "$DB_URL" -v ON_ERROR_STOP=1 -tAc \
  "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p','v','m','S')")"
if [ "$USER_RELATIONS" -gt 0 ] && [ "$ALLOW_NONEMPTY" != "true" ]; then
  echo "Erro: alvo contém $USER_RELATIONS relação(ões); defina RESTORE_ALLOW_NONEMPTY=true somente em recuperação aprovada." >&2
  exit 1
fi

TARGET_ID="$(psql "$DB_URL" -v ON_ERROR_STOP=1 -tAc \
  "select current_database() || '@' || coalesce(inet_server_addr()::text, 'local') || ':' || inet_server_port()")"
echo "ATENCAO: Isso sobrescreve todos os dados em $TARGET_ID."
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
TMP_DIR="$(mktemp -d /tmp/gestor_restore_XXXXXX)"

ARQUIVO="$BACKUP_FILE"

# 1) Decripta se .gpg (assimétrico para bundles; simétrico legado usa passphrase)
if [[ "$ARQUIVO" == *.gpg ]]; then
  DECRYPTED="$TMP_DIR/$(basename "${ARQUIVO%.gpg}")"
  if [ -n "$GPG_PASSPHRASE" ]; then
    gpg --batch --yes --pinentry-mode loopback --decrypt --passphrase "$GPG_PASSPHRASE" \
        --output "$DECRYPTED" "$ARQUIVO"
  else
    gpg --batch --yes --decrypt --output "$DECRYPTED" "$ARQUIVO"
  fi
  ARQUIVO="$DECRYPTED"
fi

# 2) Bundle canônico: valida checksums do manifesto e usa pg_restore
if [[ "$(basename "$ARQUIVO")" == gf_backup_*.tar ]]; then
  if tar -tf "$ARQUIVO" | grep -Eq '(^/|(^|/)\.\.(/|$))'; then
    echo "Erro: bundle contém caminho inseguro." >&2
    exit 1
  fi
  tar -xf "$ARQUIVO" -C "$TMP_DIR"
  (
    cd "$TMP_DIR"
    grep -E '^[0-9a-f]{64}  ' manifest.txt | sha256sum -c --quiet
  )
  echo "Checksums do manifesto validados."

  pg_restore --clean --if-exists --no-owner --no-acl -d "$DB_URL" "$TMP_DIR/db.dump"

  if [ -n "$RESTORE_UPLOADS_DIR" ] && [ -f "$TMP_DIR/uploads.tar.gz" ]; then
    if tar -tzf "$TMP_DIR/uploads.tar.gz" | grep -Eq '(^/|(^|/)\.\.(/|$))'; then
      echo "Erro: bundle de anexos contém caminho inseguro." >&2
      exit 1
    fi
    mkdir -p "$RESTORE_UPLOADS_DIR"
    tar -xzf "$TMP_DIR/uploads.tar.gz" -C "$RESTORE_UPLOADS_DIR"
    echo "Anexos restaurados em $RESTORE_UPLOADS_DIR"
  elif [ -f "$TMP_DIR/uploads.tar.gz" ]; then
    echo "AVISO: bundle contém uploads.tar.gz; defina RESTORE_UPLOADS_DIR para restaurar os anexos." >&2
  fi
else
  # 3) Legado plain SQL
  gunzip -c "$ARQUIVO" | psql "$DB_URL"
fi

echo "Restore concluido: $(date)"
echo "Valide as migrations: ./mvnw flyway:validate -f backend/pom.xml"
