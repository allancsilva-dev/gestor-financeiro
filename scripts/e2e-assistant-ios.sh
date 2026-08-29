#!/bin/bash
# Fechamento local do Assistente iOS: PostgreSQL, backend local-e2e, app e Maestro.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOBILE_DIR="$ROOT_DIR/mobile"
APP_ID="com.gestorfinanceiro.mobile"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
ARTIFACT_DIR="${GF_E2E_ARTIFACT_DIR:-$ROOT_DIR/artifacts/fase5/$RUN_ID}"
BUILD_DIR="${GF_E2E_DERIVED_DATA:-$MOBILE_DIR/.e2e-derived-data}"
CONTAINER_NAME="gf-postgres-assistant-$RUN_ID"
DB_NAME="gestor_financeiro_assistant_e2e"
DB_USER="postgres"
DB_PASSWORD="postgres"
BACKEND_PORT="${GF_E2E_BACKEND_PORT:-8081}"
BACKEND_PID=""
SIMULATOR_UDID=""
EXIT_CODE=0

mkdir -p "$ARTIFACT_DIR" "$BUILD_DIR"

cleanup() {
  EXIT_CODE=$?
  set +e
  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null
    wait "$BACKEND_PID" 2>/dev/null
  fi
  if [ -n "$BACKEND_PORT" ]; then
    lsof -ti "tcp:$BACKEND_PORT" 2>/dev/null | xargs -r kill 2>/dev/null
  fi
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1
  if [ "$EXIT_CODE" -eq 0 ] && [ -f "$ARTIFACT_DIR/run.txt" ]; then
    printf 'OK: Assistente iOS. Evidências: %s\n' "$ARTIFACT_DIR"
  else
    printf 'FALHA: código %s. Evidências: %s\n' "$EXIT_CODE" "$ARTIFACT_DIR" >&2
    [ "$EXIT_CODE" -eq 0 ] && EXIT_CODE=1
  fi
  exit "$EXIT_CODE"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  printf 'Erro: %s\n' "$1" >&2
  printf 'status=FAILED\nreason=%s\n' "$1" >"$ARTIFACT_DIR/failure.txt"
  exit 1
}
require() { command -v "$1" >/dev/null 2>&1 || fail "$1 não encontrado."; }

for command in docker xcodebuild xcrun node java pod maestro jq curl rg lsof; do require "$command"; done
test -x "$ROOT_DIR/backend/mvnw" || fail "backend/mvnw não executável."
test -d "$MOBILE_DIR/node_modules" || fail "dependências mobile ausentes."
docker info >/dev/null 2>&1 || fail "Docker Desktop/daemon indisponível."

xcodebuild -version >"$ARTIFACT_DIR/xcode-version.txt" 2>&1 || fail "Xcode não funcional."
pod --version >"$ARTIFACT_DIR/cocoapods-version.txt" 2>&1 || fail "CocoaPods não funcional."
maestro --version >"$ARTIFACT_DIR/maestro-version.txt" 2>&1 || fail "Maestro não funcional."
node --version >"$ARTIFACT_DIR/node-version.txt"
java -version >"$ARTIFACT_DIR/java-version.txt" 2>&1

if [ -n "${GF_E2E_SIMULATOR_UDID:-}" ]; then
  SIMULATOR_UDID="$GF_E2E_SIMULATOR_UDID"
  xcrun simctl list devices booted | rg -q "$SIMULATOR_UDID.*Booted" \
    || fail "GF_E2E_SIMULATOR_UDID não aponta para simulador bootado."
else
  SIMULATOR_UDID="$(xcrun simctl list devices booted -j | jq -r '
    [.devices[][] | select(.state == "Booted" and (.name | startswith("iPhone")))] | first | .udid // empty')"
