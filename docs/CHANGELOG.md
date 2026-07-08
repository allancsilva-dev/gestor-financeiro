# 📝 Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

---

## [1.4.0] - 2025-11-30

### 🔐 Segurança
- **[CRÍTICO]** Movido JWT secret para variável de ambiente
- **[CRÍTICO]** Reduzido tempo de expiração do access token de 24h para 15min
- **[CRÍTICO]** Protegidas credenciais do banco com variáveis de ambiente
- Adicionado `.env` no `.gitignore`
- Criado `application-prod.properties` para produção
- Criado `.env.example` como template
- Removidos logs com informações sensíveis

### ✅ Validações
- Sistema validado e pronto para deploy em produção
- Todos os secrets protegidos
- CORS configurado para produção

---

## [1.3.0] - 2025-11-30

### ⭐ Features
- **Refresh Token implementado** (auto-renovação de sessão)
- Access token expira em 15 minutos
- Refresh token expira em 7 dias
- Renovação automática transparente para o usuário
- Logout revoga tokens no backend

### 🗄️ Banco de Dados
- Criada tabela `refresh_tokens`
- Índices para otimização de queries
- Foreign key com cascade delete

### 🔧 Backend
- Criado `RefreshToken` entity
- Criado `RefreshTokenRepository`
- Criado `RefreshTokenService`
- Atualizado `AuthController` com novos endpoints:
  - `POST /api/auth/refresh-token` - Renovar access token
  - `POST /api/auth/logout` - Revogar refresh token
  - `POST /api/auth/logout-all` - Revogar todos os tokens

### 💻 Frontend
- Atualizado `authService` para salvar refresh token
- Implementado interceptor Axios para renovação automática
- Atualizado `AuthContext` para renovar token ao inicializar
- Adicionado `refreshToken` em `LoginResponse` type

### 📚 Documentação
- Criado `LICOES_APRENDIDAS.md` com debugging experiences
- Atualizado README com refresh token

---

## [1.2.0] - 2025-11-29

### 📊 Dashboard
- Implementados gráficos com Recharts
- Gráfico de pizza (Gastos por Categoria)
- Gráfico de linhas (Evolução Mensal)
- Cards de resumo financeiro
- Cards secundários (Cartões, Metas, Contas Fixas)

### 🐛 Correções
- Corrigido Lazy Loading do JPA/Hibernate
  - Adicionado `JOIN FETCH` em queries customizadas
  - Categorias agora carregam corretamente nas transações
- Corrigido cache do Vite com prop `chartData`
- Corrigido layout dos gráficos (grid responsivo)
- Corrigido usuário hardcoded em todas as telas

### 🔧 Backend
- Criado `DashboardController`
- Criado `DashboardService` com cálculos de:
  - Saldo total de carteiras
  - Total de entradas/saídas do mês
  - Gastos por categoria
  - Evolução mensal (6 meses)
  - Comparação mensal
- Query customizada no `TransacaoRepository`

### 💻 Frontend
- Criado componente `Dashboard`
- Criado `GraficoGastosPorCategoria`
- Criado `GraficoEvolucaoMensal`
- Criado `dashboardService`
- Implementado `useAuth()` em TODAS as páginas

---

## [1.1.0] - 2025-11-28

### ⭐ Features
- Sistema de Autenticação JWT completo
- Recuperação de senha por email
- Gestão de transações (criar, editar, deletar)
- Parcelamento de compras
- Categorias personalizadas (cores e ícones)
- Controle de cartões de crédito
- Gestão de metas financeiras
- Contas fixas mensais

### 🔧 Backend
- Spring Security configurado
- JWT authentication filter
- BCrypt para senhas
- Soft delete para categorias
- Validação de proprietário (usuário só vê seus dados)

### 💻 Frontend
- AuthContext com Context API
- Rotas protegidas
- Interceptor Axios para token
- Notificações toast
- UI responsiva com Tailwind

