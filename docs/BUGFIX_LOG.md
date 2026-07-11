# Bugfix Log — Gestor Financeiro

Registro de bugs corrigidos. Mantido pelo `docs-reporter`.

---

## BUG-0052 — Fechamento de P3 baixo em fatura, mobile UX e Docker

- **Problema relacionado:** BACKLOG-0034, BACKLOG-0035, BACKLOG-0046, BACKLOG-0049, BACKLOG-0053, BACKLOG-0055, BACKLOG-0057, BACKLOG-0069, PROB-0060
- **Data:** 2026-07-11
- **Area:** backend, frontend, mobile, infra, documentacao
- **Sintoma:** Itens P3 baixos ainda abertos: logout mobile sem confirmacao, Dashboard mobile sem pull-to-refresh, swap Vim sem ignore, pagamento parcial de fatura sem suporte, credito de cartao exibido como gasto negativo, edicao de compra parcelada redistribuindo restante por parcelas abertas, badge de ajuste/estorno sem paridade web e Dockerfile backend pulando testes.
- **Correcao aplicada:**
  1. Mobile perfil passou a confirmar logout com `Alert.alert`.
  2. Dashboard mobile recebeu `RefreshControl` refazendo resumo, transacoes recentes, projecao e insights.
  3. `.gitignore` passou a bloquear `*.swp`, `*.swo` e `*.swpx`.
  4. `pagarFatura` passou a aceitar pagamento parcial com lock pessimista na fatura, acumulando `valorPago`, liberando limite pelo valor pago e marcando `PAGA` apenas ao quitar o saldo restante; web/mobile enviam `Idempotency-Key`.
  5. Web/mobile exibem `Conta.valorGasto < 0` como credito disponivel, nao gasto negativo.
  6. Edicao de compra parcelada recalcula o cronograma cheio; parcelas pagas ficam imutaveis e a diferenca vira `AJUSTE` na proxima fatura aberta.
  7. Web ganhou badge de tipo `AJUSTE`/`ESTORNO` com remocao do prefixo textual, em paridade com mobile.
  8. `backend/Dockerfile` removeu `-DskipTests`; build de imagem agora roda testes.
- **Arquivos alterados:** `backend/Dockerfile`, `.gitignore`, `FaturaController.java`, `FaturaService.java`, `FaturaCartaoRepository.java`, `FaturaCartaoWorkflowTest.java`, `mobile/app/(app)/perfil.tsx`, `mobile/app/(app)/index.tsx`, `mobile/app/(app)/more/faturas.tsx`, `mobile/src/services/faturaService.ts`, `frontend/src/pages/Faturas.tsx`, `frontend/src/pages/contas.tsx`, `frontend/src/services/faturaService.ts`, `docs/BACKLOG.md`, `docs/DEPLOY.md`, `docs/PROBLEM_LEDGER.md`, `docs/SYSTEM_OVERVIEW.md`
- **Testes/validacoes executadas:** `./mvnw -q -Dtest=FaturaCartaoWorkflowTest test` PASS; `./mvnw -q test` PASS; `frontend npm run build -- --mode production` PASS; `mobile npm run lint` (`tsc --noEmit`) PASS.
- **Resultado:** PASS
- **Ressalvas:** Fatura com total zero/negativo por estorno puro continua sem rollover explicito (BACKLOG-0054).
- **Commit:** `a62f594`, `70f24e5`, `85277b7`, `2448089`, `9e4711e`

---

## BUG-0051 — PROB MEDIUM: rate limit distribuído, sessão mobile, duplo clique financeiro, PostgreSQL real e backup seguro

- **Problema relacionado:** PROB-0031, PROB-0048, PROB-0055, PROB-0056, PROB-0057, PROB-0058, PROB-0059
- **Data:** 2026-07-11
- **Area:** backend, frontend, mobile, infra, segurança, operação
- **Sintoma:** PROBs MEDIUM abertos cobriam duplo clique financeiro nos clientes, rate limit local em memória, contrato CSRF mobile ambíguo, validação PostgreSQL real dependente de Testcontainers quebrado no host, backup sem criptografia/restore drill e field injection em módulos centrais.
- **Correcao aplicada:**
  1. `LoginRateLimitFilter` passou a usar `RateLimitService` com tabela `rate_limit_buckets` e lock pessimista (`V24__rate_limit_buckets.sql`), eliminando `ConcurrentHashMap` local.
  2. Contrato de sessão separado: web usa cookie HttpOnly + CSRF; mobile usa refresh token no body/SecureStore, sem `Set-Cookie` e com bloqueio de cookie em request mobile.
  3. Web/mobile receberam locks/disabled para ações financeiras críticas: pagar fatura, movimentar carteira, pagar/pular conta fixa e reservar meta.
  4. `PostgresMigrationIT` aceita PostgreSQL externo; `scripts/verify-postgres-migrations.sh` sobe PostgreSQL via Docker CLI e virou gate no CI.
  5. Backup passou a exigir criptografia (`BACKUP_GPG_RECIPIENT` ou `BACKUP_ENCRYPTION_PASSPHRASE`), restore aceita `.gpg`, e `restore-drill-db.sh` automatiza drill em banco descartável; compose VPS gera `.sql.gz.gpg`.
  6. Sweep completo de `@Autowired` em `backend/src/main/java`: controllers, services, config e security passaram para constructor injection com dependencias `final` e `@RequiredArgsConstructor`.
- **Arquivos alterados:** `LoginRateLimitFilter.java`, `RateLimitService.java`, `RateLimitBucket.java`, `RateLimitBucketRepository.java`, `V24__rate_limit_buckets.sql`, `RefreshTokenCsrfFilter.java`, `AuthController.java`, `mobile/src/services/api.ts`, `mobile/src/services/authService.ts`, `frontend/src/pages/Faturas.tsx`, `frontend/src/pages/Carteira.tsx`, `frontend/src/pages/ContasFixas.tsx`, `frontend/src/pages/Metas.tsx`, `mobile/app/(app)/more/faturas.tsx`, `mobile/app/(app)/more/contas-fixas.tsx`, `PostgresMigrationIT.java`, `scripts/verify-postgres-migrations.sh`, `scripts/backup-db.sh`, `scripts/restore-db.sh`, `scripts/restore-drill-db.sh`, `docker-compose.vps.yml`, `deploy/vps/Dockerfile.postgres-backup`, `.github/workflows/ci.yml`
- **Testes/validacoes executadas:** `AuthControllerTest,SecurityTest` PASS; backend `./mvnw -q test` PASS; frontend `npm run build -- --mode production` PASS; mobile `npm run lint` (`tsc --noEmit`) PASS; `scripts/verify-postgres-migrations.sh` PASS; `bash -n scripts/backup-db.sh scripts/restore-db.sh scripts/restore-drill-db.sh` PASS; `rg "@Autowired" backend/src/main/java` sem ocorrencias; `nc -vz 127.0.0.1 8081` confirmou porta 8081 sem backend local.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** `mvn verify -Pintegration-test` ainda falha neste host porque Testcontainers recebe resposta inválida do socket Docker Desktop, apesar do Docker CLI rodar containers. O gate canônico agora é `scripts/verify-postgres-migrations.sh`. Testes Spring ainda usam `@Autowired`, aceitavel para testes de integracao/contexto Spring.
- **Commit:** pendente

---

## BUG-0047 — logout-all quebrado (NPE/500) por skip do filtro JWT em /api/auth/**

- **Problema relacionado:** Auditoria de segurança 2026-07-10
- **Data:** 2026-07-10
- **Area:** backend, segurança
- **Sintoma:** `POST /api/auth/logout-all` sempre retornava 500. Nenhum dispositivo conseguia fazer logout global.
- **Causa raiz:** `JwtAuthenticationFilter` fazia early-return para todo path iniciado por `/api/auth/`, nunca populando o `SecurityContext`. O endpoint `AuthController.logoutAll(Authentication authentication)` recebia `authentication == null` e estourava `NullPointerException` em `authentication.getName()`. Além disso, `/api/auth/**` estava como `permitAll`, então mesmo com token o Spring não exigia autenticação nessa rota.
- **Correcao aplicada:**
  1. `JwtAuthenticationFilter`: removido o early-return por prefixo `/api/auth/`. O filtro agora sempre popula o `SecurityContext` quando há Bearer token válido; ausência de token é inofensiva (rotas públicas continuam liberadas no `SecurityConfig`).
  2. `SecurityConfig`: adicionado matcher específico `/api/auth/logout-all` → `authenticated()` **antes** do `permitAll` de `/api/auth/**` (ordem importa; match mais específico primeiro). Garante `Authentication` não-nulo no controller.
- **Arquivos alterados:** `config/JwtAuthenticationFilter.java`, `config/SecurityConfig.java`
- **Testes/validacoes executadas:** `mvn -o compile` — BUILD SUCCESS.
- **Resultado:** PASS
- **Ressalvas:** Sem teste de integração automatizado do fluxo logout-all ainda. Recomendado adicionar MockMvc cobrindo 200 com token válido e 401 sem token.
- **Commit:** pendente

---

## BUG-0048 — Actuator health expunha detalhes de infra a anônimos (perfil vps)

- **Problema relacionado:** Auditoria de segurança 2026-07-10
- **Data:** 2026-07-10
- **Area:** backend, infra, segurança
- **Sintoma:** No perfil `vps`, `GET /actuator/health` (público via `permitAll`) retornava detalhes de componentes (status de banco, disco, etc.) para requisições anônimas.
- **Causa raiz:** `management.endpoint.health.show-details=always` combinado com `/actuator/health` em `permitAll`.
- **Correcao aplicada:** `application-vps.properties`: `show-details=when-authorized`. Anônimos recebem apenas `UP`/`DOWN`; detalhes só para requisições autenticadas. Perfil `prod` já usava `never` (inalterado); `dev` mantido `always` por ser ambiente local.
- **Arquivos alterados:** `application-vps.properties`
- **Testes/validacoes executadas:** `mvn -o compile` — BUILD SUCCESS.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma.
- **Commit:** pendente

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

