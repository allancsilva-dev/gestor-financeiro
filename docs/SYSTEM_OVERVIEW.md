# System Overview — Gestor Financeiro

Documentacao de alto nivel sobre como o sistema funciona. Mantido pelo `docs-reporter`.

**Ultima atualizacao:** 2026-08-29, quarta rodada do dia (working tree nao commitado): dono do
produto tentou cadastrar a assinatura da Netflix (R$60/mes no cartao de credito) e nao encontrou
como — PROB-0098. Diagnostico: o motor de recorrencia (`ContaFixa`/`ExecucaoRecorrencia`/
`RecorrenciaScheduler`) so sabia debitar caixa (`carteira_id`); no cartao so existia parcelamento.
Migration `V67__recorrencia_cartao.sql` (expand puro, ADR-0015) da a `ContaFixa` dois destinos
mutuamente exclusivos — `carteira_id` (caixa) ou `conta_id` (cartao) —, seguindo o padrao "um
destino, nunca dois" da V55; o ramo cartao reaproveita `TransacaoService.criar` →
`FaturaService.registrarCompraCartao` sem duplicar regra de fatura (ADR-0001). Cinco defeitos
pre-existentes no motor de recorrencia foram corrigidos na mesma sessao para o cartao poder usar o
motor com seguranca — ver item 29 abaixo e BUG-0098 a BUG-0102: (1) `Idempotency-Key` descartada em
qualquer compra de cartao; (2) execucao automatica nao revalidava vencimento sob lock (dupla
execucao com duas instancias); (3) `carteiraId` do corpo desviava cobranca de cartao para o caixa;
(4) corrida no unique de `execucoes_recorrencia` devolvia 500 em vez de 422; (5) exclusao de cartao
nao desativava assinaturas vinculadas. Backend 501 testes (0 falhas, +10 `ContaFixaCartaoTest`, +1
`UsuarioExclusaoTest`), mobile 447 testes/40 suites (0 falhas), `scripts/verify-postgres-
migrations.sh` exit 0 (v67 em PostgreSQL 16 real). Runtime (backend 8081, banco limpo): assinatura
criada com `cartao` no response, cobranca lancada na fatura correta sem parcelas, reexecucao 422 sem
duplicar, `carteiraId` no corpo ignorado para cartao, DELETE do cartao (204) desativou as
assinaturas, restart preservou as contagens (recuperarAoIniciar nao duplicou). Mobile:
`NovaTransacaoModal` ganhou switch "Repete todo mes" (SAIDA, fora do assistente),
`app/(app)/more/contas-fixas.tsx` ganhou seletor "Cobrar em" (Conta/Cartao), novo
`mobile/src/domain/recorrencia.ts` espelha o calculo de proximo vencimento so para exibicao. Web
fora de escopo por decisao mobile-first ja registrada (BACKLOG-0115). Ver PROB-0098,
BUG-0098/0099/0100/0101/0102 e BACKLOG-0120 a BACKLOG-0126 para o detalhamento completo.

**Atualizacao anterior (mesmo dia, terceira rodada):** 2026-08-29 (working tree nao commitado, mesma
sessao das correcoes de PROB-0092/0093/0094): dono do produto pediu verificacao de que as correcoes
estavam "100% funcionais para o UI/UX" — teste unitario nao prova layout, entao a verificacao foi
feita no simulador iOS (iPhone 17 Pro, iOS 26.5, tema escuro) com Maestro. Foram necessarias **8
rodadas ate o primeiro verde legitimo**, e o motivo e o achado mais grave da rodada: PROB-0095, o
smoke de UI (`scripts/e2e-mobile-ios.sh`) instalava o app **errado** (Release antigo sombreando o
Debug novo no mesmo `DerivedData`) e, quando corrigido para instalar o app certo, um build Debug sem
assinatura derrubava o flow com o LogBox vermelho do `expo-notifications` — ou seja, **este smoke
nao pegava regressao de UI ha tempo**, o que ajuda a explicar por que PROB-0092/0093/0094 chegaram a
producao. A propria rodada de verificacao revelou (e corrigiu, na mesma sessao) duas regressoes
novas introduzidas pelas correcoes anteriores: PROB-0096 (conteudo do padrao de tela subindo por
baixo da status bar ao rolar, regressao de aplicar a "Receita de tela" do `DESIGN.md` na correcao de
PROB-0094) e PROB-0097 (campo obrigatorio "Saldo inicial" do onboarding coberto pelo teclado depois
que PROB-0093 acrescentou o campo Banco ao passo 1). Verde final: smoke financeiro iOS (`OK: smoke
financeiro concluido`, 1 teste Maestro / 0 falhas / 167s, reconciliacao global 8 verificacoes / 0
divergencias) e Assistente iOS (`OK: Assistente iOS`, 6 flows + `importacao-mobile`, todos com prova
financeira e 0 divergencias, rodando **sem** `EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED` — prova em runtime
do gate do ADR-0018). Backend 481 testes, mobile 437, web 47, todos verdes. Ver
PROB-0095/0096/0097 e BACKLOG-0117/0118/0119 para o detalhamento completo. **Limite explicito desta
rodada:** so iPhone 17 Pro / iOS 26.5 / tema escuro — Android, telas pequenas, tema claro e fonte
ampliada nao foram testados (BACKLOG-0119). O web foi deliberadamente deixado fora desta rodada por
decisao do dono do produto (ver BACKLOG-0115, que segue como estava).

**Atualizacao anterior (mesmo dia, segunda rodada):** 2026-08-29, working tree nao commitado sobre o
`main` do fechamento da Fase 5): dono do produto subiu novo backend e novo build do app e reportou
tres problemas em producao, todos investigados e corrigidos na mesma sessao — PROB-0092
(Assistente inacessivel por dois gates independentes: env var `EXPO_PUBLIC_*` fixada no build do
app + `/api/v1/assistant/**` sem variavel de ambiente no container, ver ADR-0018), PROB-0093 (sem
edicao de saldo de conta; "Conta Principal" nao existia como dado, so como texto default de
onboarding, migration `V66`) e PROB-0094 (tela de Relatorios com dois `ScrollView` irmaos dividindo
espaco livre ~50/50, regressao do commit `d44fc43`, mais estado vazio contando so saidas). Backend
481 testes verdes, mobile 437 testes verdes, migration `V66` validada contra PostgreSQL 16 real via
`scripts/verify-postgres-migrations.sh`. Contagens conferidas contra o codigo nesta rodada: backend
com **32 controllers** (`+1`, `CapacidadesController`), **65 migrations** (`+1`, ate `V66`), 134
classes de service; mobile com 31 rotas e **25 services** (`+1`, `capacidadesService`). Ver
PROB-0092/0093/0094, BACKLOG-0115/0116 e ADR-0018 para o detalhamento completo.

**Atualizacao anterior (mesmo dia, primeira rodada):** 2026-08-29 (`main` em `61c0025`, working tree
nao commitado): rodada de fechamento operacional da Fase 5 executada de ponta a ponta em simulador
iOS, com os seis flows do assistente verdes e prova financeira por flow
(`artifacts/fase5/run-20/`). Nesta mesma rodada o assistente ganhou **parcelamento no cartao**
(migration `V65`) e foram fechados PROB-0086 a PROB-0091. Contagens conferidas contra o codigo
nesta data: backend com 31 controllers, 40 entidades, 36 repositorios, 134 classes de service, 64
migrations (numeradas ate V65) e 470 testes; mobile com 31 rotas, 24 services, 11 flows Maestro e
434 testes. Secoes "Modulos principais", "Stack real", "Fluxo principal do
produto", "Integracoes", "Limitacoes conhecidas" e "Pontos frageis" foram reconferidas linha a linha
nesta data — antes acumulavam afirmacoes de julho que ja nao descreviam o sistema (ver
BACKLOG-0091, defasagem documental).

**Atualizacao anterior:** 2026-08-26 (`main` em `e885ed7`): PR-F4-01 adiciona fundacao canonica de
importacao no backend. `FinancialDataConnector` recebe somente fonte streaming, sem `Path`, URL ou
colecao em memoria. V46 cria `import_batches` e `import_records` com ownership, lifecycle,
optimistic lock, idempotencia, constraints, indices e exclusao LGPD.

**Atualizacao anterior:** 2026-08-25 (`main` em `e885ed7`, working tree limpo): correcoes de sessao
mobile, padronizacao visual e verificacao Maestro descritas abaixo estao commitadas em `main`.
Refresh expirado/revogado retorna `SESSION_EXPIRED` (401), o mobile encerra a sessao morta e o
desbloqueio local valida a sessao no servidor. Ver item 29, PROB-0085 e BUG-0096/BUG-0097.

**Atualizacao anterior:** 2026-08-22 (branch `design/pr13-perfil`, commits `9c1335be`..`ad5fc022`,
mergeada em `main` no intervalo `12571a4..HEAD`: serie de 13 PRs que unificou o padrao visual do
app mobile — kit `ui/` normalizado, tres telas de referencia (Home/Carteira/Metas) fechadas num so
padrao, trinco `padraoVisual.test.ts` e migracao das 10 telas restantes de `app/**` para o padrao
novo — ver item 27 da lista de decisoes tecnicas, BUG-0083 a BUG-0091, BACKLOG-0098/0099/0100/0101;
rodada de verificacao runtime com os quatro flows Maestro verdes tambem faz parte desta janela — ver
item 28, BUG-0092 a BUG-0095, `docs/REVIEW_REPORTS/2026-08-22_mobile_verification_maestro-runtime-padrao-visual.md`)

**Duas atualizacoes atras:** 2026-08-21 (working tree nao commitado sobre `main` em `12cc447`: redesign de
`mobile/app/(app)/ajustes.tsx` para o padrao visual de Home/Carteira/Metas, escolha de tema
claro/escuro/sistema pelo usuario (antes so seguia o SO), exclusao de conta LGPD consumida pela
primeira vez no mobile via `DELETE /v1/usuarios/me`, e `DESIGN.md` reescrito para refletir a marca
ciano atual — ver item 26 da lista de decisoes tecnicas, PROB-0083, BACKLOG-0077/0094)

**Atualizacao anterior a essa:** 2026-08-19 (branch `chore/remove-prototipo`, working tree nao commitado: protótipo HTML e redesign visual "Fase 4" dark-first ciano descartados por inteiro por decisao do dono do produto — ver item 25 da lista de decisoes tecnicas, PROB-0082, BACKLOG-0090). **Nota de consistencia registrada em 2026-08-21:** apos essa reversao, commits legitimos e ja mergeados em `main` (`9a3b205`, `63df4b1`, `73caf8b`, `88ae1ea`, `e3250e9`, `66375e1`, `a8d39df`, `d89bd62`, `12cc447` — navegacao/tema, carteira de cartoes, home, tokens visuais e redesign de metas) substituiram a tab bar `Início · Transações · + · Planejamento · Mais` citada no item 25 pela atual `Início · Análises · + · Metas · Ajustes` (`mobile/app/(app)/_layout.tsx`) e a marca voltou a ser ciano (`mobile/src/theme/colors.ts`, `brand: '#17b3ff'`) — nao ha contradicao real, apenas o item 25 descreve um estado intermediario que ja foi superado por trabalho seguinte nao registrado em detalhe neste arquivo pelas rodadas anteriores do `docs-reporter`. Ver BACKLOG-0091 (mesmo tipo de defasagem documental, ja registrado).

**Registro historico mais antigo:** 2026-07-14 (hardening pre-producao P0+P1 commitado em `main`: `5c08ce0`, `0d1e0c0`, `c959dfc`; cadeia de resolucao de IP na stack de proxy corrigida, pagamento de parcela idempotente contra duplo debito, exclusao de carteira sem 500, indices de suporte, headers de seguranca no SPA, reset de senha sem token na query string)

---

## Stack real do projeto

| Camada | Tecnologia | Versao |
|---|---|---|
| Backend runtime | Java + Spring Boot | Java 17, Spring Boot 3.5.16 |
| Build backend | Maven Wrapper | `./mvnw` |
| Banco de dados | PostgreSQL (prod), H2 (testes) | PostgreSQL 17+ |
| ORM | Spring Data JPA / Hibernate | — |
| Seguranca | Spring Security + JWT (jjwt) | jjwt 0.11.5 |
| Password hash | BCrypt | — |
| Frontend web | React + TypeScript + Vite + Tailwind CSS | React 19.2.0, TS ~5.9.3, Vite 7.3.5 |
| Graficos web | Recharts | 3.4.1 |
| HTTP client web | Axios | 1.13.2 |
| Roteamento web | React Router DOM | 7.9.6 |
| Testes web | Vitest + Testing Library + jsdom | Vitest 3.2.4 |
| Lint web | ESLint | 9.39.1 |
| Mobile | React Native + Expo (Expo Router) | RN 0.81.5, Expo SDK ~54.0.37, expo-router ~6.0.24 |
| Estilo mobile | Tokens proprios (`src/theme/tokens.ts`) + StyleSheet | NativeWind/Tailwind **nao sao mais usados** no mobile |
| Estado mobile | TanStack React Query | 5.96.2 |
| Auth store mobile | Expo Secure Store (access/refresh token, usuario cache) | 15.0.8 |
| Audio mobile | expo-audio (gravacao M4A para o assistente) | ~1.1.1 |
| Deteccao de aparelho | expo-device (`Device.isDevice`, guarda de push) | ~8.0.10 |
| Observabilidade mobile | Sentry React Native (desligado sem DSN) | ~7.2.0 |
| E2E mobile | Maestro (flows em `mobile/.maestro/`) | 2.6.1 na maquina de referencia |
| Migrations | Flyway (64 arquivos, numerados ate `V65`) | `ddl-auto=validate` |
| Resiliencia | Resilience4j (bulkhead, timeout, circuit breaker, retry) | 2.4.0 |
| Assistente (IA) | Gemini / OpenAI via schema estrito, **desligados por padrao** | providers fake no profile `local-e2e` |
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