### 🗄️ Banco de Dados
- Tabelas: usuarios, categorias, transacoes, contas, metas, contas_fixas
- Relacionamentos JPA configurados
- Índices para performance

---

## [1.0.0] - 2025-11-25

### 🎉 Lançamento Inicial
- Estrutura básica do projeto
- Configuração Spring Boot
- Configuração React + Vite
- PostgreSQL configurado
- Primeiras telas de Login e Registro

---

## 📊 Estatísticas de Desenvolvimento

### Versão 1.4.0 (Atual)
- **Tempo de desenvolvimento:** ~15 horas
- **Commits:** 50+
- **Arquivos modificados:** 30+
- **Linhas de código:** ~8.000+
- **Problemas resolvidos:** 10+

### Principais Desafios
1. **Lazy Loading JPA** (~2h de debugging)
2. **Cache do Vite** (~1.5h)
3. **Usuário hardcoded** (~1h)
4. **Layout dos gráficos** (~30min)
5. **Refresh token implementation** (~3h)

---

## 🎯 Próximas Versões

Ver [PROXIMOS_PASSOS.md](./PROXIMOS_PASSOS.md)

### v1.5.0 (Planejado)
- Skeleton Loaders
- Filtros no Dashboard
- Rate Limiting
- Validações de entrada

### v2.0.0 (Futuro)
- Dark/Light mode
- Exportação CSV/PDF
- Notificações push
- App mobile (React Native)

---

## 📝 Notas de Versão

### [1.4.0] - Segurança
Esta versão foca em **segurança e preparação para produção**. Todas as vulnerabilidades críticas foram corrigidas e o sistema está pronto para deploy.

### [1.3.0] - Refresh Token
Implementação do sistema de **refresh token** para melhorar a experiência do usuário, permitindo sessões de até 7 dias sem necessidade de novo login.

### [1.2.0] - Dashboard
Implementação completa do **dashboard com gráficos** e correção de bugs críticos de Lazy Loading e cache.

### [1.1.0] - MVP
Primeira versão funcional completa com todas as funcionalidades principais implementadas.

### [1.0.0] - Fundação
Estrutura básica do projeto e configurações iniciais.

---

## [1.5.0] - 2026-07-08

### Fase 2 — Web e mobile de qualidade (inicio)

#### Mobile — PR-FASE2-01 (P0)
- **[P0]** Token de acesso persistido via `expo-secure-store` (sessao mantida entre cold starts)
- **[P0]** URL da API configurada via `expo-constants` (app.json extra.apiBaseUrl) — sem IP hardcoded
- **[P0]** Restore de sessao ao abrir app: valida token via `GET /api/v1/usuarios/me`
- **[P0]** Corrigido path `/dashboard/resumo` → `/v1/dashboard/resumo` em perfil.tsx
- Cache de usuario no SecureStore para restore instantaneo antes da validacao

#### Mobile — PR-FASE2-02 (P1)
- **[P1]** "Esqueceu a senha?" navega para tela de forgot-password
- **[P1]** "Ver todas" no dashboard navega para lista de transacoes
- **[P1]** App.tsx removido (template Expo morto); index.ts usa `expo-router/entry`
- **[P1]** onError adicionado em mutations: criarMutation (carteiras), pagarMutation (contas-fixas)

#### Arquivos alterados (mobile)
- `src/store/auth.ts` — SecureStore em vez de memoria
- `src/config/api.config.ts` — expo-constants em vez de IP fixo
- `src/context/AuthContext.tsx` — restoreSession + isLoading dinamico
- `src/services/authService.ts` — login/logout async com SecureStore
- `app/(app)/perfil.tsx` — path corrigido
- `app/index.tsx` — loading state durante restore
- `app.json` — extra.apiBaseUrl
- `App.tsx` — removido (template Expo morto)
- `index.ts` — `import 'expo-router/entry'`
- `app/(auth)/forgot-password.tsx` — nova tela de recuperacao de senha
- `app/(auth)/login.tsx` — "Esqueceu a senha?" navega para forgot-password
- `app/(app)/index.tsx` — "Ver todas" navega para transacoes
- `app/(app)/more/carteiras.tsx` — onError em criarMutation
- `app/(app)/more/contas-fixas.tsx` — onError em pagarMutation