## BUG-0017 — Última parcela absorve arredondamento; limite do cartão zera corretamente

- **Problema relacionado:** PROB-0038
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Compra parcelada (ex.: R$100,00 em 3x) gerava parcelas de valor arredondado (33,33 x 3 = 99,99), deixando R$0,01 residual permanente em `Conta.valorGasto` mesmo após quitar todas as faturas.
- **Causa raiz:** `valorParcela = valorTotal.divide(n, 2, HALF_UP)` aplicado identicamente em todas as N parcelas, sem reconciliar o resto da divisão inteira.
- **Correcao aplicada:** Última parcela/lançamento passa a usar `valorTotal - valorParcela*(n-1)` em vez do valor arredondado fixo. Helper `valorParcelaOuResto` criado em `TransacaoService` (usado em `criarParcelas` e `atualizarValorParcelas`); lógica equivalente aplicada inline em `FaturaService.registrarCompraCartao`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** Novo teste `FaturaCartaoWorkflowTest.ultimaParcelaAbsorveArredondamentoELimiteZeraAposPagarTodasAsFaturas`. `./mvnw -o test` → `Tests run: 76, Failures: 0, Errors: 0` (executado nesta sessão).
- **Resultado:** PASS
- **Ressalvas:** Compras/parcelas já persistidas antes da correção (se houver em ambiente real) mantêm o resíduo antigo — sem backfill.
- **Commit:** pendente

---

## BUG-0018 — Edição de valor/data de compra no cartão ressincroniza fatura e limite

- **Problema relacionado:** PROB-0039
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Editar valor ou data de uma compra já lançada no cartão não atualizava os lançamentos de fatura (`FaturaLancamento`) nem o `valorGasto` da conta/categoria — fatura e limite ficavam dessincronizados da transação real.
- **Causa raiz:** `TransacaoService.atualizar()` alterava apenas `valorTotal`/`data` da transação e o Ledger de carteira (`registrarMovimentoDiferenca`), sem tocar em fatura de cartão ou em `valorGasto`.
- **Correcao aplicada:** Para compras de cartão (`isCompraCartao`) com valor ou data alterados: cancela os lançamentos antigos (`faturaService.cancelarCompraCartao`) antes de salvar e recria (`faturaService.registrarCompraCartao`) depois; `cancelarCompraCartao` falha com `BusinessException` se alguma fatura envolvida já estiver `PAGA`. `Conta.valorGasto` e `Categoria.valorGasto` ajustados pela diferença de valor (apenas transações `SAIDA`). Parcelas legadas (`Parcela`) recalculadas via novo método `atualizarValorParcelas`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** Novo teste `FaturaCartaoWorkflowTest.editarValorDeCompraNoCartaoRessincronizaFaturaELimite`. `./mvnw -o test` → 76/76 PASS.
- **Resultado:** PASS
- **Ressalvas:** Mensagem de erro exibida no mobile/frontend quando a edição é bloqueada por fatura já paga não foi validada nesta sessão (sem teste de UI/mensagem amigável).
- **Commit:** pendente

---

## BUG-0019 — Compra retroativa não entra mais em fatura já paga

- **Problema relacionado:** PROB-0040
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Compra registrada com data retroativa cuja competência correspondia a uma fatura já `PAGA` era lançada normalmente naquela fatura, gerando inconsistência entre valor pago e valor total da fatura.
- **Causa raiz:** `registrarCompraCartao` buscava/criava a fatura pela competência calculada sem checar `fatura.getStatus()`.
- **Correcao aplicada:** Novo helper `faturaDisponivelParaLancamento(usuarioId, conta, competencia)` rola a competência mês a mês (limite de 24 iterações) até encontrar uma fatura com status diferente de `PAGA`; lança `BusinessException` se nenhuma for encontrada no período.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`
- **Testes/validacoes executadas:** Novo teste `FaturaCartaoWorkflowTest.compraRetroativaNaoEntraEmFaturaPagaVaiParaProximaAberta`. `./mvnw -o test` → 76/76 PASS.
- **Resultado:** PASS
- **Ressalvas:** Limite de 24 meses é arbitrário; risco residual aceito como extremamente improvável no fluxo real.
- **Commit:** pendente

---

## BUG-0020 — Status FECHADA da fatura agora é derivado e exibido

- **Problema relacionado:** PROB-0041
- **Data:** 2026-07-09
- **Area:** backend, frontend, mobile
- **Sintoma:** Fatura com `dataFechamento` já passada (fechada para novos lançamentos, mas ainda não vencida/paga) continuava aparecendo como "Aberta" no mobile e no frontend web.
- **Causa raiz:** Lógica de derivação de status em `FaturaService` cobria apenas `PAGA` e `VENCIDA` (via `dataVencimento`), sem checar `dataFechamento`.
- **Correcao aplicada:** Branch adicional: se `dataFechamento` já passou e a fatura não está `PAGA` nem `VENCIDA`, retorna `FaturaStatus.FECHADA`. Labels de badge adicionados: `"FECHADA"` em `mobile/app/(app)/more/faturas.tsx`, `"Fechada"` em `frontend/src/pages/Faturas.tsx`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `mobile/app/(app)/more/faturas.tsx`, `frontend/src/pages/Faturas.tsx`
- **Testes/validacoes executadas:** Revisão manual de código e diff. Nenhum teste automatizado dedicado a este branch de status foi adicionado nesta sessão.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem cobertura de teste automatizado para a transição a `FECHADA` nem para a precedência `VENCIDA > FECHADA` quando ambas as datas já passaram.
- **Commit:** pendente

---

## BUG-0021 — Falso erro "pagamento parcial não suportado" eliminado (soma de lançamentos como fonte da verdade)

- **Problema relacionado:** PROB-0042
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** `pagarFatura` podia rejeitar o pagamento do valor total exibido na tela com erro "Pagamento parcial de fatura ainda não é suportado", quando `fatura.getValorTotal()` persistido divergia da soma real dos `FaturaLancamento` (causado por PROB-0038/PROB-0039 antes da correção, ou por qualquer outra dessincronia futura).
- **Causa raiz:** `pagarFatura` e `toResponse` priorizavam `fatura.getValorTotal()` persistido em vez da soma calculada dos lançamentos ao validar/exibir o valor da fatura.
- **Correcao aplicada:** `pagarFatura` e `toResponse` agora tratam a soma dos `FaturaLancamento` (`calcularTotalLancamentos`) como fonte da verdade; usam `fatura.getValorTotal()` persistido apenas como fallback quando não há lançamentos (faturas antigas pré-migration V17).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`
- **Testes/validacoes executadas:** Coberto indiretamente pelos 3 novos testes de `FaturaCartaoWorkflowTest` (todos fazem `pagarFatura` com o valor exato da soma de lançamentos). `./mvnw -o test` → 76/76 PASS.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem teste dedicado ao caso específico de fatura pré-V17 (sem `FaturaLancamento`, apenas `valorTotal` persistido) exercitando o fallback.
- **Commit:** pendente

---

## BUG-0022 — ENTRADA com conta associada não incrementa mais valorGasto (limite) da conta

- **Problema relacionado:** PROB-0043
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Transação do tipo `ENTRADA` vinculada a uma `Conta` incrementava `Conta.valorGasto` (limite consumido do cartão) da mesma forma que uma `SAIDA`, inflando indevidamente o limite exibido.
- **Causa raiz:** `TransacaoService.criar()`, `atualizar()` e `deletar()` chamavam `contaService.adicionarGasto`/`removerGasto` sempre que havia `conta` associada, sem checar `transacao.getTipo()`.
- **Correcao aplicada:** Guarda `transacao.getTipo() == TipoTransacao.SAIDA` adicionada antes de toda chamada a `adicionarGasto`/`removerGasto` em `criar()`, `atualizar()` e `deletar()` — mesmo padrão já aplicado a `Categoria.valorGasto` em BUG-0015.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** `./mvnw -o test` → 76/76 PASS. Nenhum teste dedicado especificamente a "ENTRADA + conta + valorGasto" foi adicionado nesta sessão.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Cobertura apenas por revisão manual do código; recomenda-se teste de unidade dedicado.
- **Commit:** pendente

---

## BUG-0023 — Edição de compra com fatura paga desbloqueada (gera lançamento AJUSTE compensatório)