O backend e o centro da arquitetura: web e mobile consomem a mesma API REST. O banco e PostgreSQL
via JPA/Hibernate, com Flyway para migrations versionadas e `ddl-auto=validate` em dev e prod. O
projeto e single-tenant — cada usuario acessa apenas seus dados, com validacao de ownership em todo
endpoint que acessa recurso por ID.

**Ledger como fonte da verdade do saldo.** Saldo de carteira nao e um campo editado a esmo: toda
mudanca passa por `movimentos_carteira` (`LedgerService`), com escrita atomica de movimento + saldo
e chave de idempotencia por origem. A reconciliacao global compara o saldo materializado com o saldo
derivado do Ledger e nunca corrige dados sozinha — ela acusa divergencia
(`ReconciliacaoHealthIndicator`, `/v1/reconciliacao/global`). Todo gate de release do projeto exige
reconciliacao sem divergencia, e e por isso que o E2E do assistente prova cada flow contra o banco
em vez de confiar na tela.

**Migrations em PostgreSQL real.** `scripts/verify-postgres-migrations.sh` sobe PostgreSQL 16 em
Docker e roda `PostgresMigrationIT` com `POSTGRES_IT_JDBC_URL`, sem depender do socket do
Testcontainers; `mvn verify -Pintegration-test` continua disponivel quando o Testcontainers estiver
saudavel. Sem daemon Docker acessivel, esses dois gates simplesmente nao rodam — uma rodada
"verde" sem eles nao prova schema.

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

Contagens conferidas em 2026-08-29.

| Pacote | Responsabilidade | Itens |
|---|---|---|
| `config/` | Seguranca, JWT, CORS, rate limit, perfil de E2E | 14 classes: SecurityConfig, JwtUtil, JwtAuthenticationFilter, LoginRateLimitFilter, CustomUserDetailsService, OpenApiConfig e as duas exclusivas do profile `local-e2e` (`LocalE2eAssistantConfiguration`, `LocalE2eAssistantFaultFilter`) |
| `controller/` | Endpoints REST | **31 controllers**: Anexo, Assistant, AssistantAudio, Auth, Cartao, Categoria, Compromissos, ContaFinanceira, ContaFixa, Dashboard, Export, Fatura, Home, Import, Importacao, Insights, Investimento, Meta, Metricas, Notificacao, Onboarding, Orcamento, Parcela, ReconciliacaoGlobal, RecorrenciaSugestao, RegraCategoria, Relatorio, Transacao, Transferencia, Usuario, Whatsapp |
| `dto/` | Transferencia de dados | **83 arquivos** de DTO (muitos agrupam varios records, como `AssistantDtos`) |
| `exception/` | Tratamento de erros | GlobalExceptionHandler + 12 excecoes (inclui `AssistantException`, `SessaoExpiradaException`) |
| `model/` | Entidades JPA | **40 entidades** + 34 enums |
| `repository/` | Acesso a dados | **36 repositorios** Spring Data JPA |
| `security/` | Contexto de autenticacao | AuthenticatedUserService |
| `service/` | Regras de negocio | **134 classes**: 50 na raiz + 84 em sete subpacotes (`assistant/`, `importacao/`, `job/`, `meta/`, `notificacao/`, `orcamento/`, `recorrencia/`) |
| `util/` | Utilitarios | PaginationUtils |
| `db/migration/` | Schema versionado | **66 migrations**, numeradas ate `V67` (a serie tem lacunas de numeracao) |

### Frontend web (`frontend/src/`)

O web ficou para tras de proposito: o produto e mobile-first e a Fase 5 nao entregou nada de
assistente no SPA (decisao registrada no ADR-0017). Ultimo commit tocando `frontend/` em 2026-08-21.

| Diretorio | Responsabilidade |
|---|---|
| `pages/` | **19 paginas** (Dashboard, Login, Transacoes, Categorias, Faturas, etc.) |
| `components/` | Componentes reutilizaveis (ErrorBoundary, Chart*, UI) |
| `context/` | AuthContext (login, logout, refresh, getMe) |
| `services/` | api.ts (axios + interceptors) + domain services |
| `hooks/` | Hooks customizados |
| `domain/`, `types/` | Regras e tipos TypeScript compartilhados |

### Mobile (`mobile/`)

| Diretorio | Responsabilidade |
|---|---|
| `app/` | Expo Router file-based routing: **31 rotas** em `(auth)`, `(app)`, `(app)/(inicio)` e `(app)/more/` |
| `src/components/` | 42 componentes, dos quais o kit `ui/` (normalizado na serie de padronizacao visual) e os modais de alto trafego (`NovaTransacaoModal` — tambem a tela de revisao do assistente) |
| `src/notificacoes/` | Registro de push; guarda por `Device.isDevice` (PROB-0087) |
| `src/context/` | AuthContext, TemaContext (desde 2026-08-21 — escolha de tema pelo usuario) |
| `src/services/` | **24 arquivos**, incluindo `api.ts` (axios + interceptors) e os services de dominio (`assistantService`, `importacaoService`, `notificacaoService`, `usuarioService`) |
| `src/__tests__/` | **40 arquivos de teste**, 447 casos (Jest + Testing Library) — inclui `recorrenciaCartao.test.tsx` (novo, 2026-08-29) |
| `.maestro/` | 11 flows E2E: seis do assistente (`assistant-*`), `financial-critical`, `smoke-auth`, `privacy-consent`, `recovery-navigation`, `importacao-mobile` |
| `src/theme/` | Tema dark/light — desde 2026-08-21 o esquema efetivo (`useEsquema()`/`useTheme()` em `theme/index.ts`) le `TemaContext` quando presente (escolha do usuario em Ajustes, persistida em `src/store/temaPreferido.ts` via SecureStore) e cai em `useColorScheme()` do SO quando nao ha provider ou a preferencia e `sistema` — antes so seguia o SO, sem opt-out |
| `src/domain/periodo.ts` | Desde 2026-08-21 — `iso`, competencia, aritmetica de mes e intervalo de periodo em hora local, centralizados (antes reescrito inline em 6+ telas); testado em `src/__tests__/periodo.test.ts` |
| `src/__tests__/padraoVisual.test.ts` | Desde 2026-08-21 — trinco que varre `app/**` e falha em numero cru (fontSize/espacamento/raio), hex literal, `ActivityIndicator` e `SafeAreaView`; nao cobre `src/components/**` (ver BACKLOG-0099) |

## Fluxo de autenticacao

1. **Registro:** `POST /api/auth/register` → cria Usuario com senha BCrypt.
2. **Login:** `POST /api/auth/login` → valida credenciais, retorna `{ accessToken, usuario }` + cookie HttpOnly `refreshToken`.
   - Access token: JWT HS256, 15 min, Bearer header, subject = email.
   - Refresh token: UUID v4, **30 dias** (elevado de 7 para 30 em 2026-08-22, PROB-0085/BUG-0096 —
     decisão do dono do produto), configurável via `jwt.refresh-expiration-days` em
     `application.properties` e nos profiles `dev`/`prod`/`vps`; cookie HttpOnly (`Path=/api/auth`,
     `SameSite=Lax`, `Secure` em prod, Max-Age acompanha a mesma property). Rotação deslizante: cada
     renovação regrava a expiração a partir de "agora".
3. **Refresh:** `POST /api/auth/refresh-token` → rotaciona refresh token com deteccao de reuse (revoca todos os tokens do usuario se detectar reuso). Resposta inclui `accessToken` e `csrfToken` (desde 2026-07-09, ver BUG-0013) — o cookie `refreshToken` (HttpOnly) segue sendo a fonte de verdade, o `csrfToken` tambem vai no corpo porque clientes nativos (React Native) nao leem cookies para o padrao double-submit (o mobile grava mas nunca le esse `csrfToken` — codigo morto, ver BACKLOG-0103). **Falha de refresh (desde 2026-08-22, PROB-0085/BUG-0096):** as tres causas de falha — token expirado, revogado ou nao encontrado — respondem HTTP 401 com `code: SESSION_EXPIRED` (`SessaoExpiradaException`, mapeada em `GlobalExceptionHandler`), assim como "Refresh token nao fornecido"; reuso detectado continua 401 com `code: TOKEN_REUSE_DETECTED`. Antes disso, expirado/revogado/nao-encontrado respondiam 422/404, o que o mobile nao tratava como fim de sessao (ver item 20 abaixo). Novo `RefreshTokenScheduler` limpa tokens expirados diariamente as 03:15 `America/Sao_Paulo` (`app.refresh-token.cleanup.cron`/`.enabled`), chamando `RefreshTokenService.limparTokensExpirados()`, que existia sem caller.
4. **Interceptor Axios (web):** detecta 401, tenta refresh automatico, enfileira requisicoes concorrentes durante refresh.
5. **Interceptor Axios (mobile):** desde 2026-07-11 (BUG-0051/PROB-0056), mobile usa contrato body-only: `withCredentials:false`, `X-Client-Type: mobile`, refresh token lido do `SecureStore` e enviado no body para `/auth/refresh-token`; cookie/CSRF ficam exclusivos do contrato web. O interceptor detecta 401 fora de rotas `/auth/`, usa uma promise deduplicada entre requests concorrentes e repete a request original com o novo Bearer token. **Encerramento de sessão (desde 2026-08-22, PROB-0085/BUG-0097):** quando o refresh falha com qualquer resposta do servidor (não só 401/403 — cobre também o `SESSION_EXPIRED` 401 e os antigos 422/404 do backend), `mobile/src/services/api.ts` limpa o `SecureStore` e aciona um canal (`setOnSessionExpired`) que o `AuthContext` usa para derrubar `usuario`/`isAuthenticated` e levar a UI de volta ao login; a ausência de `response` (falha de rede/timeout) preserva os tokens — tolerância offline. `refreshAccessToken` é exportada e também é chamada pelo `AppLockGate` no desbloqueio (ver item 20 de "Principais decisões técnicas").
6. **Logout:** `POST /api/auth/logout` → revoga refresh token, limpa cookie (Max-Age=0).
7. **Forgot password:** `POST /api/auth/forgot-password` → envia token por email. `POST /api/auth/reset-password` → redefine senha.

Rate limit: login 5/min/IP, forgot-password 3/min/IP (janela movel 60s, `LoginRateLimitFilter`).
Account lockout: 5 falhas consecutivas → bloqueio 15min. Login bem-sucedido reseta contador.

**Resolucao de IP na stack de proxy (corrigido em 2026-07-14, PROB-0066/BUG-0059):** o rate limit e o
account lockout dependem de resolver corretamente o IP real do cliente a partir de `X-Forwarded-For`. Ate
2026-07-14, `forward-headers-strategy=framework` fazia o Spring confiar em todo o header recebido, e o
nginx apenas anexava (append-only) ao `X-Forwarded-For` sem sobrescrever — o primeiro IP da lista era
controlado pelo cliente, permitindo contornar o rate limit forjando o header. Corrigido trocando para
`forward-headers-strategy=native` (Tomcat `RemoteIpValve`, resolve o IP a partir de uma lista fechada de
proxies internos confiaveis) e garantindo que a camada de proxy mais externa normalize o header: no
deploy standalone (`deploy/vps/nginx.conf.template`, 1 hop), o nginx sobrescreve `X-Forwarded-For` com
`$remote_addr`; no deploy atras do Nginx Proxy Manager (`deploy/vps/nginx.npm.conf`, 2 hops), a premissa
documentada e que o NPM sempre anexa seu proprio `$remote_addr` (nunca repassa cegamente o header do
cliente). **Premissa de deploy que precisa continuar valendo:** se o proxy mais externo da cadeia deixar de
normalizar/anexar o IP real, a vulnerabilidade volta a existir silenciosamente — ver PROB-0066 e
BACKLOG-0080 (gate de smoke em staging ainda pendente).

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
7. Usuario cria carteiras (dinheiro, conta bancaria, poupanca). Exatamente uma carteira por
   titular pode ser marcada `principal` (imposto por indice unico parcial no banco desde a
   migration `V66`, 2026-08-29) — nasce assim no onboarding, pode ser trocada em
   `more/carteiras.tsx` e e a pre-selecao padrao do formulario de lancamento.
8. Usuario registra transacoes (entrada/saida, com ou sem parcelamento) — pelo formulario, por
   importacao de extrato ou **conversando com o assistente** (texto ou audio, sempre com revisao).
   Transacao so movimenta o saldo de uma carteira (Ledger) se `carteiraId` for enviado no payload — sem carteira, a transacao e contabilizada em relatorios/categorias mas nao gera `MovimentoCarteira` (ver BUG-0011/BUG-0012). Exclusao de transacao e soft-delete (`ativa=false`) com estorno automatico do movimento no Ledger; todas as leituras agregadas (dashboard, relatorios, listagens, fatura) filtram `ativa=true` desde 2026-07-09 (BUG-0014).
9. Usuario gerencia parcelas (pagar/despagar).
10. Usuario cria contas fixas mensais.
11. Usuario cria metas financeiras e acompanha progresso (com aporte automatico mensal opcional).
12. Usuario gerencia carteiras (adicionar/remover saldo) e transfere entre elas.
13. Usuario importa extrato CSV/OFX, revisa linha a linha antes de lancar e pode desfazer a
    importacao inteira.
14. Usuario acompanha orcamentos por categoria, investimentos, compromissos a vencer e insights.
15. Usuario lanca conversando com o assistente (texto/audio), sempre revisando o rascunho antes de
    confirmar — ver a secao "Assistente financeiro" abaixo.

## Integracoes

