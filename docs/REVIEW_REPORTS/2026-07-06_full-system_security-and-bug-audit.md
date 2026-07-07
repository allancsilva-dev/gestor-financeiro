# Relatorio de Auditoria Completa do Sistema

**Data:** 2026-07-06
**Motivacao:** Auditoria completa read-only antes de iniciar novos passos de desenvolvimento.

---

## Objetivo

Verificacao completa de bugs, procedimentos inacabados, rotas abertas, falhas de seguranca e gaps
de implementacao em todo o sistema (backend, frontend web, mobile). Diagnostico read-only para embasar
proximos passos.

## Escopo verificado

- **Backend:** 100% dos arquivos Java em config/, controller/, service/, security/, exception/, model/,
  repository/, dto/, util/ + application.properties, application-prod.properties, logback-spring.xml
- **Frontend web:** 100% dos arquivos TS/TSX em services/, context/, hooks/, pages/, components/,
  types/ + App.tsx, main.tsx, config files
- **Mobile:** 100% dos arquivos em app/, src/ + App.tsx, index.ts, config files
- **Docs:** PROBLEM_LEDGER.md, BUGFIX_LOG.md, BACKLOG.md, SYSTEM_OVERVIEW.md, API.md
- **Total de arquivos lidos:** ~120

## Comandos executados

| Tipo | Ferramenta | Resultado |
|---|---|---|
| Exploracao de estrutura | Glob "**/*" | Arvore completa mapeada |
| Busca de padroes | Grep TODOs, FIXMEs, any, console, deprecated | 64 any, 35 console, 2 TODOs |
| Leitura de codigo | Read (todos os arquivos fonte) | 100% cobertura |
| Analise de seguranca | Agente security-auditor | 20+ achados |
| Analise de bugs web | Agente frontend-bug-audit | 12 categorias de achados |
| Analise de bugs mobile | Agente mobile-bug-audit | 14 categorias de achados |
| Analise de codigo inacabado | Agente todo-hunt | 2 TODOs, codigo morto |

## Achados

### CRITICAL (15)

| # | Severidade | Area | Descricao | Arquivo:Linha |
|---|---|---|---|---|
| 1 | CRITICAL | backend | Cross-tenant IDOR: TransacaoService.criar() busca categoriaId/contaId sem validar ownership do usuario | `backend/.../service/TransacaoService.java:68-89` |
| 2 | CRITICAL | backend | cookie.secure ausente em application-prod.properties. RefreshToken sem flag Secure em HTTPS | `backend/.../application-prod.properties` (falta config) |
| 3 | CRITICAL | backend | ddl-auto=update em producao — risco de perda de dados por alteracao automatica de schema | `backend/.../application-prod.properties:11` |
| 4 | CRITICAL | backend | parcelaRepository.findAll() carrega TODAS parcelas do sistema em atualizarParcelasAtrasadas() | `backend/.../service/ParcelaService.java:65` |
| 5 | CRITICAL | backend | contaFixaRepository.findAll() carrega TODAS ContaFixa em atualizarContasAtrasadas() | `backend/.../service/ContaFixaService.java:166` |
| 6 | CRITICAL | backend | Race condition em CarteiraService.adicionarDinheiro()/removerDinheiro() sem @Version | `backend/.../service/CarteiraService.java:92-133` |
| 7 | CRITICAL | backend | Politica de senha apenas min 6 caracteres — sem maiuscula, digito ou especial | `backend/.../dto/RegisterRequest.java:18-19` |
| 8 | CRITICAL | backend | DashboardService carrega entidades inteiras com JOIN FETCH so para SUM de valorTotal | `backend/.../service/DashboardService.java:162-183` |
| 9 | CRITICAL | backend | CarteiraService.buscarOuCriarCategoriaTransferencia() carrega TODAS categorias do usuario | `backend/.../service/CarteiraService.java:157-164` |
| 10 | CRITICAL | mobile | Token de acesso apenas em memoria — perdido ao fechar app. expo-secure-store instalado mas nunca usado | `mobile/src/store/auth.ts:1-2` |
| 11 | CRITICAL | mobile | AuthContext.isLoading hardcoded false — app sempre flashea login no cold start | `mobile/src/context/AuthContext.tsx:31` |
| 12 | CRITICAL | mobile | IP hardcoded 192.168.15.3:8081 — inutilizavel em qualquer outro dispositivo/rede | `mobile/src/config/api.config.ts:5` |
| 13 | CRITICAL | mobile | Entry points zumbis: App.tsx (template Expo starter) e index.ts importando-o — nunca executados | `mobile/App.tsx`, `mobile/index.ts` |
| 14 | CRITICAL | mobile | API path inconsistente: Dashboard usa /v1/dashboard/resumo, Perfil usa /dashboard/resumo (404) | `mobile/app/(app)/index.tsx:17` vs `perfil.tsx:19` |
| 15 | CRITICAL | mobile | Erro silencioso ao criar carteira: criacaoMutation sem onError, catch vazio | `mobile/app/(app)/more/carteiras.tsx:26-49` |

