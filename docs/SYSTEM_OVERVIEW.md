# System Overview — Gestor Financeiro

Documentacao de alto nivel sobre como o sistema funciona. Mantido pelo `docs-reporter`.

**Ultima atualizacao:** 2026-07-06

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
| Monitoramento | Spring Boot Actuator | — |
| Logging | SLF4J + Logback + Logstash encoder | — |

## Arquitetura geral

Monolito modular dividido em tres projetos:

```
gestor-financeiro/
├── backend/      # API REST Spring Boot
├── frontend/     # SPA React + Vite (web)
└── mobile/       # App React Native + Expo
```

O backend e o centro da arquitetura. Tanto frontend web quanto mobile consomem a mesma API REST.
O banco e PostgreSQL gerenciado via JPA/Hibernate com Flyway para migrations versionadas e `ddl-auto=validate` em dev e prod. O projeto e single-tenant: cada usuario acessa apenas seus dados,
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
| `controller/` | Endpoints REST | 10 controllers: Auth, Transacao, Categoria, Carteira, Conta, ContaFixa, Meta, Parcela, Dashboard, Usuario |
| `dto/` | Transferencia de dados | 25 DTOs entre requests e responses |
| `exception/` | Tratamento de erros | GlobalExceptionHandler + 4 excecoes customizadas |
| `model/` | Entidades JPA | 11 entidades + enums |
| `repository/` | Acesso a dados | 10 repositorios Spring Data JPA |
| `security/` | Contexto de autenticacao | AuthenticatedUserService |
| `service/` | Regras de negocio | 10 services |
| `util/` | Utilitarios | PaginationUtils |

### Frontend web (`frontend/src/`)

| Diretorio | Responsabilidade |
|---|---|
| `pages/` | 11 paginas (Dashboard, Login, Transacoes, Categorias, etc.) |
| `components/` | Componentes reutilizaveis (ErrorBoundary, Chart*, UI) |
| `context/` | AuthContext (login, logout, refresh, getMe) |
| `services/` | api.ts (axios + interceptors) + 9 domain services |
| `hooks/` | Hooks customizados |
| `types/` | Tipos TypeScript compartilhados |

### Mobile (`mobile/`)

| Diretorio | Responsabilidade |
|---|---|
| `app/` | Expo Router file-based routing: (auth) e (app) |
| `src/components/ui/` | Componentes reutilizaveis |
| `src/context/` | AuthContext |
| `src/services/` | api.ts + 8 domain services |
| `src/theme/` | Tema dark/light |

## Fluxo de autenticacao

1. **Registro:** `POST /api/auth/register` → cria Usuario com senha BCrypt.
2. **Login:** `POST /api/auth/login` → valida credenciais, retorna `{ accessToken, usuario }` + cookie HttpOnly `refreshToken`.
   - Access token: JWT HS256, 15 min, Bearer header, subject = email.
   - Refresh token: UUID v4, 7 dias, cookie HttpOnly (`Path=/api/auth`, `SameSite=Lax`, `Secure` em prod).
3. **Refresh:** `POST /api/auth/refresh-token` → rotaciona refresh token com deteccao de reuse (revoca todos os tokens do usuario se detectar reuso).
4. **Interceptor Axios (web):** detecta 401, tenta refresh automatico, enfileira requisicoes concorrentes durante refresh.
5. **Interceptor Axios (mobile):** detecta erro, exibe mensagem amigavel em pt-BR.
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
3. Dashboard exibe resumo financeiro (saldo, entradas, saidas, graficos).
4. Usuario cria categorias personalizadas.
5. Usuario cria contas (credito, debito, dinheiro).
6. Usuario cria carteiras (dinheiro, conta bancaria, poupanca).
7. Usuario registra transacoes (entrada/saida, com ou sem parcelamento).
8. Usuario gerencia parcelas (pagar/despagar).
9. Usuario cria contas fixas mensais.
10. Usuario cria metas financeiras e acompanha progresso.
11. Usuario gerencia carteiras (adicionar/remover saldo).

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
4. **Flyway migrations (antes era ddl-auto=update):** schema versionado, previsível e reproduzível entre ambientes. PROB-0006 resolvido.
5. **React Context API em vez de Redux/Zustand:** simplicidade para estado global limitado (apenas auth).
6. **Tailwind CSS em vez de CSS-in-JS:** produtividade, consistencia visual, baixo bundle size.
7. **Expo em vez de React Native puro:** build e deploy simplificados, OTA updates.
8. **Axios com interceptor de refresh:** fila de requisicoes concorrentes evita multiplos refresh tokens simultaneos.
9. **Rate limit custom (sem Bucket4j):** implementacao propria em `LoginRateLimitFilter`, janela movel, sem dependencia externa.

## Limitacoes conhecidas

1. **Migrations versionadas implementadas (Flyway):** `V1__baseline_schema.sql` com 10 tabelas. `ddl-auto=validate` em dev e prod. PROB-0006 resolvido.
2. **Sem testes no mobile:** nao ha scripts de test/lint configurados no `mobile/package.json`.
3. **Cobertura de testes backend limitada:** apenas 6 arquivos de teste (Auth, Transacao, Security, Infrastructure, Application, TestDataFactory).
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
20. **49 `any` types no frontend:** zero type safety nos services (PROB-0027).

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

## Auditoria mais recente

- **Data:** 2026-07-06
- **Tipo:** Auditoria completa de seguranca, bugs e codigo inacabado (read-only)
- **Escopo:** backend (100%), frontend web (100%), mobile (100%)
- **Resultado:** PASS_COM_RESSALVA — sistema funcional mas nao pronto para producao
- **Achados:** 15 CRITICAL, 12 HIGH, 32 MEDIUM, 24 LOW (83 total)
- **Relatorio:** `docs/REVIEW_REPORTS/2026-07-06_full-system_security-and-bug-audit.md`
- **Problem Ledger:** 30 problemas registrados (PROB-0001 a PROB-0030)
- **Backlog:** 35 itens registrados (BACKLOG-0001 a BACKLOG-0035)
