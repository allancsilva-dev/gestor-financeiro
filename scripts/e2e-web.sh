#!/bin/bash
# Gestor Financeiro — ambiente E2E web dedicado (ADR-0002):
# PostgreSQL efêmero + backend (8081) + Vite (via Playwright webServer) + suíte Playwright.
# Não depende do compose de dev. Uso: ./scripts/e2e-web.sh [args extras do playwright]

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER_NAME="gf-postgres-e2e-$$"
DB_NAME="gestor_financeiro_e2e"
DB_USER="postgres"
DB_PASSWORD="postgres"
BACKEND_PID=""

cleanup() {
  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> Subindo PostgreSQL efêmero"
docker run -d --name "$CONTAINER_NAME" \
  -e POSTGRES_DB="$DB_NAME" \
  -e POSTGRES_USER="$DB_USER" \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -p 127.0.0.1::5432 \
  postgres:16-alpine >/dev/null

for _ in $(seq 1 40); do
  if docker exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null
HOST_PORT="$(docker port "$CONTAINER_NAME" 5432/tcp | sed 's/.*://')"

echo "==> Subindo backend (porta 8081)"
cd "$ROOT_DIR/backend"
SPRING_PROFILES_ACTIVE=dev \
DATABASE_URL="jdbc:postgresql://127.0.0.1:${HOST_PORT}/${DB_NAME}" \
DB_USERNAME="$DB_USER" \
DB_PASSWORD="$DB_PASSWORD" \
JWT_SECRET="e2e_secret_with_at_least_32_bytes_1234567890" \
COOKIE_SECURE=false \
./mvnw -q spring-boot:run >/tmp/gf-backend-e2e.log 2>&1 &
BACKEND_PID=$!

echo "==> Aguardando backend responder"
for _ in $(seq 1 90); do
  if curl -fsS http://localhost:8081/actuator/health >/dev/null 2>&1; then
    break
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "Backend morreu durante o boot — veja /tmp/gf-backend-e2e.log" >&2
    exit 1
  fi
  sleep 2
done
curl -fsS http://localhost:8081/actuator/health >/dev/null

echo "==> Rodando Playwright"
cd "$ROOT_DIR/frontend"
VITE_API_URL="http://localhost:8081/api" npx playwright test "$@"