#### Frontend — PR-FASE2-04 (P3)
- **[P3]** Rota 404 adicionada (pagina NotFound com link para Dashboard)
- **[P3]** 27 console.log/console.error removidos de page components
- **[P3]** Componente morto GraficoComparacaoMensal removido

#### Mobile — PR-FASE2-05 (P2)
- `parseCurrencyBR` centralizado em `utils/format.ts`

#### Frontend — PR-FASE2-06 (P2)
- Zero `any` nos services (substituidos por `Omit<T>`, `Partial<T>`, `unknown`)

#### Backend/Frontend — PR-FASE2-07 (P2)
- `confirmPassword` no RegisterRequest backend e frontend
- `@AssertTrue` validando igualdade de senhas

#### Frontend — PR-FASE2-08 (P2)
- `aria-label` em Login e Layout (menu lateral)

#### Backend — PR-LEDGER-01
- Testcontainers PostgreSQL adicionado para validar Flyway em banco limpo
- Profile Maven `integration-test` com Failsafe para testes `*IT.java`
- `PostgresMigrationIT` valida startup Spring, migrations Flyway e Hibernate `ddl-auto=validate`
- CI passou a rodar `mvn verify -Pintegration-test --batch-mode`
- Mockito configurado como `javaagent` no Surefire/Failsafe para JDK 21
- Validação equivalente em PostgreSQL VPS real concluída em 2026-07-08 com usuário `dbnexos_gestor`

#### Backend — PR-LEDGER-02
- Migration `V11__movimento_carteira.sql` criada para schema inicial do Ledger
- Entidade `MovimentoCarteira` criada com `usuario`, `carteira`, tipo, origem, valor absoluto, valor assinado, saldo resultante, moeda e idempotency key
- Enums `TipoMovimentoCarteira` e `OrigemMovimentoCarteira` adicionados
- `MovimentoCarteiraRepository` adicionado com consultas por ownership, carteira e idempotência
- `PostgresMigrationIT` ampliado para validar `V11`, constraints e FK em PostgreSQL real quando Docker estiver ativo
- Testes backend: `./mvnw -q test` -> 38/38 PASS
- BUG-0010 corrigiu `MovimentoCarteira.moeda`: migration usa `CHAR(3)` e JPA agora usa `@JdbcTypeCode(SqlTypes.CHAR)`
- Smoke VPS PostgreSQL: Flyway validou 14 migrations, schema `ddl-auto=validate` PASS

#### Backend — PR-LEDGER-03
- `LedgerService` criado para registrar movimento e atualizar `Carteira.saldo` na mesma transação
- `RegistrarMovimentoCommand` criado como command interno
- `CarteiraRepository.findByIdAndUsuarioIdForUpdate` adicionado com `PESSIMISTIC_WRITE`
- `CarteiraService` passou a usar Ledger para criar saldo inicial, ajustar saldo, adicionar e remover dinheiro
- Conflitos financeiros/locks retornam 409 (`FINANCIAL_CONFLICT`)
- `LedgerServiceTest` cobre entrada, saída, saldo insuficiente, ownership e concorrência
- Testes backend: `./mvnw -q test` -> 43/43 PASS
- Smoke VPS PostgreSQL validou Flyway/schema real