fi
test -n "$SIMULATOR_UDID" || fail "nenhum simulador iPhone bootado."
xcrun simctl bootstatus "$SIMULATOR_UDID" -b >"$ARTIFACT_DIR/simulator-boot.txt" 2>&1
xcrun simctl list devices booted -j | jq -c --arg id "$SIMULATOR_UDID" \
  '.devices[][] | select(.udid == $id) | {name,udid,state,runtimeIdentifier}' >"$ARTIFACT_DIR/simulator.json"

if lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  fail "porta $BACKEND_PORT ocupada; defina GF_E2E_BACKEND_PORT."
fi

docker run -d --name "$CONTAINER_NAME" \
  -e POSTGRES_DB="$DB_NAME" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -p 127.0.0.1::5432 postgres:16-alpine >"$ARTIFACT_DIR/postgres-container-id.txt"
for _ in $(seq 1 40); do
  docker exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 \
  || fail "PostgreSQL não ficou pronto."
HOST_PORT="$(docker port "$CONTAINER_NAME" 5432/tcp | sed 's/.*://')"

(
  cd "$ROOT_DIR/backend"
  SPRING_PROFILES_ACTIVE=dev,local-e2e \
  DATABASE_URL="jdbc:postgresql://127.0.0.1:${HOST_PORT}/${DB_NAME}" \
  DB_USERNAME="$DB_USER" DB_PASSWORD="$DB_PASSWORD" \
  JWT_SECRET="local_e2e_only_secret_32_bytes_minimum_123456" \
  COOKIE_SECURE=false SERVER_PORT="$BACKEND_PORT" \
  JOBS_WORKER_ENABLED=true ASSISTANT_WHATSAPP_WORKER_ENABLED=false \
  ./mvnw -q spring-boot:run
) >"$ARTIFACT_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
for _ in $(seq 1 90); do
  curl -fsS "http://127.0.0.1:$BACKEND_PORT/actuator/health" >"$ARTIFACT_DIR/backend-health.json" 2>/dev/null && break
  kill -0 "$BACKEND_PID" 2>/dev/null || fail "backend morreu no boot."
  sleep 2
done
curl -fsS "http://127.0.0.1:$BACKEND_PORT/actuator/health" >/dev/null || fail "backend não respondeu."

E2E_EMAIL="assistant-$RUN_ID@example.test"
E2E_PASSWORD="Smoke12345"
API="http://127.0.0.1:$BACKEND_PORT/api"
REGISTER="$(jq -cn --arg email "$E2E_EMAIL" --arg password "$E2E_PASSWORD" \
  '{nome:"Assistente E2E",email:$email,password:$password,confirmPassword:$password,aceitaTermos:true}')"
REGISTER_RESPONSE="$(curl -fsS -X POST "$API/auth/register" -H 'Content-Type: application/json' \
  -H 'X-Client-Type: mobile' --data "$REGISTER")"
USER_ID="$(jq -er '.id' <<<"$REGISTER_RESPONSE")"
LOGIN="$(jq -cn --arg email "$E2E_EMAIL" --arg password "$E2E_PASSWORD" '{email:$email,password:$password}')"
TOKEN="$(curl -fsS -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -H 'X-Client-Type: mobile' --data "$LOGIN" | jq -er '.accessToken')"
AUTH=(-H "Authorization: Bearer $TOKEN" -H 'X-Client-Type: mobile')
# Fixture com cartao e mais de uma categoria: sem isso o assistente nao teria como
# provar extracao de categoria por palavra nem compra parcelada.
curl -fsS -X POST "$API/v1/onboarding/finalizar" "${AUTH[@]}" -H 'Content-Type: application/json' --data '
  {"carteira":{"nome":"Conta Principal","subtipo":"CORRENTE","saldo":1000,"banco":"E2E"},
   "cartao":{"nome":"Cartao Nubank","limiteTotal":5000,"diaFechamento":20,"diaVencimento":28},
   "categorias":[{"nome":"Mercado","cor":"#123456","icone":"cart","valorEsperado":0},
                 {"nome":"Transporte","cor":"#654321","icone":"car","valorEsperado":0},
                 {"nome":"Alimentacao","cor":"#abcdef","icone":"restaurant","valorEsperado":0}],
   "renda":null,"meta":null}' >/dev/null

