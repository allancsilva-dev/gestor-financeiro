#!/bin/bash
# Coordenador host-side: garante janela sem escrita e recuperação da API.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${BACKUP_COMPOSE_FILE:-docker-compose.production.yml}"
COMPOSE=(docker compose -f "$COMPOSE_FILE")
API_STOPPED=false

cd "$ROOT_DIR"

fail() { echo "Erro: $*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "Docker não encontrado no host."
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 não está disponível."
[ -n "${BACKUP_GPG_RECIPIENT:-}" ] || fail "BACKUP_GPG_RECIPIENT obrigatório."
[ -n "${RCLONE_REMOTE:-}" ] || fail "RCLONE_REMOTE obrigatório."
[ -f "${BACKUP_GPG_PUBLIC_KEY_HOST_FILE:-deploy/backup/backup-public.asc}" ] || \
  fail "chave pública de backup não encontrada."
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" config --services | grep -qx api || fail "serviço api ausente no compose."
"${COMPOSE[@]}" config --services | grep -qx postgres-backup || fail "serviço postgres-backup ausente no compose."

recover_api() {
  local container_id health
  "${COMPOSE[@]}" start api >/dev/null || return 1
  for _ in $(seq 1 "${BACKUP_API_HEALTH_RETRIES:-60}"); do
    container_id=$("${COMPOSE[@]}" ps -q api)
    if [ -n "$container_id" ]; then
      health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)
      [ "$health" = "healthy" ] && API_STOPPED=false && return 0
    fi
    sleep 2
  done
  return 1
}

api_container=$("${COMPOSE[@]}" ps -q api)
[ -n "$api_container" ] || fail "serviço api não está em execução."
api_health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$api_container" 2>/dev/null || true)
[ "$api_health" = "healthy" ] || fail "serviço api não está saudável (estado: ${api_health:-desconhecido})."

cleanup() {
  local status=$?
  if [ "$API_STOPPED" = true ] && ! recover_api; then
    echo "Erro: a API não recuperou o estado saudável." >&2
    status=1
  fi
  exit "$status"
}
trap cleanup EXIT

echo "Janela de manutenção: parando api"
"${COMPOSE[@]}" stop api
API_STOPPED=true

backup_status=0
"${COMPOSE[@]}" --profile backup run --rm postgres-backup || backup_status=$?

if ! recover_api; then
  fail "backup terminou, mas a API não recuperou o estado saudável."
fi

[ "$backup_status" -eq 0 ] || fail "backup one-shot falhou (status $backup_status)."
echo "Backup concluído e API saudável."
