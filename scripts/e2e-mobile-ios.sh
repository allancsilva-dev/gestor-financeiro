#!/bin/bash
# Smoke financeiro iOS local: banco e backend descartaveis, app Debug e Maestro.
# Uso: ./scripts/e2e-mobile-ios.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOBILE_DIR="$ROOT_DIR/mobile"
FLOW_FILE="$MOBILE_DIR/.maestro/financial-critical.yaml"
APP_ID="com.gestorfinanceiro.mobile"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
ARTIFACT_DIR="/tmp/gf-mobile-smoke-$RUN_ID"
# DerivedData estável: com um diretório por execução, todo run recompilava o app
# inteiro (~5 min). Reaproveitar o cache torna a segunda rodada em diante
# incremental. GF_E2E_CLEAN_BUILD=1 força do zero.
BUILD_DIR="${GF_E2E_DERIVED_DATA:-$MOBILE_DIR/.e2e-derived-data}"
CONTAINER_NAME="gf-postgres-mobile-$RUN_ID"
DB_NAME="gestor_financeiro_mobile_e2e"
DB_USER="postgres"
DB_PASSWORD="postgres"
BACKEND_PID=""
NATIVE_CREATED=0
XCODE_UPDATES_CREATED=0
SIMULATOR_UDID=""
EXIT_CODE=0

if [ "${GF_E2E_CLEAN_BUILD:-0}" = "1" ]; then
  rm -rf "$BUILD_DIR"
fi
mkdir -p "$ARTIFACT_DIR" "$BUILD_DIR"

cleanup() {
  EXIT_CODE=$?
  set +e
  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null
    wait "$BACKEND_PID" 2>/dev/null
  fi
  # O spring-boot:run forka um JVM filho que sobrevive ao kill do Maven e mantem
  # a porta presa para a proxima execucao. A porta e nossa: o script aborta no
  # inicio se ela ja estiver ocupada.
  if [ -n "${BACKEND_PORT:-}" ]; then
    lsof -ti "tcp:$BACKEND_PORT" 2>/dev/null | xargs -r kill 2>/dev/null
  fi
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1
  if [ -n "$SIMULATOR_UDID" ]; then
    xcrun simctl uninstall "$SIMULATOR_UDID" "$APP_ID" >/dev/null 2>&1
  fi
  # BUILD_DIR e ios/ sobrevivem de proposito: sao o cache do build incremental.
  # GF_E2E_CLEAN_BUILD=1 limpa o DerivedData no inicio da proxima execucao, e
  # ios/ pode ser regerado com `npx expo prebuild --clean`.
  if [ "$NATIVE_CREATED" -eq 1 ] && [ "${GF_E2E_KEEP_NATIVE:-1}" != "1" ]; then
    rm -rf "$MOBILE_DIR/ios"
  elif [ "$XCODE_UPDATES_CREATED" -eq 1 ]; then
    rm -f "$MOBILE_DIR/ios/.xcode.env.updates"
  fi
  if [ "$EXIT_CODE" -eq 0 ]; then
    printf 'OK: smoke financeiro concluído. Evidências: %s\n' "$ARTIFACT_DIR"
  else
    printf 'FALHA: smoke financeiro terminou com código %s. Evidências preservadas: %s\n' "$EXIT_CODE" "$ARTIFACT_DIR" >&2
  fi
  exit "$EXIT_CODE"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  printf 'Erro: %s\nEvidências: %s\n' "$1" "$ARTIFACT_DIR" >&2
  exit 1
}

load_node() {
  if command -v node >/dev/null 2>&1; then return; fi
  local nvm_dir="${NVM_DIR:-${HOME:-}/.nvm}"
  if [ -s "$nvm_dir/nvm.sh" ]; then
    set +u
    # shellcheck disable=SC1090
    . "$nvm_dir/nvm.sh"
    nvm use "$(tr -d '[:space:]' < "$ROOT_DIR/.nvmrc")" >/dev/null 2>&1 \
      || nvm use node >/dev/null 2>&1 \
      || true
    set -u
  fi
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 não encontrado${2:-}."
}