- **Problema relacionado:** PROB-0044 (substitui o comportamento de bloqueio registrado em PROB-0039)
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Editar valor/data de uma compra de cartão com pelo menos uma fatura envolvida já paga era bloqueado com `BusinessException`, impedindo o usuário de corrigir o valor de uma compra parcelada após a primeira fatura ser quitada.
- **Causa raiz:** `TransacaoService.atualizar()` chamava `faturaService.cancelarCompraCartao(transacao)` (versão antiga, sem `usuarioId`), que lançava `BusinessException` para qualquer lançamento em fatura `PAGA`, tratando fatura paga como imutável sem mecanismo de compensação.
- **Correcao aplicada:** Novo método `FaturaService.ressincronizarCompraCartao(transacao, usuarioId)` chamado por `TransacaoService.atualizar()` no lugar do par cancelar+recriar. Lançamentos em faturas abertas são removidos e recriados com o valor restante redistribuído pelas parcelas ainda não pagas (última parcela em aberto absorve o arredondamento). A diferença sobre a parte já paga (fatura imutável) é lançada como `TipoFaturaLancamento.AJUSTE` (podendo ser negativo) na próxima fatura em aberto — a edição nunca mais é bloqueada.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** Novo teste `FaturaCartaoWorkflowTest.editarCompraJaPagaGeraLancamentoDeAjusteNaProximaFatura` — edita compra parcialmente paga de R$100 para R$150; fatura paga permanece em 100.00 (imutável), fatura seguinte recebe lançamento `AJUSTE` de R$50.00, e `Conta.valorGasto` reflete 50.00. `cd backend && ./mvnw -o test` → `Tests run: 78, Failures: 0, Errors: 0` (executado pelo `docs-reporter` nesta sessão).
- **Resultado:** PASS
- **Ressalvas:** Redistribuição das parcelas não pagas usa "restante ÷ parcelas não pagas" (não recalcula parcela cheia) — ver BACKLOG-0055.
- **Commit:** pendente

---

## BUG-0024 — Cancelamento de compra com fatura paga desbloqueado (gera lançamento ESTORNO compensatório)

