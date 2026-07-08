#!/bin/bash
# Gestor Financeiro — Health Check
# Uso: ./scripts/health-check.sh [BASE_URL]
# Exemplo: ./scripts/health-check.sh https://api.meudominio.com

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
HEALTH_URL="${BASE_URL}/actuator/health"

echo "Health Check: $HEALTH_URL"

RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$HEALTH_URL" 2>&1)

if [ "$RESPONSE" = "200" ]; then
  echo "Status: UP (HTTP 200)"
  exit 0
else
  echo "Status: DOWN (HTTP $RESPONSE)"
  exit 1
fi