load_node
require_command docker " ou fora do PATH"
require_command xcodebuild " (instale o Xcode)"
require_command xcrun " (instale o Xcode Command Line Tools)"
require_command node " (use a versão de .nvmrc)"
require_command maestro
require_command jq
require_command curl
require_command rg
test -x "$ROOT_DIR/backend/mvnw" || fail "backend/mvnw não é executável."
test -f "$FLOW_FILE" || fail "flow Maestro ausente: $FLOW_FILE"
test -d "$MOBILE_DIR/node_modules" || fail "dependências mobile ausentes; execute npm ci em mobile/."

# Porta do backend do e2e. 8081 é o padrão histórico, mas em máquina de dev ela
# costuma estar ocupada (BlueStacks, outro dev server) — daí ser parametrizável.
BACKEND_PORT="${GF_E2E_BACKEND_PORT:-8081}"

docker info >/dev/null 2>&1 || fail "Docker não está disponível."
xcodebuild -version >"$ARTIFACT_DIR/xcode-version.txt" 2>&1 || fail "Xcode não está funcional."
node --version >"$ARTIFACT_DIR/node-version.txt"
maestro --version >"$ARTIFACT_DIR/maestro-version.txt" 2>&1 || fail "Maestro não está funcional."

# Detecta ocupação de verdade. A versão anterior usava curl no /actuator/health
# e só abortava se OUTRO Spring saudável respondesse — se a porta estivesse com
# qualquer outra coisa (um dev server node, por exemplo), o curl falhava, o
# script seguia e o backend só morria no boot, depois de subir o Postgres.
if lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  fail "a porta $BACKEND_PORT já está em uso. Use GF_E2E_BACKEND_PORT=<outra> para trocar."
fi

SIMULATOR_UDID="$(xcrun simctl list devices booted | sed -n 's/^[[:space:]]*iPhone 17 Pro (\([0-9A-F-]*\)) (Booted)[[:space:]]*$/\1/p' | head -1)"
test -n "$SIMULATOR_UDID" || fail "nenhum iPhone 17 Pro está bootado."
xcrun simctl bootstatus "$SIMULATOR_UDID" -b >"$ARTIFACT_DIR/simulator-boot.txt" 2>&1

printf '==> PostgreSQL efêmero (%s)\n' "$CONTAINER_NAME"
docker run -d --name "$CONTAINER_NAME" \
  -e POSTGRES_DB="$DB_NAME" \
  -e POSTGRES_USER="$DB_USER" \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -p 127.0.0.1::5432 \
  postgres:16-alpine >"$ARTIFACT_DIR/postgres-container-id.txt"

for _ in $(seq 1 40); do
  docker exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 \
  || fail "PostgreSQL não ficou pronto."
HOST_PORT="$(docker port "$CONTAINER_NAME" 5432/tcp | sed 's/.*://')"

printf '==> Backend local (porta %s)\n' "$BACKEND_PORT"
(
  cd "$ROOT_DIR/backend"
  SPRING_PROFILES_ACTIVE=dev \
  DATABASE_URL="jdbc:postgresql://127.0.0.1:${HOST_PORT}/${DB_NAME}" \
  DB_USERNAME="$DB_USER" \
  DB_PASSWORD="$DB_PASSWORD" \
  JWT_SECRET="mobile_e2e_secret_with_at_least_32_bytes_1234567890" \
  COOKIE_SECURE=false \
  SERVER_PORT="$BACKEND_PORT" \
  ./mvnw -q spring-boot:run
) >"$ARTIFACT_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

for _ in $(seq 1 90); do
  curl -fsS http://127.0.0.1:$BACKEND_PORT/actuator/health >"$ARTIFACT_DIR/backend-health.json" 2>/dev/null && break
  kill -0 "$BACKEND_PID" 2>/dev/null || fail "backend morreu durante o boot; consulte backend.log."
  sleep 2