- **Problema relacionado:** PROB-0044
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Cancelar/deletar uma compra de cartão parcelada com pelo menos uma fatura já paga era bloqueado com `BusinessException` ("Não é possível cancelar compra de fatura paga").
- **Causa raiz:** `FaturaService.cancelarCompraCartao(Transacao transacao)` (assinatura antiga) percorria os lançamentos da transação e lançava `BusinessException` assim que encontrava um em fatura `PAGA`, sem mecanismo de compensação.
- **Correcao aplicada:** Assinatura alterada para `cancelarCompraCartao(Transacao transacao, Long usuarioId)`. Lançamentos em faturas abertas são removidos normalmente; a soma dos lançamentos em faturas já pagas é calculada e lançada como `TipoFaturaLancamento.ESTORNO` negativo na próxima fatura em aberto (crédito de limite) — o cancelamento nunca mais é bloqueado.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java` (chamada em `deletar()` atualizada para a nova assinatura)
- **Testes/validacoes executadas:** Novo teste `FaturaCartaoWorkflowTest.cancelarCompraParceladaComFaturaPagaGeraEstornoNaProximaFatura` — compra de R$300 em 3x com uma parcela paga (R$100), cancelada; fatura seguinte recebe lançamento `ESTORNO` de -R$100.00, `Conta.valorGasto` fica em -100.00 (crédito). `./mvnw -o test` → 78/78 PASS.
- **Resultado:** PASS
- **Ressalvas:** `Conta.valorGasto` negativo é intencional (autocorrige em compras/pagamentos futuros), mas UI pode exibir de forma pouco intuitiva — ver BACKLOG-0053. Fatura contendo só estorno (total ≤ 0) não é "pagável" pelo fluxo atual — ver BACKLOG-0054.
- **Commit:** pendente

---

## BUG-0025 — Invariante de limite de cartão centralizado no FaturaService

- **Problema relacionado:** PROB-0044
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Antes desta correção, `Conta.valorGasto` era ajustado em dois lugares diferentes para compras de cartão: em `TransacaoService` (via `contaService.adicionarGasto`/`removerGasto`) e potencialmente de forma inconsistente com os lançamentos de fatura, aumentando o risco de dessincronia entre o limite exibido e a soma real de lançamentos em faturas não pagas.
- **Causa raiz:** Ausência de um ponto único de verdade para o ajuste de `valorGasto` relacionado a compras de cartão.
- **Correcao aplicada:** Estabelecido o invariante `Conta.valorGasto == soma dos lançamentos em faturas não pagas`. Novos helpers privados em `FaturaService`: `criarLancamento(...)` e `removerLancamentoDeFaturaAberta(...)` chamam `ajustarLimiteUtilizado(conta, delta)` a cada mutação de lançamento (criação, remoção, ajuste, estorno). `TransacaoService` deixou de chamar `contaService.adicionarGasto`/`removerGasto` para transações que são compra de cartão (`isCompraCartao`) — mantido apenas para contas que não são cartão de crédito. `pagarFatura` continua liberando limite pelo total da fatura (sem alteração de contrato).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** Coberto indiretamente por todos os testes de `FaturaCartaoWorkflowTest` (7 testes na classe), que verificam `Conta.valorGasto` após criar, editar, pagar e cancelar compras de cartão. `./mvnw -o test` → 78/78 PASS.
- **Resultado:** PASS
- **Ressalvas:** Nenhum teste isola exclusivamente o helper `ajustarLimiteUtilizado` fora do fluxo de compra/edição/cancelamento — cobertura é indireta via testes de fluxo completo.
- **Commit:** pendente

---

## BUG-0026 — UI exibe lançamentos de crédito (ajuste/estorno) em verde com prefixo descritivo

- **Problema relacionado:** PROB-0044
- **Data:** 2026-07-09
- **Area:** frontend, mobile
- **Sintoma:** Antes desta correção, todo lançamento de fatura era exibido em vermelho (cor de débito), inclusive lançamentos de crédito (valor negativo) recém-introduzidos por `AJUSTE`/`ESTORNO` — visualmente indistinguível de uma compra normal.
- **Causa raiz:** Renderização de `l.valor` em `mobile/app/(app)/more/faturas.tsx` e `frontend/src/pages/Faturas.tsx` usava cor fixa (`colors.danger`/`text-red-400`) sem checar o sinal do valor.
- **Correcao aplicada:** Cor do valor do lançamento passa a depender do sinal: `l.valor < 0` renderiza em verde (`colors.success`/`text-green-400`), mantendo vermelho para valores positivos. Descrições de lançamentos de ajuste/estorno vêm prefixadas com `"Ajuste: "`/`"Estorno: "` desde o backend (`FaturaService.ressincronizarCompraCartao`/`cancelarCompraCartao`). Campo `tipo: 'COMPRA' | 'AJUSTE' | 'ESTORNO'` adicionado a `FaturaLancamentoDto` (backend), `mobile/src/types/index.ts` e `frontend/src/services/faturaService.ts`.
- **Arquivos alterados:** `mobile/app/(app)/more/faturas.tsx`, `frontend/src/pages/Faturas.tsx`, `mobile/src/types/index.ts`, `frontend/src/services/faturaService.ts`, `backend/src/main/java/com/gestor/financeiro/dto/FaturaLancamentoDto.java`
- **Testes/validacoes executadas:** Nenhum teste automatizado de UI (mobile e frontend não têm suíte e2e/component configurada para esta tela). Typecheck mobile limpo (relatado pelo agente de implementação). Erros de TypeScript pré-existentes no frontend, fora dos arquivos de fatura, não foram investigados por estarem fora do escopo desta correção.
- **Resultado:** NAO_EXECUTADO (sem teste automatizado; validação visual não confirmada por este agente)
- **Ressalvas:** Verificação de que o typecheck do frontend realmente não introduziu novos erros nos arquivos de fatura não foi reexecutada pelo `docs-reporter` (relato do agente de implementação, não verificado independentemente nesta sessão).
- **Commit:** pendente

---

## BUG-0027 — Mobile ganhou edição/exclusão de transação (EditarTransacaoModal)

- **Problema relacionado:** PROB-0045
- **Data:** 2026-07-09
- **Area:** mobile
- **Sintoma:** Não havia forma de editar ou excluir uma transação a partir do app mobile — a lista de transações não respondia a toque e não existia modal de edição.
- **Causa raiz:** Funcionalidade nunca implementada no mobile; apenas criação (`NovaTransacaoModal`) existia.
- **Correcao aplicada:** Novo componente `mobile/src/components/EditarTransacaoModal.tsx`, aberto ao tocar em uma linha de `mobile/app/(app)/transacoes.tsx`. Edita apenas `valor`, `descricao`, `data`, `observacoes` (únicos campos aplicados pelo backend em `TransacaoService.atualizar`); tipo/categoria/forma de pagamento exibidos como bloco fixo não editável. Para compra de cartão, exibe aviso de que a edição ressincroniza faturas (parte já paga vira ajuste na próxima fatura aberta, conforme `FaturaService.ressincronizarCompraCartao`). Exclusão via `Alert.alert` de confirmação, com texto específico avisando sobre estorno quando é compra de cartão. Após salvar/excluir, invalida as query keys `transacoes`, `transacoes-recentes`, `dashboard-resumo`, `dashboard-projecao`, `carteiras`, `contas`, `contas-fatura`, `fatura`, `categorias`. Subtítulo da lista passa a mostrar `· Nx` quando a transação é parcelada.
- **Arquivos alterados:** `mobile/src/components/EditarTransacaoModal.tsx` (novo), `mobile/app/(app)/transacoes.tsx`
- **Testes/validacoes executadas:** `tsc --noEmit` limpo no mobile (relatado pelo agente de implementação, não reexecutado de forma independente pelo `docs-reporter`). Validação manual de contrato contra backend local (porta 8081) com payloads exatos do app: `POST` compra 3x → `201`; `PUT` com corpo exato do modal → `200`; `DELETE` → `204`. Usuário de teste descartável usado e dados de transação removidos após o teste (restou apenas o usuário `teste-fatura-ui@teste.com` no banco local). `FaturaCartaoWorkflowTest`: 7/7 PASS (suite backend não alterada por este item).
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem teste automatizado mobile (projeto não tem suíte configurada — ver limitações conhecidas em `SYSTEM_OVERVIEW.md`). Validação de contrato foi manual e única, contra ambiente local, com um único usuário de teste — não cobre concorrência. Ver PROB-0048: a validação manual de contrato rodou contra um processo backend com build defasado (o processo não foi reiniciado na sessão — reinício ficou a cargo do usuário); ela comprova apenas o contrato HTTP (status codes/shape do payload). A validação do comportamento do código atual veio de `FaturaCartaoWorkflowTest` (7/7).
- **Commit:** pendente

---

## BUG-0028 — Badge de status da fatura no mobile passa a diferenciar ABERTA/FECHADA/VENCIDA/PAGA por cor

- **Problema relacionado:** PROB-0046
- **Data:** 2026-07-09
- **Area:** mobile
- **Sintoma:** Badge de status da fatura em `mobile/app/(app)/more/faturas.tsx` era binário: verde só para `PAGA`, vermelho para qualquer outro status — inclusive `ABERTA`, que é o estado normal de uma fatura dentro do período de compras.
- **Causa raiz:** Lógica de cor (`fatura.status === 'PAGA' ? verde : vermelho`) nunca foi atualizada quando os status `FECHADA`/`VENCIDA` passaram a ser exibidos como valores distintos (PROB-0041).
- **Correcao aplicada:** Nova constante `statusBadge` mapeando cada status a uma cor semântica: `PAGA` → `colors.success`; `VENCIDA` → `colors.danger`; `FECHADA` → `colors.warning`; `ABERTA` (padrão) → `colors.brandFg`/`colors.brandBg`, tratando o estado aberto como normal, não como alerta.
- **Arquivos alterados:** `mobile/app/(app)/more/faturas.tsx`
- **Testes/validacoes executadas:** Nenhum teste automatizado (mobile sem suíte de UI configurada). Verificação por leitura do diff.
- **Resultado:** NAO_EXECUTADO (sem teste automatizado; validação visual não confirmada por este agente)
- **Ressalvas:** Correção de baixo risco (mudança puramente visual), mas sem cobertura de teste dedicada.
- **Commit:** pendente

---

## BUG-0029 — Lançamentos de ajuste/estorno na fatura (mobile) ganham badge de tipo e descrição sem prefixo redundante

- **Problema relacionado:** PROB-0047
- **Data:** 2026-07-09
- **Area:** mobile
- **Sintoma:** Lançamentos `AJUSTE`/`ESTORNO` na tela de fatura mobile só eram distinguíveis de uma compra normal pelo prefixo textual `"Ajuste: "`/`"Estorno: "` na descrição e pela cor do valor (BUG-0026) — sem nenhum indicador visual dedicado de tipo.
- **Causa raiz:** BUG-0026 tratou apenas a cor condicional do valor; o campo `tipo` (já disponível no DTO/tipos desde BUG-0026) não tinha uso visual além disso.
- **Correcao aplicada:** Cada lançamento agora calcula um `tipoBadge`: `ESTORNO` → chip verde (`colors.success`); `AJUSTE` → chip âmbar (`colors.warning`); `COMPRA` → sem badge. O prefixo `"Estorno: "`/`"Ajuste: "` é removido da descrição exibida via regex (`/^(Estorno|Ajuste):\s*/`) já que o badge assume esse papel — a descrição original retornada pela API permanece intacta, apenas a exibição é ajustada.
- **Arquivos alterados:** `mobile/app/(app)/more/faturas.tsx`
- **Testes/validacoes executadas:** Nenhum teste automatizado (mobile sem suíte de UI configurada). Verificação por leitura do diff.
- **Resultado:** NAO_EXECUTADO (sem teste automatizado; validação visual não confirmada por este agente)
- **Ressalvas:** Regex de remoção do prefixo depende do texto exato gerado pelo backend (`FaturaService.ressincronizarCompraCartao`/`cancelarCompraCartao`); mudança futura nesse texto sem atualizar a regex causaria prefixo duplicado. Não foi verificado nesta rodada se `frontend/src/pages/Faturas.tsx` (web) recebeu o mesmo tratamento (a leitura do diff atual do web mostra apenas a cor condicional de BUG-0026, sem badge/prefixo removido) — ver PROB-0047, próximo passo, e BACKLOG a criar se aplicável.
- **Commit:** pendente

---

## BUG-0030 — Login não retornava onboardingCompleto e mandava todo usuário pro onboarding

- **Problema relacionado:** BUG-M01 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** backend (auth), mobile
- **Sintoma:** todo login no mobile redirecionava para `/onboarding`, mesmo usuário com onboarding completo.
- **Causa raiz:** map `usuario` da resposta de `POST /api/auth/login` só tinha `id/nome/email`; `login.tsx:23` checava `user.onboardingCompleto` → `undefined` → falsy.
- **Correcao aplicada:** `AuthController` inclui `onboardingCompleto` (via `usuario.isOnboardingCompleto()`) no map `usuario` do login — mesma projeção do `UsuarioResponseDto`.
- **Arquivos alterados:** `AuthController.java`
- **Testes/validacoes executadas:** `mvn test -Dtest=AuthControllerTest` — 17/17 PASS.
- **Resultado:** PASS

---

## BUG-0031 — "Exportar Dados" do mobile sempre dava 401 (URL sem autenticação)

- **Problema relacionado:** BUG-M02 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** tile "Exportar Dados" abria/compartilhava a URL crua de `/v1/exportar/completo`; endpoint exige Bearer → 401 sempre; URL da API vazava pelo Share.
- **Causa raiz:** download via browser/`Share.share` sem header `Authorization`.
- **Correcao aplicada:** CSV baixado pelo axios autenticado (`responseType: 'text'`); nativo grava com `expo-file-system` (`File`/`Paths.cache`) e compartilha o arquivo com `expo-sharing`; web baixa via Blob + anchor. Deps novas: `expo-file-system ~19.0.23`, `expo-sharing ~14.0.8` (instaladas com `--legacy-peer-deps` por conflito pré-existente react 19.1.0 × react-dom 19.2.7).
- **Arquivos alterados:** `mobile/app/(app)/more/index.tsx`, `mobile/package.json`, `mobile/package-lock.json`
- **Testes/validacoes executadas:** `npx tsc --noEmit` — limpo.
- **Resultado:** PASS
- **Ressalvas:** validação em device real pendente (fluxo Share nativo).

---

## BUG-0032 — Logout não revogava refresh token no servidor

- **Problema relacionado:** BUG-M03 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile (segurança)
- **Sintoma:** logout só limpava storage local; refresh token do cookie HttpOnly continuava válido no servidor. Em `perfil.tsx` a chamada nem tinha `await` e o logout era disparado em duplicidade (service + contexto).
- **Causa raiz:** `authService.logout()` nunca chamava `POST /api/auth/logout`.
- **Correcao aplicada:** `authService.logout()` chama `POST /auth/logout` com header `X-CSRF-Token` (best-effort: storage local sempre limpo mesmo se a rede falhar); `perfil.tsx` usa apenas o `logout()` do contexto com `await`; tipo do contexto ajustado para `() => Promise<void>`.
- **Arquivos alterados:** `mobile/src/services/authService.ts`, `mobile/app/(app)/perfil.tsx`, `mobile/src/context/AuthContext.tsx`
- **Testes/validacoes executadas:** `npx tsc --noEmit` — limpo. Backend já tinha teste de logout+CSRF em `AuthControllerTest` (17/17 PASS).
- **Resultado:** PASS

---

## BUG-0033 — carteiraService usava endpoints deprecated de adicionar/remover dinheiro

- **Problema relacionado:** BUG-M04 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** `carteiraService` apontava para `POST /{id}/adicionar` e `/{id}/remover`, marcados `@Deprecated(since = "PR-LEDGER-06")` — movimentos fora do ledger.
- **Causa raiz:** service não migrou quando o backend ganhou `POST /{id}/ajustes`.
- **Correcao aplicada:** `adicionarValor`/`removerValor` substituídos por `ajustarSaldo(id, tipo ENTRADA|SAIDA, valor, descricao?)` chamando `/v1/carteiras/{id}/ajustes`. Nenhuma tela usava os métodos antigos (código morto) — sem mudança de UI.
- **Arquivos alterados:** `mobile/src/services/carteiraService.ts`
- **Testes/validacoes executadas:** `npx tsc --noEmit` — limpo; grep confirmou zero chamadores dos métodos removidos.
- **Resultado:** PASS
- **Ressalvas:** endpoints deprecated ainda existem no backend; remover quando o frontend web também migrar.

---

## BUG-0034 — Pagar conta fixa não debitava nenhuma carteira (dinheiro sumia)

- **Problema relacionado:** PROD-M05 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** backend + mobile
- **Sintoma:** `PUT /v1/contas-fixas/{id}/pagar` criava transação SAIDA sem carteira; `saldoCarteiras` não caía ao pagar aluguel/energia.
- **Causa raiz:** `ContaFixaController` ignorava `ValorRequest.carteiraId`; `ContaFixaService.marcarComoPaga` nunca vinculava carteira à transação.
- **Correcao aplicada:** controller repassa `carteiraId`; service exige carteira (`BusinessException` 422 se ausente) e seta na transação — `TransacaoService.criar` valida ownership e registra o débito no ledger (mesma mecânica do pagamento de fatura). Mobile: modal Pagar em `more/contas-fixas.tsx` ganhou seletor de carteira (chips, padrão de `more/faturas.tsx`), pré-seleciona quando há uma só, invalida `carteiras` e `transacoes-recentes`.
- **Arquivos alterados:** `ContaFixaController.java`, `ContaFixaService.java`, `mobile/src/services/contaFixaService.ts`, `mobile/app/(app)/more/contas-fixas.tsx`
- **Testes/validacoes executadas:** `FinancialIntegrityTest`, `LedgerServiceTest`, `TransacaoServiceLedgerTest` 16/16 PASS; E2E na stack local (payloads do mobile): pagar sem carteira → 422 "Informe a carteira de pagamento"; com carteira → saldo 4000→3850 e movimento SAIDA no extrato.
- **Resultado:** PASS
- **Ressalvas:** frontend web (`ContasFixas.tsx`) ainda chama sem `carteiraId` e agora recebe 422 — alinhar quando o web for retomado (antes vazava dinheiro em silêncio; erro explícito é o comportamento correto até lá).

---

## BUG-0035 — Reservar valor em meta não saía de carteira nenhuma (dupla contagem)

- **Problema relacionado:** PROD-M06 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** backend + mobile
- **Sintoma:** `PUT /v1/metas/{id}/adicionar` só incrementava `valorReservado`; dinheiro "guardado" seguia disponível na carteira.
- **Causa raiz:** `MetaService.adicionarValor`/`removerValor` não tocavam o ledger de carteiras.
- **Correcao aplicada:** ambos exigem `carteiraId` (422 se ausente). Reserva debita a carteira (`RESERVA_META`, origem `META`, saldo insuficiente → 422); resgate credita de volta (`RESGATE_META`) e é limitado ao reservado ("Valor maior que o reservado na meta"). Enums novos em `TipoMovimentoCarteira` (coluna é VARCHAR, sem migration). Mobile: modal Adicionar em `metas.tsx` ganhou seletor "Sai de" com chips de carteira.
- **Arquivos alterados:** `TipoMovimentoCarteira.java`, `MetaService.java`, `MetaController.java`, `mobile/src/services/metaService.ts`, `mobile/app/(app)/metas.tsx`
- **Testes/validacoes executadas:** mesmos testes de ledger 16/16 PASS; E2E: reservar 200 → saldo 3850→3650 com movimento `RESERVA_META`; resgate acima do reservado → 422; resgatar 80 → saldo 3730 com `RESGATE_META`.
- **Resultado:** PASS
- **Ressalvas:** (1) web `Metas.tsx` chama sem `carteiraId` → 422 até alinhar; (2) metas antigas com `valorReservado` acumulado antes da correção nunca debitaram carteira — resgatá-las agora credita dinheiro que não saiu; avaliar backfill/zerar em dados reais; (3) `removerValor` sem UI no mobile (UX-M11) — assinatura do service já aceita `carteiraId`.

---

## BUG-0036 — Home: rótulos de saldo enganavam (patrimônio rotulado como saldo do mês)

- **Problema relacionado:** PROD-M07 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** hero mostrava `saldoCarteiras` (patrimônio) com label "Saldo total · {mês}"; KPI "Disponível" repetia o mesmo `saldoCarteiras`; `resumo.saldo` (saldo do mês) não era usado.
- **Causa raiz:** rótulos herdados do protótipo sem distinguir patrimônio × movimento do mês.
- **Correcao aplicada:** hero = "Saldo total" (sem mês); chips ↑↓ ganharam sufixo "em {mês}"; KPI "Disponível" virou "Saldo do mês" usando `resumo.saldo`; glifos de Receitas/Despesas alinhados à semântica do hero (↑ entrada, ↓ saída).
- **Arquivos alterados:** `mobile/app/(app)/index.tsx`
- **Testes/validacoes executadas:** `npx tsc --noEmit` limpo; E2E confirmou contrato: `resumo.saldo` (2700) ≠ `saldoCarteiras` (3730) no dashboard.
- **Resultado:** PASS
- **Ressalvas:** após PROD-M06 em produção, "Disponível" pode voltar como patrimônio − reservas ativas (hoje o débito da reserva já remove da carteira, então `saldoCarteiras` já reflete o disponível real).

---

## BUG-0037 — Transações: paginação falsa e somatório mentiroso

- **Problema relacionado:** UX-M08 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile + backend
- **Sintoma:** tela carregava só `page=0&size=20` sem infinite scroll; cards Entradas/Saídas somavam apenas os 20 itens carregados; filtro ENTRADA/SAIDA era client-side sobre a página; sem período nem busca.
- **Causa raiz:** listagem usava `useQuery` fixo em `/minhas` página 0; somatório calculado no client sobre a página; backend não expunha filtro de tipo/busca em `/periodo`.
- **Correcao aplicada:** backend: `/v1/transacoes/periodo` ganhou parâmetros opcionais `tipo` e `q` (busca case-insensitive por descrição; queries dedicadas no repositório, sempre `ativa = true`). Mobile: `useInfiniteQuery` com paginação real, seletor de mês (‹ mês ›, default atual, avanço bloqueado além do mês corrente), somatório do cabeçalho vindo de `/v1/relatorios` (totais do período), campo de busca com debounce 350ms e chips de tipo virando parâmetros de query. Modais de transação passaram a invalidar `['relatorio']` e `['dashboard-evolucao']`.
- **Arquivos alterados:** `backend/.../TransacaoController.java`, `TransacaoService.java`, `TransacaoRepository.java`, `mobile/app/(app)/transacoes.tsx`, `mobile/src/services/transacaoService.ts`, `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`
- **Testes/validacoes executadas:** `TransacaoControllerTest` com novo teste `listarPorPeriodo_deveFiltrarPorTipoEBusca` (tipo, q, combinado, ENTRADA) PASS; `npx tsc --noEmit` limpo.
- **Resultado:** PASS
- **Ressalvas:** backend local (porta 8081) precisa ser reiniciado para expor os novos parâmetros.

---

## BUG-0038 — Sem tela de cadastro no app

- **Problema relacionado:** UX-M09 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** backend tinha `POST /auth/register`, mas usuário novo não conseguia criar conta pelo app (só login + forgot-password).
- **Causa raiz:** tela nunca foi construída.
- **Correcao aplicada:** `mobile/app/(auth)/register.tsx` consumindo `/auth/register` (`RegisterRequest`: nome, email, password, confirmPassword), validação client espelhando o backend (`@ValidPassword`: mínimo 8, 1 letra, 1 número; nome ≥2; e-mail; confirmação), login automático após sucesso com redirect para onboarding. Link "Criar conta" no login.
- **Arquivos alterados:** `mobile/app/(auth)/register.tsx` (novo), `mobile/app/(auth)/login.tsx`
- **Testes/validacoes executadas:** `npx tsc --noEmit` limpo; `AuthControllerTest` PASS (contrato de register inalterado).
- **Resultado:** PASS
- **Ressalvas:** validar fluxo completo em device (teclado/scroll) na próxima sessão de testes manuais.

---

## BUG-0039 — Reset de senha terminava no vácuo

- **Problema relacionado:** UX-M10 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile + backend
- **Sintoma:** forgot-password enviava o e-mail, mas não existia tela para `POST /auth/reset-password` nem deep link.
- **Causa raiz:** segunda metade do fluxo nunca foi construída.
- **Correcao aplicada:** `mobile/app/(auth)/reset-password.tsx` (token + nova senha + confirmação, mesma regra `@ValidPassword`); aceita deep link `gestorfinanceiro://reset-password?token=...` via `useLocalSearchParams` (scheme já existia no `app.json`) e colagem manual do token; entrada pela tela de sucesso do forgot-password ("Já recebi o código"). `EmailService` monta o link com a property `app.reset-password-link-base` (default: scheme do app).
- **Arquivos alterados:** `mobile/app/(auth)/reset-password.tsx` (novo), `mobile/app/(auth)/forgot-password.tsx`, `backend/.../EmailService.java`
- **Testes/validacoes executadas:** `npx tsc --noEmit` limpo; backend compila; contrato `/auth/reset-password` e `ResetPasswordRequest` inalterados.
- **Resultado:** PASS
- **Ressalvas:** envio real de e-mail continua TODO (stub loga sem token); testar deep link em device real.

