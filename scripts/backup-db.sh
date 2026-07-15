#!/bin/bash
# Gestor Financeiro — Backup canônico (ADR-0006 / PROB-0081). One-shot: acionado por timer
# do host (systemd/cron) ou `docker compose --profile backup run --rm postgres-backup`.
#
# Bundle: db.dump (pg_dump -Fc) + uploads.tar.gz + manifest.txt (sha256) -> tar -> GPG
# assimétrico (somente chave PÚBLICA no servidor). Envio obrigatório via rclone; staging
# removido após upload confirmado. Retenção: 7 diários + 4 semanais, local e remota.
#
# Env:
#   DATABASE_URL ou PG* (PGHOST/PGDATABASE/PGUSER/PGPASSWORD)  conexão
#   BACKUP_GPG_RECIPIENT      obrigatório (fail-closed) — e-mail/ID da chave pública
#   BACKUP_GPG_PUBLIC_KEY_FILE opcional — arquivo .asc importado antes de criptografar
#   RCLONE_REMOTE             obrigatório (fail-closed), ex.: "b2:gf-backups/prod"
#   UPLOADS_DIR               dir de anexos a incluir (default /uploads se existir)
#   BACKUP_DIR                destino local (default ./backups)
#   API_CONTAINER             opcional — janela de manutenção: docker stop/start via trap
#   BACKUP_RETENTION_DAILY    default 7 | BACKUP_RETENTION_WEEKLY default 4
#   ALLOW_UNENCRYPTED_BACKUP=true / BACKUP_SKIP_REMOTE=true  somente dev local

set -euo pipefail

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="${BACKUP_DIR:-backups}"
UPLOADS_DIR="${UPLOADS_DIR:-/uploads}"
GPG_RECIPIENT="${BACKUP_GPG_RECIPIENT:-}"
GPG_PUBKEY_FILE="${BACKUP_GPG_PUBLIC_KEY_FILE:-}"
RCLONE_REMOTE="${RCLONE_REMOTE:-}"
API_CONTAINER="${API_CONTAINER:-}"
RETENTION_DAILY="${BACKUP_RETENTION_DAILY:-7}"
RETENTION_WEEKLY="${BACKUP_RETENTION_WEEKLY:-4}"
ALLOW_UNENCRYPTED="${ALLOW_UNENCRYPTED_BACKUP:-false}"
SKIP_REMOTE="${BACKUP_SKIP_REMOTE:-false}"
DB_URL="${1:-${DATABASE_URL:-}}"

# ── Fail-closed: produção não roda sem criptografia assimétrica nem sem remote ──
if [ -z "$GPG_RECIPIENT" ] && [ "$ALLOW_UNENCRYPTED" != "true" ]; then
  echo "Erro: BACKUP_GPG_RECIPIENT obrigatório (GPG assimétrico; chave privada fica off-host)." >&2
  echo "Somente dev local descartável pode usar ALLOW_UNENCRYPTED_BACKUP=true." >&2
  exit 1
fi
if [ -z "$RCLONE_REMOTE" ] && [ "$SKIP_REMOTE" != "true" ]; then
  echo "Erro: RCLONE_REMOTE obrigatório — backup no próprio host não protege contra perda do host." >&2
  echo "Somente dev local pode usar BACKUP_SKIP_REMOTE=true." >&2
  exit 1
fi
if [ -z "$DB_URL" ] && [ -z "${PGHOST:-}" ]; then
  echo "Erro: defina DATABASE_URL (ou as variáveis PG*)." >&2
  exit 1
fi

if [ -n "$GPG_PUBKEY_FILE" ] && [ -f "$GPG_PUBKEY_FILE" ]; then
  gpg --batch --import "$GPG_PUBKEY_FILE"
fi

mkdir -p "$BACKUP_DIR"
STAGING="$(mktemp -d "${BACKUP_DIR}/staging_XXXXXX")"

API_PARADA=false
cleanup() {
  # religa a API mesmo em falha; staging nunca sobrevive
  if [ "$API_PARADA" = "true" ]; then
    docker start "$API_CONTAINER" >/dev/null 2>&1 || echo "AVISO: falha ao religar ${API_CONTAINER}" >&2
  fi
  rm -rf "$STAGING"
}
trap cleanup EXIT