done
curl -fsS http://127.0.0.1:$BACKEND_PORT/actuator/health >"$ARTIFACT_DIR/backend-health.json" \
  || fail "backend não respondeu no prazo."

printf '==> App iOS Release assinado com API local\n'
if [ ! -d "$MOBILE_DIR/ios" ]; then
  NATIVE_CREATED=1
  (
    cd "$MOBILE_DIR"
    EXPO_PUBLIC_API_BASE_URL="http://127.0.0.1:$BACKEND_PORT/api" \
      npx expo prebuild --clean --platform ios --no-install
    cd ios
    pod install
  ) >"$ARTIFACT_DIR/prebuild.log" 2>&1
fi

WORKSPACE="$(find "$MOBILE_DIR/ios" -maxdepth 1 -name '*.xcworkspace' -print -quit)"
test -n "$WORKSPACE" || fail "workspace iOS não encontrado após o prebuild."
SCHEME="$(basename "$WORKSPACE" .xcworkspace)"
XCODE_UPDATES="$MOBILE_DIR/ios/.xcode.env.updates"
if [ ! -e "$XCODE_UPDATES" ]; then
  printf 'unset SKIP_BUNDLING\n' >"$XCODE_UPDATES"
  XCODE_UPDATES_CREATED=1
elif ! rg -q 'unset[[:space:]]+SKIP_BUNDLING' "$XCODE_UPDATES"; then
  fail "$XCODE_UPDATES existe e não libera o bundle Debug; preserve o arquivo e ajuste-o manualmente."
fi
# Release assinado pelo Xcode, igual ao runner do assistente (PROB-0088). Debug forca DEV=true
# em react-native-xcode.sh e o bundle sai com LogBox, que cobre a tela inteira no primeiro
# console.error; e CODE_SIGNING_ALLOWED=NO deixa o app sem entitlements, entao expo-notifications
# falha ao ler o Keychain e dispara justamente esse console.error. O flow morria em "Criar conta"
# atras da tela vermelha.
(
  cd "$MOBILE_DIR"
  EXPO_PUBLIC_API_BASE_URL="http://127.0.0.1:$BACKEND_PORT/api" \
  APP_ENV=local-e2e \
  APP_RELEASE_SHA="$RUN_ID" \
  FORCE_BUNDLING=1 \
  xcodebuild \
    -workspace "$WORKSPACE" \
    -scheme "$SCHEME" \
    -configuration Release \
    -sdk iphonesimulator \
    -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
    -derivedDataPath "$BUILD_DIR" \
    FORCE_BUNDLING=1 \
    EXPO_PUBLIC_API_BASE_URL="http://127.0.0.1:$BACKEND_PORT/api" \
    RCT_METRO_PORT=8082 \
    EX_DEV_CLIENT_NETWORK_INSPECTOR=0 \
    APP_ENV=local-e2e \
    APP_RELEASE_SHA="$RUN_ID" \
    build
) >"$ARTIFACT_DIR/xcodebuild.log" 2>&1

# Products/ tem uma pasta por configuracao. Buscar a partir da raiz com `-print -quit` pegava a
# PRIMEIRA que aparecesse — e como este script e o do assistente compartilham o mesmo
# DerivedData, um Release antigo daquele sombreava o Debug recem-compilado daqui. O smoke
# passava verde testando um app de horas atras. Ancorar na configuracao correta.
APP_PATH="$(find "$BUILD_DIR/Build/Products/Release-iphonesimulator" -maxdepth 1 -type d -name '*.app' -print -quit 2>/dev/null)"
test -n "$APP_PATH" || fail "app Release não encontrado no DerivedData temporário."
# Cinto e suspensorio: bundle mais velho que o inicio deste run significa build que nao aconteceu.
if [ "$APP_PATH/main.jsbundle" -ot "$ARTIFACT_DIR" ]; then
  fail "bundle JS ($APP_PATH/main.jsbundle) é anterior a este run — build não recompilou."
