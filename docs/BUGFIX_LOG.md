# Bugfix Log — Gestor Financeiro

Registro de bugs corrigidos. Mantido pelo `docs-reporter`.

---

## BUG-0001 — ddl-auto=update em produção substituído por Flyway

- **Problema relacionado:** PROB-0006
- **Data:** 2026-07-07
- **Area:** backend, banco, infra
- **Sintoma:** Hibernate `ddl-auto=update` em produção — risco de alteração destrutiva de schema, schema drift entre ambientes e perda de previsibilidade.
- **Causa raiz:** Configuração `spring.jpa.hibernate.ddl-auto=update` em `application-prod.properties` e `application.properties`. Ausência de migrations versionadas.
- **Correcao aplicada:**
  1. Adicionado `flyway-database-postgresql` ao `pom.xml`.
  2. Criada migration baseline `V1__baseline_schema.sql` com DDL das 10 tabelas existentes.
  3. `application.properties`: `ddl-auto=validate`, `flyway.enabled=true`, `baseline-on-migrate=true`.
  4. `application-prod.properties`: `ddl-auto=validate`, `flyway.enabled=true`.
  5. `application-test.properties`: `flyway.enabled=false` (H2 não suporta migrations PostgreSQL).
  6. `DEPLOY.md` atualizado para documentar migrations Flyway.
- **Arquivos alterados:** `pom.xml`, `application.properties`, `application-prod.properties`, `application-test.properties`, `V1__baseline_schema.sql`, `DEPLOY.md`
- **Testes/validacoes executadas:** `mvn test` — 13/13 passaram.
- **Resultado:** PASS
- **Ressalvas:** Na execução original, validação PostgreSQL real não rodou localmente. Fechada posteriormente em 2026-07-08 com smoke VPS: Flyway 14 migrations + schema JPA OK.
- **Commit:** pendente

---

## BUG-0003 — Optimistic locking e @Transactional adicionados

- **Problema relacionado:** PROB-0002, PROB-0012
- **Data:** 2026-07-07
- **Area:** backend
- **Sintoma:** Race conditions em Carteira, Meta, Conta e Categoria sem @Version. Operacoes de escrita sem @Transactional — risco de gravacao parcial e inconsistencia.
- **Causa raiz:** Ausencia de optimistic locking nas entidades com valores acumulados. Ausencia de @Transactional na maioria dos metodos write.
- **Correcao aplicada:**
  1. @Version adicionado em Carteira, Conta, Meta, Categoria.
  2. Migration V2 para colunas version.
  3. OptimisticLockingFailureException tratado no GlobalExceptionHandler → 409 Conflict.
  4. @Transactional adicionado em todos os metodos write de 6 services.
- **Arquivos alterados:** `Carteira.java`, `Conta.java`, `Meta.java`, `Categoria.java`, `GlobalExceptionHandler.java`, 6 services, `V2__optimistic_locking_columns.sql`, `FinancialIntegrityTest.java`
- **Testes/validacoes executadas:** `mvn test` — 29/29 passaram incluindo FinancialIntegrityTest.
- **Resultado:** PASS
- **Ressalvas:** Concorrencia real testada apenas com H2. Validacao com PostgreSQL pendente.
- **Commit:** pendente

---

## BUG-0002 — IDOR corrigido em TransacaoService, ContaService e ContaFixaService

- **Problema relacionado:** PROB-0001, PROB-0021
- **Data:** 2026-07-07
- **Area:** backend, seguranca
- **Sintoma:** Criacao de transacao aceitava categoriaId/contaId de outro usuario. Atualizacao de gasto em conta e categoria de outro usuario. ContaFixa aceitava categoriaId de outro usuario. CarteiraService possuia overload deletar(Long) sem ownership.
- **Causa raiz:** Uso de `findById()` sem filtro de usuarioId em TransacaoService.criar(), TransacaoService.deletar(), ContaService.adicionarGasto(), ContaService.removerGasto(), ContaFixaService.criar(), ContaFixaService.atualizar(). CarteiraService.deletar(Long) overload sem ownership.
- **Correcao aplicada:**
  1. TransacaoService.criar(): `findById` → `findByIdAndUsuarioId` para categoria e conta.
  2. TransacaoService.deletar(): `findById` → `findByIdAndUsuarioId` para categoria.
  3. ContaService.adicionarGasto/removerGasto: adicionado parametro usuarioId e uso de `findByIdAndUsuarioId`.
  4. CarteiraService.deletar(Long) sem ownership removido.
  5. ContaFixaService.criar/atualizar: validacao de categoriaId via `findByIdAndUsuarioId`.