| Integracao | Status | Detalhes |
|---|---|---|
| Email (password reset) | Implementado, degrada sozinho | `EmailService` usa `JavaMailSender` de verdade quando `spring.mail.host` esta definido; sem host configurado o bean nao e criado e o servico apenas registra o envio (`ObjectProvider` guarda a ausencia). Nao e mais um stub que so loga. |
| Gemini / OpenAI (assistente) | Implementado, **desligado por padrao** | Extracao estruturada com schema estrito. `assistant.external.enabled=false` por padrao, e ainda exige `billing-confirmed` e `data-policy-accepted`. Sem essas flags o sistema fica no parser deterministico. |
| WhatsApp (Meta) | Implementado, **desligado por padrao** | `assistant.whatsapp.enabled=false`; vinculo por codigo de uso unico, assinatura de webhook e janela temporal. Homologacao Meta pendente (ver runbook `WHATSAPP_ASSISTANT_SANDBOX.md`). |
| Push (Expo) | Parcial | Registro de aparelho e envio existem; falta `extra.eas.projectId` e credenciais APNs/FCM (BACKLOG-0110). Em simulador o registro desiste por `Device.isDevice`. |
| Sentry (mobile) | Condicional | `@sentry/react-native` inicializa apenas com DSN em `extra.sentryDsn`; sem DSN fica desligado e `beforeSend` remove `user`/`request`/`extra`. |
| Logstash | Parcial | `logstash-logback-encoder` no classpath, configuracao pendente. |
| Actuator | Implementado | `/actuator/health`, `/actuator/info` e `ReconciliacaoHealthIndicator` (`UNKNOWN`/`UP`/`DEGRADED`; `DEGRADED` responde 200, `DOWN`/`OUT_OF_SERVICE` respondem 503). |
| Swagger | Implementado | SpringDoc OpenAPI em `/swagger-ui.html`. Publico em dev, autenticado em prod. |
| CI | Implementado | GitHub Actions: `ci.yml`, `mobile-maestro.yml`, `mobile-release.yml`. |
| Open Finance / conector regulado | **Parcial, desligado por padrao** | Fase 6 em andamento (PR-F6-00 a 08 concluidos em 2026-09-01). Ja existem: schema completo (V68-V71), SPI `OpenFinanceProvider`, conector NDJSON no pipeline canonico, cifra com rotacao, guard de boot fail-closed e provedor fake em `local-e2e`. Ainda **nao** existem sincronizacao, endpoint, superficie mobile nem implementacao HTTP de parceiro. `openfinance.enabled=false` por padrao; ativacao em producao depende de `PROB-0081`, dos gates do PR-F4-18 e de `BACKLOG-0080`. |

## Fase 6 — conectores regulados (em andamento desde 2026-09-01)

Tres ADRs foram aceitos antes de qualquer implementacao, seguindo a regra do projeto de decidir por
ADR e nao por acidente:

- **ADR-0019** — a fonte remota e adaptada para `ImportSource`. O job de sincronizacao busca as
  paginas do parceiro, escreve um snapshot NDJSON deterministico em arquivo temporario e entrega ao
  pipeline canonico existente. HTTP, OAuth, paginacao e cursor ficam em `service/openfinance/`, fora
  do pacote `importacao`; nenhum `*Connector.java` abre conexao de rede, e o teste de arquitetura
  passa a proibir rede explicitamente. Assim `file_sha256`, `declaredBalances`, deduplicacao, previa,
  commit idempotente e reversao continuam valendo sem codigo novo.
- **ADR-0020** — consentimento por instituicao e append-only (revogar muda status, nunca apaga
  linha); credencial fica em tabela separada, cifrada em AES-GCM com `key_version`; o vinculo exige
  `state` de uso unico e PKCE, porque callback sem `state` permitiria vincular conexao alheia ao
  perfil de outra pessoa; revogar **nao** e excluir — as transacoes ja lancadas permanecem no ledger;
  webhook do parceiro fica **fora do escopo** da fase, so polling.
- **ADR-0021** — so entra fato efetivado (pendente e autorizacao nao); commit automatico e excecao e
  exige lote limpo com saldo conciliado; a deduplicacao passa a olhar instituicao canonica, lotes nao
  finalizados e registros revertidos, senao a sobreposicao de janela geraria duplicados pendentes sem
  fim e a ressincronizacao desfaria reversoes do titular; o saldo de referencia e o contabil;
  divergencia nunca se corrige sozinha.

### O que ja esta implementado (PR-F6-02 a PR-F6-08)

- **SPI de importacao estabilizada.** `FinancialDataConnector` ganhou `format()` e
  `ImportConnectorRegistry` ganhou `forFormat(...)`, que resolve o conector sem detecao heuristica.
  A guarda `CanonicalImportArchitectureTest` passou a proibir rede em `*Connector.java`, log do
  `CanonicalImportRecord` e formato duplicado entre conectores.
- **Schema (V68-V71).** `import_batches` ganhou `origin` (`UPLOAD`/`CONNECTOR`) e `instituicao_id`;
  catalogo de provedores, instituicoes e aliases; conexao, credencial cifrada e consentimento
  append-only; contas conectadas, cursor, log de sincronizacao e saldo declarado.
- **Deduplicacao corrigida em tres frentes**, valendo tambem para CSV e OFX: instituicao canonica com
  fallback textual, identidade forte contra lotes ainda em revisao (`DUPLICATE_PENDING_BATCH`) e
  contra registros revertidos (`DUPLICATE_REVERSED`).
- **Cifra e guard.** `OpenFinanceCrypto` (AES-GCM, IV por operacao, rotacao real por `key_version`) e
  `OpenFinanceConfigurationGuard`, que derruba o boot em `prod`/`vps` sem chave, HMAC, politica,
  provedor ou `redirect_uri` HTTPS fixa.
- **Conector NDJSON.** `OpenFinanceNdjsonConnector` le o snapshot pelo mesmo contrato de bytes de CSV
  e OFX, com leitura de linha limitada (nao `readLine()`, que cresceria ate derrubar a instancia) e
  conversao do instante do parceiro para data de negocio pelo `Clock` do ADR-0003.
- **Provedor fake.** `FakeOpenFinanceProvider` em `@Profile("local-e2e")` cobre paginacao,
  determinismo, identificador instavel, pedido de espera e consentimento recusado. E o que sustenta
  a decisao de nao contratar agregador nesta fase.
- **LGPD.** As sete tabelas novas entraram no manifesto de exclusao do ADR-0007, e a exportacao do
  titular ganhou conexoes, consentimentos, contas mascaradas, saldo declarado e log de sincronizacao.
  Dois guardioes amarram isso: um exige decisao explicita sobre exportacao para cada tabela nova, e
  outro proibe qualquer consulta de exportacao de mencionar token, HMAC, cursor ou id externo.

### O que ainda nao existe

Sincronizacao incremental e backfill, ciclo de vida do consentimento (incluindo o `state` de uso
unico do callback), endpoint `/api/v1/conexoes`, superficie mobile, invariante `SALDO_INSTITUICAO` na
reconciliacao global e qualquer implementacao HTTP de parceiro. Nenhuma linha desta fase abre conexao
de rede ate agora.

## Principais decisoes tecnicas

1. **Spring Boot + JPA:** ecossistema maduro, facilidade de configuracao, ampla documentacao.
2. **JWT em vez de sessao:** API stateless, compatibilidade mobile, sem sticky sessions.
3. **Refresh token com rotacao e deteccao de reuse:** seguranca contra token theft sem sacrificar UX.
4. **Flyway migrations (antes era ddl-auto=update):** schema versionado, previsível e reproduzível entre ambientes. PROB-0006 resolvido. Teste PostgreSQL real automatizado via `scripts/verify-postgres-migrations.sh`; `mvn verify -Pintegration-test` continua disponível quando Testcontainers estiver saudável.
5. **React Context API em vez de Redux/Zustand:** simplicidade para estado global limitado (apenas auth).
6. **Tailwind CSS em vez de CSS-in-JS:** produtividade, consistencia visual, baixo bundle size.
7. **Expo em vez de React Native puro:** build e deploy simplificados, OTA updates.
8. **Axios com interceptor de refresh:** fila de requisicoes concorrentes evita multiplos refresh tokens simultaneos.
9. **Rate limit custom (sem Bucket4j):** implementacao propria em `LoginRateLimitFilter`, janela movel, sem dependencia externa.
10. **Categoria.valorGasto so reflete SAIDA:** desde 2026-07-09 (BUG-0015), criar/deletar transacao so ajusta `valorGasto` da categoria quando `tipo == SAIDA` — entradas nunca contam como gasto no indicador de orcamento por categoria.
11. **Design mobile alinhado ao prototipo standalone (caminho morto desde 2026-08-19 — ver item 25):** componentes `Entrance` (`gf-rise`/`gf-pop`, respeita Reduce Motion) e `FloatEmoji` (`gf-float`) portados de `docs/Gestor Financeiro (standalone).html` para o app Expo; `Fab` com gradiente violeta `#7c5cfc`→`#8b2fff` e glow (BACKLOG-0048, 2026-07-09). O arquivo `docs/Gestor Financeiro (standalone).html` foi removido do working tree em 2026-08-19 (`git rm`, branch `chore/remove-prototipo`); permanece apenas no historico do git ate o commit `ae30d62`. Os componentes `Entrance`/`FloatEmoji`/`Fab` citados aqui continuam em uso — apenas o arquivo de referencia deixou de existir.
12. **Ultima parcela absorve arredondamento:** desde 2026-07-09 (BUG-0017), parcelas de compra no cartao (`FaturaLancamento` e `Parcela` legada) usam `valorTotal/n` HALF_UP para as N-1 primeiras parcelas e `valorTotal - soma(N-1 parcelas)` na ultima, garantindo que a soma feche exatamente com o valor total e que `Conta.valorGasto` (limite do cartao) zere apos quitacao completa.
13. **Soma de FaturaLancamento e a fonte da verdade do valor da fatura:** desde 2026-07-09 (BUG-0021), `pagarFatura` e `toResponse` calculam o valor da fatura pela soma dos lancamentos, nao pelo campo `valorTotal` persistido incrementalmente (sujeito a dessincronia). Fallback para `valorTotal` persistido apenas em faturas anteriores a migration V17, sem lancamentos.
14. **Pagamento parcial de fatura suportado:** desde 2026-07-11 (BUG-0052), `pagarFatura` aceita valor positivo ate o saldo restante (`valorTotal - valorPago`), acumula `valorPago`, debita a carteira e libera limite pelo valor efetivamente pago. A fatura so muda para `PAGA` quando `valorPago >= valorTotal`; web/mobile enviam `Idempotency-Key` por toque de pagamento.
15. **Fatura paga e imutavel — compensacao via lancamento na proxima fatura aberta:** desde 2026-07-09 (revisao 2, mesma sessao — PROB-0044), editar ou cancelar uma compra de cartao com fatura(s) ja paga(s) nunca mais bloqueia com `BusinessException` (substitui a decisao registrada horas antes no mesmo dia). Em vez disso, a parte da compra que ja esta em fatura paga e tratada como imutavel; a diferenca (edicao) ou o valor integral (cancelamento) e lancado como `TipoFaturaLancamento.AJUSTE`/`ESTORNO` (podendo ser negativo, ou seja, credito) na proxima fatura em aberto — mesmo principio de estorno de cartao de credito real. Enum `TipoFaturaLancamento` (`COMPRA`/`AJUSTE`/`ESTORNO`) e coluna `tipo` introduzidos na migration `V18__fatura_lancamento_tipo.sql`.
16. **Invariante centralizado de limite de cartao:** `Conta.valorGasto == soma dos lancamentos em faturas nao pagas menos pagamentos ja feitos` (inclui compras, ajustes, estornos e pagamentos parciais). Helpers privados `criarLancamento`/`removerLancamentoDeFaturaAberta`/`ajustarLimiteUtilizado` em `FaturaService` sao o unico ponto que ajusta `valorGasto` para compras de cartao; `TransacaoService` deixou de chamar `contaService.adicionarGasto`/`removerGasto` para transacoes que sao compra de cartao (mantido apenas para contas que nao sao cartao de credito). `pagarFatura` libera limite pelo valor pago.
17. **Mobile ganhou edicao/exclusao de transacao (`EditarTransacaoModal`):** desde 2026-07-09 (PROB-0045/BUG-0027), tocar numa linha de `mobile/app/(app)/transacoes.tsx` abre um sheet que edita apenas os campos que o backend de fato aplica em `PUT /api/v1/transacoes/{id}` (valor, descricao, data, observacoes); tipo/categoria/forma de pagamento sao exibidos como contexto fixo, nao editavel, pois o backend os ignora. Compra de cartao exibe aviso de que a edicao/exclusao ressincroniza faturas via `FaturaService.ressincronizarCompraCartao`/`cancelarCompraCartao` (PROB-0044).
18. **Badge de status/tipo de lancamento na fatura em mobile e web:** desde 2026-07-11 (BUG-0052), `mobile/app/(app)/more/faturas.tsx` e `frontend/src/pages/Faturas.tsx` exibem chip de tipo para `ESTORNO`/`AJUSTE` e removem o prefixo textual `"Ajuste: "`/`"Estorno: "` da descricao exibida.
19. **Recorrências são ocorrências mensais idempotentes:** desde 2026-07-14 (BUG-0066), `ContaFixa` representa entrada ou saída e pode ser manual ou automática. `execucoes_recorrencia` registra vencimento/status/tentativa/falha/transação com unicidade `(conta_fixa_id, data_vencimento)`; a carteira e a recorrência são bloqueadas antes do lançamento e o ledger recebe a chave `RECORRENCIA:{id}:{data}`. O scheduler roda às 00:05 em `America/Sao_Paulo` e também no `ApplicationReadyEvent` para recuperar ocorrências perdidas. Saída sem saldo permanece `FALHA_SALDO`, não cria transação e não permite saldo negativo. **Desde 2026-08-29 (PROB-0098):** o destino da cobrança deixou de ser exclusivamente a carteira (caixa) — `ContaFixa` agora aceita `carteira_id` **ou** `conta_id` (cartão), mutuamente exclusivos; ver item 29 abaixo para o detalhamento completo (migration `V67`, correções de idempotência/lock/exclusão de cartão).
20. **Dados mobile protegidos sem apagar a sessão:** tokens e usuário continuam no SecureStore, enquanto `AppLockGate` bloqueia cold start e retornos após 60 segundos em segundo plano. O desbloqueio aceita biometria/credencial do aparelho ou validação online da senha; a senha nunca é persistida. O estado `inactive/background` cobre imediatamente a interface para impedir captura dos valores pelo seletor de apps. **Desde 2026-08-22 (PROB-0085/BUG-0097):** tanto o desbloqueio por biometria quanto por senha chamam `refreshAccessToken()` contra o servidor antes de liberar a UI — antes, o desbloqueio era puramente local (só validava a biometria/senha e abria o cadeado), então uma sessão morta havia dias no servidor ficava "desbloqueada" sem que o app percebesse. Falha de rede ainda libera a UI normalmente (tolerância offline mantida); sessão recusada pelo servidor aciona o encerramento de sessão (ver item na seção "Fluxo de autenticacao"). Biometria continua sem proteger o token em repouso no `SecureStore` (`requireAuthentication` não usado) — risco residual registrado em BACKLOG-0104.
19. **Rollover de credito/saldo devedor de fatura e lazy na leitura, sem endpoint de fechamento nem scheduler:** desde 2026-07-11 (BUG-0053), decisao do dono do produto foi nao criar um passo explicito de "fechar fatura" — o status `FECHADA` continua derivado (BUG-0020) e o rollover (`FaturaService.liquidarFaturaAnterior`) e disparado ao materializar a proxima fatura de competencia (`buscarAtual`/`buscarPorMes`/`criarOuBuscarFatura`), liquidando recursivamente faturas anteriores ja fechadas. Ver secao "Regra de produto: credito de fatura e saldo devedor rolado".
20. **Resolucao de IP do cliente via `forward-headers-strategy=native` (Tomcat `RemoteIpValve`), nao `framework`:** desde 2026-07-14 (PROB-0066/BUG-0059), o Spring deixou de confiar diretamente em todo o `X-Forwarded-For` recebido — o Tomcat resolve o IP real a partir de uma lista fechada de proxies internos confiaveis (`internal-proxies`, loopback + faixas privadas Docker). A env var `SERVER_FORWARD_HEADERS_STRATEGY` nos `docker-compose.*.yml` sobrepoe o valor do profile — os dois precisam estar alinhados. Decisao acoplada a uma premissa de infraestrutura: o proxy mais externo (nginx standalone ou Nginx Proxy Manager) precisa sempre normalizar/anexar o `X-Forwarded-For` com o IP real de conexao, nunca repassar cegamente o header do cliente.
21. **SPA ganhou headers de seguranca no nginx, nao no Spring:** desde 2026-07-14 (PROB-0070/BUG-0063), como `SecurityConfig` so intercepta `/api/**`, HSTS/`X-Frame-Options`/`X-Content-Type-Options`/`Referrer-Policy`/CSP foram adicionados diretamente nos dois configs de nginx (`nginx.conf.template`, `nginx.npm.conf`), nao no backend — decisao de manter a responsabilidade de servir o SPA com seus headers de seguranca na camada que efetivamente entrega esses arquivos ao navegador.
22. **Reset de senha via POST com corpo, nao GET com query string:** desde 2026-07-14 (PROB-0071/BUG-0064), `validate-token` migrou de `GET /api/auth/validate-token?token=...` para `POST /api/auth/validate-token` com `ValidateTokenRequest { token }` no corpo, evitando que o token de reset (segredo de curta duracao) fique registrado em access logs de proxies/CDN e historico do navegador. O `GET` antigo agora retorna 405 (`HttpRequestMethodNotSupportedException` tratada explicitamente no `GlobalExceptionHandler`, que antes so tinha um catch-all generico → 500 para metodo nao mapeado).
23. **Pagamento de parcela e exclusao de carteira ganharam guards de estado/ownership de movimento (2026-07-14, PROB-0067/PROB-0068):** `ParcelaService.marcarComoPaga` retorna no-op se a parcela ja estiver `PAGO` (evita duplo debito por reenvio) e `Parcela` ganhou `@Version` (protege contra concorrencia real, mesmo padrao de PROB-0002); `CarteiraService.deletar` passou a bloquear exclusao para **qualquer** `MovimentoCarteira` associado (nao so origem `CARTEIRA_AJUSTE`), fechando o caminho mais comum de uso (transacao/parcela) que antes caia em erro 500 de FK `RESTRICT`.
24. **Recomendacoes de auditoria conscientemente rejeitadas quando conflitam com regra de produto ja travada (2026-07-14, PROB-0073/PROB-0074):** um `CHECK (valor_gasto >= 0)` em `contas` e um piso zero em `ContaService.removerGasto` foram propostos por uma auditoria de banco e **nao implementados** — ambos quebrariam o principio documentado de que `Conta.valorGasto` negativo e credito de cartao legitimo (V20:5-8, regra R1 do rollover de fatura). `Conta` ja tem `@Version` desde PROB-0002, tornando lock pessimista adicional redundante. Decisao e justificativa completas em `docs/PROBLEM_LEDGER.md` PROB-0073/PROB-0074.
25. **Protótipo HTML e redesign visual "Fase 4" (dark-first ciano) descartados por inteiro (2026-08-19, PROB-0082/BACKLOG-0090):** decisão do dono do produto na branch `chore/remove-prototipo` — "ficou horrível e só atrapalha o sistema". `docs/Gestor Financeiro (standalone).html` (protótipo claro lavanda, único protótipo commitado) removido via `git rm`; `docs/prototipo/app.html` (protótipo dark-first ciano, untracked) e `docs/prototipo/legado-claro.html` (rename staged do standalone) deixaram de existir — o diretório `docs/prototipo/` não existe mais. Todo o redesign "Fase 4" não commitado do mobile foi revertido ao estado do commit `ae30d62` via `git stash push --include-untracked`, incluindo tema dark-first ciano, telas `carteira.tsx`/`analises.tsx`, componentes `CardBadge`/`CreditCardArt`/`DiaHeader`/`MerchantLogo`/`ProgressRing`/`TransacaoRow`, `domain/marcas.ts`, `store/themePref.ts`, `utils/color.ts` e testes/Maestro associados — código preservado apenas em `git stash@{0}` (`fase4-prototipo-descartado-2026-08-19`), não commitado (ver PROB-0082 para o risco de perda acidental do stash). `mobile/app/(app)/more/relatorios.tsx` (apagado pelo redesign) foi restaurado com seu link em `more/index.tsx`; tab bar voltou para `Início · Transações · + · Planejamento · Mais`. `DESIGN.md` e `PRODUCT.md` (raiz do projeto) deixaram de citar o protótipo como fonte canônica de design — a fonte canônica passa a ser `DESIGN.md` + `mobile/src/theme/colors.ts`. Evidência: `npx tsc --noEmit` limpo; `npm test` no mobile 12 suítes / 36 testes PASS; `grep -in "prototipo|standalone" DESIGN.md PRODUCT.md` sem hits. Nenhum protótipo HTML deve ser recriado nem tratado como referência canônica em trabalho futuro (decisão do dono, não sujeita a reversão silenciosa). Ver `docs/REVIEW_REPORTS/2026-08-19_mobile_decisao_reversao-prototipo-fase4.md`.

