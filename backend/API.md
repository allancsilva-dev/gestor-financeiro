# 📡 API Reference — Gestor Financeiro

Documentação completa dos endpoints da API REST.

**Base URL:** `http://localhost:8081`  
**Autenticação:** Bearer Token (JWT) no header `Authorization`  
**Versão:** 1.4.0

---

## 🔐 Autenticação

### POST `/api/auth/register`
Cadastra novo usuário.

**Body:**
```json
{
  "nome": "Allan Carvalho",
  "email": "allan@email.com",
  "password": "suasenha"
}
```

**Response `200`:**
```json
{
  "id": 1,
  "nome": "Allan Carvalho",
  "email": "allan@email.com"
}
```

**Errors:** `400` — Email já cadastrado

---

### POST `/api/auth/login`
Realiza login e retorna access token. O refresh token é enviado em cookie HttpOnly.

**Body:**
```json
{
  "email": "allan@email.com",
  "senha": "suasenha"
}
```

**Response `200`:**
```json
{
  "message": "Login realizado com sucesso!",
  "success": true,
  "accessToken": "eyJhbGci...",
  "token": "eyJhbGci...",
  "usuario": {
    "id": 1,
    "nome": "Allan Carvalho",
    "email": "allan@email.com"
  }
}
```

**Set-Cookie:** `refreshToken=...; HttpOnly; Path=/api/auth; SameSite=Lax`

**Errors:** `401` — Email ou senha incorretos

---

### POST `/api/auth/refresh-token`
Renova o access token usando o refresh token enviado por cookie HttpOnly.

O refresh token é rotacionado a cada renovação (token anterior é revogado).

**Body:** `{}` (vazio)

**Response `200`:**
```json
{
  "accessToken": "eyJhbGci...",
  "token": "eyJhbGci..."
}
```

**Set-Cookie:** novo `refreshToken=...` HttpOnly

**Errors:** `400` — Refresh token não fornecido | `401` — Token inválido ou expirado

---

### POST `/api/auth/logout`
Revoga o refresh token do dispositivo atual enviado por cookie HttpOnly.

**Body:** `{}` (vazio)

**Response `200`:**
```json
{
  "message": "Logout realizado com sucesso"
}
```

**Set-Cookie:** `refreshToken=` com `Max-Age=0` (limpa cookie)

---

### POST `/api/auth/logout-all`
Revoga todos os refresh tokens do usuário (todos os dispositivos).

**Headers:** `Authorization: Bearer {accessToken}`

**Response `200`:**
```json
{
  "message": "Logout realizado em todos os dispositivos"
}
```

**Set-Cookie:** `refreshToken=` com `Max-Age=0` (limpa cookie do dispositivo atual)

---

### POST `/api/auth/forgot-password`
Envia email com link de recuperação de senha.

**Body:**
```json
{
  "email": "allan@email.com"
}
```

**Response `200`:** `"Se o email existir, você receberá um link de recuperação."`

> ⚠️ Sempre retorna 200, mesmo se o email não existir (segurança contra enumeração).

---

### POST `/api/auth/reset-password`
Redefine a senha usando o token recebido por email.

**Body:**
```json
{
  "token": "uuid-token-do-email",
  "novaSenha": "novaSenha123"
}
```

**Response `200`:** `"Senha alterada com sucesso!"`

**Errors:** `400` — Token inválido / expirado / já utilizado

---

### GET `/api/auth/validate-token?token={token}`
Valida se um token de reset de senha ainda é válido.

**Response `200`:** `"Token válido!"`

**Errors:** `400` — Token inválido / expirado / já utilizado

---

## 👤 Usuário

### GET `/api/usuarios/me`
Retorna os dados do usuário autenticado.

**Headers:** `Authorization: Bearer {accessToken}`

**Response `200`:**
```json
{
  "id": 1,
  "nome": "Allan Carvalho",
  "email": "allan@email.com"
}
```

**Errors:** `401` — Token inválido ou ausente

---

## 📊 Dashboard

> Todos os endpoints de dashboard exigem autenticação. O usuário é identificado pelo token, não por ID na URL.

### GET `/api/dashboard/resumo`
Retorna resumo financeiro geral: saldo total, entradas e saídas do mês.

**Headers:** `Authorization: Bearer {accessToken}`

**Response `200`:** `Map<String, Object>` com dados financeiros consolidados.

---

### GET `/api/dashboard/gastos-por-categoria`
Retorna dados para o gráfico de pizza com gastos agrupados por categoria.

**Headers:** `Authorization: Bearer {accessToken}`

---

### GET `/api/dashboard/evolucao-mensal`
Retorna dados para o gráfico de linhas com evolução dos últimos 6 meses.

**Headers:** `Authorization: Bearer {accessToken}`

---

### GET `/api/dashboard/comparacao-mensal`
Retorna dados para o gráfico de barras com comparação mensal entre entradas e saídas.

**Headers:** `Authorization: Bearer {accessToken}`

---

## 💸 Transações

### GET `/api/transacoes/usuario/{usuarioId}`
Lista todas as transações do usuário.

**Response `200`:** `Array<Transacao>`

---

### GET `/api/transacoes/periodo`
Lista transações de um usuário em um período específico.

**Query Params:**
- `usuarioId` (Long) — ID do usuário
- `inicio` (Date ISO) — Data inicial `YYYY-MM-DD`
- `fim` (Date ISO) — Data final `YYYY-MM-DD`

**Exemplo:** `GET /api/transacoes/periodo?usuarioId=1&inicio=2025-11-01&fim=2025-11-30`

---

### GET `/api/transacoes/{id}`
Busca uma transação pelo ID.

---

### POST `/api/transacoes`
Cria nova transação.