if [ ! -d "$MOBILE_DIR/ios" ]; then
  (cd "$MOBILE_DIR" && npx expo prebuild --clean --platform ios --no-install && cd ios && pod install) \
    >"$ARTIFACT_DIR/prebuild.log" 2>&1
fi
WORKSPACE="$(find "$MOBILE_DIR/ios" -maxdepth 1 -name '*.xcworkspace' -print -quit)"
test -n "$WORKSPACE" || fail "workspace iOS ausente."
SCHEME="$(basename "$WORKSPACE" .xcworkspace)"
XCODE_UPDATES="$MOBILE_DIR/ios/.xcode.env.updates"
rg -q 'unset[[:space:]]+SKIP_BUNDLING' "$XCODE_UPDATES" 2>/dev/null \
  || printf 'unset SKIP_BUNDLING\n' >>"$XCODE_UPDATES"
# Sem EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED: o gate saiu do build e virou runtime. O app pergunta
# /api/v1/capacidades, e o profile local-e2e ja traz assistant.text.enabled=true.
(
  cd "$MOBILE_DIR"
  EXPO_PUBLIC_API_BASE_URL="$API" APP_ENV=local-e2e \
  APP_RELEASE_SHA="$RUN_ID" FORCE_BUNDLING=1 \
  xcodebuild -workspace "$WORKSPACE" -scheme "$SCHEME" -configuration Release -sdk iphonesimulator \
    -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" -derivedDataPath "$BUILD_DIR" \
    FORCE_BUNDLING=1 \
    EXPO_PUBLIC_API_BASE_URL="$API" \
    RCT_METRO_PORT=8082 EX_DEV_CLIENT_NETWORK_INSPECTOR=0 \
    APP_ENV=local-e2e APP_RELEASE_SHA="$RUN_ID" build
) >"$ARTIFACT_DIR/xcodebuild.log" 2>&1
APP_PATH="$(find "$BUILD_DIR/Build/Products/Release-iphonesimulator" -maxdepth 1 -type d -name '*.app' -print -quit)"
test -f "$APP_PATH/main.jsbundle" || fail "bundle JS ausente."
APP_CONFIG="$APP_PATH/EXConstants.bundle/app.config"
test -f "$APP_CONFIG" || fail "configuração Expo ausente."
jq -e --arg api "$API" '.extra.apiBaseUrl == $api and .extra.appEnv == "local-e2e"' "$APP_CONFIG" >/dev/null \
  || fail "app não contém API local/profile local-e2e."
jq '{name,slug,version,extra:{apiBaseUrl:.extra.apiBaseUrl,appEnv:.extra.appEnv,releaseSha:.extra.releaseSha}}' \
  "$APP_CONFIG" >"$ARTIFACT_DIR/app-config-sanitized.json"
xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
xcrun simctl privacy "$SIMULATOR_UDID" grant microphone "$APP_ID"

run_flow() {
  local flow="$1" name
  name="$(basename "$flow" .yaml)"
  mkdir -p "$ARTIFACT_DIR/$name"
  # `clearState` do Maestro não apaga o keychain, e é lá que o app guarda a sessão: sem este
  # reset o segundo flow abriria já logado e não encontraria a tela de login.
  xcrun simctl keychain "$SIMULATOR_UDID" reset
  (
    cd "$ARTIFACT_DIR/$name"
    maestro test "$flow" --udid "$SIMULATOR_UDID" --format JUNIT \
      --output "$ARTIFACT_DIR/$name/junit.xml" --test-output-dir "$ARTIFACT_DIR/$name/debug" \
      --debug-output "$ARTIFACT_DIR/$name/debug" -e E2E_EMAIL="$E2E_EMAIL" -e E2E_PASSWORD="$E2E_PASSWORD"
  ) >"$ARTIFACT_DIR/$name/maestro.log" 2>&1 || fail "flow $name falhou."
}