26. **Ajustes redesenhado para o padrão visual de Home/Carteira/Metas; tema deixa de ser
    exclusivamente automático; exclusão de conta LGPD passa a ser consumida no mobile
    (2026-08-21):** o slot "Ajustes" da tab bar (arquivo `mobile/app/(app)/ajustes.tsx`) tinha
    título visível "Mais" e era um grid de 11 cards de ferramentas com estilo totalmente hardcoded
    (`fontSize: 23`, `flexBasis: '47%'`, tile `44x44`), sem consumir `mobile/src/theme/tokens.ts`;
    não havia bloco de conta real (perfil, sair, política de privacidade acessível a quem já tem
    conta, exclusão de conta). Reescrita em seções: conta (perfil + notificações com badge),
    APARÊNCIA (tema), FERRAMENTAS (grid 2 colunas), DADOS E PRIVACIDADE (importar/exportar CSV —
    ambos já existiam antes desta sessão, apenas relocados — e política de privacidade), rodapé
    com "Sair da conta" e "Excluir minha conta". Três primitivas novas extraídas para
    `src/components/ui/`: `CabecalhoDeTela` (header inline com safe area, título e ação circular),
    `SuperficieComBrilho` (base + dois `RadialGradient` SVG, extraída de `CardMeta`, que passou a
    consumi-la sem mudança visual) e `CabecalhoSecao` (movido de `src/components/metas/`, ganhou a
    prop `escalar` — o `e()` de `theme/escala` só se aplica onde há mock medido em `.design/`, hoje
    só a tela de metas). Token novo `typography.screenTitle` em `tokens.ts` substitui o `fontSize`
    de título de tela que estava solto por tela. **Escolha de tema:** antes o app só seguia
    `useColorScheme()` do SO, sem opt-out; agora `src/store/temaPreferido.ts` (SecureStore, mesmo
    molde de `saldoVisivel`/`lancamentoPrefs`) e `src/context/TemaContext.tsx` guardam
    `sistema | claro | escuro`, e `useTheme()`/`useEsquema()` (`src/theme/index.ts`) leem o
    contexto quando presente, caindo no comportamento antigo (SO) na ausência de provider —
    `app/_layout.tsx` passou a envolver a árvore com `TemaProvider`, `src/components/ui/Card.tsx`
    migrou de `useColorScheme()` direto para `useEsquema()`. **Exclusão de conta LGPD:** o endpoint
    `DELETE /v1/usuarios/me` já existia no backend, coberto por `UsuarioExclusaoLgpdIT` (ver
    PROB-0076, corrigido em 2026-07-15), mas nenhum arquivo do mobile o chamava até esta sessão —
    novo `src/services/usuarioService.ts` e um fluxo de confirmação dupla (`Alert` + modal
    `pageSheet` pedindo senha) em `ajustes.tsx`. Verificado em runtime contra backend local (porta
    8093, banco `gf_ajustes`): senha errada → 422 `BUSINESS_ERROR`/"Senha incorreta" (ver
    BUG-0069/PROB-0083 para o achado de mensagem de erro que isso expôs), senha certa → 204 e login
    seguinte falhando (exclusão real confirmada). A política de privacidade
    (`app/(auth)/privacidade.tsx`, já existente) ganhou um segundo ponto de entrada a partir de
    Ajustes, alcançável também por quem já tem conta (antes só linkável no cadastro). `DESIGN.md`
    reescrito nesta mesma sessão: estava defasado desde antes (descrevia marca violeta `#7c5cfc`,
    tema claro por padrão e tab bar "Início/Transações/+/Planejamento/Mais" — o mesmo estado
    intermediário citado no item 25/nota de consistência acima), passou a descrever a marca ciano
    real de `mobile/src/theme/colors.ts`, a tab bar atual e o padrão de tela (`CabecalhoDeTela`,
    `SuperficieComBrilho`, `CabecalhoSecao`, receita de tela). `mobile/.maestro/smoke-auth.yaml`
    corrigido (BUG-0068): assertava rótulos de aba que não existem mais e que, de todo modo, a tab
    bar nativa não expõe na árvore de acessibilidade do Maestro — passou a assertar pelo conteúdo
    da Home ("Saldo Disponível"). Testes novos: `AjustesScreen.test.tsx` (10 casos),
    `temaPreferido.test.ts` (3 casos). Suite completa do mobile: 172 testes PASS; `npm run lint` e
    `npm run typecheck` limpos (ver BACKLOG-0093, fechado nesta rodada — o erro pré-existente de
    `react-hooks/exhaustive-deps` não reproduziu mais). Validação visual: app rodado em simulador
    iPhone 17 Pro (Release) com a tela conferida na tela, mas **sem rodada formal de Maestro/visual
    regression** — mesma pendência crítica acumulada desde o Bloco B da Fase 3. Nenhuma migration.
    Ver `docs/PROBLEM_LEDGER.md` (PROB-0083), `docs/BUGFIX_LOG.md` (BUG-0068, BUG-0069),
    `docs/BACKLOG.md` (BACKLOG-0077 atualizado, BACKLOG-0093 fechado, BACKLOG-0094 novo).

