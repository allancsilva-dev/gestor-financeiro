# System Overview — Gestor Financeiro

Documentacao de alto nivel sobre como o sistema funciona. Mantido pelo `docs-reporter`.

**Ultima atualizacao:** 2026-07-14 (hardening pre-producao P0+P1 commitado em `main`: `5c08ce0`, `0d1e0c0`, `c959dfc`; cadeia de resolucao de IP na stack de proxy corrigida, pagamento de parcela idempotente contra duplo debito, exclusao de carteira sem 500, indices de suporte, headers de seguranca no SPA, reset de senha sem token na query string)

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
| Auth store mobile | Expo Secure Store (access/refresh token, usuario cache) | 15.0.8 |
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
O banco e PostgreSQL gerenciado via JPA/Hibernate com Flyway para migrations versionadas e `ddl-auto=validate` em dev e prod. O PR-LEDGER-01 adicionou validação automatizada com Testcontainers PostgreSQL para Flyway em banco limpo; o PR-LEDGER-02 adicionou o schema inicial do Ledger (`movimentos_carteira`) na migration `V11`; o PR-LEDGER-03 adicionou `LedgerService` para escrita atômica de movimento + saldo; o PR-LEDGER-04 adicionou reconciliação entre saldo materializado e saldo derivado do Ledger; o PR-LEDGER-05 adicionou backfill inicial idempotente na migration `V12`. Em 2026-07-11, o gate canônico de PostgreSQL real passou a ser `scripts/verify-postgres-migrations.sh`: o script sobe PostgreSQL 16 via Docker CLI e executa `PostgresMigrationIT` com `POSTGRES_IT_JDBC_URL`, evitando dependência exclusiva do socket Testcontainers local. O projeto e single-tenant: cada usuario acessa apenas seus dados,
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
5. **Interceptor Axios (mobile):** desde 2026-07-11 (BUG-0051/PROB-0056), mobile usa contrato body-only: `withCredentials:false`, `X-Client-Type: mobile`, refresh token lido do `SecureStore` e enviado no body para `/auth/refresh-token`; cookie/CSRF ficam exclusivos do contrato web. O interceptor detecta 401 fora de rotas `/auth/`, usa uma promise deduplicada entre requests concorrentes e repete a request original com o novo Bearer token.
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
4. **Flyway migrations (antes era ddl-auto=update):** schema versionado, previsível e reproduzível entre ambientes. PROB-0006 resolvido. Teste PostgreSQL real automatizado via `scripts/verify-postgres-migrations.sh`; `mvn verify -Pintegration-test` continua disponível quando Testcontainers estiver saudável.
5. **React Context API em vez de Redux/Zustand:** simplicidade para estado global limitado (apenas auth).
6. **Tailwind CSS em vez de CSS-in-JS:** produtividade, consistencia visual, baixo bundle size.
7. **Expo em vez de React Native puro:** build e deploy simplificados, OTA updates.
8. **Axios com interceptor de refresh:** fila de requisicoes concorrentes evita multiplos refresh tokens simultaneos.
9. **Rate limit custom (sem Bucket4j):** implementacao propria em `LoginRateLimitFilter`, janela movel, sem dependencia externa.
10. **Categoria.valorGasto so reflete SAIDA:** desde 2026-07-09 (BUG-0015), criar/deletar transacao so ajusta `valorGasto` da categoria quando `tipo == SAIDA` — entradas nunca contam como gasto no indicador de orcamento por categoria.
11. **Design mobile alinhado ao prototipo standalone:** componentes `Entrance` (`gf-rise`/`gf-pop`, respeita Reduce Motion) e `FloatEmoji` (`gf-float`) portados de `docs/Gestor Financeiro (standalone).html` para o app Expo; `Fab` com gradiente violeta `#7c5cfc`→`#8b2fff` e glow (BACKLOG-0048, 2026-07-09).
12. **Ultima parcela absorve arredondamento:** desde 2026-07-09 (BUG-0017), parcelas de compra no cartao (`FaturaLancamento` e `Parcela` legada) usam `valorTotal/n` HALF_UP para as N-1 primeiras parcelas e `valorTotal - soma(N-1 parcelas)` na ultima, garantindo que a soma feche exatamente com o valor total e que `Conta.valorGasto` (limite do cartao) zere apos quitacao completa.
13. **Soma de FaturaLancamento e a fonte da verdade do valor da fatura:** desde 2026-07-09 (BUG-0021), `pagarFatura` e `toResponse` calculam o valor da fatura pela soma dos lancamentos, nao pelo campo `valorTotal` persistido incrementalmente (sujeito a dessincronia). Fallback para `valorTotal` persistido apenas em faturas anteriores a migration V17, sem lancamentos.
14. **Pagamento parcial de fatura suportado:** desde 2026-07-11 (BUG-0052), `pagarFatura` aceita valor positivo ate o saldo restante (`valorTotal - valorPago`), acumula `valorPago`, debita a carteira e libera limite pelo valor efetivamente pago. A fatura so muda para `PAGA` quando `valorPago >= valorTotal`; web/mobile enviam `Idempotency-Key` por toque de pagamento.
15. **Fatura paga e imutavel — compensacao via lancamento na proxima fatura aberta:** desde 2026-07-09 (revisao 2, mesma sessao — PROB-0044), editar ou cancelar uma compra de cartao com fatura(s) ja paga(s) nunca mais bloqueia com `BusinessException` (substitui a decisao registrada horas antes no mesmo dia). Em vez disso, a parte da compra que ja esta em fatura paga e tratada como imutavel; a diferenca (edicao) ou o valor integral (cancelamento) e lancado como `TipoFaturaLancamento.AJUSTE`/`ESTORNO` (podendo ser negativo, ou seja, credito) na proxima fatura em aberto — mesmo principio de estorno de cartao de credito real. Enum `TipoFaturaLancamento` (`COMPRA`/`AJUSTE`/`ESTORNO`) e coluna `tipo` introduzidos na migration `V18__fatura_lancamento_tipo.sql`.
16. **Invariante centralizado de limite de cartao:** `Conta.valorGasto == soma dos lancamentos em faturas nao pagas menos pagamentos ja feitos` (inclui compras, ajustes, estornos e pagamentos parciais). Helpers privados `criarLancamento`/`removerLancamentoDeFaturaAberta`/`ajustarLimiteUtilizado` em `FaturaService` sao o unico ponto que ajusta `valorGasto` para compras de cartao; `TransacaoService` deixou de chamar `contaService.adicionarGasto`/`removerGasto` para transacoes que sao compra de cartao (mantido apenas para contas que nao sao cartao de credito). `pagarFatura` libera limite pelo valor pago.
17. **Mobile ganhou edicao/exclusao de transacao (`EditarTransacaoModal`):** desde 2026-07-09 (PROB-0045/BUG-0027), tocar numa linha de `mobile/app/(app)/transacoes.tsx` abre um sheet que edita apenas os campos que o backend de fato aplica em `PUT /api/v1/transacoes/{id}` (valor, descricao, data, observacoes); tipo/categoria/forma de pagamento sao exibidos como contexto fixo, nao editavel, pois o backend os ignora. Compra de cartao exibe aviso de que a edicao/exclusao ressincroniza faturas via `FaturaService.ressincronizarCompraCartao`/`cancelarCompraCartao` (PROB-0044).
18. **Badge de status/tipo de lancamento na fatura em mobile e web:** desde 2026-07-11 (BUG-0052), `mobile/app/(app)/more/faturas.tsx` e `frontend/src/pages/Faturas.tsx` exibem chip de tipo para `ESTORNO`/`AJUSTE` e removem o prefixo textual `"Ajuste: "`/`"Estorno: "` da descricao exibida.
19. **Recorrências são ocorrências mensais idempotentes:** desde 2026-07-14 (BUG-0066), `ContaFixa` representa entrada ou saída e pode ser manual ou automática. `execucoes_recorrencia` registra vencimento/status/tentativa/falha/transação com unicidade `(conta_fixa_id, data_vencimento)`; a carteira e a recorrência são bloqueadas antes do lançamento e o ledger recebe a chave `RECORRENCIA:{id}:{data}`. O scheduler roda às 00:05 em `America/Sao_Paulo` e também no `ApplicationReadyEvent` para recuperar ocorrências perdidas. Saída sem saldo permanece `FALHA_SALDO`, não cria transação e não permite saldo negativo.
20. **Dados mobile protegidos sem apagar a sessão:** tokens e usuário continuam no SecureStore, enquanto `AppLockGate` bloqueia cold start e retornos após 60 segundos em segundo plano. O desbloqueio aceita biometria/credencial do aparelho ou validação online da senha; a senha nunca é persistida. O estado `inactive/background` cobre imediatamente a interface para impedir captura dos valores pelo seletor de apps.
19. **Rollover de credito/saldo devedor de fatura e lazy na leitura, sem endpoint de fechamento nem scheduler:** desde 2026-07-11 (BUG-0053), decisao do dono do produto foi nao criar um passo explicito de "fechar fatura" — o status `FECHADA` continua derivado (BUG-0020) e o rollover (`FaturaService.liquidarFaturaAnterior`) e disparado ao materializar a proxima fatura de competencia (`buscarAtual`/`buscarPorMes`/`criarOuBuscarFatura`), liquidando recursivamente faturas anteriores ja fechadas. Ver secao "Regra de produto: credito de fatura e saldo devedor rolado".
20. **Resolucao de IP do cliente via `forward-headers-strategy=native` (Tomcat `RemoteIpValve`), nao `framework`:** desde 2026-07-14 (PROB-0066/BUG-0059), o Spring deixou de confiar diretamente em todo o `X-Forwarded-For` recebido — o Tomcat resolve o IP real a partir de uma lista fechada de proxies internos confiaveis (`internal-proxies`, loopback + faixas privadas Docker). A env var `SERVER_FORWARD_HEADERS_STRATEGY` nos `docker-compose.*.yml` sobrepoe o valor do profile — os dois precisam estar alinhados. Decisao acoplada a uma premissa de infraestrutura: o proxy mais externo (nginx standalone ou Nginx Proxy Manager) precisa sempre normalizar/anexar o `X-Forwarded-For` com o IP real de conexao, nunca repassar cegamente o header do cliente.
21. **SPA ganhou headers de seguranca no nginx, nao no Spring:** desde 2026-07-14 (PROB-0070/BUG-0063), como `SecurityConfig` so intercepta `/api/**`, HSTS/`X-Frame-Options`/`X-Content-Type-Options`/`Referrer-Policy`/CSP foram adicionados diretamente nos dois configs de nginx (`nginx.conf.template`, `nginx.npm.conf`), nao no backend — decisao de manter a responsabilidade de servir o SPA com seus headers de seguranca na camada que efetivamente entrega esses arquivos ao navegador.
22. **Reset de senha via POST com corpo, nao GET com query string:** desde 2026-07-14 (PROB-0071/BUG-0064), `validate-token` migrou de `GET /api/auth/validate-token?token=...` para `POST /api/auth/validate-token` com `ValidateTokenRequest { token }` no corpo, evitando que o token de reset (segredo de curta duracao) fique registrado em access logs de proxies/CDN e historico do navegador. O `GET` antigo agora retorna 405 (`HttpRequestMethodNotSupportedException` tratada explicitamente no `GlobalExceptionHandler`, que antes so tinha um catch-all generico → 500 para metodo nao mapeado).
23. **Pagamento de parcela e exclusao de carteira ganharam guards de estado/ownership de movimento (2026-07-14, PROB-0067/PROB-0068):** `ParcelaService.marcarComoPaga` retorna no-op se a parcela ja estiver `PAGO` (evita duplo debito por reenvio) e `Parcela` ganhou `@Version` (protege contra concorrencia real, mesmo padrao de PROB-0002); `CarteiraService.deletar` passou a bloquear exclusao para **qualquer** `MovimentoCarteira` associado (nao so origem `CARTEIRA_AJUSTE`), fechando o caminho mais comum de uso (transacao/parcela) que antes caia em erro 500 de FK `RESTRICT`.
24. **Recomendacoes de auditoria conscientemente rejeitadas quando conflitam com regra de produto ja travada (2026-07-14, PROB-0073/PROB-0074):** um `CHECK (valor_gasto >= 0)` em `contas` e um piso zero em `ContaService.removerGasto` foram propostos por uma auditoria de banco e **nao implementados** — ambos quebrariam o principio documentado de que `Conta.valorGasto` negativo e credito de cartao legitimo (V20:5-8, regra R1 do rollover de fatura). `Conta` ja tem `@Version` desde PROB-0002, tornando lock pessimista adicional redundante. Decisao e justificativa completas em `docs/PROBLEM_LEDGER.md` PROB-0073/PROB-0074.

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

