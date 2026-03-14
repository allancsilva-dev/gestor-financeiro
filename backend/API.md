#  API Reference  Gestor Financeiro

Documentação da API REST atualizada para o estado pós-fases 1-5.

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

### GET `/api/auth/validate-token?token=...`
Valida token de recuperação.

---

##  Usuário (`/api/v1/usuarios`)

### GET `/api/v1/usuarios/me`
Retorna usuário autenticado.

---

##  Dashboard (`/api/v1/dashboard`)
- `GET /api/v1/dashboard/resumo`
- `GET /api/v1/dashboard/gastos-por-categoria`
- `GET /api/v1/dashboard/evolucao-mensal`
- `GET /api/v1/dashboard/comparacao-mensal`

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
- `GET /api/v1/transacoes/{id}`
- `POST /api/v1/transacoes`
- `PUT /api/v1/transacoes/{id}`
- `DELETE /api/v1/transacoes/{id}`

##  Categorias (`/api/v1/categorias`)
- `GET /api/v1/categorias/minhas` (paginado)
- `GET /api/v1/categorias/{id}`
- `POST /api/v1/categorias`
- `PUT /api/v1/categorias/{id}`
- `DELETE /api/v1/categorias/{id}`

##  Carteiras (`/api/v1/carteiras`)
- `GET /api/v1/carteiras/minhas` (paginado)
- `GET /api/v1/carteiras/{id}`
- `POST /api/v1/carteiras`
- `PUT /api/v1/carteiras/{id}`
- `POST /api/v1/carteiras/{id}/adicionar`
- `POST /api/v1/carteiras/{id}/remover`
- `GET /api/v1/carteiras/minhas/saldo-total`
- `DELETE /api/v1/carteiras/{id}`

##  Contas (`/api/v1/contas`)
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
- `DELETE /api/v1/contas-fixas/{id}`

##  Metas (`/api/v1/metas`)
- `GET /api/v1/metas/minhas` (paginado)
- `GET /api/v1/metas/{id}`
- `GET /api/v1/metas/{id}/progresso`
- `POST /api/v1/metas`
- `PUT /api/v1/metas/{id}`
- `PUT /api/v1/metas/{id}/adicionar`
- `PUT /api/v1/metas/{id}/remover`
- `DELETE /api/v1/metas/{id}`

##  Parcelas (`/api/v1/parcelas`)
- `GET /api/v1/parcelas/transacao/{transacaoId}` (paginado)
- `GET /api/v1/parcelas/{id}`
- `PUT /api/v1/parcelas/{id}/pagar`
- `PUT /api/v1/parcelas/{id}/despagar`

---

## Enums Principais
- `TipoTransacao`: `ENTRADA`, `SAIDA`
- `StatusTransacao`: `PENDENTE`, `PAGO`, `ATRASADO`, `CANCELADO`
- `TipoCarteira`: `DINHEIRO`, `CONTA_BANCARIA`, `POUPANCA`
- `TipoConta`: `CREDITO`, `DEBITO`, `DINHEIRO`, `POUPANCA`