- **Arquivos alterados:** `TransacaoService.java`, `ContaService.java`, `CarteiraService.java`, `ContaFixaService.java`, `TransacaoControllerTest.java`, `TestDataFactory.java`
- **Testes/validacoes executadas:** `mvn test` — 25/25 passaram (incluindo 2 novos testes IDOR de cross-user categoriaId e contaId).
- **Resultado:** PASS
- **Ressalvas:** Nenhuma.
- **Commit:** pendente

---

## BUG-0004 — Performance de consultas críticas corrigida

- **Problema relacionado:** PROB-0003, PROB-0004, PROB-0020
- **Data:** 2026-07-07
- **Area:** backend, banco
- **Sintoma:** findAll() massivo em ParcelaService e ContaFixaService. Dashboard agregacoes em memoria. CarteiraService scan de categorias. Contagens via size() em lista carregada.
- **Correcao aplicada:**
  1. ParcelaRepository: atualizarStatusParcelasAtrasadas via JPQL UPDATE.
  2. ContaFixaRepository: resetarContasPagasVencidas + atualizarStatusContasAtrasadas.
  3. TransacaoRepository: 3 queries SUM para dashboard.
  4. Repositories: countBy queries para contagens.
  5. CarteiraRepository.sumSaldoByUsuarioId. CategoriaRepository.findByNomeIgnoreCase.
  6. Migration V3: 11 indices de performance.
- **Arquivos alterados:** 7 repositories, 3 services, DashboardService, V3__performance_indexes.sql
- **Testes/validacoes executadas:** mvn test → 29/29 PASS, BUILD SUCCESS
- **Resultado:** PASS
- **Ressalvas:** Performance real validada apenas com H2.
- **Commit:** pendente

---

## BUG-0005 — Segurança de sessão, CORS, rate limit e logs

- **Problema relacionado:** PROB-0005, PROB-0008, PROB-0009, PROB-0010, PROB-0011
- **Data:** 2026-07-07
- **Area:** backend, seguranca
- **Sintoma:** Cookie sem Secure em prod. CORS fallback localhost em prod. Rate limit apenas login/forgot-password. Email e token em logs.
- **Correcao aplicada:** cookie.secure=true prod. CORS fallback removido. Rate limit register/reset/validate-token. EmailService maskEmail, token nunca logado.
- **Arquivos alterados:** application-prod.properties, LoginRateLimitFilter.java, EmailService.java
- **Testes/validacoes executadas:** mvn test → 29/29 PASS, BUILD SUCCESS
- **Resultado:** PASS
- **Ressalvas:** CSRF dispensado (JWT stateless + SameSite=Lax). PROB-0009 parcial (dev mantem defaults).
- **Commit:** pendente

---

## BUG-0006 — Contrato de erro padronizado com requestId

- **Problema relacionado:** N/A (melhoria estrutural Fase 0)
- **Data:** 2026-07-07
- **Area:** backend, observabilidade
- **Sintoma:** Erros sem requestId — difícil rastrear falhas entre frontend, mobile e logs.
- **Correcao aplicada:** ApiError +requestId. RequestIdFilter UUID/MDC/X-Request-Id. GlobalExceptionHandler inclui requestId em todos erros. Log 500 com requestId.
- **Arquivos alterados:** ApiError.java, RequestIdFilter.java (novo), GlobalExceptionHandler.java
- **Testes/validacoes executadas:** mvn test → 29/29 PASS
- **Resultado:** PASS
- **Ressalvas:** Health check de banco ja incluso via DataSourceHealthIndicator do Actuator.
- **Commit:** pendente

---

## BUG-0007 — Política de senha, account lockout e memory leak do rate limit

