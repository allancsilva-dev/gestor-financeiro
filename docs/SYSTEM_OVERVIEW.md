# System Overview — Gestor Financeiro

Documentacao de alto nivel sobre como o sistema funciona. Mantido pelo `docs-reporter`.

**Ultima atualizacao:** 2026-07-09

---

## Stack real do projeto

| Camada | Tecnologia | Versao |
|---|---|---|
| Backend runtime | Java + Spring Boot | Java 17, Spring Boot 3.5.7 |
| Build backend | Maven Wrapper | `./mvnw` |
| Banco de dados | PostgreSQL (prod), H2 (testes) | PostgreSQL 17+ |
| ORM | Spring Data JPA / Hibernate | — |
| Seguranca | Spring Security + JWT (jjwt) | jjwt 0.11.5 |
| Password hash | BCrypt | — |
| Frontend web | React + TypeScript + Vite + Tailwind CSS | React 19.2.0, TS ~5.9.3 |
| Graficos web | Recharts | 3.4.1 |
| HTTP client web | Axios | 1.13.2 |
| Roteamento web | React Router DOM | 7.9.6 |
| Testes web | Vitest + Testing Library + jsdom | Vitest 3.2.4 |
| Lint web | ESLint | 9.39.1 |
| Mobile | React Native + Expo | RN 0.81.5, Expo SDK 54 |
| Estilo mobile | NativeWind + Tailwind CSS | 4.2.3 / 3.4.17 |
| Estado mobile | TanStack React Query | 5.96.2 |
| Auth store mobile | Expo Secure Store (instalado, nao utilizado) | 15.0.8 |
| Documentacao API | SpringDoc OpenAPI (Swagger UI) | — |
| Monitoramento | Spring Boot Actuator + health-check script | — |
| Logging | SLF4J + Logback + Logstash encoder | — |
| CI/CD | GitHub Actions | — |
| Backup | Scripts pg_dump + Neon PITR | — |
| Deploy | Railway (backend), Vercel (frontend), Neon (DB) | — |

## Arquitetura geral

Monolito modular dividido em tres projetos:

```
gestor-financeiro/
├── backend/      # API REST Spring Boot
├── frontend/     # SPA React + Vite (web)
└── mobile/       # App React Native + Expo
```

O backend e o centro da arquitetura. Tanto frontend web quanto mobile consomem a mesma API REST.
O banco e PostgreSQL gerenciado via JPA/Hibernate com Flyway para migrations versionadas e `ddl-auto=validate` em dev e prod. O PR-LEDGER-01 adicionou validação automatizada com Testcontainers PostgreSQL para Flyway em banco limpo; o PR-LEDGER-02 adicionou o schema inicial do Ledger (`movimentos_carteira`) na migration `V11`; o PR-LEDGER-03 adicionou `LedgerService` para escrita atômica de movimento + saldo; o PR-LEDGER-04 adicionou reconciliação entre saldo materializado e saldo derivado do Ledger; o PR-LEDGER-05 adicionou backfill inicial idempotente na migration `V12`. Em 2026-07-08, o PostgreSQL VPS foi validado com Flyway 14 migrations, PostgreSQL 17.10 e Hibernate `ddl-auto=validate`; BUG-0010 corrigiu o mapeamento `moeda CHAR(3)`. Execução local da integração Testcontainers ainda requer Docker ativo. O projeto e single-tenant: cada usuario acessa apenas seus dados,
com validacao de ownership em todo endpoint que acessa recurso por ID.

### Diagrama de camadas (backend)

```
Controller (REST, orquestracao, validacao @Valid)
  └── Service (regra de negocio, @Transactional)
       └── Repository (Spring Data JPA, queries)
            └── Entidade (JPA, mapeamento relacional)
```

### Diagrama de camadas (frontend web)

```
Page (renderizacao, eventos)
  └── Hook (estado, cache, efeitos colaterais)
       └── Service (HTTP calls puras, Axios)
            └── Interceptor (token Bearer, refresh automatico)
```