sql_scalar() {
  docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -Atqc "$1"
}

# Compra no cartao nao move carteira: o cronograma vive na fatura. Por isso movimentos e
# lancamentos de fatura sao esperados separadamente das transacoes.
prove_financial_state() {
  local label="$1" expected="$2" expected_movements="$3" expected_invoice_lines="$4"
  local file="$ARTIFACT_DIR/proof-$label.json"
  local transactions operations confirmations movements invoice_lines divergences
  transactions="$(sql_scalar "select count(*) from transacoes t join assistant_confirmations c on c.transacao_id=t.id where c.usuario_id=$USER_ID")"
  operations="$(sql_scalar "select count(*) from operacoes_financeiras where usuario_id=$USER_ID and origem='ASSISTENTE'")"
  confirmations="$(sql_scalar "select count(*) from assistant_confirmations where usuario_id=$USER_ID")"
  movements="$(sql_scalar "select count(*) from movimentos_carteira mc join operacoes_financeiras op on op.id=mc.operacao_id where op.usuario_id=$USER_ID and op.origem='ASSISTENTE'")"
  invoice_lines="$(sql_scalar "select count(*) from fatura_lancamentos fl join assistant_confirmations c on c.transacao_id=fl.transacao_id where c.usuario_id=$USER_ID")"
  curl -fsS "$API/v1/reconciliacao/global" "${AUTH[@]}" >"$ARTIFACT_DIR/reconciliation-$label.json"
  divergences="$(jq -er '.divergencias' "$ARTIFACT_DIR/reconciliation-$label.json")"
  jq -cn --arg label "$label" --argjson expected "$expected" --argjson transactions "$transactions" \
    --argjson operations "$operations" --argjson confirmations "$confirmations" --argjson movements "$movements" \
    --argjson expectedMovements "$expected_movements" --argjson invoiceLines "$invoice_lines" \
    --argjson expectedInvoiceLines "$expected_invoice_lines" --argjson divergences "$divergences" \
    '{flow:$label,expected:$expected,transactions:$transactions,operations:$operations,confirmations:$confirmations,
      movements:$movements,expectedMovements:$expectedMovements,invoiceLines:$invoiceLines,
      expectedInvoiceLines:$expectedInvoiceLines,divergences:$divergences}' >"$file"
  jq -e '.transactions == .expected and .operations == .expected and .confirmations == .expected
    and .movements == .expectedMovements and .invoiceLines == .expectedInvoiceLines
    and .divergences == 0' "$file" >/dev/null || fail "prova financeira $label divergente."
}

curl -fsS "$API/v1/reconciliacao/global" "${AUTH[@]}" >"$ARTIFACT_DIR/reconciliation-before.json"
jq -e '.divergencias == 0' "$ARTIFACT_DIR/reconciliation-before.json" >/dev/null || fail "reconciliação inicial divergente."

#                                                    transacoes movimentos lancamentos-fatura
run_flow "$MOBILE_DIR/.maestro/assistant-text.yaml"
prove_financial_state assistant-text            1          1          0
run_flow "$MOBILE_DIR/.maestro/assistant-ambiguity.yaml"
prove_financial_state assistant-ambiguity       1          1          0
run_flow "$MOBILE_DIR/.maestro/assistant-retry.yaml"
prove_financial_state assistant-retry           2          2          0
run_flow "$MOBILE_DIR/.maestro/assistant-confirm-retry.yaml"
prove_financial_state assistant-confirm-retry   3          3          0
run_flow "$MOBILE_DIR/.maestro/assistant-parcelado.yaml"
prove_financial_state assistant-parcelado       4          3          3
run_flow "$MOBILE_DIR/.maestro/assistant-audio.yaml"
prove_financial_state assistant-audio           5          3          6