### HIGH (12)

| # | Severidade | Area | Descricao | Arquivo:Linha |
|---|---|---|---|---|
| 16 | HIGH | backend | Senha DB default '1234' em application.properties | `backend/.../application.properties:9` |
| 17 | HIGH | backend | JWT secret default 'altere_este_valor...' em dev | `backend/.../application.properties:22` |
| 18 | HIGH | backend | CORS producao fallback para localhost:5173 | `backend/.../application-prod.properties:37` |
| 19 | HIGH | backend | Email destino + token reset logados em INFO/DEBUG — PII e seguranca | `backend/.../service/EmailService.java:24-25` |
| 20 | HIGH | backend | Race condition em Meta.valorReservado sem @Version | `backend/.../service/MetaService.java:48-76` |
| 21 | HIGH | backend | Rate limit ausente em /register, /reset-password, /validate-token | `backend/.../config/LoginRateLimitFilter.java:29-31` |
| 22 | HIGH | backend | @Transactional ausente em write methods de CategoriaService, ContaService, MetaService, ContaFixaService | Varios services |
| 23 | HIGH | mobile | Botao "Esqueceu a senha?" sem onPress — botao morto | `mobile/app/(auth)/login.tsx:47-49` |
| 24 | HIGH | mobile | Link "Ver todas" no Dashboard nao e clicavel — engana usuario | `mobile/app/(app)/index.tsx:68` |
| 25 | HIGH | mobile | more/_layout.tsx com headerShown:false — sem botao voltar nas sub-telas | `mobile/app/(app)/more/_layout.tsx` |
| 26 | HIGH | mobile | console.error vaza URL e status de API em producao | `mobile/src/services/api.ts:24` |
| 27 | HIGH | frontend | CSRF completamente ausente — withCredentials:true sem token CSRF | `frontend/src/services/api.ts:20` |

### MEDIUM (32)