- **Problema relacionado:** PROB-0007, PROB-0023, PROB-0024
- **Data:** 2026-07-07
- **Area:** backend, seguranca
- **Sintoma:** Senhas de 6 caracteres sem complexidade aceitas. Sem lockout de conta apos falhas consecutivas. Rate limit ConcurrentHashMap crescia indefinidamente sem limpeza proativa.
- **Correcao aplicada:**
  1. @ValidPassword: min 8 caracteres, ao menos 1 letra e 1 numero.
  2. Validacao aplicada em RegisterRequest e ResetPasswordRequest.
  3. Campos failedAttempts e lockedUntil na entidade Usuario.
  4. Migration V4: colunas failed_attempts e locked_until.
  5. Account lockout no AuthController com configs max-failed-attempts e lockout-minutes.
  6. AccountLockedException + handler 429 ACCOUNT_LOCKED.
  7. @Scheduled cleanup a cada 60s no LoginRateLimitFilter.
  8. @EnableScheduling na FinanceiroApplication.
  9. docker-compose.yml com PostgreSQL 17-alpine.
  10. application-dev.properties para validacao local com PostgreSQL.
  11. LOCAL_POSTGRES_VALIDATION.md com instrucoes.
- **Arquivos alterados:** 18 arquivos (ver relatorio PR-FOUNDATION-07).
- **Testes/validacoes executadas:** mvn test → 34/34 PASS, BUILD SUCCESS
- **Resultado:** PASS
- **Ressalvas:** Na execução original, PostgreSQL validation com Docker nao executou neste ambiente. Fechada posteriormente em 2026-07-08 com smoke VPS.
- **Commit:** pendente

---

## BUG-0008 — Correções pós-auditoria de CORS, CSRF, rate limit e VPS

- **Problema relacionado:** PROB-0008, PROB-0010, PROB-0019, PEND-001, PEND-002
- **Data:** 2026-07-07
- **Area:** backend, frontend, seguranca, banco
- **Sintoma:** CORS de produção ainda podia herdar fallback localhost pelo `@Value` do código. `validate-token` era `GET`, mas o rate limit só processava `POST`. Refresh/logout usavam cookie HttpOnly sem defesa CSRF ponta a ponta. Validação PostgreSQL real não tinha profile para VPS informada.
- **Correcao aplicada:**
  1. `SecurityConfig` passou a ler `cors.allowed.origins`, respeitando profile prod com default vazio.
  2. `LoginRateLimitFilter` passou a limitar `GET /api/auth/validate-token`.
  3. Criado `RefreshTokenCsrfFilter` para exigir `X-CSRF-Token` em `refresh-token` e `logout` quando `refreshToken` cookie existe.
  4. `AuthController` passou a emitir/rotacionar cookie `csrfToken` e limpar refresh + CSRF no logout.
  5. Frontend envia `X-CSRF-Token` automaticamente em refresh/logout, inclusive no refresh do interceptor.
  6. Logs de payload de cadastro removidos do frontend.
  7. Profile padrão do backend passou a ser `vps`; `application-dev.properties` aceita override por env; `application-vps.properties` e `application-prod.properties` apontam para `187.77.61.191:5433/dbnexos-gestor-financeiro`.
  8. `LOCAL_POSTGRES_VALIDATION.md` documenta execução com profile `vps`.
  9. Conectividade TCP com `187.77.61.191:5433` validada.
  10. Smoke Spring Boot contra profile `vps` tentou conectar no PostgreSQL remoto; servidor respondeu, mas rejeitou senha para `admin_nexos`.
- **Arquivos alterados:** `SecurityConfig.java`, `LoginRateLimitFilter.java`, `RefreshTokenCsrfFilter.java`, `AuthController.java`, `AuthControllerTest.java`, `api.ts`, `authService.ts`, `application.properties`, `application-dev.properties`, `application-prod.properties`, `application-vps.properties`, `logback-spring.xml`, `.env.example`, `README-backend.md`, `LOCAL_POSTGRES_VALIDATION.md`, `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`
- **Testes/validacoes executadas:** `./mvnw -q -Dtest=AuthControllerTest test` -> 17/17 PASS; `./mvnw -q test` -> 36/36 PASS; `npm run build` no frontend -> PASS; `nc -vz -w 5 187.77.61.191 5433` -> PASS.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Smoke Flyway/schema no PostgreSQL VPS nao executou porque a credencial de `admin_nexos` foi rejeitada.
- **Commit:** pendente