#### Backend — PR-LEDGER-04
- Reconciliação de saldo adicionada para comparar `Carteira.saldo` com soma de `MovimentoCarteira.valorAssinado`
- `LedgerReconciliationService` criado com status `OK` ou `DIVERGENTE`
- Endpoints adicionados: `GET /api/v1/carteiras/{id}/reconciliacao` e `GET /api/v1/carteiras/minhas/reconciliacao`
- Logs de divergência usam apenas `usuarioId`, `carteiraId` e `diferenca`
- `PostgresMigrationIT` ampliado com query real de reconciliação para PostgreSQL quando Docker estiver ativo
- Testes backend: 47/47 unitários PASS
- Smoke VPS PostgreSQL validou Flyway/schema real

#### Backend — PR-LEDGER-05
- Migration `V12__ledger_backfill_carteiras.sql` criada para backfill inicial idempotente de carteiras
- Backfill calcula `saldo_materializado - saldo_ledger`, evitando duplicar carteiras que já possuem movimentos
- Unique parcial adicionada para impedir mais de um `BACKFILL` por carteira
- `LedgerBackfillService` e `LedgerBackfillResult` criados para rotina interna por usuário ou todas as carteiras
- Diferença negativa bloqueia backfill e exige auditoria manual
- `LedgerBackfillServiceTest` cobre abertura, idempotência, reconciliação, isolamento por usuário, diferença parcial e bloqueio
- Testes backend: 53/53 unitários PASS
- Smoke VPS PostgreSQL validou Flyway/schema real

#### Backend — PR-LEDGER-06
- Endpoint `POST /api/v1/carteiras/{id}/ajustes` — ajuste manual explícito via Ledger com payload `{tipo, valor, descricao}`
- Endpoint `GET /api/v1/carteiras/{id}/movimentos` — extrato paginado de movimentos do Ledger por carteira
- Endpoints `POST /{id}/adicionar` e `POST /{id}/remover` marcados `@Deprecated(since = "PR-LEDGER-06")` — continuam funcionais
- DTO `AjusteCarteiraRequest` e `MovimentoCarteiraResponse` criados
- `CarteiraService.ajustarSaldo` e `CarteiraService.listarMovimentos` delegam para `LedgerService`
- `CarteiraControllerTest` com 10 testes: ajuste entrada/saída, tipo inválido, ownership cruzado, listagem, reconciliação, deprecated
- Testes backend: 63/63 unitários PASS
- Smoke VPS PostgreSQL validou Flyway/schema real

#### Backend — PR-LEDGER-07
- Migration `V13__transacao_carteira.sql`: coluna `carteira_id` FK opcional e `ativa` (default true) em transacoes
- Transacao ganha campos `carteira` e `ativa`; TransacaoRequest ganha `carteiraId`
- `TransacaoService.criar()`: cria movimento Ledger (ENTRADA/SAIDA) quando carteiraId presente
- `TransacaoService.atualizar()`: computa delta de valor e registra ajuste com direcao correta (SAIDA/ENTRADA)
- `TransacaoService.deletar()`: soft-delete (`ativa = false`) + estorno via Ledger; `cancelar()` alias
- `TransacaoServiceLedgerTest` com 6 testes: criar com/sem carteira, atualizar, cancelar entrada/saida
- Testes backend: 69/69 unitários PASS

#### Documentacao
- 15 Backlog items fechados (0005, 0006, 0009, 0014, 0015, 0016, 0017, 0018, 0022, 0023, 0024, 0025, 0031, 0032, 0033)
- PROB-0019 fechado
- PR-LEDGER-00 a PR-LEDGER-20 registrados no checklist
- Fase Ledger registrada com 20/20 PRs em status aceito (`PASS` ou `PASS_COM_RESSALVA`)
- PostgreSQL VPS real validado: PostgreSQL 17.10, Flyway 14 migrations, schema JPA OK
- BUG-0010 registrado e corrigido (`moeda CHAR(3)` vs mapeamento JPA)
- Fundação contábil estabelecida: Ledger, reconciliação, backfill, idempotência
- Fase 2 concluida

---

**Ultima atualizacao:** 2026-07-08 (BUG-0010, validacao PostgreSQL VPS)
**Mantido por:** Zero (Allan Carvalho)