27. **Padrao visual do mobile unificado em 13 PRs; kit `ui/` normalizado; trinco automatizado contra
    regressao (2026-08-21/22, branch `design/pr13-perfil`, commits `9c1335be`..`ad5fc022`):** o dono
    do produto pediu que o visual de Home, Cartao/Carteira e Metas virasse o padrao das demais telas
    e perguntou quais telas ainda faltavam. A revisao encontrou duas coisas que mudaram o plano: (1)
    as tres telas de referencia nao eram, de fato, o mesmo padrao — tres tamanhos de titulo de tela
    (26 literal na Carteira, `e(20)` escalado em Metas, `greeting` 22 na Fatura), cinco botoes
    ad-hoc e tres implementacoes de barra de progresso diferentes; (2) o proprio kit
    `src/components/ui/` nao seguia o `DESIGN.md` (`ListRow`, `Chip`, `Field`, `Badge`, `Fab`,
    `BackButton`, `IconTile` com numeros crus). A ordem de execucao foi: fechar o padrao (kit + tres
    telas de referencia) → travar com teste → so entao propagar para as 10 telas restantes.
    - **Decisoes do dono cravadas nesta rodada:** (D1) titulo de tela unifica em
      `typography.screenTitle` (26/800) com acao circular de 36, via `ui/CabecalhoDeTela`; a tela de
      Metas passa a divergir do mock medido apenas no header (a geometria do card de meta continua
      seguindo a medicao original) — ressalva registrada no fim de `mobile/.design/MEDICOES-metas.md`.
      (D2) `ui/Botao` ganha o tamanho `pill`, as variantes `invertido` e `sucesso`, e as props `dica`
      (`accessibilityHint`) e `hitSlop` — volta a valer que `ui/Botao` e a unica forma de botao do
      app. (D3) bugs funcionais encontrados durante a migracao de uma tela sao corrigidos no PR da
      propria tela, nao extraidos para um PR separado — ver BUG-0083 a BUG-0091, todos commitados
      junto do PR de migracao visual da tela onde foram achados.
    - **Primitivas novas** (`mobile/src/components/ui/`, exceto a ultima): `CabecalhoSubTela` (voltar
      + titulo + apoio, para sub-telas de `more/`), `FolhaModal` (barra saida/titulo/acao do
      `pageSheet`, que estava copiada a mao 17 vezes), `Contador` (bolha de nao lidas), `CampoBusca`
      (lupa dentro do campo, sem rotulo em cima — nao e `ui/Field`), `RotuloDeGrupo` (titulo de uma
      fileira de controles que nao e campo de texto, ex.: "Sai de"/"Bandeira"/filtros de status),
      `NavegadorDeMes` (setas de mes com alvo 44 e `accessibilityRole`, antes duplicado e incompleto
      em orcamentos); e `mobile/src/domain/periodo.ts` (com teste em
      `mobile/src/__tests__/periodo.test.ts`) centralizando `iso`, competencia, aritmetica de mes e
      intervalo de periodo em hora local, que estava reescrito inline em pelo menos 6 telas.
    - **Trinco (`mobile/src/__tests__/padraoVisual.test.ts`, mesmo mecanismo de `tema.test.ts`):**
      varre `app/**` e falha quando encontra `fontSize`/espacamento/raio numerico cru, hex literal,
      `ActivityIndicator` ou `SafeAreaView`. Duas listas: excecoes permanentes com motivo (hoje so
      `app/index.tsx`, portao de sessao que roda antes de existir conteudo para o skeleton imitar) e
      telas ainda nao migradas (encolhe a cada PR; ao final da serie, **vazia** — as 28 telas de
      `app/**` passam). Um arquivo listado que ja esteja limpo tambem quebra o teste, entao excecao
      obsoleta nao se acumula silenciosamente.
    - **Telas migradas nesta serie (10):** `analises.tsx`, `(inicio)/transacoes.tsx`, `metas.tsx`
      (quatro folhas internas), `more/investimentos.tsx`, `more/carteiras.tsx`,
      `more/contas-fixas.tsx`, `more/orcamentos.tsx`, `more/visao-financeira.tsx`,
      `more/categorias.tsx`, `perfil.tsx`.
    - **Divergencias entre `DESIGN.md` e o codigo, fechadas nesta serie:** o token `shadow` estava
      morto (nao usado); o FAB tinha 56px em vez dos 53 efetivamente medidos; `Chip` usava fonte 13
      contra `typography.chip` (14); o rotulo do `Field` era CAIXA ALTA de 10pt com tracking manual;
      `ui/ProgressBar` usava `colors.border` como cor de trilha — o mesmo bug de contraste que
      `mobile/src/__tests__/tema.test.ts` ja documentava — e havia **cinco** implementacoes
      independentes da mesma barra de progresso espalhadas pelo app.
    - **Correcoes nas medicoes de referencia:** `mobile/.design/MEDICOES-metas.md` tinha uma
      contradicao interna (peso de fonte 700 na tabela, 500 na calibracao) e a trilha da barra de
      progresso aparecia com tres valores diferentes (28%, 30% e 0.36) em tres lugares do mesmo
      arquivo.
    - **Bugs funcionais corrigidos durante a migracao (D3):** ver `docs/BUGFIX_LOG.md` BUG-0083
      (BLOQUEADOR — falha de rede em `more/orcamentos.tsx` exibida como orcamento inexistente, risco
      de duplicacao), BUG-0084 (ALTO — `accessibilityLabel` curado apagando conteudo real da arvore
      de acessibilidade, reincidencia da classe BACKLOG-0096, em `ui/ListRow` e cinco telas), BUG-0085
      (ALTO — FAB de investimentos atras do painel da tab bar), BUG-0086 (ALTO — troca de senha do
      perfil com campos crus e erro de negocio roteado para o campo errado), BUG-0087 (MEDIO —
      encadeamento de folhas de `metas.tsx` sem cleanup de timer), BUG-0088 (MEDIO — erro generico da
      API sempre jogado no campo Nome em `carteiras.tsx`/`categorias.tsx`), BUG-0089 (MEDIO — secao de
      projecao de `visao-financeira.tsx` sumindo sem aviso em erro), BUG-0090 (MEDIO — salvamento mudo
      e setas de mes inacessiveis em `more/orcamentos.tsx`), BUG-0091 (BAIXO — ordenacao assumida e
      `key={i}` em `analises.tsx`, seletor de cor rotulado por posicao em `categorias.tsx`).
    - **Maestro:** `mobile/.maestro/financial-critical.yaml` teve 5 passos ajustados ao longo da
      serie para acompanhar rotulos curados removidos (regex parcial no lugar de rotulo exato), a
      espera do extrato de conta olhando texto visivel em vez de rotulo de selo, e o guard-rail de
      erro ganhando `.*Não deu para.*` (as telas migradas deixaram de escrever "Erro ao..."). **Os
      quatro flows Maestro nao foram executados nesta maquina** — pendencia registrada em
      BACKLOG-0098.
    - **Verificacao local executada ao final da serie:** `npm run typecheck`, `npm run lint` e
      `npm test` do mobile limpos; **244 testes em 29 suites** (eram 200 em 26 suites antes da
      serie).
    - **Pendencias registradas:** BACKLOG-0098 (rodar os quatro flows Maestro), BACKLOG-0099 (tres
      `pageSheet` desenhados a mao em `mobile/src/components/` — `ComposicaoMetricaModal`,
      `EditarTransacaoModal`, `NovaTransacaoModal` — fora do alcance do trinco, que so varre
      `app/**`), BACKLOG-0100 (divergencia de nome nao resolvida: a aba se chama "Analises", mas o
      titulo da tela e a entrada do hub em Ajustes dizem "Relatorios" — decisao do dono pendente),
      BACKLOG-0101 (aritmetica monetaria em `float` repetida sem util central em `analises.tsx`,
      `more/investimentos.tsx`, `more/orcamentos.tsx` — divida conhecida, sem erro observado).

28. **Rodada de verificacao em runtime da serie de padronizacao visual, com os quatro flows Maestro
    executados e verdes (2026-08-22, commits `9c1335be`..`ba199be`, verificacao feita sobre working
    tree apos `ba199be`, ainda nao commitada):** decorre diretamente do item 27. Ambiente: simulador
    iPhone 17 Pro (iOS 26.5), Postgres efemero em container, backend Spring na porta 8081, banco
    descartavel `gf_verify`, app iOS Debug com bundle embutido apontando para
    `http://127.0.0.1:8081/api`, `APP_ENV=local-e2e`.
    - **Resultado dos quatro flows:** `financial-critical.yaml` verde ponta a ponta (0 falhas, 6
      screenshots, todos os guard-rails de erro passaram) apos 5 correcoes no flow e 3 correcoes de
      bug de app; `smoke-auth.yaml` (17s), `privacy-consent.yaml` (34s) e `recovery-navigation.yaml`
      (9s) passaram sem alteracao. Suites ao final: mobile `npx tsc --noEmit` limpo, `npm run lint`
      limpo, 244 testes/29 suites PASS; backend 292 testes PASS, `BUILD SUCCESS`.
    - **Quatro bugs de app corrigidos (ver `docs/BUGFIX_LOG.md` BUG-0092 a BUG-0095):** (1)
      `more/fatura.tsx` sem `keyboardShouldPersistTaps="handled"` no `ScrollView` — primeiro toque
      em "Pagar Fatura" engolido pelo fechamento do teclado, unica tela em `app/**` com esse defeito
      (pre-existente, nao introduzido pela serie); (2) mesma tela, teclado ficava aberto cobrindo a
      lista apos pagamento — `Keyboard.dismiss()` adicionado; (3) `CardMeta.tsx` com
      `TouchableOpacity` na raiz envolvendo tanto o bloco de informacao quanto a linha de acoes —
      iOS fundia tudo num so no de acessibilidade e "Depositar"/"Editar"/"Excluir" ficavam
      inacessiveis ao VoiceOver e ao proprio Maestro (confirmado por dump de arvore antes/depois);
      unico caso de touchable aninhado no app; (4) `RelatorioService.gastosPorCategoria`
      (`String.valueOf(row[4])` transformava icone `NULL` na string literal `"null"`, exibida na
      tela em vez do fallback 🏷️) — helper `asTexto(Object)` corrige.
    - **Cinco correcoes de manutencao no proprio flow `financial-critical.yaml`** (nao sao bug de
      app): cartao de teste passou a usar `diaFechamento = 31` em vez de `5` (com fechamento 5 a
      compra do dia so caia na competencia atual em ~5 dos ~31 dias do mes, por causa de
      `FaturaDatas.competencia`); tres asserts de regex corrigidos para casar o no de texto inteiro
      em vez de exigir que o valor esperado fique no fim da string (pago/restante da fatura,
      percentual arredondado da meta, saldo do cofre); navegacao para Relatorios trocada de
      `tapOn: "Voltar"` + `tapOn: "Relatórios"` (nao funcionava — pilha do `more` mantinha a fatura
      embaixo de Contas) para toque por coordenada na aba Analises.
    - **Verificacoes manuais adicionais em runtime, todas com screenshot:** `orcamentos.tsx`
      (BUG-0083) confirmado nos dois caminhos reais (404 de mes sem orcamento → estado vazio com
      "Criar orcamento"; backend fora do ar → "Nao deu para carregar" sem oferecer criacao); FAB de
      investimentos (BUG-0085) acima da barra flutuante, sem colisao; folha dentro de folha
      (`MovimentoModal` sobre `DetalheAtivoModal`, encadeamento de `metas.tsx`) sem travar; CTA
      "Depositar" do card de meta nao corta texto, alvo de toque efetivo ~49pt; `CabecalhoDeTela`
      sem padding dobrado/zerado em Metas/Carteira/Analises; `ui/ProgressBar` verificada a 0%/100%/
      cor de entidade nos dois temas, trilha visivel no tema claro (regressao que o componente foi
      criado para evitar, confirmada corrigida); Aparencia (Ajustes) com os tres modos aplicando
      corretamente; `perfil.tsx` com olho/medidor de forca nos campos de senha, caminho de "senha
      atual incorreta" verificado por API + leitura de codigo (nao pela UI); reconciliacao
      financeira do extrato fechando exata (R$ 825,00, "Diferenca: R$ 0,00").
    - **Nao verificado nesta rodada (pendencias honestas, registradas em `docs/BACKLOG.md`):**
      tela estreita ~320dp para o card de contas fixas (BACKLOG-0102, novo); VoiceOver/TalkBack
      realmente ligado no dispositivo (a acessibilidade foi verificada por dump de arvore do
      Maestro, mesma fonte que o leitor de tela consome, mas nao o leitor em si — nota adicionada em
      BACKLOG-0078); Reduce Motion (idem); Android (sem `adb` disponivel nesta maquina, tudo acima e
      iOS).
    - **Pendencias de decisao do dono mantidas sem alteracao de escopo:** BACKLOG-0100 (divergencia
      "Analises"/"Relatorios", reconfirmada visualmente nesta rodada) e BACKLOG-0099 (tres
      `pageSheet` a mao fora do trinco — `NovaTransacaoModal` foi exercitado em runtime e renderiza
      corretamente, mas segue fora do alcance do trinco).
    - **BACKLOG-0098 fechado** com esta rodada como evidencia de conclusao do criterio de aceite.
      Ver `docs/REVIEW_REPORTS/2026-08-22_mobile_verification_maestro-runtime-padrao-visual.md` para
      o relatorio completo.

29. **Sessão mobile deixa de travar em erro silencioso após desbloqueio por digital; refresh token
    passa a 30 dias configuráveis (2026-08-22, PROB-0085, BUG-0096/BUG-0097):** dono do produto
    reportou que, usando o app com desbloqueio por digital, "deu falha e simplesmente não
    carregava", log acusando token expirado — só se recuperava deslogando e logando manualmente. Dois
    defeitos somados: (1) o backend respondia à falha de refresh com três status diferentes
    (422/404/401) e o mobile só tratava 401/403 como fim de sessão, deixando tokens mortos no
    `SecureStore` e a UI presa, montada como autenticada, sem aviso ao `AuthContext`; (2) o
    desbloqueio por biometria (`AppLockGate.unlockWithDevice`) validava só localmente e nunca
    chamava o servidor, então uma sessão morta havia dias ficava "desbloqueada" sem que o app
    percebesse. **Decisões do dono cravadas nesta rodada:** sessão com renovação deslizante, janela
    elevada de 7 para 30 dias; desbloqueio por digital renova o token contra o servidor, com
    tolerância offline (falha de rede não prende o usuário fora do app). Backend: nova
    `SessaoExpiradaException` (401, `code: SESSION_EXPIRED`) substitui os status 422/404 nas três
    causas de falha de refresh; janela configurável via `jwt.refresh-expiration-days`; novo
    `RefreshTokenScheduler` liga a limpeza diária de tokens expirados que já existia sem caller.
    Mobile: `api.ts` encerra a sessão em qualquer resposta do servidor na falha de refresh (não só
    401/403), preservando tokens só na ausência de resposta (offline); `AuthContext` reage via novo
    canal `setOnSessionExpired`; `AppLockGate` passa a chamar `refreshAccessToken()` no desbloqueio
    (biometria e senha), com dedup de promise para não confundir refresh paralelo com reuso de
    token. Verificação: backend 296 testes PASS (4 novos), mobile 30 suítes/254 testes PASS (7 casos
    novos em `sessaoExpirada.test.ts`, +3 em `AppLockGate.test.tsx`), runtime na stack local
    confirmando os três códigos de falha de refresh como 401 `SESSION_EXPIRED`, reuso como 401
    `TOKEN_REUSE_DETECTED`, deslizamento de expiração e scheduler de limpeza. Riscos residuais
    registrados: `csrfToken` do mobile é código morto (BACKLOG-0103), biometria não protege o token
    em repouso no `SecureStore` (BACKLOG-0104, P2). Ver `docs/PROBLEM_LEDGER.md` PROB-0085 e
    `docs/BUGFIX_LOG.md` BUG-0096/BUG-0097 para o detalhamento completo.