---

## BUG-0040 — Backend rico, mobile cego: extrato de carteira e evolução mensal (parcial da seção 4)

- **Problema relacionado:** Seção 4 (docs/AUDITORIA_MOBILE_2026-07-10.md) — prioridades "extrato de carteira e gráficos primeiro"
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** `/v1/carteiras/{id}/movimentos` e `/v1/dashboard/evolucao-mensal` prontos no backend, sem UI.
- **Causa raiz:** backlog de evolução.
- **Correcao aplicada:** Carteiras: tocar no card abre extrato do ledger (paginação infinita, valor assinado verde/vermelho, saldo resultante por movimento, labels pt-BR por tipo em `TIPO_MOVIMENTO_LABEL`). Relatórios: card "Evolução mensal" com barras entradas × saídas dos últimos 6 meses (Views puras, sem lib de gráfico; legenda; acessibilidade por mês).
- **Arquivos alterados:** `mobile/app/(app)/more/carteiras.tsx`, `mobile/app/(app)/more/relatorios.tsx`, `mobile/src/services/carteiraService.ts`, `mobile/src/services/relatorioService.ts`, `mobile/src/types/index.ts`, `mobile/src/utils/format.ts`
- **Testes/validacoes executadas:** `npx tsc --noEmit` limpo.
- **Resultado:** PASS
- **Ressalvas:** restante da seção 4 segue pendente (comparação mensal, insights, parcelas, anexos, importação CSV, investimentos, reconciliação com UI).

---

## BUG-0041 — UX-M11 a M14: metas, categoria, nomenclatura e perfil