fi
test -f "$APP_PATH/main.jsbundle" || fail "bundle JavaScript não foi incorporado ao app."
APP_CONFIG="$APP_PATH/EXConstants.bundle/app.config"
test -f "$APP_CONFIG" || fail "configuração Expo não foi incorporada ao app Debug."
jq -e --arg api "http://127.0.0.1:$BACKEND_PORT/api" \
  '.extra.apiBaseUrl == $api and .extra.appEnv == "local-e2e"' "$APP_CONFIG" >/dev/null \
  || fail "app não contém a API local e o ambiente local-e2e esperados."
cp "$APP_CONFIG" "$ARTIFACT_DIR/app.config.json"
xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
# `clearState` do Maestro nao apaga o Keychain, e e la que o app guarda a sessao (PROB-0089):
# sem o reset o flow abriria ja logado e nunca acharia a tela de login.
xcrun simctl keychain "$SIMULATOR_UDID" reset

E2E_EMAIL="mobile-smoke-$RUN_ID@example.test"
E2E_PASSWORD="Smoke12345"
E2E_DATE_INPUT="$(date +%d%m%Y)"
printf 'run_id=%s\nemail=%s\nsimulator=%s\napi=%s\n' \
  "$RUN_ID" "$E2E_EMAIL" "$SIMULATOR_UDID" "http://127.0.0.1:$BACKEND_PORT/api" \
  >"$ARTIFACT_DIR/run.txt"

printf '==> Maestro financial-critical\n'
(
  cd "$ARTIFACT_DIR"
  maestro test "$FLOW_FILE" \
    --udid "$SIMULATOR_UDID" \
    --format JUNIT \
    --output "$ARTIFACT_DIR/maestro-junit.xml" \
    --test-output-dir "$ARTIFACT_DIR/maestro-debug" \
    --debug-output "$ARTIFACT_DIR/maestro-debug" \
    -e E2E_EMAIL="$E2E_EMAIL" \
    -e E2E_PASSWORD="$E2E_PASSWORD" \
    -e E2E_DATE_INPUT="$E2E_DATE_INPUT"
) >"$ARTIFACT_DIR/maestro.log" 2>&1 || fail "flow Maestro falhou; consulte maestro.log e maestro-debug/."

printf '==> Reconciliação técnica por API\n'
LOGIN_JSON="$ARTIFACT_DIR/api-login.json"
curl -fsS -X POST http://127.0.0.1:$BACKEND_PORT/api/auth/login \
  -H 'Content-Type: application/json' \
  -H 'X-Client-Type: mobile' \
  --data "$(jq -cn --arg email "$E2E_EMAIL" --arg password "$E2E_PASSWORD" '{email:$email,password:$password}')" \
  >"$LOGIN_JSON"
TOKEN="$(jq -er '.accessToken' "$LOGIN_JSON")" || fail "login técnico não retornou accessToken."

api_get() {
  curl -fsS "http://127.0.0.1:$BACKEND_PORT/api$1" -H "Authorization: Bearer $TOKEN" -H 'X-Client-Type: mobile'
}

api_get '/v1/reconciliacao/global' >"$ARTIFACT_DIR/reconciliacao-global.json"
api_get '/v1/contas-financeiras/minhas?page=0&size=100' >"$ARTIFACT_DIR/contas-financeiras.json"
api_get '/v1/cartoes?page=0&size=100' >"$ARTIFACT_DIR/cartoes.json"
api_get '/v1/metas/minhas?page=0&size=20' >"$ARTIFACT_DIR/metas.json"
api_get '/v1/metricas' >"$ARTIFACT_DIR/metricas.json"

CARTAO_ID="$(jq -er '.content[] | select(.nome == "Cartão Principal") | .id' "$ARTIFACT_DIR/cartoes.json")" \
  || fail "Cartão Principal não encontrado na validação técnica."
api_get "/v1/faturas/cartao/$CARTAO_ID/atual" >"$ARTIFACT_DIR/fatura-atual.json"