## Limitacoes conhecidas

1. **Migrations versionadas implementadas (Flyway):** `V1__baseline_schema.sql` com 10 tabelas e migrations até `V11__movimento_carteira.sql`. `ddl-auto=validate` em dev e prod. PROB-0006 resolvido.
2. **Sem testes no mobile:** nao ha scripts de test/lint configurados no `mobile/package.json`.
3. **Cobertura de testes backend limitada:** suite atual passa com 43 testes, mas cobre poucos fluxos comparado ao tamanho do domínio.
4. **Cobertura de testes frontend limitada:** poucos testes Vitest configurados.
5. **Sem politica de privacidade documentada:** relevante para conformidade LGPD.
6. **Sem exportacao de dados do usuario:** nao ha endpoint de portabilidade (LGPD).
7. **Sem CI/CD:** build e deploy manuais.
8. **Mobile sem testes e2e:** nao ha Detox, Maestro ou similar.
9. **CSP basico em `/api/**` (SecurityConfig), separado da CSP do SPA:** o backend (`SecurityConfig.java`) tem uma CSP propria e pouco restritiva para as rotas `/api/**`, nao alterada nesta rodada. Desde 2026-07-14 (PROB-0070/BUG-0063), o SPA (fora de `/api/**`, servido pelo nginx) ganhou sua **propria** CSP restritiva (`default-src 'self'`, sem `unsafe-inline`) diretamente nos configs de nginx — as duas politicas sao independentes e vivem em camadas diferentes; a CSP do backend em `/api/**` continua como debito tecnico de hardening futuro.
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
21. **Tabela `Parcela` legada coexiste com `FaturaLancamento` para compras de cartao:** ambas as estruturas sao mantidas em paralelo (redundancia) — `Parcela` usa vencimento comecando 1 mes apos a compra, `FaturaLancamento` e o que alimenta o calculo real de fatura desde a migration V17 (e agora carrega o `tipo` COMPRA/AJUSTE/ESTORNO desde V18 — `Parcela` legada nao tem equivalente de ajuste/estorno). Bugs de arredondamento (BUG-0017) precisaram ser corrigidos nos dois lugares. Candidata a aposentadoria futura (BACKLOG-0050).
22. **Status FECHADA da fatura sem teste automatizado dedicado:** a derivacao de `FaturaStatus.FECHADA` (quando `dataFechamento` ja passou) foi adicionada em 2026-07-09 (BUG-0020) e validada apenas por revisao manual de codigo, nao por teste automatizado.
23. **`Conta.valorGasto` pode ficar temporariamente negativo:** um lancamento `ESTORNO` maior que as compras em aberto no cartao pode deixar `valorGasto` negativo (credito). Desde 2026-07-11 (BUG-0052), web/mobile exibem esse caso como "credito disponivel" em vez de gasto negativo.
24. **Edicao de compra parcelada recalcula parcela cheia:** desde 2026-07-11 (BUG-0052), `ressincronizarCompraCartao` recalcula o cronograma completo do parcelamento. Parcelas em faturas pagas ficam imutaveis; diferenca entre parcela paga antiga e parcela cheia recalculada entra como `AJUSTE` na proxima fatura aberta.
25. **Idempotencia do rollover de fatura nao exercitada em teste automatizado contra o unique index de banco:** desde 2026-07-11 (BUG-0053), o rollover R1/R2 (`FaturaService.liquidarFaturaAnterior`) tem dupla protecao contra duplicacao — guard em codigo (`existsByFaturaOrigemId`) e unique index parcial `ux_fatura_rollover_origem_tipo` (migration `V25__fatura_rollover.sql`). O schema de teste (H2 create-drop, Flyway desligado em teste) nao cria esse unique index, entao os testes de `FaturaRolloverTest` validam apenas o guard de codigo; o backstop de banco e o cenario de concorrencia real de 2 threads simultaneas materializando a mesma fatura futura nao tem cobertura de teste automatizado.
26. **Idempotencia de investimentos ainda nao implementada:** desde 2026-07-14 (auditoria abrangente), `InvestimentoService.adicionarMovimentacao` nao usa `Idempotency-Key` como o fluxo de fatura (BUG-0052) e agora o de parcela (BUG-0060) usam — reenvio pode duplicar compra/venda/dividendo na posicao do ativo (BACKLOG-0081).
27. **Listagem de investimentos sem paginacao:** desde 2026-07-14 (auditoria abrangente), a listagem de ativos/movimentacoes de investimento nao aceita `page`/`size` como outras listagens do sistema (BACKLOG-0082).
28. **Correcao de rate limit/nginx/redes do hardening pre-producao ainda nao validada em ambiente real:** desde 2026-07-14 (PROB-0066/BUG-0059, PROB-0070/BUG-0063), a troca de `forward-headers-strategy` (framework→native), a nova rede Docker interna `web<->API` e os headers de seguranca do SPA foram implementados e testados apenas no nivel de unidade/build — `nginx -t`, recriacao de redes e smoke em staging (X-Forwarded-For forjado, cookie Secure, CSP do SPA carregado de fato) ficam pendentes (BACKLOG-0080).