**Body:** `Transacao` (objeto completo com categoria, valor, data, tipo, etc.)

---

### PUT `/api/transacoes/{id}`
Atualiza uma transação existente.

**Body:** `Transacao` atualizado

---

### DELETE `/api/transacoes/{id}`
Remove uma transação.

**Response `204`** No Content

---

## 🏷️ Categorias

### GET `/api/categorias/minhas`
Lista categorias do usuário autenticado.

**Headers:** `Authorization: Bearer {accessToken}`

**Response `200`:** `Array<Categoria>`

---

### GET `/api/categorias/usuario/{usuarioId}`
Lista categorias por ID de usuário.

---

### POST `/api/categorias`
Cria nova categoria.

**Body:**
```json
{
  "nome": "Alimentação",
  "cor": "#FF5733",
  "icone": "🍔",
  "valorEsperado": 500.00
}
```

---

### PUT `/api/categorias/{id}`
Atualiza uma categoria.

**Body:**
```json
{
  "nome": "Alimentação",
  "cor": "#FF5733",
  "icone": "🍔",
  "valorEsperado": 600.00
}
```

---

### DELETE `/api/categorias/{id}`
Inativa (soft delete) uma categoria.

**Response `204`** No Content

---

## 👛 Carteiras

### GET `/api/carteiras/usuario/{usuarioId}`
Lista carteiras do usuário.

---

### GET `/api/carteiras/{id}`
Busca carteira pelo ID.

---

### GET `/api/carteiras/usuario/{usuarioId}/saldo-total`
Retorna o saldo total consolidado de todas as carteiras do usuário.

**Response `200`:** `BigDecimal`

---

### POST `/api/carteiras`
Cria nova carteira.

**Body:** `Carteira`

---

### PUT `/api/carteiras/{id}`
Atualiza uma carteira.

---

### POST `/api/carteiras/{id}/adicionar`
Adiciona valor ao saldo da carteira.

**Body:**
```json
{ "valor": 500.00 }
```

---

### POST `/api/carteiras/{id}/remover`
Remove valor do saldo da carteira.

**Body:**
```json
{ "valor": 100.00 }
```

---

### DELETE `/api/carteiras/{id}`
Remove uma carteira.

**Response `204`** No Content

---

## 🏦 Contas

### GET `/api/contas/usuario/{usuarioId}`
Lista contas do usuário.

---

### GET `/api/contas/{id}`
Busca conta pelo ID.

---

### POST `/api/contas`
Cria nova conta.

**Body:** `Conta`

---

### PUT `/api/contas/{id}`
Atualiza uma conta.

---

### DELETE `/api/contas/{id}`
Remove uma conta.

**Response `204`** No Content

---

## 📋 Contas Fixas

### GET `/api/contas-fixas/usuario/{usuarioId}`
Lista contas fixas do usuário.

---

### GET `/api/contas-fixas/{id}`
Busca conta fixa pelo ID.

---

### POST `/api/contas-fixas`
Cria nova conta fixa.

**Body:** `ContaFixa`

---

### PUT `/api/contas-fixas/{id}`
Atualiza uma conta fixa.

---

### PUT `/api/contas-fixas/{id}/pagar`
Marca conta fixa como paga.

**Body:**
```json
{ "valorPago": 350.00 }
```

---

### DELETE `/api/contas-fixas/{id}`
Desativa uma conta fixa.

**Response `200`** No Content

---

## 🎯 Metas

### GET `/api/metas/usuario/{usuarioId}`
Lista metas do usuário.

---

### GET `/api/metas/{id}`
Busca meta pelo ID.

---

### GET `/api/metas/{id}/progresso`
Calcula o progresso de uma meta.

**Response `200`:**
```json
{
  "metaId": 1,
  "valorTotal": 5000.00,
  "valorReservado": 1500.00,
  "valorRestante": 3500.00,
  "progresso": 30.00
}
```

---

### POST `/api/metas`
Cria nova meta.

**Body:** `Meta`

---

### PUT `/api/metas/{id}`
Atualiza uma meta.

---

### PUT `/api/metas/{id}/adicionar`
Adiciona valor reservado à meta.

**Body:**
```json
{ "valor": 200.00 }
```

---

### PUT `/api/metas/{id}/remover`
Remove valor reservado da meta.

**Body:**
```json
{ "valor": 100.00 }
```

---

### DELETE `/api/metas/{id}`
Remove uma meta.

**Response `204`** No Content

---

## 🔢 Parcelas

### GET `/api/parcelas/transacao/{transacaoId}`
Lista todas as parcelas de uma transação parcelada.

---

### GET `/api/parcelas/{id}`
Busca parcela pelo ID.

---

### PUT `/api/parcelas/{id}/pagar`
Marca uma parcela como paga.

---

### PUT `/api/parcelas/{id}/despagar`
Reverte uma parcela para status pendente.

---

## 🔑 Autenticação nos Endpoints

A maioria dos endpoints exige autenticação via JWT. Inclua o header em todas as requisições protegidas:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

O access token expira em **15 minutos**. Use `/api/auth/refresh-token` para renová-lo automaticamente.

---

## ⚙️ Enums

| Enum | Valores |
|------|---------|
| `TipoTransacao` | `ENTRADA` — dinheiro que entra (salário, freelance) / `SAIDA` — dinheiro que sai (compras, contas) |
| `TipoCarteira` | `DINHEIRO` / `CONTA_BANCARIA` / `POUPANCA` |
| `TipoConta` | `CREDITO` / `DEBITO` / `DINHEIRO` / `POUPANCA` |
| `StatusPagamento` | `PAGO` / `PENDENTE` / `ATRASADO` / `CANCELADO` |

---

**Última atualização:** Fevereiro 2026  
**Mantido por:** Zero (Allan Carvalho)