# ── Janela de manutenção curta: pausa escrita da API durante o dump ──
if [ -n "$API_CONTAINER" ] && command -v docker >/dev/null 2>&1; then
  echo "Janela de manutenção: parando ${API_CONTAINER}"
  docker stop "$API_CONTAINER" >/dev/null
  API_PARADA=true
fi

echo "Backup iniciado: $(date)"

if [ -n "$DB_URL" ]; then
  pg_dump "$DB_URL" --no-owner --no-acl -Fc -f "$STAGING/db.dump"
else
  pg_dump --no-owner --no-acl -Fc -f "$STAGING/db.dump"
fi

if [ -d "$UPLOADS_DIR" ]; then
  tar -czf "$STAGING/uploads.tar.gz" -C "$UPLOADS_DIR" .
else
  echo "AVISO: UPLOADS_DIR ($UPLOADS_DIR) inexistente — bundle sem anexos." >&2
fi

# ── Manifesto com checksums ──
(
  cd "$STAGING"
  {
    echo "gerado_em=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "banco=${PGDATABASE:-desconhecido}"
    sha256sum db.dump uploads.tar.gz 2>/dev/null || sha256sum db.dump
  } > manifest.txt
)

# religa a API antes de criptografar/enviar (dump e tar já são consistentes)
if [ "$API_PARADA" = "true" ]; then
  docker start "$API_CONTAINER" >/dev/null
  API_PARADA=false
  echo "Janela de manutenção encerrada: ${API_CONTAINER} religada"
fi

# semanal aos domingos: entra na retenção de 4 semanas
SUFIXO=""
[ "$(date +%u)" = "7" ] && SUFIXO="_weekly"
BUNDLE_NAME="gf_backup_${TIMESTAMP}${SUFIXO}.tar"

EXTRAS=""
[ -f "$STAGING/uploads.tar.gz" ] && EXTRAS="uploads.tar.gz"
tar -cf "$STAGING/$BUNDLE_NAME" -C "$STAGING" manifest.txt db.dump $EXTRAS

FINAL_FILE="$BACKUP_DIR/$BUNDLE_NAME"
if [ -n "$GPG_RECIPIENT" ]; then
  FINAL_FILE="$BACKUP_DIR/${BUNDLE_NAME}.gpg"
  gpg --batch --yes --trust-model always --encrypt --recipient "$GPG_RECIPIENT" \
      --output "$FINAL_FILE" "$STAGING/$BUNDLE_NAME"
else
  mv "$STAGING/$BUNDLE_NAME" "$FINAL_FILE"
fi

echo "Bundle: $FINAL_FILE ($(du -h "$FINAL_FILE" | cut -f1))"

# ── Envio off-host com verificação; staging some no trap ──
if [ "$SKIP_REMOTE" != "true" ]; then
  rclone copyto "$FINAL_FILE" "$RCLONE_REMOTE/$(basename "$FINAL_FILE")"
  TAM_LOCAL=$(wc -c < "$FINAL_FILE" | tr -d ' ')
  TAM_REMOTO=$(rclone lsl "$RCLONE_REMOTE/$(basename "$FINAL_FILE")" | awk '{print $1}')
  if [ "$TAM_LOCAL" != "$TAM_REMOTO" ]; then
    echo "Erro: tamanho remoto ($TAM_REMOTO) difere do local ($TAM_LOCAL) — upload inválido." >&2
    exit 1
  fi
  echo "Upload confirmado em $RCLONE_REMOTE"

  # retenção remota: diários > RETENTION_DAILY dias; semanais > RETENTION_WEEKLY semanas
  rclone delete "$RCLONE_REMOTE" --min-age "${RETENTION_DAILY}d" --exclude "*_weekly*" || true
  rclone delete "$RCLONE_REMOTE" --min-age "$((RETENTION_WEEKLY * 7 + 1))d" --include "*_weekly*" || true
fi

# ── Retenção local ──
ls -t "$BACKUP_DIR"/gf_backup_*[0-9].tar.gpg "$BACKUP_DIR"/gf_backup_*[0-9].tar 2>/dev/null \
  | tail -n +$((RETENTION_DAILY + 1)) | xargs rm -f 2>/dev/null || true
ls -t "$BACKUP_DIR"/gf_backup_*_weekly.tar.gpg "$BACKUP_DIR"/gf_backup_*_weekly.tar 2>/dev/null \
  | tail -n +$((RETENTION_WEEKLY + 1)) | xargs rm -f 2>/dev/null || true

echo "Backup concluído: $(date)"
