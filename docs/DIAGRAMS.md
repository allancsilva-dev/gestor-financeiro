# Diagrams — Gestor Financeiro

Registro de diagramas do sistema. Mantido pelo `docs-reporter`.

**Ultima atualizacao:** 2026-07-06

---

## Status atual

O projeto possui um arquivo Draw.io multi-page:

- `docs/diagrams/sistema-completo.drawio`

Paginas incluidas:

1. `01 Arquitetura`
2. `02 Auth flow`
3. `03 Ownership`
4. `04 ERD`

## Diagramas criados

### 1. Arquitetura

Representado em `docs/diagrams/sistema-completo.drawio`, pagina `01 Arquitetura`.

Referencia textual:

```
┌─────────────────────────────────────────────────┐
│                   Client Layer                    │
│  ┌──────────────┐  ┌──────────────────────────┐ │
│  │ Frontend Web  │  │  Mobile (React Native)   │ │
│  │ (React+Vite)  │  │  (Expo)                   │ │
│  └──────┬───────┘  └────────────┬─────────────┘ │
└─────────┼───────────────────────┼────────────────┘
          │      HTTPS + JWT      │
┌─────────▼───────────────────────▼────────────────┐
│                   API Layer                       │
│  ┌──────────────────────────────────────────────┐│
│  │         Spring Boot 3.5.7 (Java 17)           ││
│  │  ┌──────────┐  ┌──────────┐  ┌────────────┐  ││
│  │  │Security  │  │Controllers│  │ Exception   │  ││
│  │  │(JWT+BCrypt│  │(REST)    │  │ Handler     │  ││
│  │  │+RateLimit)│  │           │  │             │  ││
│  │  └──────────┘  └──────────┘  └────────────┘  ││
│  │  ┌──────────────────────────────────────────┐ ││
│  │  │           Service Layer                   │ ││
│  │  │  (Regras de negocio, @Transactional)      │ ││
│  │  └──────────────────────────────────────────┘ ││
│  │  ┌──────────────────────────────────────────┐ ││
│  │  │         Repository Layer                  │ ││
│  │  │  (Spring Data JPA, @EntityGraph)          │ ││
│  │  └──────────────────────────────────────────┘ ││
│  └──────────────────────────────────────────────┘│
└────────────────────┬─────────────────────────────┘
                     │ SQL
┌────────────────────▼─────────────────────────────┐
│                Data Layer                         │
│  ┌──────────────────────────────────────────────┐│
│  │           PostgreSQL 17+                      ││
│  │  ┌────────┐ ┌──────────┐ ┌───────────────┐  ││
│  │  │usuarios│ │transacoes│ │refresh_tokens │  ││
│  │  └────────┘ └──────────┘ └───────────────┘  ││
│  │  ┌────────┐ ┌──────────┐ ┌───────────────┐  ││
│  │  │categor │ │carteiras │ │contas_fixas   │  ││
│  │  │ias     │ │          │ │               │  ││
│  │  └────────┘ └──────────┘ └───────────────┘  ││
│  │  ┌────────┐ ┌──────────┐ ┌───────────────┐  ││
│  │  │contas  │ │metas     │ │parcelas       │  ││
│  │  └────────┘ └──────────┘ └───────────────┘  ││
│  └──────────────────────────────────────────────┘│
└──────────────────────────────────────────────────┘
```

### 2. Fluxo de autenticacao

Representado em `docs/diagrams/sistema-completo.drawio`, pagina `02 Auth flow`.

```
Usuario ──► Login (email/senha)
               │
               ▼
         AuthController
               │
               ▼
      AuthenticationManager
      (BCryptPasswordEncoder)
               │
          ┌────▼────┐
          │ Sucesso? │
          └────┬────┘
          sim  │    nao
               │     └──► Erro generico
               ▼          "Email ou senha incorretos"
         JwtUtil.generateToken()
         (access token HS256)
               │
               ▼
         RefreshTokenService
         .criarRefreshToken()
         (UUID v4, 7 dias)
               │
               ▼
         Resposta HTTP:
         { accessToken, usuario }
         + Set-Cookie: refreshToken
           (HttpOnly, SameSite=Lax)
               │
               ▼
         Frontend armazena:
         - accessToken em memoria (variavel JS)
         - refreshToken no cookie (automatico)
               │
               ▼
         Interceptor Axios:
         - Adiciona Authorization: Bearer
         - Detecta 401 → refresh automatico
         - Enfileira requisicoes concorrentes
```

### 3. Fluxo single-tenant / ownership

Representado em `docs/diagrams/sistema-completo.drawio`, pagina `03 Ownership`.

```
Request: GET /api/v1/transacoes/42
Authorization: Bearer eyJ... (subject: alice@email.com)
               │
               ▼
      JwtAuthenticationFilter
      (extrai subject = email do JWT)
               │
               ▼
      SecurityContextHolder
      (seta Authentication com email)
               │
               ▼
      AuthenticatedUserService
      .getAuthenticatedUserId()
      (busca Usuario por email no banco)
      retorna userId = 1 (Alice)
               │
               ▼
      TransacaoController
      .buscarPorId(42)
               │
               ▼
      TransacaoService
      .buscarPorIdDoUsuario(42, userId=1)
               │
          ┌────▼────┐
          │ transacao│
          │.usuario  │──► Se diferente de 1,
          │.id = 1?  │    lança UnauthorizedAccessException
          └────┬────┘
          sim  │
               ▼
         Retorna TransacaoResponseDto
```

### 4. Entidades e relacionamentos

Representado em `docs/diagrams/sistema-completo.drawio`, pagina `04 ERD`.

```
Usuario (1)
  │
  ├── (1:N) Categoria
  │     ├── id, nome, cor, icone, valorEsperado, valorGasto, ativo
  │     └── (1:N) Transacao, ContaFixa
  │
  ├── (1:N) Carteira
  │     └── id, nome, tipo (DINHEIRO|CONTA_BANCARIA|POUPANCA), saldo, banco
  │
  ├── (1:N) Conta
  │     └── id, nome, tipo (CREDITO|DEBITO|DINHEIRO|POUPANCA), limiteTotal, cor
  │         └── (1:N) Transacao
  │
  ├── (1:N) ContaFixa
  │     └── id, nome, valorPlanejado, valorReal, diaVencimento, status, recorrente
  │         └── (N:1) Categoria (opcional)
  │
  ├── (1:N) Meta
  │     └── id, nome, valorTotal, valorReservado, valorMensal, dataPrevista, ativa
  │
  ├── (1:N) Transacao
  │     ├── id, descricao, valorTotal, tipo (ENTRADA|SAIDA), data, status, parcelado
  │     ├── (N:1) Categoria
  │     ├── (N:1) Conta (opcional)
  │     └── (1:N) Parcela
  │           └── id, numeroParcela, valor, dataVencimento, status (PAGO|PENDENTE|ATRASADO|CANCELADO)
  │
  ├── (1:N) RefreshToken
  │     └── id, token (UUID, unique), dataExpiracao, dataCriacao, revogado
  │
  └── (1:N) PasswordResetToken
        └── id, token, dataExpiracao, usado
```

### 5. Proximos passos opcionais

1. Exportar PNG/SVG de cada pagina do `.drawio` para preview rapido em PRs.
2. Atualizar o diagrama sempre que endpoints, entidades, auth ou infra mudarem.
3. Separar paginas em arquivos `.drawio` individuais se o time preferir revisao por diff menor.

---

> Este arquivo e mantido pelo `docs-reporter`. Diagramas sao documentacao viva — devem ser atualizados
> sempre que a topologia do sistema mudar.