---

## BUG-0009 — Validação PostgreSQL real automatizada para Ledger

- **Problema relacionado:** PEND-001, PR-LEDGER-01
- **Data:** 2026-07-08
- **Area:** backend, banco, testes, CI
- **Sintoma:** Evolução Ledger ainda dependia de validação H2/local manual. Não havia suíte automatizada que subisse PostgreSQL real, aplicasse Flyway em banco limpo e validasse schema com Hibernate `ddl-auto=validate`.
- **Correcao aplicada:**
  1. Adicionadas dependências Testcontainers (`junit-jupiter` e `postgresql`).
  2. Criado profile Maven `integration-test` com Failsafe para testes `*IT.java`.
  3. Criado `PostgresMigrationIT` usando `postgres:16-alpine`.
  4. Criado `application-postgres-it.properties` com Flyway ativo e `baseline-on-migrate=false`.
  5. Configurado Mockito como `javaagent` no Surefire/Failsafe para evitar falha de self-attach no JDK 21.
  6. CI passou a executar `mvn verify -Pintegration-test --batch-mode`.
- **Arquivos alterados:** `.github/workflows/ci.yml`, `backend/pom.xml`, `backend/src/test/java/com/gestor/financeiro/PostgresMigrationIT.java`, `backend/src/test/resources/application-postgres-it.properties`, `LOCAL_POSTGRES_VALIDATION.md`, `LEDGER_ROADMAP_GESTOR_FINANCEIRO.md`, `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`
- **Testes/validacoes executadas:** `cd backend && ./mvnw -q test` -> 36/36 PASS; `docker info --format '{{.ServerVersion}}'` -> FAIL_AMBIENTE; smoke VPS em 2026-07-08 com `dbnexos_gestor` -> PostgreSQL 17.10, Flyway validou 14 migrations e schema JPA inicializou.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Testcontainers não executou localmente porque Docker daemon estava desligado (`Cannot connect to the Docker daemon`). Validação equivalente em PostgreSQL VPS real passou com usuario `dbnexos_gestor`.
- **Commit:** pendente

---

## BUG-0010 — Mapeamento `moeda` do Ledger incompatível com PostgreSQL real