## Modulos principais

### Backend (`backend/src/main/java/com/gestor/financeiro/`)

| Pacote | Responsabilidade | Itens |
|---|---|---|
| `config/` | Seguranca, JWT, CORS, rate limit | SecurityConfig, JwtUtil, JwtAuthenticationFilter, LoginRateLimitFilter, CustomUserDetailsService, OpenApiConfig |
| `controller/` | Endpoints REST | 15 controllers: Auth, Transacao, Categoria, Carteira, Conta, ContaFixa, Meta, Parcela, Dashboard, Usuario, Onboarding, Orcamento, Fatura, Relatorio, Export |
| `dto/` | Transferencia de dados | 35+ DTOs entre requests e responses |
| `exception/` | Tratamento de erros | GlobalExceptionHandler + 6 excecoes customizadas |
| `model/` | Entidades JPA | 14 entidades + enums |
| `repository/` | Acesso a dados | 14 repositorios Spring Data JPA |
| `security/` | Contexto de autenticacao | AuthenticatedUserService |
| `service/` | Regras de negocio | 21 services |
| `util/` | Utilitarios | PaginationUtils |

### Frontend web (`frontend/src/`)

| Diretorio | Responsabilidade |
|---|---|
| `pages/` | 15 paginas (Dashboard, Login, Transacoes, Categorias, etc.) |
| `components/` | Componentes reutilizaveis (ErrorBoundary, Chart*, UI) |
| `context/` | AuthContext (login, logout, refresh, getMe) |
| `services/` | api.ts (axios + interceptors) + 13 domain services |
| `hooks/` | Hooks customizados |
| `types/` | Tipos TypeScript compartilhados |

### Mobile (`mobile/`)

| Diretorio | Responsabilidade |
|---|---|
| `app/` | Expo Router file-based routing: (auth) e (app) |
| `src/components/ui/` | Componentes reutilizaveis |
| `src/context/` | AuthContext |
| `src/services/` | api.ts + 12 domain services |
| `src/theme/` | Tema dark/light |

## Fluxo de autenticacao

1. **Registro:** `POST /api/auth/register` → cria Usuario com senha BCrypt.
2. **Login:** `POST /api/auth/login` → valida credenciais, retorna `{ accessToken, usuario }` + cookie HttpOnly `refreshToken`.
   - Access token: JWT HS256, 15 min, Bearer header, subject = email.
   - Refresh token: UUID v4, 7 dias, cookie HttpOnly (`Path=/api/auth`, `SameSite=Lax`, `Secure` em prod).
3. **Refresh:** `POST /api/auth/refresh-token` → rotaciona refresh token com deteccao de reuse (revoca todos os tokens do usuario se detectar reuso). Resposta inclui `accessToken` e `csrfToken` (desde 2026-07-09, ver BUG-0013) — o cookie `refreshToken` (HttpOnly) segue sendo a fonte de verdade, o `csrfToken` tambem vai no corpo porque clientes nativos (React Native) nao leem cookies para o padrao double-submit.
4. **Interceptor Axios (web):** detecta 401, tenta refresh automatico, enfileira requisicoes concorrentes durante refresh.
5. **Interceptor Axios (mobile):** desde 2026-07-09 (BUG-0013), detecta 401 fora de rotas `/auth/`, chama `refresh-token` com `withCredentials:true` + header `X-CSRF-Token` (lido do `SecureStore`), usa uma promise deduplicada entre requests concorrentes e repete a request original com o novo Bearer token. Antes disso, o mobile so exibia mensagem amigavel e exigia login manual apos os 15 min de expiracao do access token.
6. **Logout:** `POST /api/auth/logout` → revoga refresh token, limpa cookie (Max-Age=0).
7. **Forgot password:** `POST /api/auth/forgot-password` → envia token por email. `POST /api/auth/reset-password` → redefine senha.

Rate limit: login 5/min/IP, forgot-password 3/min/IP (janela movel 60s, `LoginRateLimitFilter`).
Account lockout: 5 falhas consecutivas → bloqueio 15min. Login bem-sucedido reseta contador.

