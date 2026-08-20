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

## Simulador iOS — armadilhas confirmadas (2026-08-20)

**Postgres do host sombreia o container.** Existe um Postgres nativo do macOS
escutando em `127.0.0.1:5432` e `[::1]:5432`; o `gf-postgres` publica em `*:5432`.
`localhost` resolve para o nativo, que não tem o role `postgres` — a conexão morre
com `FATAL: role "postgres" does not exist`. Use o IP da máquina:

```bash
IP=$(ipconfig getifaddr en0)
DATABASE_URL="jdbc:postgresql://$IP:5432/<db>"
```

**Porta 8081 é disputada em dois níveis.** Além do BlueStacks, um dev server node
de outro projeto pode estar nela devolvendo HTML para qualquer path — o Spring
recusa subir ("Port 8081 was already in use") e o Metro do Expo cai no mesmo
buraco. Suba o backend em outra porta e o Metro também:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8092
npx expo start --port 8100 --dev-client
```

**Dev build serve bundle velho.** `EXPO_PUBLIC_API_BASE_URL` só entra no bundle
que o Metro **daquela sessão** transpila. Se o app continuar apontando para
produção (sintoma: `failed_attempts` fica 0 no banco local e nenhum
`refresh_token` novo aparece), faça build Release, que embute o JS:

```bash
APP_ENV=local-e2e EXPO_PUBLIC_API_BASE_URL="http://localhost:8092/api" \
  npx expo run:ios --configuration Release --device <UDID>
```

`APP_ENV=local-e2e` também desliga o `secureTextEntry` da tela de registro.

**Causa raiz do campo de senha:** o AutoFill do iOS sequestra o `TextInput` com
`textContentType` de senha e, num input controlado, só o ÚLTIMO caractere
sobrevive. Em `APP_ENV=local-e2e` a tela de registro usa `textContentType="none"`
justamente para tirar o AutoFill do caminho. A tela de LOGIN não tem essa
escotilha — por isso ali a área de transferência continua sendo o caminho.

**Login não dá para automatizar digitando.** O campo de senha é um `TextInput`
controlado com `secureTextEntry`: o `inputText` do Maestro e o `keystroke` do
AppleScript perdem, duplicam ou trocam caracteres (o `.` vira `,` no layout
PT-BR). O que funciona é área de transferência:

```bash
printf 'senha' | xcrun simctl pbcopy <UDID>
# maestro: tapOn no campo de senha
osascript -e 'tell application "System Events" to keystroke "v" using command down'
```

E-mail pelo Maestro (`tapOn: "seu@email.com"` + `inputText`) funciona normal.
O alerta "Salvar Senha?" do iOS fica fora da árvore do app — dispense por
coordenada (`tapOn: point: "31%,64%"`), não por texto.

**Sessão sobrevive ao uninstall** (tokens no Keychain). Para estado limpo:
`xcrun simctl keychain <UDID> reset`.

**Tema:** `xcrun simctl ui <UDID> appearance dark`.

**Tab bar nativa é invisível para o Maestro.** Depois do redesign da Home a barra
virou painel nativo (react-native-screens) e os rótulos "Início", "Metas" e
"Ajustes" não aparecem na árvore de acessibilidade: `tapOn: "Ajustes"` falha.
Toque por coordenada (`point: "89%,92%"`) e faça as asserções pelo conteúdo da
tela, não pelo nome da aba.

**`hideKeyboard` é instável no iOS** — o próprio Maestro recomenda tocar num
elemento não interativo. `tapOn: point: "50%,4%"` (topo central) funciona.