- **Data:** 2026-07-08
- **Problema relacionado:** PR-LEDGER-02, PEND-001, PEND-004
- **Severidade:** MEDIA
- **Sintoma:** Smoke VPS autenticado conectou no PostgreSQL e validou Flyway, mas Hibernate `ddl-auto=validate` falhou em `movimentos_carteira.moeda`: banco tinha `CHAR(3)`/`bpchar`, enquanto o mapeamento JPA era tratado como `VARCHAR(3)`.
- **Causa raiz:** `columnDefinition = "char(3)"` documentava o DDL, mas Hibernate 6 ainda validava o atributo Java como `VARCHAR` sem tipo JDBC explícito.
- **Correção aplicada:** Adicionado `@JdbcTypeCode(SqlTypes.CHAR)` no campo `MovimentoCarteira.moeda`, alinhando JPA com a migration `V11__movimento_carteira.sql`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/model/MovimentoCarteira.java`, `docs/BUGFIX_LOG.md`, `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`, `docs/LOCAL_POSTGRES_VALIDATION.md`, `docs/GESTOR_FINANCEIRO_ALTO_NIVEL_PROXIMOS_PASSOS.md`, `docs/SYSTEM_OVERVIEW.md`, `docs/LEDGER_ROADMAP_GESTOR_FINANCEIRO.md`
- **Testes/validacoes executadas:** `./mvnw -q test` -> PASS; smoke VPS com `dbnexos_gestor` -> PASS; Flyway validou 14 migrations; schema JPA inicializou com PostgreSQL 17.10.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma para validação VPS. Testcontainers local continua dependente de Docker ativo.
- **Commit:** pendente

---

## BUG-0011 — 500 ao criar transação com carteiraId (detached entity)

- **Problema relacionado:** PROB-0032
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** `POST /api/v1/transacoes` com `carteiraId` no payload retornava 500 `INTERNAL_ERROR` — "Detached entity with generated id ... Carteira.version null".
- **Causa raiz:** `TransacaoController.toEntity()` cria um stub `new Carteira()` só com o `id` recebido. `TransacaoService.criar()` não resolvia essa carteira via repository antes do `save()`, causando cascade em entidade detached sem `version` (viola `@Version`).
- **Correcao aplicada:** `TransacaoService.criar()` passou a resolver a carteira via `carteiraRepository.findByIdAndUsuarioId(id, usuarioId)` (valida ownership) e substituir o stub detached antes de persistir a transação.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** `mvn test` -> 69/69 PASS. Replicação manual do payload exato do app mobile contra API local (porta 8081) confirmando reprodução do erro antes e ausência do erro depois. Fluxo E2E: carteira inicial 1000 + entrada 3000 − saída 200 = saldo 3800.
- **Resultado:** PASS
- **Ressalvas:** Transações antigas criadas antes da correção, sem carteira resolvida, não têm movimento retroativo no Ledger (ver BACKLOG-0045).
- **Commit:** pendente

---

## BUG-0012 — Saldo total congelado (mobile não enviava carteiraId)

- **Problema relacionado:** PROB-0033
- **Data:** 2026-07-09
- **Area:** mobile
- **Sintoma:** Saldo total de carteiras/dashboard nunca mudava após o usuário criar transações pelo app.
- **Causa raiz:** `NovaTransacaoModal.tsx` não incluía `carteiraId` no payload de `POST /api/v1/transacoes`. Sem carteira associada, `TransacaoService.criar()` não registra movimento no Ledger (por design), então a transação não movimenta saldo.
- **Correcao aplicada:** Adicionado seletor de carteira (chips) no `NovaTransacaoModal`, pré-selecionando a primeira carteira do usuário. `carteiraId?: number` adicionado a `TransacaoRequest`. Invalidação de queries ampliada para incluir `carteiras` e `dashboard-projecao` após criar transação.
- **Arquivos alterados:** `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/src/types/index.ts`
- **Testes/validacoes executadas:** Fluxo E2E via API com payload do mobile incluindo `carteiraId`: carteira 1000 + entrada 3000 − saída 200 = saldo 3800; delete com estorno → 4000.
- **Resultado:** PASS
- **Ressalvas:** Depende de BUG-0011 estar corrigido no backend. Transações antigas sem carteira continuam sem movimento retroativo (BACKLOG-0045).
- **Commit:** pendente

---

## BUG-0013 — Sessão mobile expira sem refresh automático (após ~15 min)

- **Problema relacionado:** PROB-0034
- **Data:** 2026-07-09
- **Area:** mobile, backend, seguranca
- **Sintoma:** Após ~15 minutos de uso, todas as chamadas autenticadas do app mobile passavam a falhar com 401, exigindo novo login manual.
- **Causa raiz:** Access token JWT expira em `900000ms` (15 min). O interceptor Axios do mobile (`api.ts`) apenas traduzia o 401 em mensagem amigável, sem tentar renovar a sessão via `refresh-token`, ao contrário do interceptor web.
- **Correcao aplicada:**
  1. Interceptor de resposta em `mobile/src/services/api.ts` detecta 401 fora de rotas `/auth/`, chama `refreshAccessToken()` (promise compartilhada/deduplicada entre requests concorrentes) e repete a request original com o novo Bearer token.
  2. `refreshAccessToken()` chama `POST /api/auth/refresh-token` enviando cookie HttpOnly (`withCredentials: true`) + header `X-CSRF-Token` lido do `SecureStore`.
  3. `AuthController` (backend) passou a devolver `csrfToken` também no corpo de login/refresh, além do cookie — clientes nativos não leem cookies para o double-submit; o double-submit segue seguro pois o corpo cross-origin não é legível pelo browser.
  4. `csrfToken` persistido em `SecureStore` via `store/auth.ts`.
- **Arquivos alterados:** `mobile/src/services/api.ts`, `mobile/src/store/auth.ts`, `mobile/src/types/index.ts`, `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`
- **Testes/validacoes executadas:** `mvn test` -> 69/69 PASS. Validação manual: refresh-token rotaciona corretamente e o novo access token funciona na chamada seguinte.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma identificada para o fluxo mobile.
- **Commit:** pendente

---

## BUG-0014 — Transações soft-deletadas continuavam somando (ativa=true ausente nas queries)

- **Problema relacionado:** PROB-0035
- **Data:** 2026-07-09
- **Area:** backend, banco
- **Sintoma:** Transações deletadas (soft-delete `ativa=false`) continuavam sendo somadas em dashboard, relatórios, insights, orçamento e apareciam em listagens paginadas e na fatura de cartão.
- **Causa raiz:** Queries de `TransacaoRepository` (SUM agregados, agrupamento por categoria, listagens, consulta de fatura) não filtravam `ativa = true`.
- **Correcao aplicada:** Adicionado `AND t.ativa = true` em `sumValorTotalByUsuarioIdAndTipoAndDataBetween`, `sumValorEfetivoByUsuarioIdAndTipoAndDataBetween`, `sumValorEfetivoAgrupadoPorCategoria`, `sumSaidasByUsuarioIdAndPeriodo`, `sumSaidasByCategoria` e `findByUsuarioIdAndDataBetweenWithCategoria`. Criadas as variantes derivadas `findByUsuarioIdAndAtivaTrue` e `findByUsuarioIdAndDataBetweenAndAtivaTrue` usadas por `TransacaoService.listarPorUsuario`/`listarPorPeriodo`, e `findByUsuarioIdAndContaIdAndDataBetweenAndAtivaTrue` usada por `FaturaService.gerarOuBuscarFatura` (substituindo `findByUsuarioIdAndContaIdAndDataBetween`).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/repository/TransacaoRepository.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`, `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`
- **Testes/validacoes executadas:** `mvn test` -> 69/69 PASS. Fluxo E2E: delete de transação com estorno → saldo retorna corretamente (4000) e valor some das agregações.
- **Resultado:** PASS
- **Ressalvas:** Método derivado antigo `findByUsuarioIdAndDataBetween` (sem `AndAtivaTrue`) permanece no repository; confirmar que nenhum outro caller (ex: exportação CSV, insights) ainda o utiliza sem filtro de `ativa`.
- **Commit:** pendente