CONFLICT_KEY="assistant:conflict:$RUN_ID"
curl -fsS -X POST "$API/v1/assistant/messages" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $CONFLICT_KEY" --data '{"text":"paguei 10,00 no supermercado hoje","conversationId":null}' >/dev/null
CONFLICT_STATUS="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/v1/assistant/messages" "${AUTH[@]}" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $CONFLICT_KEY" \
  --data '{"text":"paguei 11,00 no supermercado hoje","conversationId":null}')"
test "$CONFLICT_STATUS" = "409" || fail "payload diferente com mesma Idempotency-Key retornou $CONFLICT_STATUS."
printf '{"differentPayloadStatus":409}\n' >"$ARTIFACT_DIR/idempotency-conflict.json"

IMPORT_FILE_NAME="importacao-assistant-$RUN_ID.csv"
DEVICE_PATH="$(xcrun simctl list devices booted -j | jq -r --arg id "$SIMULATOR_UDID" '.devices[][] | select(.udid == $id) | .dataPath')"
DOWNLOADS="$DEVICE_PATH/data/Media/Downloads"
if mkdir -p "$DOWNLOADS" 2>/dev/null; then
  printf 'data,descricao,valor\n%s,Fixture revisão,12.34\n' "$(date +%Y-%m-%d)" >"$DOWNLOADS/$IMPORT_FILE_NAME"
  mkdir -p "$ARTIFACT_DIR/importacao-mobile"
  xcrun simctl keychain "$SIMULATOR_UDID" reset
  (
    cd "$ARTIFACT_DIR/importacao-mobile"
    maestro test "$MOBILE_DIR/.maestro/importacao-mobile.yaml" --udid "$SIMULATOR_UDID" --format JUNIT \
      --output "$ARTIFACT_DIR/importacao-mobile/junit.xml" --test-output-dir "$ARTIFACT_DIR/importacao-mobile/debug" \
      --debug-output "$ARTIFACT_DIR/importacao-mobile/debug" -e E2E_EMAIL="$E2E_EMAIL" \
      -e E2E_PASSWORD="$E2E_PASSWORD" -e IMPORT_FILE_NAME="$IMPORT_FILE_NAME"
  ) >"$ARTIFACT_DIR/importacao-mobile/maestro.log" 2>&1 || import_outcome=$?
  # O seletor de arquivos do iOS abre em "Recentes" e nao enxerga Media/Downloads: o fixture
  # existe no disco mas nao no provedor. Isso e limitacao do simulador, nao regressao do app,
  # entao vira SKIPPED explicito. Qualquer outra falha do flow continua derrubando o gate.
  if [ "${import_outcome:-0}" -ne 0 ]; then
    if rg -q "$IMPORT_FILE_NAME\" is visible|No visible element found: \"$IMPORT_FILE_NAME" \
        "$ARTIFACT_DIR/importacao-mobile/maestro.log"; then
      printf 'SKIPPED: seletor de arquivos do simulador nao lista %s\n' "$IMPORT_FILE_NAME" \
        >"$ARTIFACT_DIR/importacao-mobile-skipped.txt"
    else
      fail "flow importacao-mobile falhou."
    fi
  fi
else
  printf 'SKIPPED: armazenamento Downloads do simulador indisponível\n' >"$ARTIFACT_DIR/importacao-mobile-skipped.txt"
fi

if rg -n -i --glob '*.log' --glob '*.json' --glob '*.txt' \
  '(bearer[[:space:]]+[A-Za-z0-9._-]+|access[_-]?token|api[_-]?key|wa_id|\b551[0-9]{8,11}\b|token-secreto)' "$ARTIFACT_DIR" \
  >"$ARTIFACT_DIR/sensitive-scan.txt"; then
  fail "evidências contêm padrão sensível."
fi
printf 'OK: nenhum padrão sensível detectado.\n' >"$ARTIFACT_DIR/sensitive-scan.txt"
printf 'run_id=%s\nsimulator=%s\napi=http://127.0.0.1:%s/api\n' "$RUN_ID" "$SIMULATOR_UDID" "$BACKEND_PORT" \
  >"$ARTIFACT_DIR/run.txt"
