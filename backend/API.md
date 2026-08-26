#  API Reference  Gestor Financeiro

Documentação da API REST conferida em 25/08/2026 contra `main` (`e885ed7`).

> Rotas `/carteiras`, `/contas` e `/dashboard/resumo` permanecem por compatibilidade. Novos
> clientes devem usar contas financeiras, cartões, métricas e compromissos.

**Base URL:** `http://localhost:8081`  
**Swagger UI:** `/swagger-ui.html`  
**Health Check:** `/actuator/health`

## Visão Geral
- Endpoints de autenticação ficam em `/api/auth` (sem versionamento).
- Demais recursos ficam em `/api/v1/**`.
- Endpoints protegidos usam `Authorization: Bearer <accessToken>`.
- Refresh token é enviado via cookie HttpOnly (`refreshToken`).

## Formato de Erro
Todos os erros seguem o contrato `ApiError`:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Dados de entrada inválidos",
  "timestamp": "2026-03-14T21:00:00Z",
  "details": {
    "email": "Email inválido"
  }
}
```

Campos:
- `code`: código estável para frontend/mobile
- `message`: mensagem legível
- `timestamp`: instante UTC
- `details`: mapa de erros por campo (quando aplicável)

## Rate Limit (Auth)
Aplicado em:
- `POST /api/auth/login`: 5 tentativas/min por IP
- `POST /api/auth/forgot-password`: 3 tentativas/min por IP

Resposta de bloqueio (`429`) inclui:
- `Retry-After: 60`
- `X-RateLimit-Limit`
- `X-RateLimit-Remaining: 0`

---

##  Autenticação (`/api/auth`)

### POST `/api/auth/register`
Cria usuário.

Body:
```json
{
  "nome": "Allan Carvalho",
  "email": "allan@email.com",
  "password": "123456"
}
```

Resposta `200`:
```json
{
  "id": 1,
  "nome": "Allan Carvalho",
  "email": "allan@email.com"
}
```

### POST `/api/auth/login`
Retorna access token e define cookie HttpOnly de refresh.

Body:
```json
{
  "email": "allan@email.com",
  "password": "123456"
}
```

Observação: o backend aceita também `senha` por compatibilidade.

Resposta `200`:
```json
{
  "message": "Login realizado com sucesso!",
  "success": true,
  "accessToken": "eyJhbGci...",
  "usuario": {
    "id": 1,
    "nome": "Allan Carvalho",
    "email": "allan@email.com"
  }
}
```

Set-Cookie:
- `refreshToken=...; HttpOnly; Path=/api/auth; SameSite=Lax`

### POST `/api/auth/refresh-token`
Rotaciona refresh token e retorna novo access token.

Body: `{}`

Resposta `200`:
```json
{
  "accessToken": "eyJhbGci..."
}
```

Set-Cookie: novo `refreshToken=...`.

Erros comuns:
- `TOKEN_REUSE_DETECTED` (`401`) quando há reuso de token revogado
- `BUSINESS_ERROR` (`422`) em token expirado

### POST `/api/auth/logout`
Revoga token da sessão atual e limpa cookie.

Body: `{}`

Resposta `200`:
```json
{
  "message": "Logout realizado com sucesso"
}
```

### POST `/api/auth/logout-all`
Revoga todos os refresh tokens do usuário autenticado.

### POST `/api/auth/forgot-password`
Solicita recuperação de senha.

### POST `/api/auth/reset-password`
Redefine senha com token.

### POST `/api/auth/validate-token`
Valida token de recuperação. Corpo: `{ "token": "..." }` (evita o token na query string / access log).

---

##  Usuário (`/api/v1/usuarios`)

### GET `/api/v1/usuarios/me`
Retorna usuário autenticado.

---

##  Dashboard (`/api/v1/dashboard`)
Endpoint legado. `GET /resumo` está deprecado; use `/api/v1/metricas` e
`/api/v1/compromissos`. Rotas de gráficos continuam disponíveis:
- `GET /api/v1/dashboard/resumo`
- `GET /api/v1/dashboard/gastos-por-categoria`
- `GET /api/v1/dashboard/evolucao-mensal`
- `GET /api/v1/dashboard/comparacao-mensal`

## Reconciliação (`/api/v1/reconciliacao`)

### GET `/api/v1/reconciliacao/global`

Executa, em snapshot read-only, as quatro invariantes financeiras somente para o titular do
token. Retorna `401` sem autenticação. A lista `detalhes` contém apenas divergências.

```json
{
  "status": "OK",
  "executadoEm": "2026-07-16T03:30:00Z",
  "verificacoes": 12,
  "divergencias": 0,
  "resumo": [
    {"invariante":"SALDO_LEDGER","verificacoes":3,"aprovadas":3,"divergencias":0},
    {"invariante":"PASSIVO_FATURAS","verificacoes":1,"aprovadas":1,"divergencias":0},
    {"invariante":"COFRE_META","verificacoes":1,"aprovadas":1,"divergencias":0},
    {"invariante":"TRANSACAO_INCOMPLETA","verificacoes":7,"aprovadas":7,"divergencias":0}
  ],
  "detalhes": []
}
```

`status` é `OK` ou `DIVERGENTE`. Os invariantes são `SALDO_LEDGER`, `PASSIVO_FATURAS`,
`COFRE_META` e `TRANSACAO_INCOMPLETA`. Não existe endpoint HTTP multiusuário.

---

## Paginação em Listagens
Listagens usam `Page<T>` com parâmetros:
- `page` (default `0`)
- `size` (default `20`, máximo `100`)
- `sort` (ex.: `data,desc`)

Formato de resposta paginada:

```json
{
  "content": [],
  "totalPages": 1,
  "totalElements": 0,
  "size": 20,
  "number": 0
}
```

---

##  Transações (`/api/v1/transacoes`)
- `GET /api/v1/transacoes/minhas` (paginado)
- `GET /api/v1/transacoes/periodo?inicio=YYYY-MM-DD&fim=YYYY-MM-DD&page=0&size=20&sort=data,desc` (paginado)
- `GET /api/v1/transacoes/sugestao-categoria?descricao=...`
- `GET /api/v1/transacoes/{id}`
- `GET /api/v1/transacoes/{id}/cronograma`
- `POST /api/v1/transacoes`
- `PUT /api/v1/transacoes/{id}`
- `DELETE /api/v1/transacoes/{id}`

##  Categorias (`/api/v1/categorias`)
- `GET /api/v1/categorias/minhas` (paginado)
- `GET /api/v1/categorias/{id}`
- `POST /api/v1/categorias`
- `PUT /api/v1/categorias/{id}`
- `DELETE /api/v1/categorias/{id}`

## Contas financeiras (`/api/v1/contas-financeiras`)
- `GET /api/v1/contas-financeiras/minhas` (paginado)
- `GET /api/v1/contas-financeiras/{id}`
- `POST /api/v1/contas-financeiras`
- `PUT /api/v1/contas-financeiras/{id}`
- `DELETE /api/v1/contas-financeiras/{id}`
- `POST /api/v1/contas-financeiras/{id}/ajustes`
- `GET /api/v1/contas-financeiras/{id}/movimentos` (paginado)
- `GET /api/v1/contas-financeiras/minhas/reconciliacao`
- `GET /api/v1/contas-financeiras/{id}/reconciliacao`

Criação pública aceita contas de caixa `ATIVO`. Contas `PASSIVO/CARTAO`, `COFRE` e `CUSTODIA`
são criadas pelos casos de uso correspondentes. Saldo é materializado pelo ledger.

## Cartões (`/api/v1/cartoes`)
- `GET /api/v1/cartoes` (alias `/meus`)
- `GET /api/v1/cartoes/carteira`
- `GET /api/v1/cartoes/{id}`
- `POST /api/v1/cartoes`
- `PUT /api/v1/cartoes/{id}`
- `DELETE /api/v1/cartoes/{id}`

## Faturas (`/api/v1/faturas`)
- `GET /api/v1/faturas/cartao/{cartaoId}/atual`
- `GET /api/v1/faturas/cartao/{cartaoId}`
- `POST /api/v1/faturas/cartao/{cartaoId}`
- `PUT /api/v1/faturas/{id}/pagar` — aceita `Idempotency-Key`

## Métricas e compromissos
- `GET /api/v1/metricas`
- `GET /api/v1/metricas/{metrica}/origens`
- `GET /api/v1/compromissos?ate=YYYY-MM-DD`

## Onboarding (`/api/v1/onboarding`)
- `GET /api/v1/onboarding/status`
- `POST /api/v1/onboarding/completar`
- `POST /api/v1/onboarding/finalizar` — transacional e idempotente; usado por web/mobile

## Carteiras legadas (`/api/v1/carteiras`)
Compatibilidade. Novos clientes devem usar `/contas-financeiras`.
- `GET /api/v1/carteiras/minhas` (paginado)
- `GET /api/v1/carteiras/{id}`
- `POST /api/v1/carteiras`
- `PUT /api/v1/carteiras/{id}`
- `POST /api/v1/carteiras/{id}/adicionar`
- `POST /api/v1/carteiras/{id}/remover`
- `GET /api/v1/carteiras/minhas/saldo-total`
- `DELETE /api/v1/carteiras/{id}`

## Contas/cartões legados (`/api/v1/contas`)
Compatibilidade de configuração de cartão. Novos clientes devem usar `/cartoes`.
- `GET /api/v1/contas/minhas` (paginado)
- `GET /api/v1/contas/{id}`
- `POST /api/v1/contas`
- `PUT /api/v1/contas/{id}`
- `DELETE /api/v1/contas/{id}`

##  Contas Fixas (`/api/v1/contas-fixas`)
- `GET /api/v1/contas-fixas/minhas` (paginado)
- `GET /api/v1/contas-fixas/{id}`
- `POST /api/v1/contas-fixas`
- `PUT /api/v1/contas-fixas/{id}`
- `PUT /api/v1/contas-fixas/{id}/pagar`
- `PUT /api/v1/contas-fixas/{id}/realizar`
- `PUT /api/v1/contas-fixas/{id}/pular`
- `PUT /api/v1/contas-fixas/{id}/reativar`
- `GET /api/v1/contas-fixas/falhas-pendentes`
- `DELETE /api/v1/contas-fixas/{id}`

##  Metas (`/api/v1/metas`)
- `GET /api/v1/metas/minhas?status=ATIVA|CONCLUIDA|ARQUIVADA` (paginado; sem `status`, usa `ATIVA`)
- `GET /api/v1/metas/{id}`
- `GET /api/v1/metas/{id}/progresso`
- `POST /api/v1/metas`
- `PUT /api/v1/metas/{id}`
- `PUT /api/v1/metas/{id}/adicionar`
- `PUT /api/v1/metas/{id}/remover`
- `DELETE /api/v1/metas/{id}`

O campo canônico da resposta é `status`. O booleano `ativa` permanece apenas por
compatibilidade e está deprecado. Excluir uma meta com valor reservado retorna HTTP 422
(`BUSINESS_ERROR`); resgate o valor para uma carteira antes de excluir. Um valor inválido
em `status` retorna HTTP 400 (`INVALID_PARAMETER`).

##  Parcelas (`/api/v1/parcelas`)
- `GET /api/v1/parcelas/transacao/{transacaoId}` (paginado)
- `GET /api/v1/parcelas/{id}`
- `PUT /api/v1/parcelas/{id}/pagar`
- `PUT /api/v1/parcelas/{id}/despagar`

## Orçamentos (`/api/v1/orcamentos`)
- `GET /api/v1/orcamentos/atual`
- `GET /api/v1/orcamentos`
- `POST /api/v1/orcamentos`

## Relatórios e exportação
- `GET /api/v1/relatorios`
- `GET /api/v1/exportar/transacoes`
- `GET /api/v1/exportar/categorias`
- `GET /api/v1/exportar/contas`
- `GET /api/v1/exportar/completo`

## Importação e anexos

### Pipeline canônico (`/api/v1/importacoes`)
- `POST /api/v1/importacoes` — `multipart/form-data`, parte `file` (CSV ou OFX). Aceita
  `Idempotency-Key`. Devolve `201` com o lote (`status`, `format`, contadores). O arquivo é
  detectado, normalizado e persistido como lote auditável; **nada é lançado no ledger neste passo**.
- `GET /api/v1/importacoes/{id}` — situação do lote do titular; `404` para lote de outro titular.
- `GET /api/v1/importacoes/{id}/registros` — prévia das linhas normalizadas. Parâmetros:
  `status` (`VALID`, `INVALID`, `PENDING_REVIEW`, `DUPLICATE`, `APPROVED`, `COMMITTED`, `REVERSED`),
  `aposLinha` (cursor por `sourceLine`, começa em `0`) e `tamanho` (default `50`, teto `200`).
  A resposta traz `registros` e `proximaLinha` — `null` quando acabou. Paginação é por cursor, não
  por `OFFSET`: um lote chega a dezenas de milhares de linhas.

Respostas de erro do envio:

| Situação | Status | `code` |
|---|---|---|
| Reenvio da mesma `Idempotency-Key` com o mesmo arquivo | `201` | — (mesmo lote) |
| Mesma `Idempotency-Key` com arquivo diferente | `409` | `FINANCIAL_CONFLICT` |
| Arquivo acima do limite | `413` | `UPLOAD_TOO_LARGE` |
| Parte `file` ausente | `400` | `MISSING_REQUEST_PART` |
| Corpo multipart malformado | `400` | `INVALID_MULTIPART` |
| Formato irreconhecível, limite estrutural, hash divergente | `422` | `IMPORT_PARSING_FAILED` (+ `details.failureCode`) |
| Muitos envios ou importação anterior em processamento | `429` | `RATE_LIMITED` (+ header `Retry-After`) |

### Legado e anexos
- `POST /api/v1/importar/csv` — **desativado por padrão** (`410 LEGACY_IMPORT_DISABLED`); grava
  direto no ledger, sem prévia nem reversão.
- `POST /api/v1/anexos/{transacaoId}`
- `GET /api/v1/anexos/{transacaoId}`
- `GET /api/v1/anexos/{id}/download`
- `DELETE /api/v1/anexos/{id}`

## Investimentos (`/api/v1/investimentos`)
- `POST /api/v1/investimentos`
- `GET /api/v1/investimentos` — `Page`, `size<=100`
- `PUT /api/v1/investimentos/{id}`
- `DELETE /api/v1/investimentos/{id}`
- `POST /api/v1/investimentos/{ativoId}/movimentacoes` — aceita `Idempotency-Key`
- `GET /api/v1/investimentos/{ativoId}/movimentacoes` — `Page`, `size<=100`

Reenvio da mesma chave para o mesmo titular devolve a movimentação existente sem duplicar posição,
caixa ou ledger.

---

## Enums Principais
- `TipoTransacao`: `ENTRADA`, `SAIDA`
- `StatusTransacao`: `PENDENTE`, `PAGO`, `ATRASADO`, `CANCELADO`
- `TipoCarteira`: `DINHEIRO`, `CONTA_BANCARIA`, `POUPANCA`
- `TipoConta`: `CREDITO`, `DEBITO`, `DINHEIRO`, `POUPANCA`
- `StatusMeta`: `ATIVA`, `CONCLUIDA`, `ARQUIVADA`