| # | Severidade | Area | Descricao | Arquivo:Linha |
|---|---|---|---|---|
| 28 | MEDIUM | backend | CarteiraService.deletar(Long id) sem validacao de ownership — overload perigoso | `CarteiraService.java:187-190` |
| 29 | MEDIUM | backend | JwtUtil usa API deprecated do JJWT (parserBuilder/setSigningKey) | `JwtUtil.java:74-79` |
| 30 | MEDIUM | backend | SameSite=Lax no cookie refreshToken — Strict mais seguro para app financeiro | `AuthController.java:295` |
| 31 | MEDIUM | backend | LoginRateLimitFilter sem cleanup de entradas stale — memory leak lento | `LoginRateLimitFilter.java:33,62-76` |
| 32 | MEDIUM | backend | Sem @Size em campos string dos DTOs (observacoes, descricao, banco) | Varios DTOs |
| 33 | MEDIUM | backend | SECRET_KEY.getBytes() sem charset UTF-8 explicito | `JwtUtil.java:36-38` |
| 34 | MEDIUM | backend | Sem iss/aud claims no JWT | `JwtUtil.java:45-54` |
| 35 | MEDIUM | backend | Sem confirmPassword no registro — typo na senha = conta perdida | `RegisterRequest.java:17-19` |
| 36 | MEDIUM | backend | Sem lockout de conta — apenas rate limit por IP | `LoginRateLimitFilter.java` |
| 37 | MEDIUM | backend | Sem validacao de tamanho maximo em totalParcelas do TransacaoRequest | `TransacaoRequest.java:42` |
| 38 | MEDIUM | backend | @Autowired field injection em vez de constructor injection | Todos services/controllers |
| 39 | MEDIUM | backend | Spring Security DEBUG log level em dev profile — ruido excessivo | `application.properties:48` |
| 40 | MEDIUM | frontend | 49 any types em 19 arquivos | Varios |
| 41 | MEDIUM | frontend | window.location.href = '/login' no refresh fail — bypassa React Router | `api.ts:112` |
| 42 | MEDIUM | frontend | Sem validacao de email nos formularios login/register/forgot-password | Login.tsx, Register.tsx, ForgotPassword.tsx |
| 43 | MEDIUM | frontend | Sem validacao de negativos/divisao-por-zero em campos numericos | Transacoes.tsx, Metas.tsx, Carteira.tsx |
| 44 | MEDIUM | frontend | ResetPassword setTimeout sem cleanup no unmount | `ResetPassword.tsx:68-70` |
| 45 | MEDIUM | frontend | Duplo gerenciamento de estado (local + useApi) com risco de race em Metas e Categorias | Metas.tsx:65-70, Categorias.tsx |
| 46 | MEDIUM | frontend | Entidade Conta sempre filtrada por CREDITO sem explicacao na UI | `contas.tsx:38-39` |
| 47 | MEDIUM | mobile | parseBRCurrency duplicado em 5 arquivos (transacoes, metas, contas, carteiras, contas-fixas) | Varios |
| 48 | MEDIUM | mobile | onError de mutations atribui erro ao campo errado (sempre nomeError) | categorias.tsx:31-33, contas.tsx:33-35 |
| 49 | MEDIUM | mobile | pagarMutation em contas-fixas sem onError — pagamento falha sem feedback | `contas-fixas.tsx:24-31` |
| 50 | MEDIUM | mobile | Sem RefreshControl no Dashboard — sem pull-to-refresh | `index.tsx` |
| 51 | MEDIUM | mobile | QueryClient sem staleTime/retry config | `_layout.tsx` |
| 52 | MEDIUM | mobile | isValidDateBR so valida regex, nao valida data real (99/99/9999 passa) | `utils/format.ts:42-43` |
| 53 | MEDIUM | mobile | Sem error boundary no root layout | `_layout.tsx` |
| 54 | MEDIUM | mobile | Sem loading state no redirector app/index.tsx | `index.tsx` |
| 55 | MEDIUM | mobile | more.tsx com router.push(rota as any) — cast inseguro | `more.tsx:20` |
| 56 | MEDIUM | mobile | formato de moeda inconsistente em contas-fixas (bypassa formatCurrency) | `contas-fixas.tsx:86` |
| 57 | MEDIUM | mobile | Badge.tsx: ATRASADO e CANCELADO mapeiam mesmas cores | `Badge.tsx` |
| 58 | MEDIUM | mobile | Sem confirmacao de logout no perfil.tsx | `perfil.tsx:21` |
| 59 | MEDIUM | mobile | contaService com export nomeado e default — inconsistente | `contaService.ts:4,19` |

### LOW (24)

