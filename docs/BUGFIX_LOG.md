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
- **Ressalvas:** Sem Docker Compose — validação de startup com PostgreSQL limpo não executada localmente. Risco baixo: migration gerada a partir das entidades JPA existentes.
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
- **Ressalvas:** PostgreSQL validation com Docker nao executada neste ambiente (requer Docker runtime). Flyway validado apenas via entidades JPA + H2.
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

> Este arquivo e mantido pelo `docs-reporter`. Bugs corrigidos devem ser registrados com o proximo ID
> sequencial (BUG-0002, BUG-0003, ...). Para historico de versoes, consulte `docs/CHANGELOG.md`.