jq -e '
  .status == "OK"
  and .divergencias == 0
  and (.detalhes | length) == 0
  and (.resumo | length) == 5
  and ([.resumo[].invariante] | sort) == (["CATEGORIA_VALOR_GASTO","COFRE_META","PASSIVO_FATURAS","SALDO_LEDGER","TRANSACAO_INCOMPLETA"] | sort)
  and all(.resumo[]; .divergencias == 0)
' "$ARTIFACT_DIR/reconciliacao-global.json" >/dev/null || fail "reconciliação global divergente."

# Passivo do cartao: 75 (compra) + 60 (assinatura Netflix, V67) - 25 (pago) = 110.
# A Conta Principal segue em 825: assinatura de cartao entra na fatura e nao toca
# o caixa — e essa igualdade e a prova disso no fluxo real.
jq -e '
  any(.content[]; .nome == "Conta Principal" and .natureza == "ATIVO" and .saldo == 825)
  and any(.content[]; .nome == "Cofre: Meta Smoke" and .subtipo == "COFRE" and .saldo == 50)
  and any(.content[]; .subtipo == "CARTAO" and .natureza == "PASSIVO" and .saldo == 110)
' "$ARTIFACT_DIR/contas-financeiras.json" >/dev/null || fail "saldos de conta, cofre ou passivo incoerentes."

jq -e 'any(.content[]; .nome == "Cartão Principal" and .saldoDevedor == 110 and .limiteTotal == 5000)' \
  "$ARTIFACT_DIR/cartoes.json" >/dev/null || fail "saldo do cartão incoerente."
jq -e 'any(.content[]; .nome == "Meta Smoke" and .valorTotal == 1000 and .valorReservado == 50 and .cofreId != null)' \
  "$ARTIFACT_DIR/metas.json" >/dev/null || fail "progresso/cofre da meta incoerente."
# Fatura: 75 + 60 = 135, com 25 pagos. A assinatura precisa aparecer como
# lancamento proprio — e o que separa recorrencia real de intencao registrada.
jq -e '.valorTotal == 135 and .valorPago == 25 and ((.valorTotal - .valorPago) == 110)
  and any(.lancamentos[]; .descricao == "Compra cartão smoke" and .valor == 75)
  and any(.lancamentos[]; .descricao == "Netflix" and .valor == 60)' \
  "$ARTIFACT_DIR/fatura-atual.json" >/dev/null || fail "fatura ou lançamento incoerente."
# Disponivel e reservado nao mudam (o cartao nao move caixa); dividas e resultado
# absorvem os 60 da assinatura, e o patrimonio cai na mesma medida.
jq -e '
  .disponivelAgora == 875
  and .reservado == 50
  and .dividas == 110
  and .resultadoMensal == -235
  and .patrimonioLiquido == 765
' "$ARTIFACT_DIR/metricas.json" >/dev/null || fail "métricas principais incoerentes."

if rg -n 'HTTP[^[:digit:]]*5[0-9]{2}|status[=: ]+5[0-9]{2}|Internal Server Error' "$ARTIFACT_DIR/backend.log" \
  >"$ARTIFACT_DIR/backend-5xx.txt"; then
  fail "backend registrou resposta 5xx."
fi

{
  printf 'Smoke financeiro iOS: OK\n'
  printf 'Execução: %s\nUsuário descartável: %s\n' "$RUN_ID" "$E2E_EMAIL"
  jq -r '"Reconciliação: \(.status); verificações=\(.verificacoes); divergências=\(.divergencias)"' "$ARTIFACT_DIR/reconciliacao-global.json"
  jq -r '"Métricas: disponível=\(.disponivelAgora); reservado=\(.reservado); dívidas=\(.dividas); resultado=\(.resultadoMensal); patrimônio=\(.patrimonioLiquido)"' "$ARTIFACT_DIR/metricas.json"
  jq -r '"Fatura: total=\(.valorTotal); pago=\(.valorPago); restante=\(.valorTotal - .valorPago)"' "$ARTIFACT_DIR/fatura-atual.json"
  printf 'Saldos esperados: Conta Principal=825; Cofre=50; Passivo=110\n'
} >"$ARTIFACT_DIR/reconciliation-report.txt"