- **Problema relacionado:** UX-M11 a UX-M14 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile + backend
- **Sintoma:** metas sem editar/excluir/retirar valor; edição de transação sem troca de categoria; "Contas" e "Carteiras" confundiam; perfil escondido e sem ações.
- **Causa raiz:** UI mobile incompleta para contratos já existentes e falta de endpoints de perfil.
- **Correcao aplicada:** Metas: sheet de detalhe com editar, excluir, adicionar e retirar valor; form expõe `valorMensal`. Transações: edição permite trocar categoria; backend valida ownership e ajusta gasto por categoria. Nomenclatura: UI usa "Contas" para saldos/dinheiro (`carteiras`) e "Cartões" para `contas`. Perfil: entrada no hub "Mais"; formulário de nome e senha; backend `PUT /v1/usuarios/me` e `PUT /v1/usuarios/me/senha`.
- **Arquivos alterados:** `mobile/app/(app)/metas.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`, `backend/.../TransacaoService.java`, `backend/.../UsuarioController.java`, `mobile/app/(app)/perfil.tsx`, `mobile/app/(app)/more/index.tsx`, telas de contas/cartões/onboarding.
- **Testes/validacoes executadas:** `npm --prefix mobile run lint -- --pretty false` PASS; `backend/./mvnw -q -Dtest=TransacaoServiceLedgerTest test` PASS; `backend/./mvnw -q -Dtest=TransacaoControllerTest test` PASS com permissão elevada por Mockito/ByteBuddy.
- **Resultado:** PASS
- **Ressalvas:** nomes internos, rotas e serviços permanecem `carteiras`/`contas` para evitar migração desnecessária.

## BUG-0042 — Seção 4: comparação mensal no mobile

- **Problema relacionado:** Seção 4 (docs/AUDITORIA_MOBILE_2026-07-10.md) — endpoint `/v1/dashboard/comparacao-mensal` pronto no backend, sem UI.
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** Relatórios mostrava evolução de 6 meses, mas não respondia rapidamente se o mês atual melhorou ou piorou contra o mês anterior.
- **Causa raiz:** backlog de evolução; service mobile ainda não consumia o endpoint de comparação mensal.
- **Correcao aplicada:** `relatorioService.comparacaoMensal()` consome `/v1/dashboard/comparacao-mensal`; Relatórios ganhou card "Comparação mensal" com saldo, entradas, saídas e variação absoluta/percentual entre mês atual e anterior; criação/edição de transações invalidam o novo cache.
- **Arquivos alterados:** `mobile/app/(app)/more/relatorios.tsx`, `mobile/src/services/relatorioService.ts`, `mobile/src/types/index.ts`, `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`
- **Testes/validacoes executadas:** `npm --prefix mobile run lint -- --pretty false` PASS; detector Impeccable local retornou `[]`.
- **Resultado:** PASS
- **Ressalvas:** demais itens da seção 4 seguem pendentes (insights, parcelas, anexos, importação CSV, investimentos, reconciliação com UI).

---

## BUG-0043 — Seção 4: reconciliação, insights e parcelas no mobile

- **Problema relacionado:** Seção 4 (docs/AUDITORIA_MOBILE_2026-07-10.md) — endpoints de reconciliação, insights e parcelas prontos no backend, sem UI mobile.
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** Usuário não via conferência ledger × saldo da conta, não recebia alertas/recomendações na home e não conseguia pagar/despagar parcelas individualmente pelo app.
- **Causa raiz:** backlog de evolução mobile sobre contratos já existentes.
- **Correcao aplicada:** Carteiras: extrato mostra status de reconciliação, saldos comparados e diferença. Home: novo card de insights consome `/v1/insights`, exibe resumo, gasto do mês, média, alertas de categoria e recomendação principal. Transações: edição de transação parcelada lista parcelas via `/v1/parcelas/transacao/{id}` e permite pagar/despagar com invalidação de dashboards, relatórios, faturas e parcelas.
- **Arquivos alterados:** `mobile/app/(app)/index.tsx`, `mobile/app/(app)/more/carteiras.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`, `mobile/src/services/carteiraService.ts`, `mobile/src/services/insightsService.ts`, `mobile/src/services/parcelaService.ts`, `mobile/src/types/index.ts`, `docs/AUDITORIA_MOBILE_2026-07-10.md`.
- **Testes/validacoes executadas:** `npm --prefix mobile run lint -- --pretty false` PASS.
- **Resultado:** PASS
- **Ressalvas:** anexos, importação CSV e investimentos foram fechados depois em BUG-0044.

---

## BUG-0044 — Seção 4: anexos, importação CSV e investimentos no mobile

- **Problema relacionado:** Seção 4 (docs/AUDITORIA_MOBILE_2026-07-10.md) — últimos endpoints prontos no backend sem UI mobile.
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** Usuário não conseguia anexar comprovantes por transação, importar extrato CSV pelo app nem gerenciar investimentos.
- **Causa raiz:** mobile ainda não tinha fluxo de seleção/câmera/arquivo nem tela dedicada de investimentos.
- **Correcao aplicada:** Instalação das dependências Expo compatíveis `expo-document-picker` e `expo-image-picker`. Edição de transação ganhou seção "Comprovantes" com upload por câmera/arquivo, listagem e exclusão via `/v1/anexos`. Hub "Mais" ganhou importação CSV autenticada via `/v1/importar/csv`. Novo módulo `Investimentos` consome `/v1/investimentos`, permitindo listar, criar, editar, excluir ativos e registrar/listar movimentações.
- **Arquivos alterados:** `mobile/package.json`, `mobile/package-lock.json`, `mobile/app/(app)/more/index.tsx`, `mobile/app/(app)/more/investimentos.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`, `mobile/src/services/anexoService.ts`, `mobile/src/services/importService.ts`, `mobile/src/services/investimentoService.ts`, `mobile/src/types/index.ts`, `docs/AUDITORIA_MOBILE_2026-07-10.md`.
- **Testes/validacoes executadas:** `npm --prefix mobile run lint -- --pretty false` PASS.
- **Resultado:** PASS
- **Ressalvas:** validar câmera, seletor de documentos e upload multipart em device real/simulador com API rodando.

---

## BUG-0045 — /v1/insights retornava 500 com dados reais (visto no simulador iOS)

- **Problema relacionado:** Seção 4 (docs/AUDITORIA_MOBILE_2026-07-10.md) — home logava `[API Error] {"status": 500, "url": "/v1/insights"}` em loop.
- **Data:** 2026-07-10
- **Area:** backend
- **Sintoma:** `GET /v1/insights` 500 para qualquer usuário com gasto no mês anterior; usuário sem histórico recebia 200.
- **Causa raiz:** (1) `InsightsService.gerarAlertasCategoria` lia `row[1]` (nome da categoria, String) como `BigDecimal` — `sumSaidasByCategoria` retorna `[id, nome, soma]` → `ClassCastException`. (2) Variação mensal dividia por `gastoMesAtual` — denominador errado (correto: média) e `ArithmeticException` com mês atual zerado.
- **Correcao aplicada:** `mapAnterior` passa a usar `row[2]` (com conversores defensivos `asLong`/`asBigDecimal`); variação divide por `gastoMedioMensal`.
- **Arquivos alterados:** `backend/.../InsightsService.java`, `backend/src/test/java/com/gestor/financeiro/InsightsServiceTest.java` (novo)
- **Testes/validacoes executadas:** `InsightsServiceTest` PASS (regressão dos dois cenários); E2E no backend local: usuário com gasto de R$600 no mês anterior e R$900 no atual → 200 com variação 350% (sobre média de 3 meses) e alerta de categoria +50%.
- **Resultado:** PASS
- **Ressalvas:** dependências `expo-image-picker`/`expo-document-picker` estavam em versões do SDK 57 com Expo 54 (`createPermissionHook is not a function` no simulador) — realinhadas para 17.0.10/14.0.8 via `npm install --legacy-peer-deps` (conflito de peer `react-dom@19.2.7` × `react@19.1.0` pré-existente na árvore). Recarregar o app no simulador para pegar os módulos corretos.

---

## BUG-0046 — Web: pagar conta fixa e reservar meta sem carteiraId (422)

- **Problema relacionado:** Ressalva da seção 6, item 2 (docs/AUDITORIA_MOBILE_2026-07-10.md) — após PROD-M05/M06 o backend passou a exigir `carteiraId`, mas o frontend web continuava enviando só `{ valor }`.
- **Data:** 2026-07-10
- **Area:** frontend (web)
- **Sintoma:** No web, "Marcar como Paga" (Contas Fixas) e "Adicionar Dinheiro" (Metas) retornavam 422 sempre.
- **Causa raiz:** `contaFixaService.marcarComoPaga` e `metaService.adicionarValor`/`removerValor` não enviavam `carteiraId`, agora obrigatório no contrato.
- **Correcao aplicada:** services passam a enviar `carteiraId` no body; `ContasFixas.tsx` ganhou select "Pagar com qual conta?" no form inline de pagamento e `Metas.tsx` ganhou select "Sai de qual conta?" no form de adicionar valor (ambos listam carteiras com saldo via `/carteiras/minhas`, validação obrigatória, toast com mensagem do backend em erro).
- **Arquivos alterados:** `frontend/src/services/contaFixaService.ts`, `frontend/src/services/metaService.ts`, `frontend/src/pages/ContasFixas.tsx`, `frontend/src/pages/Metas.tsx`.
- **Testes/validacoes executadas:** `npx tsc --noEmit` PASS no frontend.
- **Resultado:** PASS
- **Ressalvas:** `metaService.removerValor` foi atualizado no contrato, mas a UI web ainda não expõe "retirar valor" (paridade com mobile fica para depois). Validar fluxo E2E no browser com API rodando.

---

## BUG-0047 — Auditoria segurança/LGPD: itens #2, #4, #4b, #6, #8, #10, #11 implementados