30. **Assistente ganha parcelamento restrito ao cartao, e o fechamento E2E do iOS passa a rodar de
    verdade (2026-08-29, PROB-0086 a PROB-0091, BACKLOG-0111/0112/0113):** o rascunho do assistente
    modelava so conta/categoria/valor/descricao/data — "em 3x" era possivel de falar, mas a
    confirmacao sempre gravava `parcelado = false`. **Decisao do dono:** parcelar continua sendo
    privilegio do cartao, como no formulario manual, mesmo o backend sabendo parcelar fora dele
    (`TransacaoService.criarParcelas`) — ver adendo no ADR-0017 e BACKLOG-0113.
    - **Contrato:** `TransactionDraftV1` e o schema estrito enviado aos fornecedores ganharam
      `cartaoNome` e `parcelas` (2..48); migration `V65` acrescentou `conta_id`/`parcelas` a
      `assistant_drafts`. O rascunho **guarda o pedido de parcelar antes de conhecer o cartao** — e
      isso que permite perguntar "Qual cartao voce usou?" em vez de lancar a vista, em silencio,
      algo que a pessoa pediu parcelado; quem recusa e o `confirm`, por rascunho incompleto.
    - **Bug que a feature revelou (PROB-0086):** `FinancialQuestionClassifier` classificava por
      presenca de termo, entao qualquer frase com "cartao" virava consulta de fatura e nunca chegava
      ao parser — "comprei 300 no Cartao Nubank em 3x" era impossivel de registrar. O classificador
      passou a exigir forma de pergunta antes de assumir consulta.
    - **Runner E2E:** o fechamento da Fase 5 nunca tinha rodado ate o fim. Foram necessarios quatro
      consertos no proprio runner (PROB-0088 a PROB-0090): build Release com assinatura (sem
      entitlements nao ha Keychain, e a sessao mora nele), `simctl keychain reset` por flow
      (`clearState` do Maestro nao limpa Keychain), correcao de `local` com `set -u`, de "OK" falso
      no `cleanup` e de falso positivo na varredura de segredos. Um bug de app tambem caiu aqui
      (PROB-0087): `Constants.isDevice` nao existe mais no expo-constants 18, entao o app pedia push
      no simulador e o dialogo do sistema travava toda automacao.
    - **Providers fake deixaram de ser fixture e viraram extrator deterministico** no profile
      `local-e2e`: valor com centavos, data relativa, conta, cartao, parcelas e categoria por
      palavra dita. Isso permitiu trocar frases-codigo ("mercado 50 hoje") por frases de gente
      ("paguei 137,90 no supermercado hoje pela Conta Principal") sem chamar servico pago.
    - **Evidencia:** `artifacts/fase5/run-20/` — seis flows verdes, `409` para `Idempotency-Key`
      divergente, varredura de segredos limpa; backend 470 testes, mobile 434. A prova financeira
      passou a cobrar movimentos de carteira e lancamentos de fatura separadamente, porque compra no
      cartao nao move carteira: `assistant-parcelado` fecha em 4 transacoes / 3 movimentos / 3
      lancamentos de fatura.
    - **Pendencias:** `importacao-mobile` fica SKIPPED por limitacao do seletor de arquivos do
      simulador (BACKLOG-0111) e `missingFields` ainda alterna vocabulario nome/id conforme o
      endpoint (BACKLOG-0112).

31. **Gate de feature do app sai do build e vira runtime via `GET /api/v1/capacidades` (2026-08-29,
    PROB-0092, ADR-0018):** o Assistente saiu inacessivel em producao apos um deploy de backend e
    publicacao de app porque havia **dois** gates independentes resolvendo a mesma pergunta em
    lugares diferentes — `EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED` embutida no bundle do app (so setada
    no runner de E2E, nunca no workflow de release) e `@ConditionalOnProperty` no backend sem a
    variavel de ambiente correspondente nos `docker-compose.*.yml` de deploy. **Decisao:** a
    pergunta "isso esta ligado?" deixou de ser respondida em dois lugares fixados (build do app,
    topologia HTTP do backend) e passou a ter uma unica fonte consultavel em runtime — o endpoint
    `CapacidadesController`, deliberadamente sem `@ConditionalOnProperty` (ele existe para dizer que
    algo esta desligado, entao nao pode sumir junto). O mobile consome via `useCapacidades`
    (React Query, `staleTime` 5 min, fail-closed: erro/loading = tudo desligado). As env vars
    `EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED`/`EXPO_PUBLIC_ASSISTANT_WHATSAPP_ENABLED` foram eliminadas
    do codigo do app. `docker-compose.vps.yml`/`docker-compose.production.yml` passaram a repassar
    `ASSISTANT_TEXT_ENABLED`/`ASSISTANT_AUDIO_ENABLED` (default `false`); `ASSISTANT_EXTERNAL_ENABLED`
    e chaves de IA continuam de fora por decisao do dono do produto. Ver ADR-0018 para o registro
    completo da decisao e a alternativa descartada (manter as `EXPO_PUBLIC_*`, so garantindo que o
    workflow de release as defina).

32. **"Conta principal" vira dado de dominio; tela de carteiras do mobile ganha edicao/exclusao
    (2026-08-29, PROB-0093, migration `V66`):** ate aqui "Conta Principal" era so o texto default
    de um campo do onboarding — sem coluna, flag ou regra — e o formulario de lancamento
    pre-selecionava `carteiras[0]` (ordem de insercao, nao decisao do titular). Migration
    `V66__carteira_principal.sql` acrescenta `carteiras.principal BOOLEAN NOT NULL DEFAULT FALSE`
    com indice unico **parcial** `ux_carteiras_principal_usuario ON carteiras (usuario_id) WHERE
    principal` (unicidade e do banco, nao da aplicacao) e backfill deterministico marcando a conta
    ATIVO manual de menor id de cada titular. `CarteiraService.definirPrincipal` desmarca a atual,
    faz flush, so entao marca a nova (a ordem inversa viola o indice). No contrato,
    `ContaFinanceiraRequest.principal` e opcional e so `true` tem efeito — `null`/`false` preservam
    o estado atual, porque desmarcar sem eleger outra deixaria o titular sem conta padrao.
    `mobile/app/(app)/more/carteiras.tsx` deixou de ser somente-leitura: agora edita nome, banco,
    tipo, saldo (o PUT converte a diferenca em `AJUSTE_MANUAL` no ledger) e o toggle "conta
    principal", com exclusao elegendo sucessora automaticamente. O web (`frontend/src/pages/Carteira.tsx`)
    nao recebeu a mesma tela — ver BACKLOG-0115.

33. **Regressao de layout mobile por dois `ScrollView` irmaos sem `style` disputando espaco livre
    (2026-08-29, PROB-0094):** `mobile/app/(app)/analises.tsx` tinha a faixa de chips de periodo
    como `<ScrollView horizontal>` sem prop `style`, irma direta do `ScrollView` de conteudo, ambos
    dentro de `View flex:1` — o RN aplica `flexGrow:1, flexShrink:1` a todo `ScrollView`
    (`ScrollView.js`, RN 0.81.5), entao os dois dividiam o espaco livre ~50/50. Regressao do commit
    `d44fc43`; era a unica tela do app com essa combinacao. Corrigida reestruturando a tela para a
    "Receita de tela" do `DESIGN.md` (`ScrollView` raiz unico, chips e intervalo dentro dele). Na
    mesma correcao: nova query `countAtivasByUsuarioIdAndPeriodo` substitui
    `countSaidasByUsuarioIdAndPeriodo` no gatilho do estado vazio (um mes so com entrada nao
    aparecia mais como "sem dados"); card "Gastos por conta" passou a renderizar no mobile
    (payload ja existia, so o web usava); aba renomeada de "Analises" para "Relatorios".

34. **Smoke de UI iOS (`scripts/e2e-mobile-ios.sh`) passou a compilar Release assinado, nao mais
    Debug (2026-08-29, PROB-0095):** o script instalava o app **errado** — `find` varria
    `Build/Products` a partir da raiz e pegava a primeira configuracao encontrada; como este script
    e `scripts/e2e-assistant-ios.sh` compartilham o mesmo `DerivedData`
    (`mobile/.e2e-derived-data`), um `Release-iphonesimulator` deixado pelo runner do assistente
    sombreava o `Debug-iphonesimulator` recem-compilado, e o smoke testava um app de horas antes
    sem avisar. Corrigido o `find` (achado ainda expunha um segundo defeito: Debug sem assinatura
    e sem entitlements faz `expo-notifications` falhar lendo o Keychain, cobrindo a tela com o
    LogBox vermelho), o script passou a buildar **Release assinado pelo Xcode**
    (`-configuration Release`, sem `CODE_SIGNING_ALLOWED=NO`), igual ao runner do assistente. Por
    isso o smoke de UI agora reflete o mesmo tipo de build que vai pra loja, nao um Debug que nunca
    rodou em producao. Ver PROB-0095 para os quatro defeitos do script (app errado, Debug quebrado,
    Keychain nao resetado entre flows, lista de invariantes travada em 4 quando o backend ja
    devolvia 5). Risco estrutural remanescente: os dois scripts ainda compartilham `DerivedData`
    (BACKLOG-0118).

35. **Faixa de status bar centralizada em `mobile/app/(app)/_layout.tsx` (2026-08-29,
    PROB-0096):** a "Receita de tela" do `DESIGN.md` (`ScrollView` raiz unico com cabecalho dentro
    dele, adotada na correcao de PROB-0094) faz o conteudo subir ate `y=0` ao rolar, cobrindo
    relogio/wifi/bateria — nao e defeito de uma tela, e do padrao, presente potencialmente desde
    que a Home adotou a mesma receita. Correcao vive no layout, nao em cada tela: `View` absoluto
    `top:0`, `height: insets.top`, `backgroundColor: colors.bg`, `pointerEvents="none"`. Modais
    (`FolhaModal`) usam o `Modal` nativo do RN, que renderiza em janela separada e ja trata o
    proprio inset — nao precisaram de ajuste. Verificado so em iOS/tema escuro; `insets.top` se
    comporta diferente no Android conforme a status bar seja translucida ou opaca (BACKLOG-0119).

36. **Encadeamento de foco no onboarding para no esconder campo obrigatorio sob o teclado
    (2026-08-29, PROB-0097):** o campo Banco (adicionado ao passo 1 pela correcao de PROB-0093)
    empurrou "Tipo" e "Saldo inicial (R$)" para baixo da dobra; com o teclado aberto, o Saldo
    (obrigatorio) ficava coberto. `mobile/app/onboarding.tsx` ganhou `returnKeyType="next"` +
    `onSubmitEditing` encadeando Banco → Nome → Saldo via `ref` (o componente `Field` ja suportava
    `ref` para isso). O RN so rola um `TextInput` para a area visivel quando ele recebe foco, entao
    o encadeamento resolve as duas coisas ao mesmo tempo. Testes novos no formulario de conta
    (`mobile/app/(app)/more/carteiras.tsx`) ganharam `testID="conta-form-nome"`,
    `testID="conta-form-banco"` e `testID="conta-form-saldo"` porque o seletor Maestro por texto
    "Saldo" casava com o rotulo do campo em vez do input.

## Regra de produto: credito de fatura e saldo devedor rolado (IMPLEMENTADO, 2026-07-11)

> Spec de produto para PROB-0050 / BACKLOG-0059 / BACKLOG-0054. Decisao de produto travada em 2026-07-11 e **implementada no mesmo dia** (BUG-0053). Pagamento parcial *dentro* da fatura aberta ja existia (BUG-0052); este bloco cobre o comportamento no **fechamento** e nos casos de **credito**, agora em producao no codigo.

### Principio geral

Tudo que sobra (credito) ou falta (divida) numa fatura ao fechar **fica no proprio cartao** e e **carregado para a proxima fatura** ate zerar. Credito de cartao **nao** vira saldo em carteira automaticamente (espelha o mundo real: estorno de cartao vira credito na fatura, nao dinheiro em conta corrente). Nenhum credito/divida fica "preso sem destino".

### Arquitetura de implementacao (decisao do dono do produto)

**Rollover lazy na leitura + servico idempotente + trava de banco.** Nao ha endpoint de fechar fatura nem scheduler — o status `FECHADA` continua **derivado** na leitura (comportamento existente desde BUG-0020), como antes. O gatilho e o metodo `FaturaService.liquidarFaturaAnterior(...)`, chamado por `buscarAtual`, `buscarPorMes` e `criarOuBuscarFatura`: ao materializar a fatura da competencia M, o servico liquida recursivamente para tras (M-1, M-2, ...) as faturas existentes que ja cruzaram `dataFechamento`. A recursao termina por: competencia decresce estritamente, fatura anterior inexistente (nunca materializa fatura retroativa vazia so para rollover) ou teto de seguranca de 24 meses.

### R1 — Fatura com total `<= 0` (so estorno / credito)

- **Regra:** quando uma fatura fecha com total `<= 0`, o valor absoluto vira **credito do cartao** e e lancado na **proxima fatura em aberto** do mesmo cartao (lancamento `CREDITO_ANTERIOR`, valor negativo), abatendo o total dela. Repete ate o credito zerar. Nunca vira `MovimentoCarteira` de ENTRADA na carteira do usuario.
- **Fatura de origem:** fechada como `PAGA` (nada a cobrar; `dataPagamento = dataFechamento`), sem exigir acao do usuario.
- **Exemplo:**
  - Fatura Jul: compra R$100, estorno -R$150 → total **-R$50**. Fecha `PAGA`, gera credito R$50.
  - Fatura Ago: compras R$200 + lancamento de credito **-R$50** → total a pagar **R$150**.
  - Se Ago nao tivesse compras: total -R$50, credito rola de novo para Set.

### R2 — Fatura fechada com pagamento parcial (saldo devedor)

- **Regra:** se a fatura fecha com `valorPago < total`, o restante (`total - valorPago`) vira **saldo devedor carregado** e e lancado na **proxima fatura** como divida (lancamento `SALDO_DEVEDOR_ANTERIOR`, valor positivo). A fatura fecha normalmente (**nao bloqueia** o fechamento; status derivado sem alteracao alem do padrao). **Sem juros/rotativo no MVP.**
- **Alerta:** web/mobile exibem aviso claro de saldo devedor carregado (nao e erro) — chip ambar/alerta, nunca vermelho.
- **Exemplo:**
  - Fatura Jul: total R$200, pago R$120 → saldo devedor **R$80**. Fecha (status derivado normal), gera divida R$80.
  - Fatura Ago: compras R$300 + lancamento de saldo devedor **+R$80** → total **R$380**.