## Fluxo single-tenant (ownership)

O sistema e **single-tenant** — nao ha multi-tenancy corporativa. Cada usuario acessa apenas seus dados.

- Toda entidade possui `usuario_id` (FK para `usuarios`).
- `AuthenticatedUserService.getAuthenticatedUserId()` extrai o usuario do contexto Spring Security.
- Todo endpoint que acessa recurso por ID valida ownership: `buscarPorIdDoUsuario(id, userId)`.
- Nenhum endpoint aceita `usuario_id` no body da request (protecao IDOR).
- Listagens sempre filtradas por `usuarioId` no repository.

## Fluxo principal do produto

1. Usuario se cadastra (`/register`).
2. Usuario faz login (`/login`).
3. Onboarding financeiro guiado — configuracao de carteira, conta, categorias, renda e meta inicial.
4. Dashboard exibe resumo financeiro (saldo, entradas, saidas, graficos).
5. Usuario cria categorias personalizadas.
6. Usuario cria contas (credito, debito, dinheiro).
7. Usuario cria carteiras (dinheiro, conta bancaria, poupanca).
8. Usuario registra transacoes (entrada/saida, com ou sem parcelamento). Transacao so movimenta o saldo de uma carteira (Ledger) se `carteiraId` for enviado no payload — sem carteira, a transacao e contabilizada em relatorios/categorias mas nao gera `MovimentoCarteira` (ver BUG-0011/BUG-0012). Exclusao de transacao e soft-delete (`ativa=false`) com estorno automatico do movimento no Ledger; todas as leituras agregadas (dashboard, relatorios, listagens, fatura) filtram `ativa=true` desde 2026-07-09 (BUG-0014).
9. Usuario gerencia parcelas (pagar/despagar).
10. Usuario cria contas fixas mensais.
11. Usuario cria metas financeiras e acompanha progresso.
12. Usuario gerencia carteiras (adicionar/remover saldo).

## Integracoes

| Integracao | Status | Detalhes |
|---|---|---|
| Email (password reset) | Stub | Apenas loga no console. Implementacao real pendente (BACKLOG-0030). |
| Logstash | Parcial | `logstash-logback-encoder` no classpath, configuracao pendente. |
| Actuator | Implementado | `/actuator/health`, `/actuator/info`. Sem health check de banco. |
| Swagger | Implementado | SpringDoc OpenAPI em `/swagger-ui.html`. Publico em dev, autenticado em prod. |

## Principais decisoes tecnicas

1. **Spring Boot + JPA:** ecossistema maduro, facilidade de configuracao, ampla documentacao.
2. **JWT em vez de sessao:** API stateless, compatibilidade mobile, sem sticky sessions.
3. **Refresh token com rotacao e deteccao de reuse:** seguranca contra token theft sem sacrificar UX.
4. **Flyway migrations (antes era ddl-auto=update):** schema versionado, previsível e reproduzível entre ambientes. PROB-0006 resolvido. Teste PostgreSQL real automatizado via `mvn verify -Pintegration-test`.
5. **React Context API em vez de Redux/Zustand:** simplicidade para estado global limitado (apenas auth).
6. **Tailwind CSS em vez de CSS-in-JS:** produtividade, consistencia visual, baixo bundle size.
7. **Expo em vez de React Native puro:** build e deploy simplificados, OTA updates.
8. **Axios com interceptor de refresh:** fila de requisicoes concorrentes evita multiplos refresh tokens simultaneos.
9. **Rate limit custom (sem Bucket4j):** implementacao propria em `LoginRateLimitFilter`, janela movel, sem dependencia externa.
10. **Categoria.valorGasto so reflete SAIDA:** desde 2026-07-09 (BUG-0015), criar/deletar transacao so ajusta `valorGasto` da categoria quando `tipo == SAIDA` — entradas nunca contam como gasto no indicador de orcamento por categoria.
11. **Design mobile alinhado ao prototipo standalone:** componentes `Entrance` (`gf-rise`/`gf-pop`, respeita Reduce Motion) e `FloatEmoji` (`gf-float`) portados de `docs/Gestor Financeiro (standalone).html` para o app Expo; `Fab` com gradiente violeta `#7c5cfc`→`#8b2fff` e glow (BACKLOG-0048, 2026-07-09).

