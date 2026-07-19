---
name: verify
description: Como subir a stack local e dirigir o app web/API do Gestor Financeiro para verificar mudanças em runtime.
---

# Verificação local — Gestor Financeiro

## Subir a stack

Postgres: container `gf-postgres` (porta 5432, user `postgres`, senha `1234`). Porta 8080 é
BlueStacks; o backend usa 8081.

Backend exige env vars (application.properties referencia `${SPRING_PROFILES_ACTIVE}` sem default):

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev \
  DATABASE_URL="jdbc:postgresql://localhost:5432/<db>" \
  DB_USERNAME=postgres DB_PASSWORD=1234 \
  JWT_SECRET="local-dev-secret-jwt-32-chars-minimo-ok!" \
  ./mvnw spring-boot:run
```

**Gotcha:** o banco dev antigo `gestor_financeiro` NÃO sobe — migration
`V36__remove_redundant_card_parcels.sql` aborta ("Divergencia entre parcelas e fatura_lancamentos").
Use banco limpo descartável: `docker exec gf-postgres psql -U postgres -c "CREATE DATABASE gf_verify_x;"`.

Frontend web: `cd frontend && npm run dev` → http://localhost:5173. Node via nvm:
`export PATH="$HOME/.nvm/versions/node/v22.23.1/bin:$PATH"` (npx/npm não estão no PATH default).

## Seed via API (payloads exatos do mobile)

```bash
curl -X POST localhost:8081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"nome":"X","email":"x@example.com","password":"SenhaF0rte!23","confirmPassword":"SenhaF0rte!23","aceitaTermos":true}'
# login: campo do token é "accessToken" (não "token")
curl -X POST localhost:8081/api/v1/onboarding/finalizar -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"carteira":{"nome":"Conta Principal","subtipo":"CORRENTE","saldo":2500,"banco":"Banco Teste"}}'
```

Auth em `/api/auth/*` (sem /v1); demais rotas `/api/v1/*`.

## Dirigir o web (Playwright)

Chromium já em cache (`~/Library/Caches/ms-playwright`). Import direto:
`import { chromium } from '<repo>/frontend/node_modules/playwright-core/index.mjs'` num script `.mjs`.

**Gotcha:** `page.goto()` (reload completo) derruba a sessão em dev — refresh cookie não cruza
5173→8081 (422/403 no console). Navegue client-side clicando nos links do menu depois do login.

## Fluxos que valem dirigir

- Login → Dashboard → expandir métrica → clicar origem → extrato/transações filtradas.
- `/transacoes?inicio=&fim=&tipo=` → banner "Limpar filtro".
- `/contas-financeiras?contaId=N` → extrato abre sozinho.
