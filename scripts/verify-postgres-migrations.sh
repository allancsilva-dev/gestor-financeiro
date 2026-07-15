#!/bin/bash
# Gestor Financeiro — valida migrations/Flyway/Hibernate contra PostgreSQL real

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER_NAME="gf-postgres-it-$$"
DB_NAME="gestor_financeiro_it"
DB_USER="postgres"
DB_PASSWORD="postgres"

cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

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

cd "$ROOT_DIR/backend"
POSTGRES_IT_JDBC_URL="jdbc:postgresql://127.0.0.1:${HOST_PORT}/${DB_NAME}" \
POSTGRES_IT_USERNAME="$DB_USER" \
POSTGRES_IT_PASSWORD="$DB_PASSWORD" \
./mvnw -q -Dtest='PostgresMigrationIT,MetaStatusBackfillIT,UsuarioExclusaoLgpdIT' test