## Pontos frageis atuais

1. **Ownership implementado:** todos os services validam posse via `findByIdAndUsuarioId` ou `buscarPorIdDoUsuario`. PROB-0001 e PROB-0021 resolvidos.
2. **Rate limit com account lockout:** login/register/reset-password/forgot-password/validate-token com rate limit por IP. Desde 2026-07-11, tentativas ficam em `rate_limit_buckets` no PostgreSQL com lock pessimista por chave, consistente entre instancias e resistente a restart de JVM (PROB-0055 resolvido). Account lockout por email apos falhas consecutivas. Limpeza periodica de buckets expirados. **Ressalva critica desde 2026-07-14 (PROB-0066):** a chave do rate limit e o IP resolvido de `X-Forwarded-For` — a correcao de `forward-headers-strategy` (framework→native) e a normalizacao do header pelo nginx fecham o contorno conhecido, mas o mecanismo continua tao confiavel quanto a cadeia de proxy real na frente da API (ver BACKLOG-0080 para o gate de smoke pendente).
3. **Schema versionado com Flyway:** migrations em `db/migration/`, `ddl-auto=validate` em dev e prod. PROB-0006 resolvido.
4. **Refresh token no banco:** se o banco ficar lento, toda autenticacao sofre. Sem cache (Redis) para tokens.
5. **LoginRateLimitFilter com protecao completa:** register (5/min), login (5/min), forgot-password (3/min), reset-password (5/min), validate-token (10/min). Account lockout apos 5 falhas. @Scheduled cleanup de entradas expiradas a cada 60s. (PROB-0024 resolvido).
6. **Logs sem PII:** EmailService com maskEmail. Token nunca logado. (PROB-0011 resolvido).
7. **Contrato de erro padronizado:** ApiError com code, message, timestamp, requestId. X-Request-Id header. MDC requestId para correlacao de logs.
8. **Mobile usa Expo Secure Store:** instalado mas nao implementado. Token em memoria apenas (PROB-0013).
9. **Configurações de produção seguras:** cookie.secure=true, CORS sem fallback, secrets sem default (PROB-0005, PROB-0010 resolvidos).
10. **TransacaoService com ownership corrigido:** categoriaId e contaId validados via `findByIdAndUsuarioId` (PROB-0001 resolvido).
11. **Operacoes transacionais corrigidas:** todos os metodos de escrita com @Transactional (PROB-0012 resolvido).
12. **Constructor injection em producao:** sweep de 2026-07-11 removeu `@Autowired` de `backend/src/main/java`; controllers, services, config e security usam constructor injection com dependencias `final`. Testes Spring ainda usam `@Autowired`, aceitavel para testes de integracao/contexto Spring. PROB-0057 fechado.
13. **Ledger so movimenta com carteiraId explicito:** ausencia de `carteiraId` no payload de transacao e uma decisao de design (nao um bug), mas exige que todo client (web/mobile) sempre envie a carteira; o mobile so passou a fazer isso a partir de 2026-07-09 (BUG-0012). Transacoes antigas sem carteira nao tem movimento retroativo (BACKLOG-0045).
14. **Interceptor de refresh agora simetrico entre web e mobile:** ambos os clientes renovam o access token automaticamente em 401 (BUG-0013, 2026-07-09). Antes disso, apenas o web tinha esse comportamento.
15. **Fluxo de compra no cartao + faturas ressincronizado:** editar valor/data de uma compra de cartao ja lancada recria os lancamentos de fatura e reajusta `Conta.valorGasto`/`Categoria.valorGasto`; compra retroativa nunca mais cai em fatura ja `PAGA` (rola ate 24 meses a frente); `valorGasto` da conta so e ajustado para transacoes `SAIDA` (BUG-0017 a BUG-0022, 2026-07-09).
16. **Edicao/cancelamento de compra com fatura paga nunca mais bloqueia:** decisao de bloquear com `BusinessException` (adotada horas antes, mesma sessao) foi substituida por compensacao automatica via lancamento `AJUSTE`/`ESTORNO` na proxima fatura em aberto — fatura paga tratada como imutavel, nunca como trava de operacao do usuario (BUG-0023, BUG-0024, PROB-0044, 2026-07-09).
17. **Mobile sem cobertura de teste para o fluxo de fatura/transacao:** `EditarTransacaoModal` (BUG-0027) e os badges de status/tipo de fatura (BUG-0028, BUG-0029) foram validados apenas por `tsc --noEmit`, leitura de diff e um teste manual de contrato contra o backend local — sem suite automatizada de UI mobile (limitacao conhecida item 2/8).
18. **Investimentos com integridade de posicao e integracao de caixa opcional (PROB-0054 resolvido, 2026-07-11):** `InvestimentoService` bloqueia venda acima da posicao atual (`BusinessException`), valida quantidade > 0 e preco >= 0 (> 0 exceto BONIFICACAO), trata DIVIDENDO como provento sem alterar posicao e BONIFICACAO com custo zero. Integracao com carteira/caixa e **opcional**: se `MovimentacaoRequest.carteiraId` for informado, COMPRA debita e VENDA/DIVIDENDO creditam o caixa via `LedgerService` (origem `INVESTIMENTO`, migration `V22__movimentos_carteira_origem_investimento.sql`); sem `carteiraId`, so a posicao e atualizada (compatibilidade com o mobile atual, que ainda nao envia carteira nas movimentacoes de ativo — ver BACKLOG-0063).

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
- **Correcoes 2026-07-09 (revisao do fluxo de compra no cartao + parcelas, apos commit `69e3a3b` "feat(faturas): add card purchase flow"):** BUG-0017 a BUG-0022 corrigidos (PROB-0038 a PROB-0043) — ultima parcela absorve arredondamento (limite do cartao zera exatamente apos quitacao), edicao de valor/data de compra no cartao ressincroniza fatura e limite (bloqueada com `BusinessException` se a fatura ja estiver paga), compra retroativa nao entra mais em fatura ja paga (rola ate 24 meses a frente), status `FECHADA` da fatura agora e derivado e exibido no mobile/frontend, soma de `FaturaLancamento` passa a ser fonte da verdade do valor da fatura (elimina falso erro de "pagamento parcial nao suportado"), transacao `ENTRADA` com conta associada nao incrementa mais `valorGasto`. 3 novos testes em `FaturaCartaoWorkflowTest`; suite completa `./mvnw -o test` → 76/76 PASS (verificado nesta sessao pelo `docs-reporter`). Mudancas no working tree, nao commitadas. Ver `docs/BUGFIX_LOG.md` (BUG-0017..BUG-0022), `docs/PROBLEM_LEDGER.md` (PROB-0038..PROB-0043) e `docs/REVIEW_REPORTS/2026-07-09_backend_review_fatura-cartao-fluxo.md`.
- **Correcoes 2026-07-09, segunda rodada (mesma sessao) — modelo definitivo de ajuste/estorno:** BUG-0023 a BUG-0026 implementados (PROB-0044), substituindo o bloqueio de edicao/cancelamento de compra com fatura paga registrado horas antes na primeira rodada. Principio adotado: fatura paga e imutavel — edicao gera lancamento `AJUSTE` e cancelamento gera lancamento `ESTORNO` (podendo ser negativo/credito) na proxima fatura em aberto, nunca mais bloqueando a operacao do usuario. Nova coluna `tipo` em `fatura_lancamentos` (migration `V18__fatura_lancamento_tipo.sql`) e enum `TipoFaturaLancamento` (COMPRA/AJUSTE/ESTORNO). Invariante centralizado: `Conta.valorGasto == soma dos lancamentos em faturas nao pagas`, com helpers dedicados em `FaturaService` (`criarLancamento`/`removerLancamentoDeFaturaAberta`/`ajustarLimiteUtilizado`); `TransacaoService` deixou de ajustar `valorGasto` diretamente para compras de cartao. UI (mobile e frontend) exibe lancamentos de credito (valor negativo) em verde com prefixo "Ajuste: "/"Estorno: ". +2 testes em `FaturaCartaoWorkflowTest` (total da classe: 7). Suite completa `./mvnw -o test` → 78/78 PASS (verificado nesta sessao pelo `docs-reporter`). Mudancas no working tree, nao commitadas. Ver `docs/BUGFIX_LOG.md` (BUG-0023..BUG-0026), `docs/PROBLEM_LEDGER.md` (PROB-0044) e `docs/REVIEW_REPORTS/2026-07-09_backend_review_fatura-cartao-fluxo.md` (secao de atualizacao).
- **Correcoes 2026-07-09, terceira rodada (mesma sessao) — complemento mobile do modulo de fatura/cartao:** BUG-0027 a BUG-0029 implementados (PROB-0045 a PROB-0047), fechando 3 lacunas encontradas ao verificar a integracao do modulo de faturas/cartao no app mobile: (1) mobile nao tinha nenhuma forma de editar/excluir transacao — novo `mobile/src/components/EditarTransacaoModal.tsx`, que edita apenas valor/descricao/data/observacoes (unicos campos aplicados pelo `PUT` do backend) e avisa sobre ressincronizacao de fatura para compra de cartao; (2) badge de status da fatura no mobile passou de binario (PAGA verde / resto vermelho) para as 4 cores semanticas (ABERTA=marca, FECHADA=warning, VENCIDA=danger, PAGA=success); (3) lancamentos `AJUSTE`/`ESTORNO` ganharam chip de tipo e tiveram o prefixo textual da descricao removido da exibicao. Contrato de `EditarTransacaoModal` validado manualmente contra o backend local (porta 8081) com payloads exatos do app (POST 201, PUT 200, DELETE 204); `tsc --noEmit` limpo; `FaturaCartaoWorkflowTest` 7/7 PASS (suite backend nao alterada nesta rodada). Risco de ambiente identificado e mitigado durante a validacao: processo backend local rodando build defasado por falta de reinicio apos recompilacao (PROB-0048, nao e bug de codigo). Identificada divergencia de paridade: o badge de tipo de lancamento nao foi replicado no frontend web (BACKLOG-0057). Mudancas no working tree, nao commitadas. Ver `docs/BUGFIX_LOG.md` (BUG-0027..BUG-0029), `docs/PROBLEM_LEDGER.md` (PROB-0045..PROB-0048) e `docs/REVIEW_REPORTS/2026-07-09_backend_review_fatura-cartao-fluxo.md` (secao "Atualizacao (revisao 3)").
- **Correcao 2026-07-11 — modulo de investimentos redesenhado (PROB-0054/BACKLOG-0063 fechados):** `InvestimentoService.adicionarMovimentacao`/`updateAtivoPosicao` reescritos para bloquear venda acima da posicao atual (`BusinessException`, elimina quantidade negativa e divisao por zero), validar quantidade > 0 e preco >= 0 (> 0 exceto BONIFICACAO), tratar DIVIDENDO sem alterar posicao/custo e BONIFICACAO com custo zero, e migrar lookups de ativo para `ResourceNotFoundException`. Integracao de caixa implementada como **opcional e nao-breaking**: novo campo `MovimentacaoRequest.carteiraId` — se informado, COMPRA debita e VENDA/DIVIDENDO creditam o caixa via `LedgerService.registrarMovimento` (nova origem `INVESTIMENTO` em `OrigemMovimentoCarteira`, migration `V22__movimentos_carteira_origem_investimento.sql` estendendo o CHECK criado na V20); sem `carteiraId`, apenas a posicao e atualizada (compatibilidade com o mobile atual). 14 novos testes em `InvestimentoServiceTest`; suite completa 116/116 PASS; migration V22 validada em PostgreSQL 16 real via Docker CLI. Mudancas no working tree, nao commitadas. Ver `docs/PROBLEM_LEDGER.md` (PROB-0054) e `docs/BACKLOG.md` (BACKLOG-0063).
- **Correcao 2026-07-11 — pacote PROB MEDIUM (BUG-0051):** fechados PROB-0031, PROB-0048, PROB-0055, PROB-0056, PROB-0057, PROB-0058 e PROB-0059. Rate limit migrou para PostgreSQL (`rate_limit_buckets`, migration V24), contrato mobile virou body-only sem cookies, clientes ganharam locks contra duplo clique financeiro, `backend/src/main/java` ficou sem `@Autowired`, backup/restore passaram a suportar/exigir criptografia e restore drill, e CI passou a validar migrations via `scripts/verify-postgres-migrations.sh`. Validações: backend suite PASS, frontend build PASS, mobile `tsc --noEmit` PASS, PostgreSQL real via Docker CLI PASS. Ver `docs/BUGFIX_LOG.md` BUG-0051.
- **Correcao 2026-07-14 — hardening pre-producao P0+P1 (commits `5c08ce0`, `0d1e0c0`, `c959dfc` em `main`):** origem em auditoria abrangente (security-auditor, lgpd-auditor, quality-reviewer, database-engineer). Fechados PROB-0066 a PROB-0072 com correcao aplicada (BUG-0059 a BUG-0065): rate limit de auth deixa de ser contornavel via `X-Forwarded-For` forjado (`forward-headers-strategy` framework→native + `RemoteIpValve` + rede Docker interna `web<->API`), pagamento de parcela deixa de duplicar debito (guard de estado + `@Version` em `Parcela`, migration `V28__pre_production_hardening.sql`), exclusao de carteira em uso normal deixa de retornar 500 (`existsByCarteiraId` generico), indices novos em `movimentos_carteira.carteira_id` e `refresh_tokens.usuario_id`, SPA ganha headers de seguranca no nginx (HSTS/X-Frame-Options/CSP), token de reset de senha migra de `GET`+query string para `POST`+corpo (`ValidateTokenRequest`, GET agora 405), `totalParcelas` ganha teto `@Max(120)`. Duas recomendacoes da mesma auditoria foram avaliadas e **rejeitadas** com justificativa (PROB-0073: `CHECK contas.valor_gasto >= 0` conflitaria com credito de cartao legitimo documentado na V20; PROB-0074: piso zero + lock pessimista em `removerGasto` seria redundante com `@Version` ja existente em `Conta` e engoliria credito legitimo). Validacoes desta rodada: backend `./mvnw -q verify` 155/155 PASS; frontend 15/15 testes, lint com 0 erros e build PASS; `scripts/verify-postgres-migrations.sh` PASS contra PostgreSQL real. Mobile intocado. Gates de deploy real (nginx -t, redes, smoke em staging) ficam pendentes em BACKLOG-0080; 5 itens P2 identificados na mesma auditoria ficam em BACKLOG-0081 a BACKLOG-0085. Ver `docs/BUGFIX_LOG.md` (BUG-0059..BUG-0065), `docs/PROBLEM_LEDGER.md` (PROB-0066..PROB-0074), `docs/BACKLOG.md` (BACKLOG-0080..BACKLOG-0085) e `docs/REVIEW_REPORTS/2026-07-14_full-system_implementation_pre-production-hardening.md`.
