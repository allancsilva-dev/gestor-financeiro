# Implementacao de Hardening Pre-Producao (P0+P1)

**Arquivo:** `2026-07-14_full-system_implementation_pre-production-hardening.md`
**Data:** 2026-07-14
**Branch:** `main`
**Commits de implementacao:** `5c08ce0` (auth), `0d1e0c0` (consistencia financeira/migration), `c959dfc` (deploy/proxy)
**Escopo:** backend e frontend web. Mobile intocado nesta rodada.

---

## Objetivo

Registrar a implementacao dos achados P0 (3 itens) e P1 (4 itens) de uma auditoria abrangente
(security-auditor, lgpd-auditor, quality-reviewer, database-engineer) sobre a stack de deploy VPS/nginx,
o fluxo de pagamento de parcelas, exclusao de carteiras e o endpoint de validacao de token de reset de
senha, alem de registrar duas recomendacoes da mesma auditoria que foram avaliadas e explicitamente
rejeitadas.

## Escopo verificado

- Rate limit de autenticacao (`LoginRateLimitFilter`, `AuthController`) e a cadeia de resolucao de IP via
  `X-Forwarded-For` na stack de deploy (Spring `forward-headers-strategy`, Tomcat `RemoteIpValve`, nginx
  standalone e nginx atras de Nginx Proxy Manager, redes Docker Compose);
- fluxo de pagamento de parcela (`ParcelaService.marcarComoPaga`) sob reenvio/concorrencia;
- fluxo de exclusao de carteira (`CarteiraService.deletar`) contra o superset real de origens de
  `MovimentoCarteira`;
- indices de suporte em `movimentos_carteira.carteira_id` e `refresh_tokens.usuario_id`;
- headers de seguranca HTTP nas rotas fora de `/api/**` (SPA servido por nginx);
- contrato do endpoint `validate-token` de reset de senha (GET com query string → POST com corpo);
- teto de validacao em `TransacaoRequest.totalParcelas`;
- duas recomendacoes de auditoria nao implementadas: `CHECK contas.valor_gasto >= 0` e piso
  zero + lock pessimista em `ContaService.removerGasto`.

## Arquivos lidos

- `backend/src/main/resources/application-vps.properties`
- `backend/src/main/java/com/gestor/financeiro/config/LoginRateLimitFilter.java`
- `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`
- `backend/src/main/java/com/gestor/financeiro/dto/TransacaoRequest.java`
- `backend/src/main/java/com/gestor/financeiro/dto/ValidateTokenRequest.java` (novo)
- `backend/src/main/java/com/gestor/financeiro/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/gestor/financeiro/model/Parcela.java`
- `backend/src/main/java/com/gestor/financeiro/repository/MovimentoCarteiraRepository.java`
- `backend/src/main/java/com/gestor/financeiro/service/CarteiraService.java`
- `backend/src/main/java/com/gestor/financeiro/service/ParcelaService.java`
- `backend/src/main/resources/db/migration/V28__pre_production_hardening.sql` (novo)
- `backend/src/test/java/com/gestor/financeiro/AuthControllerTest.java`
- `backend/src/test/java/com/gestor/financeiro/CarteiraControllerTest.java`
- `backend/src/test/java/com/gestor/financeiro/ParcelaServiceTest.java`
- `deploy/vps/README.md`
- `deploy/vps/nginx.conf.template`
- `deploy/vps/nginx.npm.conf`
- `docker-compose.production.yml`
- `docker-compose.vps.yml`
- `frontend/src/pages/ResetPassword.tsx`
- `docs/SYSTEM_OVERVIEW.md` (secoes de rollover de fatura e limitacoes conhecidas, para embasar a
  rejeicao das duas recomendacoes de banco)

## Comandos executados (por este agente, `docs-reporter`)

| Comando | Resultado |
|---|---|
| `git status` / `git log --oneline` | branch `main`; implementacao separada em 3 commits rastreaveis |
| `git diff --stat` (por grupo de arquivos) | confirma extensao das mudancas em cada area (rate limit/nginx, parcela/carteira, testes) |
| leitura de `V28__pre_production_hardening.sql` | confirma coluna `version` em `parcelas` e os 2 indices novos |
| `grep`/leitura de `docs/*.md` | confirma proximos IDs livres em PROBLEM_LEDGER, BUGFIX_LOG e BACKLOG |
| `./mvnw -q verify` | 155/155 testes backend PASS |
| `npm run test -- --run`, `npm run lint`, `npm run build` | 15/15 testes PASS; lint 0 erros (88 warnings preexistentes); build PASS |
| `scripts/verify-postgres-migrations.sh` | PASS contra PostgreSQL real via Docker, incluindo V28 |