- **Fora de escopo do MVP:** juros rotativo, IOF, multa, mora, taxa por banco. So entram se virarem requisito explicito (exigem regras por instituicao e datas exatas).

### Mapeamento no Ledger / modelo

- **Novos `TipoFaturaLancamento`:** `CREDITO_ANTERIOR` (valor negativo, origem = credito rolado de R1) e `SALDO_DEVEDOR_ANTERIOR` (valor positivo, origem = divida rolada de R2). Ambos entram na proxima fatura como lancamentos normais, entrando no calculo de total (soma de `FaturaLancamento` continua sendo a fonte da verdade — decisao 13) e no invariante de `Conta.valorGasto` (decisao 16). `FaturaLancamento.transacao` e nullable (lancamentos de rollover nao tem transacao de origem); novo campo `faturaOrigem` referencia a fatura que gerou o rollover, para rastreabilidade.
- **Idempotencia:** guard em codigo `FaturaLancamentoRepository.existsByFaturaOrigemId` evita gerar o rollover duas vezes; lock pessimista na fatura de origem (`findWithLockByIdAndUsuarioId`) serializa leituras concorrentes; unique index parcial `ux_fatura_rollover_origem_tipo (fatura_origem_id, tipo) WHERE fatura_origem_id IS NOT NULL` (migration `V25__fatura_rollover.sql`) e o backstop de banco — violacao (`DataIntegrityViolationException`) e tratada como no-op.
- **Rastreabilidade:** cada lancamento rolado referencia a fatura de origem via `faturaOrigem` (para o usuario ver de onde veio o credito/divida).
- **Sem impacto direto em carteira:** R1 e R2 nunca criam `MovimentoCarteira` — so movem valor entre faturas do mesmo cartao. A carteira so e tocada por pagamento real (`pagarFatura`, ja existente).

### Estado de implementacao

- **Backend:** `FaturaService.liquidarFaturaAnterior` (novo), `model/FaturaLancamento.java` (campo `faturaOrigem`, `transacao` nullable), `model/enums/TipoFaturaLancamento.java` (`CREDITO_ANTERIOR`, `SALDO_DEVEDOR_ANTERIOR`), `repository/FaturaLancamentoRepository.java` (`existsByFaturaOrigemId`), migration `V25__fatura_rollover.sql`.
- **Testes:** `FaturaRolloverTest` (novo, 7 casos: R1 basico, credito abate a proxima, credito rola de novo, R2 saldo devedor, pagamento total sem rollover, idempotencia em dupla leitura, cadeia com mes pulado; invariante `Conta.valorGasto` assertado nos casos 1/4/6). Execucao real desta rodada: `./mvnw -q test` → Tests run: 142, Failures: 0, Errors: 0; `scripts/verify-postgres-migrations.sh` → PASS (`PostgresMigrationIT` 5/0). Nao-regressao: `FaturaCartaoWorkflowTest` 9/9.
- **UI:** web (`frontend/src/pages/Faturas.tsx`, `frontend/src/services/faturaService.ts`) e mobile (`mobile/app/(app)/more/faturas.tsx`, `mobile/src/services/faturaService.ts`, `mobile/src/types/index.ts`) exibem `CREDITO_ANTERIOR` em verde ("Credito anterior") e `SALDO_DEVEDOR_ANTERIOR` em ambar/alerta ("Saldo devedor anterior").
- **Ressalva conhecida:** o unique index `ux_fatura_rollover_origem_tipo` da V25 nao existe no schema de teste (H2 create-drop, Flyway desligado em teste) — idempotencia validada em teste apenas pelo guard de codigo; o backstop de banco e concorrencia real de 2 threads nao tem cobertura de teste automatizado (design coberto por lock pessimista + unique index, a validar em producao). Ver PROB-0050 (riscos residuais) e BACKLOG-0059/0054 (fechados).

## Assistente financeiro (lançar conversando)

Fluxo: mensagem de texto ou áudio → parser determinístico (`RuleBasedFinancialInputParser`) → se
faltar dado, provider estruturado (Gemini/OpenAI, desligados por padrão) → **rascunho** →
**revisão obrigatória** no mesmo `NovaTransacaoModal` do lançamento manual → confirmação. Nada entra
no extrato sem a pessoa confirmar na tela.

Invariantes que o desenho carrega:

- **Parser antes de qualquer provider externo.** Frase completa nunca chega a chamar serviço pago.
- **Uma pergunta só.** Faltando um campo, o assistente pergunta uma vez; faltando mais, manda para o
  formulário.
- **Confirmação exatamente uma vez.** Lock pessimista, `@Version`, operação de origem `ASSISTENTE` e
  snapshot imutável; `Idempotency-Key` reusada com payload diferente devolve `409`.
- **Parcelamento é privilégio do cartão.** O rascunho aceita "em 3x" antes de saber o cartão, mas o
  `confirm` recusa rascunho incompleto e cobra o cartão — mesma regra do formulário manual. Com
  cartão, a transação é uma só e o cronograma vive em `fatura_lancamentos`; a carteira não é
  movimentada. Sem cartão, o lançamento é à vista. Ver BACKLOG-0113.
- **Cartão substitui a conta no rascunho.** `contaNome` e `cartaoNome` são mutuamente exclusivos:
  compra de cartão não tem carteira.

Perguntas financeiras ("quanto gastei", "qual meu saldo") seguem outro caminho —
`FinancialQuestionClassifier` → serviços oficiais de leitura — e nunca criam rascunho. O
classificador só assume consulta quando a frase realmente pergunta: frase com verbo de lançamento ou
valor é tratada como lançamento, mesmo citando "cartão" ou "fatura" (PROB-0086).

Para rodar tudo isso localmente sem provider pago, o profile `local-e2e` publica providers
determinísticos e um injetor de falha; ver `docs/runbooks/FASE5_FECHAMENTO_OPERACIONAL.md`.

**Descoberta do canal (desde 2026-08-29, PROB-0092, ADR-0018):** nem o app nem a decisão de exibir
o item "Assistente" dependem mais de nenhuma variável embutida no build. O cliente consulta `GET
/api/v1/capacidades` (autenticado, sem `@ConditionalOnProperty` de propósito) e liga/desliga a UI
em runtime a partir de `assistenteTexto`/`assistenteAudio`/`assistenteWhatsapp` — as mesmas
properties que já controlam os controllers reais. Falha ou carregamento tratam tudo como desligado
(fail-closed).

## Limitacoes conhecidas

Lista reconferida contra o codigo em **2026-08-29**. Nove itens que ainda diziam "sem testes no
mobile", "43 testes no backend", "sem CI/CD", "sessao nao persiste", "IP hardcoded" e "email e stub"
descreviam o sistema de julho e foram removidos ou reescritos — o estado real de cada um esta abaixo.

### Cobertura e automacao

1. **Migrations versionadas (Flyway):** `V1__baseline_schema.sql` ate `V67__recorrencia_cartao.sql`, **66 arquivos** (a numeracao tem lacunas), com `ddl-auto=validate` em dev e prod. PROB-0006 resolvido. `V67` valida contra PostgreSQL 16 real via `scripts/verify-postgres-migrations.sh` (2026-08-29).
2. **Testes existem nas tres frentes:** backend **501** testes, mobile **447** (40 suites Jest), web **12** arquivos de teste Vitest — o web segue a frente menos coberta.
3. **E2E mobile existe:** 11 flows Maestro em `mobile/.maestro/`, incluindo os seis do assistente. Nao ha Detox. O gate `mobile-maestro.yml` roda no CI.
4. **CI existe:** GitHub Actions (`ci.yml`, `mobile-maestro.yml`, `mobile-release.yml`). Deploy segue manual.
5. **Integracao PostgreSQL depende de Docker local:** `mvn verify -Pintegration-test` e `scripts/verify-postgres-migrations.sh` exigem daemon Docker acessivel; em sandbox sem Docker esses gates nao rodam e isso ja mascarou rodadas anteriores como "verdes".
6. **`importacao-mobile` sem cobertura E2E real:** o seletor de arquivos do iOS nao lista o fixture escrito em `Media/Downloads`, entao o flow fica SKIPPED (BACKLOG-0111).
7. **Trinco visual cobre `app/**` mas nao `src/components/**`:** `ComposicaoMetricaModal`, `EditarTransacaoModal` e `NovaTransacaoModal` continuam com `pageSheet` a mao e podem regredir sem quebrar o build (BACKLOG-0099).
8. **Acessibilidade nao verificada com leitor de tela real:** a arvore foi conferida por dump do Maestro, nunca com VoiceOver/TalkBack ligado; Reduce Motion e Android tambem seguem sem verificacao (BACKLOG-0078).
8b. **Verificacao de UI/UX so cobre iPhone 17 Pro / iOS 26.5 / tema escuro (2026-08-29, PROB-0096/0097):** nenhuma rodada deste projeto ainda exercitou Android, telas pequenas, tema claro ou fonte ampliada — a faixa de status bar do item 35 (Principais decisoes tecnicas) e especialmente sensivel a isso, porque `insets.top` no Android varia com a status bar ser translucida ou opaca (BACKLOG-0119).
8c. **`scripts/e2e-mobile-ios.sh` e `scripts/e2e-assistant-ios.sh` compartilham `DerivedData` (`mobile/.e2e-derived-data`):** a causa do app errado instalado pelo smoke de UI (PROB-0095) foi corrigida ancorando cada script na sua configuracao, mas dois runners escrevendo no mesmo `DerivedData` continua sendo uma armadilha estrutural para a proxima variacao de build (BACKLOG-0118).

### Seguranca e hardening

9. **CSP basica em `/api/**`:** `SecurityConfig` mantem uma CSP pouco restritiva para a API; o SPA tem CSP propria e restritiva no nginx desde 2026-07-14. Divida de hardening conhecida.
10. **Rate limit depende da cadeia de proxy:** a chave e o IP resolvido de `X-Forwarded-For`. A correcao de `forward-headers-strategy` fecha o contorno conhecido, mas o smoke em staging continua pendente (BACKLOG-0080).
11. **Biometria nao protege o token em repouso:** `requireAuthentication` nao e usado no SecureStore (BACKLOG-0104).
12. **`csrfToken` do mobile e codigo morto:** o app grava e nunca le (BACKLOG-0103).
13. **34 `: any` no frontend web:** type safety fraca nos services e componentes do SPA (antes o numero registrado era 54).

### Dominio financeiro

14. **Tabela `Parcela` legada coexiste com `FaturaLancamento`:** redundancia mantida; `FaturaLancamento` e a fonte real do calculo de fatura desde a V17. Candidata a aposentadoria (BACKLOG-0050).
15. **Status `FECHADA` da fatura sem teste dedicado:** a derivacao continua validada so por revisao de codigo.
16. **`Conta.valorGasto` pode ficar negativo:** estorno maior que as compras em aberto vira credito; web e mobile exibem como "credito disponivel". E regra, nao defeito (PROB-0073/0074).
17. **Edicao de compra parcelada recalcula o cronograma inteiro:** parcelas em faturas pagas ficam imutaveis e a diferenca entra como `AJUSTE` na proxima fatura aberta.
18. **Idempotencia do rollover sem teste contra o unique index:** o schema de teste (H2) nao cria `ux_fatura_rollover_origem_tipo`, entao so o guard de codigo e exercitado; concorrencia real de duas threads nao tem cobertura.
19. **Ledger so movimenta com `carteiraId` explicito:** e decisao de design, mas obriga todo cliente a enviar a carteira; transacoes antigas sem carteira nao ganharam movimento retroativo (BACKLOG-0045).
19b. **Recorrencia de cartao so sabe periodicidade mensal (2026-08-29, PROB-0098):** `ContaFixa.plusMonths(1)` e hard-coded; assinaturas anuais/semanais/quinzenais nao tem caminho de cadastro (BACKLOG-0120).
19c. **`CarteiraService.deletar` nao verifica `contas_fixas` (2026-08-29, achado colateral de PROB-0098):** mesma classe de furo que `CartaoService.deletarCartao` tinha antes de BUG-0102 — uma carteira sem `movimentos_carteira`, mas referenciada por uma recorrencia, pode estourar FK `RESTRICT` como 500 em vez de um 422 informativo (BACKLOG-0122).
19d. **Estouro de limite de cartao nao notifica o usuario (2026-08-29, PROB-0098):** decisao do dono do produto foi lancar e avisar, nunca bloquear; o "lancar" existe (nao ha validacao de limite alguma hoje — `Conta.limiteTotal` e informativo), o "avisar" ainda nao foi implementado (BACKLOG-0125).

### Assistente

20. **Providers externos desligados por padrao:** `assistant.text/audio/whatsapp/external.enabled` nascem `false`, e o externo ainda exige `billing-confirmed` e `data-policy-accepted`. Sem isso o sistema opera so com o parser deterministico.
21. **`missingFields` fala dois vocabularios:** nome (`contaNome`, `cartaoNome`) no fluxo de mensagem e id (`carteiraId`, `cartaoId`) no patch (BACKLOG-0112).
22. **Classificador de perguntas e lexical:** desde PROB-0086 ele exige forma de pergunta antes de assumir consulta, mas continua decidindo por palavra — uma pergunta sem pronome interrogativo e com valor pode ser lida como lancamento.
23. **Parcelamento so no cartao:** decisao de produto registrada (BACKLOG-0113); o backend sabe parcelar fora do cartao, e a capacidade esta deliberadamente nao exposta.
24. **Homologacao Meta/WhatsApp pendente:** producao segue desligada ate aprovacao (runbook `WHATSAPP_ASSISTANT_SANDBOX.md`).

### Operacao