| # | Severidade | Area | Descricao | Arquivo:Linha |
|---|---|---|---|---|
| 60 | LOW | backend | Nenhum health check de banco no Actuator | `application-prod.properties` |
| 61 | LOW | backend | Sem Kubernetes probes (liveness/readiness) no Actuator | `application-prod.properties` |
| 62 | LOW | backend | Sem HTTPS enforcement explicito no SecurityConfig | `SecurityConfig.java` |
| 63 | LOW | backend | Email em mensagem de excecao no CustomUserDetailsService | `CustomUserDetailsService.java:23` |
| 64 | LOW | backend | RefreshToken.toString() expoe 20 caracteres do token | `RefreshToken.java:152-160` |
| 65 | LOW | backend | Atualizacao em lote de parcelas poderia usar query nativa em vez de loop | `ParcelaService.java:64-76` |
| 66 | LOW | frontend | 5 console.log() soltos em producao | authService.ts, Login.tsx, GraficoEvolucaoMensal.tsx, GraficoGastosPorCategoria.tsx |
| 67 | LOW | frontend | GraficoComparacaoMensal.tsx definido mas nunca importado — dead code | `GraficoComparacaoMensal.tsx` |
| 68 | LOW | frontend | Sem tela de loading durante init do auth (tela branca) | `App.tsx:88` |
| 69 | LOW | frontend | Sem rota 404 para paths desconhecidos | `App.tsx` |
| 70 | LOW | frontend | Layout sem aria-labels em nav, links, botoes | `Layout.tsx` |
| 71 | LOW | frontend | CategoriaDropdown sem ARIA (role, expanded, listbox, option) | `CategoriaDropdown.tsx` |
| 72 | LOW | frontend | Modal em Carteira sem role=dialog, aria-modal, focus trap | `Carteira.tsx:439-484` |
| 73 | LOW | frontend | Graficos sem alternativas acessiveis para leitores de tela | Chart components |
| 74 | LOW | frontend | _usuarioId passado mas nunca usado em 5 services | Varios service files |
| 75 | LOW | mobile | formatPhone, formatDateTime, isValidCPF, isValidPhone, isValidCEP — funcoes nunca usadas | `utils/format.ts`, `utils/validate.ts` |
| 76 | LOW | mobile | AsyncState, AsyncStatus, EvolucaoMensal, GastoPorCategoria, Parcela types definidos mas nunca usados | `types/index.ts` |
| 77 | LOW | mobile | nativewind + tailwindcss como dependencias mas ZERO uso de className | `package.json` |
| 78 | LOW | mobile | Cores nao usadas em theme/colors.ts (top, brand2, info, infoBg) | `colors.ts` |
| 79 | LOW | mobile | Sem labels de acessibilidade em TODAS as telas (14 arquivos) | app/(app)/, app/(auth)/ |
| 80 | LOW | mobile | SkeletonBox: Animated.View com cast as any por limitacao de tipo | `SkeletonBox.tsx:30,33` |
| 81 | LOW | mobile | Sem tratamento de erro no carregamento de categorias do modal transacao | `transacoes.tsx:38` |
| 82 | LOW | mobile | "Entrada por IA (em breve)" — placeholder permanente sem funcionalidade | `more.tsx:11` |
| 83 | LOW | mobile | ActivityIndicator importado mas nao usado em contas-fixas | `contas-fixas.tsx:2` |
| 83 | LOW | geral | Testes: backend 6 arquivos, frontend poucos, mobile zero | — |

## O que foi corrigido

Nada nesta auditoria — escopo foi exclusivamente read-only. Relatorio para embasar correcoes futuras.

## O que ficou pendente

Todos os 83 achados listados acima estao pendentes de correcao. Prioridades:
1. P0 (imediato): CRITICAL 1-15 (IDOR, race conditions, ddl-auto em prod, token mobile, IP hardcoded, findAll massivo, cookie secure, senha fraca)
2. P1 (curto prazo): HIGH 16-27 (rate limit, defaults inseguros, CSRF, @Transactional, elementos UI mortos)
3. P2 (medio prazo): MEDIUM 28-59 (deprecated APIs, validacao, acessibilidade, refactors)
4. P3 (longo prazo): LOW 60-83 (codigo morto, imports nao usados, polish)

## Recomendacao final

**STATUS: PASS_COM_RESSALVA**

O sistema e funcional e bem arquitetado para projeto pessoal/early-stage. Pontos fortes:
- Isolamento de tenant via ownership validation (com gaps pontuais)
- Refresh token com rotacao e deteccao de reuse
- Interceptors bem implementados (frontend web com fila de retry)
- Separacao clara de camadas (controller → service → repository)
- API documentada e versionada

Porem, **nao esta pronto para producao**. Os 15 achados CRITICAL precisam ser resolvidos antes de qualquer
deploy publico. Principais bloqueadores:
1. IDOR cross-tenant no TransacaoService (risco de vazamento/manipulacao de dados entre usuarios)
2. Race conditions financeiras em Carteira e Meta (risco de corrupcao de saldo)
3. Configuracoes de producao inseguras (ddl-auto, cookie seguro, CORS)
4. Mobile sem persistencia de sessao e com IP hardcoded
5. findAll() massivo que causara OOM com volume real de dados

---

> Relatorio mantido pelo `docs-reporter`. Gerado em auditoria completa do sistema em 2026-07-06.