As suites backend/frontend e a migration V28 foram reexecutadas nesta rodada antes dos commits.

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | HIGH | Rate limit de login/forgot-password/register contornavel via `X-Forwarded-For` forjado | `PROB-0066`; `application-vps.properties`, `docker-compose.production.yml`, `docker-compose.vps.yml` |
| 2 | HIGH | Pagamento de parcela duplicava debito na carteira sob reenvio/concorrencia | `PROB-0067`; `ParcelaService.java:marcarComoPaga` |
| 3 | HIGH | Exclusao de carteira em uso normal retornava HTTP 500 (FK RESTRICT) | `PROB-0068`; `CarteiraService.java:deletar` |
| 4 | LOW | Indices ausentes em `movimentos_carteira.carteira_id` e `refresh_tokens.usuario_id` | `PROB-0069`; `V28__pre_production_hardening.sql` |
| 5 | MEDIUM | SPA (fora de `/api/**`) sem headers de seguranca (HSTS, X-Frame-Options, CSP, etc.) | `PROB-0070`; `nginx.conf.template`, `nginx.npm.conf` |
| 6 | MEDIUM | Token de reset de senha trafegava na query string (`GET /api/auth/validate-token?token=...`) | `PROB-0071`; `AuthController.java`, `ValidateTokenRequest.java` |
| 7 | LOW | `TransacaoRequest.totalParcelas` sem teto de validacao | `PROB-0072`; `TransacaoRequest.java` |
| 8 | MEDIUM | Recomendacao de auditoria: `CHECK contas.valor_gasto >= 0` — **rejeitada** | `PROB-0073`; conflita com V20:5-8 e regra de produto R1 do rollover de fatura |
| 9 | MEDIUM | Recomendacao de auditoria: piso zero + lock pessimista em `removerGasto` — **rejeitada** | `PROB-0074`; `Conta` ja tem `@Version`; piso engoliria credito legitimo |

## O que foi corrigido

Achados 1 a 7 foram corrigidos e commitados em `main` (`5c08ce0`, `0d1e0c0`, `c959dfc`).
Detalhe tecnico completo de cada correcao esta em `docs/PROBLEM_LEDGER.md`
(PROB-0066 a PROB-0072) e `docs/BUGFIX_LOG.md` (BUG-0059 a BUG-0065). Resumo:

- **P0-1 (achado 1):** `forward-headers-strategy` framework→native + `RemoteIpValve` + env var nos dois
  compose files + nginx normalizando `X-Forwarded-For` + rede Docker interna `web<->API` isolando a API
  do acesso direto do NPM.
- **P0-2 (achado 2):** guard de estado (`PAGO` → no-op) + `@Version` em `Parcela` (migration V28).
- **P0-3 (achado 3):** `existsByCarteiraId` genérico substitui checagem restrita por origem.
- **P1-4 (achado 4):** 2 indices novos na mesma migration V28.
- **P1-5 (achado 5):** headers de seguranca nos dois nginx configs.
- **P1-6 (achado 6):** endpoint `validate-token` migrado de GET+query para POST+body; GET agora 405.
- **P1-7 (achado 7):** `@Max(120)` em `totalParcelas`.

Achados 8 e 9 foram **avaliados e conscientemente nao implementados**, com justificativa tecnica
registrada em `PROB-0073` e `PROB-0074`.

## O que ficou pendente

- Gates de deploy real: `nginx -t` nos dois configs; recriacao das redes do
  `docker-compose.production.yml` (rede interna e nova); confirmacao de que o Proxy Host do Nginx Proxy
  Manager aponta para `GestorFinanceiro-Web`; smoke em staging validando que `X-Forwarded-For` forjado nao
  muda o bucket de rate limit e que o cookie `Secure` continua funcionando sem loop de redirect apos a
  troca framework→native. Registrado como **BACKLOG-0080** (P0).
- 5 itens P2 fora deste release, registrados como **BACKLOG-0081** a **BACKLOG-0085**: idempotencia de
  `InvestimentoService`, paginacao de investimentos, `RefreshToken.toString()` expondo PII, Lombok `@Data`
  em pares bidirecionais, revisao ampla de defaults inseguros em `application.properties` base.
- Deploy e smoke em staging permanecem pendentes; implementacao e documentacao estao commitadas.

## Recomendacao final

As 7 correcoes P0/P1 sao tecnicamente solidas e cobertas por teste automatizado no backend (onde
aplicavel). A recomendacao mais critica antes de promover para producao e **nao pular o gate de deploy
real** (BACKLOG-0080): a mudanca de `forward-headers-strategy` e de topologia de rede tem historico, neste
mesmo projeto, de causar regressao silenciosa se validada apenas por teste de unidade (o proprio bug
corrigido aqui — PROB-0066 — so existia porque a configuracao de proxy real divergia do que o codigo
assumia). As duas rejeicoes de recomendacao (achados 8 e 9) estao corretamente fundamentadas em regra de
produto ja documentada e nao devem ser reabertas sem uma nova decisao explicita do dono do produto.

## Status final

`PASS_COM_RESSALVA` — implementacao completa e testada (backend 155/155, frontend build+15/15 testes,
lint com 0 erros, migration V28 validada em PostgreSQL real); validacao de infraestrutura real
(nginx, redes, staging) continua pendente.

---

> Relatorio de implementacao mantido pelo `docs-reporter`. Baseline: `main` em `deada2c`;
> implementacao em `5c08ce0`, `0d1e0c0` e `c959dfc`.