## Limitacoes conhecidas

1. **Migrations versionadas implementadas (Flyway):** `V1__baseline_schema.sql` com 10 tabelas e migrations até `V11__movimento_carteira.sql`. `ddl-auto=validate` em dev e prod. PROB-0006 resolvido.
2. **Sem testes no mobile:** nao ha scripts de test/lint configurados no `mobile/package.json`.
3. **Cobertura de testes backend limitada:** suite atual passa com 43 testes, mas cobre poucos fluxos comparado ao tamanho do domínio.
4. **Cobertura de testes frontend limitada:** poucos testes Vitest configurados.
5. **Sem politica de privacidade documentada:** relevante para conformidade LGPD.
6. **Sem exportacao de dados do usuario:** nao ha endpoint de portabilidade (LGPD).
7. **Sem CI/CD:** build e deploy manuais.
8. **Mobile sem testes e2e:** nao ha Detox, Maestro ou similar.
9. **CSP basico:** Content-Security-Policy configurado, mas pouco restritivo.
10. **Sem cache de API (Redis):** sem caching de respostas frequentes.
11. **IDOR em TransacaoService corrigido:** categoriaId/contaId agora validam ownership via `findByIdAndUsuarioId` (PROB-0001 resolvido).
12. **Optimistic locking implementado:** @Version em Carteira, Conta, Meta e Categoria. OptimisticLockingFailureException → 409 Conflict. PROB-0002 resolvido.
13. **Politica de senha fortalecida:** min 8 caracteres, ao menos 1 letra e 1 digito, via @ValidPassword. (PROB-0007 resolvido).
14. **Account lockout implementado:** bloqueio temporario apos N falhas consecutivas, reset apos sucesso. Configuravel via security.auth.*. (PROB-0023 resolvido).
14. **Mobile sem persistencia de sessao:** token em memoria — login obrigatorio toda abertura do app (PROB-0013).
15. **Mobile com IP hardcoded:** URL da API fixa — inutilizavel fora da rede do dev (PROB-0014).
16. **Email service stub:** recuperacao de senha nao envia email real (BACKLOG-0030).
17. **CSRF ausente no frontend web:** withCredentials:true sem token CSRF (PROB-0019).
18. **Queries otimizadas:** findAll() substituido por JPQL UPDATE filtrado (PROB-0003, PROB-0020 resolvidos).
19. **Dashboard otimizado:** agregacoes SQL SUM/COUNT, zero carregamento de entidades em memoria (PROB-0004 resolvido).
20. **54 `any` types no frontend:** zero type safety nos services e componentes (PROB-0027).

## Pontos frageis atuais

