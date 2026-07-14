# Diagrams — Gestor Financeiro

Registro de diagramas do sistema. Mantido pelo `docs-reporter`.

**Ultima atualizacao:** 2026-07-14 (hardening pre-producao: item 7 de "Proximos passos opcionais" registra a pendencia de diagrama de topologia de rede/proxy e fronteira de confianca do X-Forwarded-For, PROB-0066)

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
│  │           Flyway + ddl-auto=validate          ││
│  │           Testcontainers em PR-LEDGER-01      ││
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

> **Pendencia (2026-07-09, BUG-0013):** o diagrama acima descreve apenas o fluxo web. Desde
> 2026-07-09 o mobile tambem tem interceptor de refresh automatico simetrico ao do web (promise
> deduplicada, retry da request original), e as respostas de login/refresh passaram a incluir
> `csrfToken` no corpo (alem do cookie), pois clientes React Native nao leem cookies para o
> double-submit. A pagina `02 Auth flow` do `.drawio` ainda nao foi atualizada para refletir isso —
> atualizar quando o arquivo `.drawio` for revisado.

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
  │     ├── id, descricao, valorTotal, tipo (ENTRADA|SAIDA), data, status, parcelado, ativa (soft-delete, V13)
  │     ├── (N:1) Categoria
  │     ├── (N:1) Conta (opcional)
  │     ├── (N:1) Carteira (opcional, V13 — so movimenta Ledger se presente, ver BUG-0011/BUG-0012)
  │     └── (1:N) Parcela
  │           └── id, numeroParcela, valor, dataVencimento, status (PAGO|PENDENTE|ATRASADO|CANCELADO)
  │
  ├── (1:N) MovimentoCarteira (Ledger, V11 — nao representado no diagrama .drawio atual)
  │     └── id, carteira_id, tipo (ENTRADA|SAIDA|AJUSTE|BACKFILL), valorAssinado, moeda (CHAR(3)), criadoEm
  │
  ├── (1:N) RefreshToken
  │     └── id, token (UUID, unique), dataExpiracao, dataCriacao, revogado
  │
  └── (1:N) PasswordResetToken
        └── id, token, dataExpiracao, usado