- **Problema relacionado:** docs/REVIEW_REPORTS/2026-07-10_full-system_security-lgpd-audit.md
- **Data:** 2026-07-10
- **Area:** backend
- **Sintoma/risco por item:**
  - **#2** Host/usuário do banco de produção commitados como default em `application-prod.properties`.
  - **#8** `DB_PASSWORD:1234` e `jwt.secret` com default fraco no `application.properties` base.
  - **#6** Upload de anexo sem validação de tipo (stored XSS via HTML com MIME arbitrário; filename do cliente no path).
  - **#4** Refresh token em texto puro no banco (vazamento do DB = roubo de sessão).
  - **#4b** Token de reset de senha idem (achado colateral do #4).
  - **#10** LGPD art. 18: sem endpoint de eliminação; exportação incompleta (faltavam cadastro, carteiras, metas, contas fixas).
  - **#11** LGPD: cadastro sem registro de consentimento.
- **Correcao aplicada:**
  - **#2/#8** Removidos todos os defaults sensíveis; env obrigatório (falha no boot se ausente). Perfil dev mantém defaults locais próprios.
  - **#6** `AnexoService`: whitelist pdf/jpg/jpeg/png/webp + verificação de magic bytes; nome em disco = `UUID.ext`; MIME canônico da whitelist (nunca o do cliente); download com `contentTypeSeguro()` (neutraliza MIME legado) e `Content-Disposition` via builder (sem injeção de header).
  - **#4/#4b** `TokenHasher` novo (`security/`): valor cru de 256 bits entregue uma única vez; banco guarda só SHA-256 hex. Aplicado a refresh token e reset de senha. Sem DDL; tokens antigos deixam de validar (re-login único).
  - **#10** `DELETE /api/v1/usuarios/me` (confirmação por senha) → `UsuarioExclusaoService` apaga todos os dados do titular em transação única, em ordem de FK; arquivos de upload removidos após commit. Exportação `/api/v1/exportar/completo` ganhou dados cadastrais, carteiras, metas e contas fixas (incluindo inativas).
  - **#11** `V19__consentimento_usuario.sql` (`politica_versao`, `consentimento_em`); register exige `aceitaTermos=true` e grava versão (`app.politica.versao`) + timestamp.
- **Arquivos alterados:** `application.properties`, `application-prod.properties`, `AnexoService.java`, `AnexoController.java`, `TokenHasher.java` (novo), `RefreshTokenService.java`, `AuthController.java`, `UsuarioExclusaoService.java` (novo), `UsuarioController.java`, `ExcluirContaRequest.java` (novo), `ExportService.java`, `MetaRepository.java`, `ContaFixaRepository.java`, `Usuario.java`, `RegisterRequest.java`, `V19__consentimento_usuario.sql` (novo), testes: `AnexoServiceTest.java` (novo), `UsuarioExclusaoTest.java` (novo), `AuthControllerTest.java`.
- **Testes/validacoes executadas:** suíte completa do backend PASS (95 testes, 0 falhas).
- **Resultado:** PASS
- **Ressalvas:**
  - **BREAKING para clientes:** register agora exige `aceitaTermos: true` — web e mobile precisam de checkbox de consentimento antes do deploy conjunto. Upload de anexo fora da whitelist retorna 422 (HEIC do iOS não incluído; converter no app ou ampliar whitelist).
  - **#2 parte infra pendente (só o operador pode fazer):** firewall no Postgres da VPS (porta 5433 restrita ao IP da app), trocar usuário/senha do banco. Host/user antigos permanecem no histórico Git — mitigação é rotacionar, não reescrever.
  - Pendentes do backlog: #5 (rate limit atrás de proxy confiável), #9 (SMTP real no EmailService).

---

## BUG-0048 — Auditoria de UI mobile: tokens, design system e acessibilidade (PROB-0061 a PROB-0064)

- **Problema relacionado:** PROB-0061, PROB-0062, PROB-0063, PROB-0064
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma/risco por item:**
  - **PROB-0061** Onboarding com paleta Tailwind fora da canônica (categoria criada ficava com cor não re-selecionável no editor), CTA final verde `#22C55E` (viola "verde é dinheiro, violeta é marca"), inputs/chips manuais e zero a11y.
  - **PROB-0062** `#ffffff`/`#fff` fixos em perfil e splash (contraste quebrado no dark mode) e tiles arco-íris no hub "Mais" (anti-referência do PRODUCT.md).
  - **PROB-0063** Telas de auth com inputs manuais sem `accessibilityLabel`, links em `brand` (~3.5:1, falha AA) e alvos de toque < 44pt.
  - **PROB-0064** Categorias com FAB caseiro sem label, swatches de cor sem role/estado e espaçamento duplicado.
- **Correcao aplicada:**
  - Onboarding: `CATEGORIAS_SUGERIDAS` → `CATEGORY_COLORS` (novo cinza neutro na paleta para "Outros"); CTA em `brand`/`brandText`; inputs → `Field`, chips → `Chip`; roles `checkbox`/`button` + estados + alvos ≥44pt; barra de progresso simplificada.
  - Tema: `brandText` no botão brand do perfil; "Sair" em `dangerBg`+`danger`; splash em `colors.bg`; tiles do hub todos em `brandBg`; badge "Em breve" 8→10pt.
  - Auth (login/register/forgot/reset): inputs → `Field` (com `autoComplete`/`textContentType`); links → `brandFg` (AA); minHeight 44 e `accessibilityRole` nos toques; radius 12 unificado.
  - Categorias: componente `Fab` ("Nova categoria"); swatches com role `radio` + `selected` + hitSlop; Nome via `Field`; Cancelar/Salvar com role, alvo 44pt e `brandFg`.
- **Arquivos alterados:** `mobile/app/onboarding.tsx`, `mobile/app/index.tsx`, `mobile/app/(app)/perfil.tsx`, `mobile/app/(app)/more/index.tsx`, `mobile/app/(app)/more/categorias.tsx`, `mobile/app/(auth)/login.tsx`, `mobile/app/(auth)/register.tsx`, `mobile/app/(auth)/forgot-password.tsx`, `mobile/app/(auth)/reset-password.tsx`, `mobile/src/utils/format.ts`.
- **Testes/validacoes executadas:** `npx tsc --noEmit` PASS no mobile (script `lint`).
- **Resultado:** PASS
- **Ressalvas:** Mudanças visuais/a11y — validar no Expo nos dois temas (onboarding e auth). Demais telas `more/` (faturas, contas-fixas, investimentos, ...) ainda têm inputs manuais fora do `Field` — mesmo padrão, pendente de replicação. Verificações Entrance/ScreenTransition/FloatEmoji: já respeitavam Reduce Motion (nenhuma ação).

---

## BUG-0049 — Investimentos: venda acima da posição, divisão por zero e 500 em tipo inválido