25. **Backup off-host e restore drill pendentes (`PROB-0081`):** gate que ainda bloqueia promocao.
26. **Push sem `extra.eas.projectId` e sem credenciais APNs/FCM:** o registro desiste em silencio; aviso so chega na caixa in-app (BACKLOG-0110).
27. **Sem cache de API (Redis):** nem para respostas frequentes, nem para refresh token — banco lento afeta toda autenticacao.
28. **Node 20 e o exigido pelo projeto:** a rodada de 2026-08-29 correu em Node 26 sem falha observada, mas o runbook continua pedindo 20.
29. **`ContaFixa` ganhou dois destinos de cobranca mutuamente exclusivos — caixa (`carteira_id`) ou cartao (`conta_id`) — na migration `V67` (2026-08-29, PROB-0098):** ate entao o motor de recorrencia so sabia debitar caixa; uma assinatura cobrada no cartao (ex.: Netflix) nao tinha caminho de cadastro, so parcelamento. `V67__recorrencia_cartao.sql` (expand puro, ADR-0015) segue o padrao "um destino, nunca dois" ja usado na V55: adiciona `contas_fixas.conta_id`, troca `ck_contas_fixas_automatica_carteira` por `ck_contas_fixas_destino_automatico` e adiciona `ck_contas_fixas_destino_unico`/`ck_contas_fixas_cartao_saida`; cada `CHECK` tem validacao equivalente em `ContaFixaService.resolverDestino` devolvendo 4xx antes de chegar ao banco (exclusividade, ownership do cartao 404, cartao inativo 422, ENTRADA+cartao 400). O ramo cartao cria `Transacao` com `conta` setada e `carteira=null`; como `TransacaoService.criar` ja chamava `FaturaService.registrarCompraCartao`, nenhuma regra de fatura foi reescrita ou duplicada (ADR-0001). **Mudanca de semantica de idempotencia (BUG-0098):** `TransacaoService.registrarMovimentoCriacao` descartava `ledgerIdempotencyKey` silenciosamente sempre que `carteira == null` — ou seja, em qualquer compra de cartao, nao so recorrencia; `FaturaService.registrarCompraCartao` ganhou sobrecarga `(Transacao, Long, String idempotencyKey)` que propaga a chave para `CriarOperacaoCommand` quando presente (a versao de 2 argumentos delega com `null`, nenhum chamador existente muda de comportamento). Decisoes do dono do produto: assinatura de cartao e automatica por padrao; criacao exposta no botao "Nova" e na tela Recorrencias; estouro de limite lanca e avisa, nunca bloqueia (nao ha validacao de limite alguma hoje — `Conta.limiteTotal` e informativo — entao o "avisar" fica pendente, ver BACKLOG-0125). Quatro outros defeitos pre-existentes do motor de recorrencia foram corrigidos na mesma sessao para o cartao poder usar o motor com seguranca: execucao automatica nao revalidava vencimento sob lock (BUG-0099, risco de dupla execucao com duas instancias, vale para os dois destinos); `carteiraId` do corpo da requisicao de `realizar` desviava cobranca de cartao para o caixa (BUG-0100); corrida no unique de `execucoes_recorrencia` devolvia 500 em vez de 422 (BUG-0101); exclusao de cartao (soft delete) nao desativava assinaturas vinculadas (BUG-0102, novo `ContaFixaRepository.desativarPorConta`). Evidencia: backend 501 testes/0 falhas (10 novos em `ContaFixaCartaoTest`, +1 em `UsuarioExclusaoTest` para a FK nova na exclusao LGPD), mobile 447 testes/40 suites/0 falhas, `scripts/verify-postgres-migrations.sh` exit 0 contra PostgreSQL 16 real. Riscos residuais e pendencias em BACKLOG-0120 a BACKLOG-0126 (frequencia so mensal, refactor de `@Data`, `CarteiraService.deletar` com o mesmo tipo de furo de FK para `contas_fixas`, flag morta `Transacao.recorrente`, sugestao de recorrencia detectada nao herda cartao, notificacao de estouro de limite, paridade web). Ver PROB-0098 para o relato completo do sintoma/causa/correcao.

## Pontos frageis atuais

1. **Ownership implementado:** todos os services validam posse via `findByIdAndUsuarioId` ou `buscarPorIdDoUsuario`. PROB-0001 e PROB-0021 resolvidos.
2. **Rate limit com account lockout:** login/register/reset-password/forgot-password/validate-token com rate limit por IP. Desde 2026-07-11, tentativas ficam em `rate_limit_buckets` no PostgreSQL com lock pessimista por chave, consistente entre instancias e resistente a restart de JVM (PROB-0055 resolvido). Account lockout por email apos falhas consecutivas. Limpeza periodica de buckets expirados. **Ressalva critica desde 2026-07-14 (PROB-0066):** a chave do rate limit e o IP resolvido de `X-Forwarded-For` — a correcao de `forward-headers-strategy` (framework→native) e a normalizacao do header pelo nginx fecham o contorno conhecido, mas o mecanismo continua tao confiavel quanto a cadeia de proxy real na frente da API (ver BACKLOG-0080 para o gate de smoke pendente).
3. **Schema versionado com Flyway:** migrations em `db/migration/`, `ddl-auto=validate` em dev e prod. PROB-0006 resolvido.
4. **Refresh token no banco:** se o banco ficar lento, toda autenticacao sofre. Sem cache (Redis) para tokens.
5. **LoginRateLimitFilter com protecao completa:** register (5/min), login (5/min), forgot-password (3/min), reset-password (5/min), validate-token (10/min). Account lockout apos 5 falhas. @Scheduled cleanup de entradas expiradas a cada 60s. (PROB-0024 resolvido).
6. **Logs sem PII:** EmailService com maskEmail. Token nunca logado. (PROB-0011 resolvido).
7. **Contrato de erro padronizado:** ApiError com code, message, timestamp, requestId. X-Request-Id header. MDC requestId para correlacao de logs.
8. **Mobile usa Expo Secure Store de fato:** access token, refresh token e cache de usuario ficam no SecureStore desde 2026-07 (PROB-0013 fechado). A sessao sobrevive ao fechamento do app; `AppLockGate` cobre cold start e retorno apos 60s em segundo plano. **Consequencia descoberta em 2026-08-29:** como o Keychain do iOS sobrevive a reinstalacao, o `clearState` do Maestro nao derruba a sessao — todo E2E precisa de `simctl keychain reset` entre flows (PROB-0089).
9. **Configurações de produção seguras:** cookie.secure=true, CORS sem fallback, secrets sem default (PROB-0005, PROB-0010 resolvidos).
10. **TransacaoService com ownership corrigido:** categoriaId e contaId validados via `findByIdAndUsuarioId` (PROB-0001 resolvido).
11. **Operacoes transacionais corrigidas:** todos os metodos de escrita com @Transactional (PROB-0012 resolvido).
12. **Constructor injection em producao:** sweep de 2026-07-11 removeu `@Autowired` de `backend/src/main/java`; controllers, services, config e security usam constructor injection com dependencias `final`. Testes Spring ainda usam `@Autowired`, aceitavel para testes de integracao/contexto Spring. PROB-0057 fechado.
13. **Ledger so movimenta com carteiraId explicito:** ausencia de `carteiraId` no payload de transacao e uma decisao de design (nao um bug), mas exige que todo client (web/mobile) sempre envie a carteira; o mobile so passou a fazer isso a partir de 2026-07-09 (BUG-0012). Transacoes antigas sem carteira nao tem movimento retroativo (BACKLOG-0045).
14. **Interceptor de refresh agora simetrico entre web e mobile:** ambos os clientes renovam o access token automaticamente em 401 (BUG-0013, 2026-07-09). Antes disso, apenas o web tinha esse comportamento.
15. **Fluxo de compra no cartao + faturas ressincronizado:** editar valor/data de uma compra de cartao ja lancada recria os lancamentos de fatura e reajusta `Conta.valorGasto`/`Categoria.valorGasto`; compra retroativa nunca mais cai em fatura ja `PAGA` (rola ate 24 meses a frente); `valorGasto` da conta so e ajustado para transacoes `SAIDA` (BUG-0017 a BUG-0022, 2026-07-09).
16. **Edicao/cancelamento de compra com fatura paga nunca mais bloqueia:** decisao de bloquear com `BusinessException` (adotada horas antes, mesma sessao) foi substituida por compensacao automatica via lancamento `AJUSTE`/`ESTORNO` na proxima fatura em aberto — fatura paga tratada como imutavel, nunca como trava de operacao do usuario (BUG-0023, BUG-0024, PROB-0044, 2026-07-09).
17. **Mobile sem cobertura de teste para o fluxo de fatura/transacao:** `EditarTransacaoModal` (BUG-0027) e os badges de status/tipo de fatura (BUG-0028, BUG-0029) foram validados apenas por `tsc --noEmit`, leitura de diff e um teste manual de contrato contra o backend local — sem suite automatizada de UI mobile (limitacao conhecida item 2/8).
18. **Investimentos com integridade de posicao e integracao de caixa opcional (PROB-0054 resolvido, 2026-07-11):** `InvestimentoService` bloqueia venda acima da posicao atual (`BusinessException`), valida quantidade > 0 e preco >= 0 (> 0 exceto BONIFICACAO), trata DIVIDENDO como provento sem alterar posicao e BONIFICACAO com custo zero. Integracao com carteira/caixa e **opcional**: se `MovimentacaoRequest.carteiraId` for informado, COMPRA debita e VENDA/DIVIDENDO creditam o caixa via `LedgerService` (origem `INVESTIMENTO`, migration `V22__movimentos_carteira_origem_investimento.sql`); sem `carteiraId`, so a posicao e atualizada (compatibilidade com o mobile atual, que ainda nao envia carteira nas movimentacoes de ativo — ver BACKLOG-0063). **Atualizacao 2026-08-21:** `adicionarMovimentacao` ganhou idempotencia por header `Idempotency-Key` e a listagem (`GET /api/v1/investimentos`, `GET /api/v1/investimentos/{ativoId}/movimentacoes`) passou a ser paginada (`Page`, `size` maximo 100) — ver item 26/27 em "Limitacoes conhecidas" acima, BACKLOG-0081/BACKLOG-0082 (FECHADOS) e BUG-0076/BUG-0077.
19. **Kit visual do mobile (`src/components/ui/`) normalizado, mas rotulo de acessibilidade ainda depende de revisao manual (2026-08-21/22):** a serie de 13 PRs de padronizacao visual (item 27 em "Principais decisoes tecnicas") fechou cinco implementacoes divergentes de barra de progresso numa so (`ui/ProgressBar`) e corrigiu nove ocorrencias de `accessibilityLabel` curado apagando conteudo real da arvore de acessibilidade (BUG-0084) — mas essa classe de bug so foi encontrada por revisao manual durante a migracao de cada tela; o trinco `padraoVisual.test.ts` nao varre `accessibilityLabel`, entao uma nova ocorrencia em tela futura nao quebra o build. Sem verificacao com VoiceOver/TalkBack real em nenhuma das telas corrigidas.

## Auditoria e estado atual

### Estado em 2026-08-29 (rodada mais recente — verificacao de UI/UX pos PROB-0092/0093/0094)

- **Backend:** 481 testes verdes.
- **Mobile:** 437 testes verdes; **Web:** 47 testes verdes.
- **E2E iOS — smoke financeiro:** `scripts/e2e-mobile-ios.sh` fechou com `OK: smoke financeiro
  concluido` apos a correcao de PROB-0095 (app errado + Debug quebrado + Keychain nao resetado +
  lista de invariantes desatualizada) — flow Maestro 1 teste / 0 falhas / 167s, reconciliacao
  global 8 verificacoes / 0 divergencias, conta final `Conta Principal · saldo 825 · banco Nubank ·
  principal=true`.
- **E2E iOS — Assistente:** `scripts/e2e-assistant-ios.sh` fechou com `OK: Assistente iOS` — seis
  flows (assistant-text, ambiguity, retry, confirm-retry, parcelado, audio) + `importacao-mobile`,
  todos com prova financeira e 0 divergencias, rodando **sem** `EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED`
  (prova em runtime do gate do ADR-0018).
- **Verificado por screenshot na UI real:** selo verde "Principal" no card da conta, banco "Nubank"
  exibido, botoes Editar/Excluir presentes na conta manual e ausentes em Cofre/Cartao ("Somente
  leitura · gerenciada no modulo de origem"), edicao de saldo R$ 825,00 → R$ 900,00 → R$ 825,00
  persistindo, card novo "Gastos por conta" em Relatorios, aba renomeada para "Relatorios", faixa
  solida sob o relogio/wifi/bateria ao rolar (PROB-0096 corrigido).
- **Nao executado nesta rodada:** `mvn verify -Pintegration-test` (PostgreSQL), Android, tema claro,
  fonte ampliada, leitor de tela real, smoke em staging (ver item 8b/8c em "Limitacoes conhecidas",
  BACKLOG-0119).
- **Gates externos que seguem abertos:** billing/politica de dados dos fornecedores de IA,
  homologacao Meta/WhatsApp, backup off-host e restore drill (`PROB-0081`) e o deploy com smoke de
  producao. Enquanto isso, `assistant.*` e os providers externos permanecem desligados por padrao.

### Auditoria fundacional (registro historico)

- **Data:** 2026-07-06
- **Tipo:** Auditoria completa de seguranca, bugs e codigo inacabado (read-only)
- **Escopo:** backend (100%), frontend web (100%), mobile (100%)
- **Resultado original:** PASS_COM_RESSALVA — sistema funcional mas nao pronto para producao
- **Achados originais:** 15 CRITICAL, 12 HIGH, 32 MEDIUM, 24 LOW (83 total)
- **Relatorio:** `docs/REVIEW_REPORTS/2026-07-06_full-system_security-and-bug-audit.md`
- **Registro acumulado desde entao:** 97 problemas (`PROB-0001`..`PROB-0097`), entradas de backlog
  numeradas ate `BACKLOG-0119` e bugs corrigidos numerados ate `BUG-0103` — contagem de itens
  ABERTO/FECHADO nao reconferida linha a linha nesta atualizacao, so os IDs mais altos.