```

> **Pendencia (ERD):** a pagina `04 ERD` do `.drawio` ainda nao inclui a tabela `movimentos_carteira`
> (Ledger, introduzida em `V11__movimento_carteira.sql`) nem os campos `carteira_id`/`ativa` de
> `Transacao` (introduzidos em `V13__transacao_carteira.sql`). Atualizar na proxima revisao do
> arquivo `.drawio`.

> **Pendencia (ERD) — Fatura de cartao, 2026-07-09:** o ERD tambem nao representa `FaturaCartao`
> (`conta_id`, `mes`, `ano`, `valorTotal`, `dataFechamento`, `dataVencimento`, `status`
> ABERTA|FECHADA|VENCIDA|PAGA — introduzida na migration V17) nem `FaturaLancamento`
> (`fatura_id`, `transacao_id`, `descricao`, `valor`, `dataCompra`, `parcelaNumero`, `totalParcelas`,
> `tipo` COMPRA|AJUSTE|ESTORNO — coluna `tipo` introduzida na migration `V18__fatura_lancamento_tipo.sql`,
> 2026-07-09, mesma sessao). A revisao do fluxo de compra no cartao (ver
> `docs/REVIEW_REPORTS/2026-07-09_backend_review_fatura-cartao-fluxo.md`, `docs/BUGFIX_LOG.md`
> BUG-0017..BUG-0026) confirmou que `Transacao` parcelada no cartao gera registros em **duas** estruturas
> paralelas: `Parcela` (legada, 1:N a partir de `Transacao`, vencimento comecando 1 mes apos a compra, sem
> conceito de ajuste/estorno) e `FaturaLancamento` (atual, agrupado por `FaturaCartao`, fonte da verdade do
> valor exibido/pago da fatura desde 2026-07-09, com suporte a lancamentos de valor negativo/credito desde a
> segunda rodada da mesma sessao). Essa redundancia deve ficar explicita no proximo diagrama de entidades —
> ver BACKLOG-0050 (avaliar aposentadoria de `Parcela` para compras de cartao).
>
> **Pendencia adicional (fluxo) — Fatura paga imutavel, 2026-07-09:** o modelo de compensacao
> (`ressincronizarCompraCartao`/`cancelarCompraCartao` gerando lancamentos `AJUSTE`/`ESTORNO` na proxima
> fatura em aberto quando a fatura original ja esta `PAGA`, ver PROB-0044) tambem nao esta representado em
> nenhum diagrama existente. Candidato ao diagrama de fluxo sugerido no item 4 de "Proximos passos
> opcionais" abaixo.

### 5. Proximos passos opcionais

1. Exportar PNG/SVG de cada pagina do `.drawio` para preview rapido em PRs.
2. Atualizar o diagrama sempre que endpoints, entidades, auth ou infra mudarem.
3. Separar paginas em arquivos `.drawio` individuais se o time preferir revisao por diff menor.
4. **Sugerido em 2026-07-09:** criar diagrama de fluxo textual/`.drawio` especifico para "Compra no
   cartao de credito → Fatura", cobrindo: `Transacao` (SAIDA, `conta` cartao) → `FaturaService.registrarCompraCartao`
   → `faturaDisponivelParaLancamento` (rola competencia se fatura ja paga) → `FaturaLancamento` (N parcelas,
   ultima absorve arredondamento) + `Parcela` legada (redundante) → `Conta.valorGasto` incrementado →
   `pagarFatura` (soma de lancamentos como fonte da verdade) → `Conta.valorGasto` decrementado. Objetivo:
   tornar visivel a duplicidade `Parcela`/`FaturaLancamento` antes de decidir sobre BACKLOG-0050.
5. **Sugerido em 2026-07-09 (segunda rodada, mesma sessao):** estender o diagrama do item 4 com o ramo de
   edicao/cancelamento apos fatura paga: `TransacaoService.atualizar`/`deletar` (compra de cartao) →
   `FaturaService.ressincronizarCompraCartao`/`cancelarCompraCartao` → separa lancamentos por
   fatura-aberta (removidos/recriados) vs. fatura-paga (imutavel, soma calculada) → lancamento
   `AJUSTE`/`ESTORNO` (valor podendo ser negativo) criado na proxima fatura em aberto via
   `faturaDisponivelParaLancamento` → `ajustarLimiteUtilizado` atualiza `Conta.valorGasto` (podendo ficar
   negativo/credito). Objetivo: tornar visivel o principio "fatura paga e imutavel, sempre compensa" antes
   de qualquer decisao sobre BACKLOG-0054 (rollover de credito entre faturas). **Atualizacao 2026-07-11:**
   BACKLOG-0054 e BACKLOG-0059 foram decididos e implementados (BUG-0053) — ver item 6 abaixo para o
   diagrama sugerido do fluxo de rollover resultante.
6. **Sugerido em 2026-07-11 (BUG-0053):** criar diagrama de fluxo textual/`.drawio` para o rollover de
   credito/saldo devedor de fatura: `FaturaService.buscarAtual`/`buscarPorMes`/`criarOuBuscarFatura`
   (materializa fatura de competencia M) → `liquidarFaturaAnterior` (recursivo, M-1, M-2, ... ate
   competencia anterior inexistente ou teto de 24 meses) → para cada fatura anterior ja fechada:
   total `<= 0` → **R1** gera `FaturaLancamento(CREDITO_ANTERIOR, valor negativo)` na proxima fatura
   aberta + marca origem `PAGA` (`dataPagamento = dataFechamento`); total `> 0` e `valorPago < total` →
   **R2** gera `FaturaLancamento(SALDO_DEVEDOR_ANTERIOR, valor positivo)` na proxima fatura aberta →
   protecao dupla contra duplicacao: guard `existsByFaturaOrigemId` (codigo) + lock pessimista
   (`findWithLockByIdAndUsuarioId`) + unique index parcial `ux_fatura_rollover_origem_tipo` (banco,
   `V25__fatura_rollover.sql`). Objetivo: tornar visivel que o rollover e "lazy" (disparado na leitura,
   sem endpoint de fechamento nem scheduler) e a cadeia recursiva entre faturas do mesmo cartao.
7. **Sugerido em 2026-07-14 (PROB-0066/BUG-0059, hardening pre-producao):** criar diagrama de topologia
   de rede/proxy mostrando a cadeia de resolucao de IP e a fronteira de confianca do `X-Forwarded-For`
   nas duas variantes de deploy documentadas em `deploy/vps/`:
   - **Standalone (`nginx.conf.template`, 1 hop):** `Cliente → nginx (sobrescreve X-Forwarded-For com
     $remote_addr) → API (Tomcat RemoteIpValve, forward-headers-strategy=native, internal-proxies =
     loopback + faixas privadas Docker)`.
   - **Atras do Nginx Proxy Manager (`nginx.npm.conf`, 2 hops):** `Cliente → NPM (anexa seu proprio
     $remote_addr ao X-Forwarded-For recebido — premissa de configuracao documentada em
     deploy/vps/README.md, nao verificavel pelo codigo do repositorio) → nginx interno (append-only) →
     API (RemoteIpValve resolve o IP a partir da lista de proxies internos confiaveis)`.
   - Rede Docker: `docker-compose.production.yml` isola a API numa rede interna dedicada `web<->API`,
     removida da rede `proxy` — o NPM so alcanca o container `web`, nunca a API diretamente.
   Objetivo: tornar visivel, para qualquer alteracao futura de deploy/proxy, qual componente e
   responsavel por normalizar o `X-Forwarded-For` em cada topologia — se essa responsabilidade mudar de
   lugar sem atualizar o diagrama, o contorno de rate limit corrigido em PROB-0066 pode voltar a existir
   silenciosamente. Pendente de materializacao em `.drawio`; ver BACKLOG-0080 para o gate de validacao
   real (`nginx -t`, redes, smoke em staging) que precede qualquer promocao para producao.

---

> Este arquivo e mantido pelo `docs-reporter`. Diagramas sao documentacao viva — devem ser atualizados
> sempre que a topologia do sistema mudar.