1. **Ownership implementado:** todos os services validam posse via `findByIdAndUsuarioId` ou `buscarPorIdDoUsuario`. PROB-0001 e PROB-0021 resolvidos.
2. **Rate limit com account lockout:** login/register/reset-password/forgot-password/validate-token com rate limit por IP. Account lockout por email apos falhas consecutivas. Limpeza periodica de entradas expiradas. (PROB-0008, PROB-0023, PROB-0024 resolvidos).
3. **Schema versionado com Flyway:** migrations em `db/migration/`, `ddl-auto=validate` em dev e prod. PROB-0006 resolvido.
4. **Refresh token no banco:** se o banco ficar lento, toda autenticacao sofre. Sem cache (Redis) para tokens.
5. **LoginRateLimitFilter com protecao completa:** register (5/min), login (5/min), forgot-password (3/min), reset-password (5/min), validate-token (10/min). Account lockout apos 5 falhas. @Scheduled cleanup de entradas expiradas a cada 60s. (PROB-0024 resolvido).
6. **Logs sem PII:** EmailService com maskEmail. Token nunca logado. (PROB-0011 resolvido).
7. **Contrato de erro padronizado:** ApiError com code, message, timestamp, requestId. X-Request-Id header. MDC requestId para correlacao de logs.
8. **Mobile usa Expo Secure Store:** instalado mas nao implementado. Token em memoria apenas (PROB-0013).
9. **Configurações de produção seguras:** cookie.secure=true, CORS sem fallback, secrets sem default (PROB-0005, PROB-0010 resolvidos).
10. **TransacaoService com ownership corrigido:** categoriaId e contaId validados via `findByIdAndUsuarioId` (PROB-0001 resolvido).
11. **Operacoes transacionais corrigidas:** todos os metodos de escrita com @Transactional (PROB-0012 resolvido).
12. **@Autowired field injection em vez de constructor injection:** dificulta testes unitarios e immutability.
13. **Ledger so movimenta com carteiraId explicito:** ausencia de `carteiraId` no payload de transacao e uma decisao de design (nao um bug), mas exige que todo client (web/mobile) sempre envie a carteira; o mobile so passou a fazer isso a partir de 2026-07-09 (BUG-0012). Transacoes antigas sem carteira nao tem movimento retroativo (BACKLOG-0045).
14. **Interceptor de refresh agora simetrico entre web e mobile:** ambos os clientes renovam o access token automaticamente em 401 (BUG-0013, 2026-07-09). Antes disso, apenas o web tinha esse comportamento.

## Auditoria e estado atual

- **Data:** 2026-07-06
- **Tipo:** Auditoria completa de seguranca, bugs e codigo inacabado (read-only)
- **Escopo:** backend (100%), frontend web (100%), mobile (100%)
- **Resultado original:** PASS_COM_RESSALVA — sistema funcional mas nao pronto para producao
- **Achados originais:** 15 CRITICAL, 12 HIGH, 32 MEDIUM, 24 LOW (83 total)
- **Relatorio:** `docs/REVIEW_REPORTS/2026-07-06_full-system_security-and-bug-audit.md`
- **Problem Ledger:** 30 problemas registrados (PROB-0001 a PROB-0030)
- **Backlog:** 35 itens registrados (BACKLOG-0001 a BACKLOG-0035)
- **Atualizacao pos-auditoria:** Fase 0 backend concluida em 2026-07-07 com ressalvas (7 PRs). Fase 1 concluida (7 PRs). Fase 2 concluida (8 PRs). Fase 3 concluida em 2026-07-08 (5 PRs: CI/CD, deploy, backup, monitoramento, docs). Fase 4 concluida em 2026-07-08 (4 PRs: importacao CSV, anexos, investimentos, insights). Fase Ledger registrada em 2026-07-08: PR-LEDGER-00 `PASS`, PR-LEDGER-01..20 `PASS_COM_RESSALVA`. Backend: 69/69 testes PASS; smoke VPS PostgreSQL PASS.
- **Correcoes 2026-07-09 (diagnostico manual via replicacao de payloads mobile contra API local):** BUG-0011 a BUG-0016 corrigidos (PROB-0032 a PROB-0037) — detached entity Carteira ao criar transacao com `carteiraId` (500), saldo total congelado por ausencia de `carteiraId` no mobile, sessao mobile sem refresh automatico apos 15 min, agregacoes financeiras ignorando soft-delete (`ativa=true`), `categoria.valorGasto` somando ENTRADA indevidamente, vazamento de hash de senha no response de `/api/auth/register`. Todos com `mvn test` 69/69 PASS e validacao E2E manual via API. Efeitos visuais do prototipo standalone (`Entrance`, `FloatEmoji`, `Fab` com gradiente) portados para o mobile (BACKLOG-0048). Ver `docs/BUGFIX_LOG.md` (BUG-0011..BUG-0016) e `docs/PROBLEM_LEDGER.md` (PROB-0032..PROB-0037) para detalhes.