- **Problema relacionado:** PROB-0054
- **Data:** 2026-07-11
- **Area:** backend, investimentos, integridade financeira
- **Sintoma:** `InvestimentoService.adicionarMovimentacao` permitia VENDA com quantidade maior que a posição atual (quantidade final negativa), dividia por zero ao calcular preço médio em VENDA com posição zero, lançava `RuntimeException` genérica (500) para tipo de movimentação inválido, e BONIFICACAO somava `valorTotal` ao custo indevidamente (deveria ser custo zero, reduzindo o preço médio).
- **Causa raiz:** `adicionarMovimentacao`/`updateAtivoPosicao` implementados como atualização aritmética direta sobre `Ativo.quantidade`/`custoTotal` sem validação de domínio nem tratamento por tipo de movimentação (COMPRA/VENDA/DIVIDENDO/BONIFICACAO).
- **Correcao aplicada:** Reescrita de `adicionarMovimentacao` e `updateAtivoPosicao`: VENDA acima da posição rejeitada com `BusinessException` ("Quantidade insuficiente para venda..."); quantidade sempre > 0 e preço >= 0 (> 0 exceto BONIFICACAO); tipo inválido vira `BusinessException` em vez de exceção não tratada; DIVIDENDO não altera quantidade/custo; BONIFICACAO usa custo ZERO. Lookups de ativo migrados de `RuntimeException` para `ResourceNotFoundException`. Adicionalmente, integração opcional de caixa: novo campo `MovimentacaoRequest.carteiraId` — se informado, COMPRA debita e VENDA/DIVIDENDO creditam a carteira via `LedgerService.registrarMovimento` (nova origem `INVESTIMENTO`).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/InvestimentoService.java`, `backend/src/main/java/com/gestor/financeiro/dto/MovimentacaoRequest.java`, `backend/src/main/java/com/gestor/financeiro/model/enums/OrigemMovimentoCarteira.java`, `backend/src/main/resources/db/migration/V22__movimentos_carteira_origem_investimento.sql`, `backend/src/test/java/com/gestor/financeiro/InvestimentoServiceTest.java` (novo).
- **Testes/validacoes executadas:** 14 novos testes em `InvestimentoServiceTest` (venda acima da posição, venda sem posição não divide por zero, quantidade/preço não positivos, tipo inválido, bonificação sem custo, dividendo sem alterar posição, compra/venda/dividendo movimentando caixa, saldo insuficiente na compra, origem `INVESTIMENTO`, sem carteira não gera movimento). Suite completa: 116 testes, 0 falha. Migration V22 (chain V1..V22) aplicada em PostgreSQL 16 real via Docker CLI; CHECK confirmado aceitando `INVESTIMENTO` e rejeitando valor fora do domínio.
- **Resultado:** PASS
- **Ressalvas:** Integração de caixa é opt-in — enquanto o mobile não enviar `carteiraId`, patrimônio de investimentos e caixa seguem desacoplados (decisão de produto, não regressão). Migrations V20/V21/V22 e as mudanças de código deste fix ainda não commitadas/deployadas. `PostgresMigrationIT` segue dependente de Docker (PROB-0058).
- **Commit:** pendente

---

## BUG-0050 — Relatório somava transações canceladas em maiores despesas, gasto por conta e contagem

- **Problema relacionado:** PROB-0053 (também PROB-0035)
- **Data:** 2026-07-11
- **Area:** backend, relatórios, performance
- **Sintoma:** No relatório, `totalEntradas`/`totalSaidas` já excluíam transações canceladas (`ativa = false`), mas "maiores despesas", "gasto por conta" e a contagem de transações vinham de `findByUsuarioIdAndDataBetween` — que **não filtrava `ativa`** — então uma SAIDA cancelada aparecia entre as maiores despesas, somava no gasto por conta e inflava a contagem, divergindo dos totais. Em paralelo, relatórios e projeções carregavam listas completas em memória (risco de OOM com histórico grande).
- **Causa raiz:** `RelatorioService` e `ProjecaoService` mantiveram o padrão antigo de carregar entidades e filtrar/somar em Java (o dashboard já havia migrado para SQL). O load em memória do relatório usava uma query sem o predicado `ativa = true`.
- **Correcao aplicada:** `RelatorioService` migrado para 3 queries agregadas em `TransacaoRepository` (`findMaioresDespesas` com LEFT JOIN categoria + `ORDER BY valorTotal DESC` + `Pageable(0,10)`; `sumSaidasAgrupadoPorConta` com `GROUP BY` conta + `ORDER BY SUM DESC` + `Pageable(0,8)`; `countSaidasByUsuarioIdAndPeriodo`) — todas filtrando `ativa = true`, alinhando os três blocos aos totais. `ProjecaoService` trocou os helpers por `SUM(COALESCE(...))` no banco (`ContaFixaRepository.somarPlanejadoNoPeriodo`, `ParcelaRepository.somarValorNoPeriodo`, `FaturaCartaoRepository.somarValorTotalPorStatusNoPeriodo`). Contrato dos endpoints mantido.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/RelatorioService.java`, `.../service/ProjecaoService.java`, `.../repository/TransacaoRepository.java`, `.../repository/ContaFixaRepository.java`, `.../repository/ParcelaRepository.java`, `.../repository/FaturaCartaoRepository.java`, `backend/src/main/resources/db/migration/V23__relatorio_projecao_support_indexes.sql` (novo), `backend/src/test/java/com/gestor/financeiro/RelatorioServiceTest.java` (novo), `.../ProjecaoServiceTest.java` (novo).
- **Testes/validacoes executadas:** `RelatorioServiceTest` (3: totais ignorando ENTRADA e cancelada, maiores despesas ordenadas/limitadas/cor padrão sem categoria, gasto por conta agrupado/ordenado/tipo resolvido) e `ProjecaoServiceTest` (2: soma conta fixa pendente do mês e ignora paga, sem lançamentos mantém saldo). SQL logado confirma `group by`/`order by`/`fetch first N rows only`. Suíte completa: 121 testes, 0 falha. Migrations V1..V23 aplicadas em PostgreSQL 16 real (psql em container descartável); 3 índices de suporte criados (2 parciais `WHERE ativa/ativo = true` + 1 composto).
- **Resultado:** PASS
- **Ressalvas:** Índices de suporte não validados via `PostgresMigrationIT` (segue dependente de Docker/Testcontainers — PROB-0058); validação feita por psql direto. Projeção ainda emite ~3 queries por mês projetado (N pequeno). Mudanças ainda não commitadas.
- **Commit:** pendente

---

---

## BUG-0053 — Implementado rollover de credito/saldo devedor de fatura de cartao (R1/R2)

- **Problema relacionado:** PROB-0050 (fecha o restante do escopo, ja que pagamento parcial foi resolvido por BUG-0052)
- **Data:** 2026-07-11
- **Area:** backend, frontend, mobile, produto financeiro
- **Sintoma:** A regra de produto para credito de fatura (total `<= 0`) e saldo devedor rolado (pagamento parcial no fechamento) estava **especificada** em `SYSTEM_OVERVIEW.md` (decisao travada em 2026-07-11) mas **nao implementada** em codigo: `pagarFatura` nao tratava o caso de fatura com total zero/negativo e nao havia rollover explicito de credito ou divida entre faturas.
- **Causa raiz:** Modelo de fatura tratava credito/estorno como quitacao simples dentro da mesma fatura, sem mecanismo de "carregar" saldo (credor ou devedor) para a proxima competencia.
- **Correcao aplicada:** Arquitetura escolhida pelo dono do produto — **rollover lazy na leitura + servico idempotente + trava de banco**, sem endpoint de fechar fatura nem scheduler (status `FECHADA` continua derivado na leitura, como desde BUG-0020). Novo metodo `FaturaService.liquidarFaturaAnterior(...)`, chamado por `buscarAtual`, `buscarPorMes` e `criarOuBuscarFatura`: ao materializar a fatura de competencia M, liquida recursivamente para tras (M-1, M-2, ...) as faturas existentes ja fechadas. Recursao termina por competencia decrescendo estritamente, fatura anterior inexistente (nao materializa fatura retroativa vazia) ou teto de 24 meses.
  - **R1** (total da origem `<= 0`): gera lancamento `CREDITO_ANTERIOR` (valor negativo) na proxima fatura em aberto; marca a fatura de origem `PAGA` com `dataPagamento = dataFechamento`. Nunca cria `MovimentoCarteira`.
  - **R2** (total `> 0` e `valorPago < total`): gera `SALDO_DEVEDOR_ANTERIOR` (valor positivo = total - valorPago) na proxima fatura; nao altera status da origem alem do derivado padrao. Sem juros (fora de escopo do MVP).
  - **Idempotencia/trava:** guard em codigo `FaturaLancamentoRepository.existsByFaturaOrigemId` + lock pessimista na fatura de origem (`findWithLockByIdAndUsuarioId`) + unique index parcial `ux_fatura_rollover_origem_tipo (fatura_origem_id, tipo) WHERE fatura_origem_id IS NOT NULL` (backstop de banco, migration `V25__fatura_rollover.sql`); `DataIntegrityViolationException` tratada como no-op.
  - **Modelo:** enum `TipoFaturaLancamento` ganhou `CREDITO_ANTERIOR` e `SALDO_DEVEDOR_ANTERIOR`. `FaturaLancamento.transacao` passou a ser nullable; novo campo `faturaOrigem` para rastreabilidade. `toResponse` corrigido para nao dar NPE em lancamento sem transacao.
  - **UI web+mobile:** lancamentos `CREDITO_ANTERIOR` exibidos em verde ("Credito anterior"); `SALDO_DEVEDOR_ANTERIOR` em ambar/alerta ("Saldo devedor anterior", nunca vermelho). Tipos TS estendidos.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `backend/src/main/java/com/gestor/financeiro/model/FaturaLancamento.java`, `backend/src/main/java/com/gestor/financeiro/model/enums/TipoFaturaLancamento.java`, `backend/src/main/java/com/gestor/financeiro/repository/FaturaLancamentoRepository.java`, `backend/src/main/resources/db/migration/V25__fatura_rollover.sql` (novo), `frontend/src/pages/Faturas.tsx`, `frontend/src/services/faturaService.ts`, `mobile/app/(app)/more/faturas.tsx`, `mobile/src/services/faturaService.ts`, `mobile/src/types/index.ts`.
- **Testes/validacoes executadas:** `FaturaRolloverTest.java` (novo) — 7 casos: R1 basico, credito abate a proxima fatura, credito rola de novo (fatura seguinte tambem `<= 0`), R2 saldo devedor rolado, pagamento total sem gerar rollover, idempotencia em dupla leitura (buscar 2x nao duplica lancamento), cadeia de rollover com mes pulado (fatura intermediaria inexistente). Invariante `Conta.valorGasto` assertado dentro dos casos 1, 4 e 6. Execucao real desta rodada: `./mvnw -q test` → **Tests run: 142, Failures: 0, Errors: 0**; `scripts/verify-postgres-migrations.sh` → PASS (`PostgresMigrationIT` 5/0); frontend `npm run build --silent` → PASS; mobile `npm run lint --silent` → PASS. Nao-regressao: `FaturaCartaoWorkflowTest` 9/9 continua verde.
- **Resultado:** PASS
- **Ressalvas:**
  - Unique index `ux_fatura_rollover_origem_tipo` da migration V25 **nao existe no schema de teste** (H2 create-drop, Flyway desligado em teste) — idempotencia testada apenas pelo guard de codigo `existsByFaturaOrigemId`; o backstop de banco nao e exercitado por teste automatizado. Concorrencia real de 2 threads simultaneas materializando a mesma fatura futura **nao tem teste dedicado** (design coberto por lock pessimista + unique index, a validar em producao/PostgreSQL real — mesma limitacao estrutural de `PostgresMigrationIT` dependente de Docker, PROB-0058).
  - Unique index de rollover foi validado via `scripts/verify-postgres-migrations.sh` em PostgreSQL real de teste (`PostgresMigrationIT`), mas concorrencia real de 2 threads simultaneas materializando a mesma fatura futura ainda nao tem teste dedicado.
- **Commit:** `a62f594`, `70f24e5`

---

> Este arquivo e mantido pelo `docs-reporter`. Bugs corrigidos devem ser registrados com o proximo ID
> sequencial (BUG-0002, BUG-0003, ...). Para historico de versoes, consulte `docs/CHANGELOG.md`.