---

## BUG-0015 — categoria.valorGasto somava também transações de ENTRADA

- **Problema relacionado:** PROB-0036
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** `Categoria.valorGasto` era incrementado mesmo quando a transação era do tipo ENTRADA, inflando o indicador de orçamento/gasto por categoria.
- **Causa raiz:** `TransacaoService.criar()` e `TransacaoService.deletar()` ajustavam `categoria.valorGasto` incondicionalmente, sem checar `transacao.getTipo()`.
- **Correcao aplicada:** Ajuste de `valorGasto` restrito a `transacao.getTipo() == TipoTransacao.SAIDA` tanto na criação quanto na deleção (estorno).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** `mvn test` -> 69/69 PASS. Fluxo E2E: orçamento jul/2026 com gasto 150/500 correto após misturar entrada e saída na mesma categoria/mês.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma.
- **Commit:** pendente

---

## BUG-0016 — Vazamento de hash de senha no response de registro

- **Problema relacionado:** PROB-0037
- **Data:** 2026-07-09
- **Area:** backend, seguranca
- **Sintoma:** `POST /api/auth/register` retornava a entidade `Usuario` completa no corpo da resposta, incluindo hash bcrypt da senha, `failedAttempts` e `lockedUntil`.
- **Causa raiz:** `AuthController.register()` fazia `ResponseEntity.ok(usuarioSalvo)` com a entidade JPA diretamente.
- **Correcao aplicada:** Resposta trocada para `Map.of("id", ..., "nome", ..., "email", ...)`, sem nenhum campo sensível.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`
- **Testes/validacoes executadas:** `mvn test` -> 69/69 PASS. Inspeção manual do payload de resposta de `POST /api/auth/register` contra API local antes/depois da correção.
- **Resultado:** PASS
- **Ressalvas:** Não foi verificado se outros endpoints (ex: `GET /usuarios/me`) também retornam a entidade completa em vez de DTO — ver BACKLOG a ser aberto se confirmado.
- **Commit:** pendente

---

> Este arquivo e mantido pelo `docs-reporter`. Bugs corrigidos devem ser registrados com o proximo ID
> sequencial (BUG-0002, BUG-0003, ...). Para historico de versoes, consulte `docs/CHANGELOG.md`.
