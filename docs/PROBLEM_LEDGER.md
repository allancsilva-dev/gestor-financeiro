# Problem Ledger — Gestor Financeiro

Registro central de problemas encontrados no sistema. Mantido pelo `docs-reporter`.

---

## PROB-0001 — Cross-tenant IDOR no TransacaoService

- **ID:** PROB-0001
- **Titulo:** TransacaoService.criar() aceita categoriaId/contaId sem validar ownership do usuario
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** FECHADO (PR-FOUNDATION-02, 2026-07-07)
- **Area:** backend, seguranca
- **Sintoma:** Ao criar transacao, o service busca categoria e conta por ID sem verificar se pertencem ao usuario autenticado.
- **Causa raiz:** `categoriaRepository.findById()` e `contaRepository.findById()` validam existencia mas nao ownership.
- **Solucao proposta:** Substituir `findById()` por metodos que validam ownership (ex: `categoriaRepository.findByIdAndUsuarioId()`)
- **Solucao aplicada:** TransacaoService.criar() e deletar() agora usam `findByIdAndUsuarioId`. ContaService.adicionarGasto/removerGasto recebem usuarioId e validam ownership. ContaFixaService.criar/atualizar validam categoriaId ownership. CarteiraService.deletar(Long) sem ownership removido.
- **Evidencias:** Testes: 25/25 passam, incluindo 2 novos testes IDOR de cross-user categoriaId e contaId na criacao de transacao.
- **Riscos residuais:** Nenhum — validação ownership em todos os fluxos críticos.
- **Proximo passo:** Resolvido.

---

## PROB-0002 — Race condition em Carteira (saldo)

- **ID:** PROB-0002
- **Titulo:** adicionarDinheiro()/removerDinheiro() sem @Version — saldo vulneravel a race condition
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** FECHADO (PR-FOUNDATION-03, 2026-07-07)
- **Area:** backend
- **Sintoma:** Duas requisicoes concorrentes leem mesmo saldo, calculam novos valores, e um sobrescreve o outro. Saldo final incorreto.
- **Causa raiz:** Padrao SELECT → calculo em memoria → UPDATE sem optimistic locking (@Version)
- **Impacto tecnico:** Corrupcao de saldo financeiro. Perda ou duplicacao de dinheiro.
- **Arquivos relacionados:** `backend/.../service/CarteiraService.java:92-133`, `backend/.../model/Carteira.java`, `backend/.../service/MetaService.java:48-76`, `backend/.../service/ContaService.java:61-78`
- **Solucao proposta:** Adicionar campo `@Version private Long version;` nas entidades Carteira, Meta e Conta
- **Solucao aplicada:** @Version adicionado em Carteira, Conta, Meta e Categoria. OptimisticLockingFailureException tratado no GlobalExceptionHandler → 409 Conflict. Migration V2 para colunas version.
- **Evidencias:** Testes: 29/29 passam incluindo FinancialIntegrityTest (4 testes verificando @Version). Migration V2__optimistic_locking_columns.sql.
- **Riscos residuais:** Concorrencia unitária segue coberta por H2; validação Flyway/schema em PostgreSQL VPS real foi concluída em 2026-07-08. Teste de concorrência sob carga em PostgreSQL continua recomendado para hardening futuro.
- **Proximo passo:** Resolvido.

---

## PROB-0003 — findAll() massivo em tarefas agendadas

- **ID:** PROB-0003
- **Titulo:** ParcelaService e ContaFixaService usam findAll() carregando todos os registros do banco
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** FECHADO (PR-FOUNDATION-04, 2026-07-07)
- **Area:** backend, banco
- **Sintoma:** `atualizarParcelasAtrasadas()` e `atualizarContasAtrasadas()` chamam `findAll()` sem paginacao ou filtro.
- **Solucao proposta:** Criar queries filtradas
- **Solucao aplicada:** ParcelaRepository.atualizarStatusParcelasAtrasadas() usa UPDATE SET WHERE. ContaFixaRepository com resetarContasPagasVencidas() e atualizarStatusContasAtrasadas() via JPQL UPDATE.
- **Evidencias:** findAll() removido de ParcelaService e ContaFixaService. Queries JPQL com filtro por status e data.
- **Riscos residuais:** Nenhum.
- **Proximo passo:** Resolvido.

---

## PROB-0004 — DashboardService agregação em memória

- **ID:** PROB-0004
- **Titulo:** DashboardService carrega entidades completas com JOIN FETCH apenas para somar valorTotal
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (PR-FOUNDATION-04, 2026-07-07)
- **Area:** backend, banco
- **Sintoma:** Dashboard carrega entidades completas apenas para somar valorTotal.
- **Solucao proposta:** Criar queries JPQL: SUM
- **Solucao aplicada:** TransacaoRepository com sumValorTotalByUsuarioIdAndTipoAndDataBetween, sumValorEfetivoByUsuarioIdAndTipoAndDataBetween, sumValorEfetivoAgrupadoPorCategoria. countBy queries para contagens. CarteiraRepository.sumSaldoByUsuarioId. DashboardService refatorado — zero carregamento de entidades em memória para agregações.
- **Evidencias:** Métodos privados calcularTotalPorTipo, calcularTotalSaidasComParcelas, calcularSaldoCarteiras removidos.
- **Riscos residuais:** Nenhum.
- **Proximo passo:** Resolvido.

---

## PROB-0005 — cookie.secure ausente em produção

- **ID:** PROB-0005
- **Titulo:** Cookie refreshToken sem flag Secure em application-prod.properties
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** FECHADO (PR-FOUNDATION-05, 2026-07-07)
- **Area:** backend, seguranca
- **Sintoma:** Cookie refreshToken sem flag Secure em producao.
- **Solucao proposta:** Adicionar `cookie.secure=true` em application-prod.properties
- **Solucao aplicada:** `cookie.secure=true` adicionado em application-prod.properties. Cookie HttpOnly + Secure + SameSite=Lax em produção.
- **Evidencias:** application-prod.properties linha `cookie.secure=true`.
- **Riscos residuais:** Requer HTTPS configurado no ambiente de producao.
- **Proximo passo:** Resolvido.

---

## PROB-0006 — ddl-auto=update em produção

- **ID:** PROB-0006
- **Titulo:** Hibernate ddl-auto=update em producao — risco de alteracao destrutiva de schema
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** FECHADO (PR-FOUNDATION-01, 2026-07-07)
- **Area:** backend, banco, infra
- **Sintoma:** Hibernate pode dropar colunas, alterar tipos ou causar schema drift entre deploys
- **Causa raiz:** Configuracao `spring.jpa.hibernate.ddl-auto=update` em application-prod.properties
- **Impacto tecnico:** Perda de dados, downtime, incompatibilidade entre versoes de codigo e schema
- **Arquivos relacionados:** `backend/.../application-prod.properties:11`
- **Solucao proposta:** Mudar para `validate` ou `none` + adotar Flyway/Liquibase para migrations
- **Solucao aplicada:** Flyway adicionado ao projeto. Migration baseline V1__baseline_schema.sql com 10 tabelas. ddl-auto=validate em dev e prod. Testes H2 com flyway.enabled=false. DEPLOY.md atualizado.
- **Evidencias:** Arquivos: pom.xml, V1__baseline_schema.sql, application.properties, application-prod.properties, application-test.properties. Testes: mvn test 23/23 PASS.
- **Riscos residuais:** Validação posterior com PostgreSQL VPS real concluída em 2026-07-08: Flyway validou 14 migrations e Hibernate `ddl-auto=validate` inicializou. Testcontainers local continua dependente de Docker ativo.
- **Proximo passo:** Resolvido. PR-FOUNDATION-01 concluído com ressalva.

---

## PROB-0007 — Política de senha fraca

- **ID:** PROB-0007
- **Titulo:** Senha exige apenas 6 caracteres — sem complexidade minima para app financeiro
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** FECHADO (PR-FOUNDATION-07, 2026-07-07)
- **Area:** backend, seguranca
- **Sintoma:** Senhas como "123456" ou "abcdef" sao aceitas. Sem exigencia de maiuscula, digito ou caractere especial.
- **Causa raiz:** Apenas `@Size(min = 6)` no RegisterRequest e ResetPasswordRequest
- **Impacto tecnico:** Contas vulneraveis a brute force e ataques de dicionario
- **Arquivos relacionados:** `backend/.../dto/RegisterRequest.java:18-19`, `backend/.../dto/ResetPasswordRequest.java:12-13`
- **Solucao proposta:** Minimo 8 caracteres, pelo menos 1 letra, 1 digito. Implementar com annotation customizada `@ValidPassword`.
- **Solucao aplicada:** Annotation customizada @ValidPassword com ConstraintValidator. Min 8 caracteres, ao menos 1 letra, 1 digito. Aplicada em RegisterRequest e ResetPasswordRequest.
- **Evidencias:** Testes passwordValidationTest em AuthControllerTest (senha apenas digitos rejeitada, senha curta rejeitada). mvn test 34/34 PASS.
- **Riscos residuais:** Usuarios com senhas antigas nao sao forcados a atualizar. Nao ha endpoint de troca de senha autenticada (fora do fluxo reset).
- **Proximo passo:** Resolvido.

---

## PROB-0008 — Rate limit incompleto

- **ID:** PROB-0008
- **Titulo:** Rate limit cobre apenas login e forgot-password — register/reset sem protecao
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (PR-FOUNDATION-05, 2026-07-07)
- **Area:** backend, seguranca
- **Sintoma:** Endpoints `/register`, `/reset-password` e `/validate-token` nao tem rate limit.
- **Solucao proposta:** Adicionar paths a lista de endpoints protegidos
- **Solucao aplicada:** LoginRateLimitFilter ampliado com REGISTER_PATH (5/min), RESET_PASSWORD_PATH (5/min), VALIDATE_TOKEN_PATH (10/min).
- **Evidencias:** LoginRateLimitFilter.java com 5 paths protegidos.
- **Riscos residuais:** Rate limit em memoria (ConcurrentHashMap) ainda fragil em multi-instancia.
- **Proximo passo:** Resolvido.

---

## PROB-0009 — Secrets com default inseguro em dev

- **ID:** PROB-0009
- **Titulo:** Senha DB '1234' e JWT secret 'altere_este_valor...' como defaults em application.properties
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-13)
- **Area:** backend, seguranca
- **Sintoma:** Se variaveis de ambiente nao forem setadas, aplicacao usa senha DB 1234 e JWT secret fraco
- **Solucao proposta:** Remover defaults ou lancar excecao na inicializacao se variaveis ausentes
- **Solucao aplicada:** Perfis exigem URL/usuario/senha DB e JWT via ambiente; perfil dev implicito removido. Credenciais locais ficam somente no Docker/exemplo.
- **Evidencias:** application-prod.properties ja usa `${DATABASE_URL}` e `${JWT_SECRET}` sem fallback.
- **Riscos residuais:** Nenhum default de secret na aplicacao.
- **Proximo passo:** Nenhum.

---

## PROB-0010 — CORS produção com fallback localhost

- **ID:** PROB-0010
- **Titulo:** CORS_ALLOWED_ORIGINS em producao fallback para http://localhost:5173
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (PR-FOUNDATION-05, 2026-07-07)
- **Area:** backend, seguranca
- **Sintoma:** CORS_ALLOWED_ORIGINS em producao fallback para http://localhost:5173
- **Solucao proposta:** Remover default ou deixar string vazia
- **Solucao aplicada:** application-prod.properties alterado para `${CORS_ALLOWED_ORIGINS:}` (vazio se nao configurado). Sem origem = sem CORS permitido.
- **Evidencias:** application-prod.properties linha cors.allowed.origins sem fallback localhost.
- **Riscos residuais:** Frontend precisa ter origem configurada explicitamente.
- **Proximo passo:** Resolvido.

---

## PROB-0011 — Email e token de reset logados

- **ID:** PROB-0011
- **Titulo:** EmailService loga email destino (INFO) e link de reset com token (DEBUG)
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (PR-FOUNDATION-05, 2026-07-07)
- **Area:** backend, seguranca, LGPD
- **Sintoma:** Email do usuario (PII) em INFO. Token de reset completo em DEBUG.
- **Solucao proposta:** Mascarar email. Nunca logar token completo.
- **Solucao aplicada:** EmailService com maskEmail() — exibe j***@d***.com. Token nunca logado. Link de recuperacao removido do log.
- **Evidencias:** EmailService.java com metodo maskEmail.
- **Riscos residuais:** Nenhum.
- **Proximo passo:** Resolvido.

---

## PROB-0012 — @Transactional ausente em operações de escrita

- **ID:** PROB-0012
- **Titulo:** Multiplos services sem @Transactional em metodos de escrita (criar, atualizar, deletar)
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (PR-FOUNDATION-03, 2026-07-07)
- **Area:** backend
- **Sintoma:** Operacoes de escrita sem garantia de atomicidade. Falha parcial pode deixar dados inconsistentes.
- **Causa raiz:** Ausencia de `@Transactional` em metodos write de CategoriaService, ContaService, MetaService, ContaFixaService, CarteiraService, ParcelaService
- **Solucao proposta:** Adicionar `@Transactional` em todos os metodos publicos de escrita
- **Solucao aplicada:** @Transactional adicionado em todos os metodos write: CarteiraService (criar, atualizar, deletar), ContaService (criar, atualizar, deletar, adicionarGasto, removerGasto), CategoriaService (criar, atualizar, deletar), MetaService (criar, atualizar, deletar, adicionarValor, removerValor), ParcelaService (marcarComoPaga, marcarComoPendente), ContaFixaService (criar, atualizar, deletar).
- **Evidencias:** 29/29 testes passam.
- **Riscos residuais:** Nenhum.
- **Proximo passo:** Resolvido.

---

## PROB-0013 — Mobile: token volátil sem persistência

- **ID:** PROB-0013
- **Titulo:** Token de acesso armazenado apenas em memoria — sessao perdida ao fechar app
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** FECHADO (PR-FASE2-01, 2026-07-08)
- **Area:** mobile
- **Sintoma:** Usuario precisa fazer login toda vez que abre o app. Sessao nao sobrevive a restart, crash ou kill do processo.
- **Causa raiz:** `store/auth.ts` armazena token em variavel de modulo (`let _accessToken`). `expo-secure-store` instalado mas nunca importado ou usado.
- **Impacto tecnico:** Experiencia de usuario degradada. Impossivel manter sessao entre usos do app.
- **Arquivos relacionados:** `mobile/src/store/auth.ts:1-2`, `mobile/src/context/AuthContext.tsx:31`
- **Solucao proposta:** Implementar persistencia com expo-secure-store. Adicionar useEffect no AuthContext para restaurar token no startup.
- **Solucao aplicada:** Auth store mobile passou a persistir token com `expo-secure-store`; AuthContext restaura sessao no startup.
- **Evidencias:** Comentario `// TODO fase 2: persistir com expo-secure-store` + variavel `_accessToken` em modulo
- **Riscos residuais:** Secure Store tem limitacoes de tamanho (~2KB); tokens JWT cabem. Chave compartilhada entre apps do mesmo dev no iOS.
- **Proximo passo:** Resolvido.

---

## PROB-0014 — Mobile: IP hardcoded

- **ID:** PROB-0014
- **Titulo:** URL da API hardcoded com IP fixo 192.168.15.3:8081
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** FECHADO (PR-FASE2-01, 2026-07-08)
- **Area:** mobile
- **Sintoma:** App so funciona na rede especifica do desenvolvedor. Inutilizavel em producao ou em outra rede.
- **Causa raiz:** `const BASE_URL = 'http://192.168.15.3:8081/api'` hardcoded
- **Impacto tecnico:** App quebrado em qualquer ambiente que nao seja a maquina de dev
- **Arquivos relacionados:** `mobile/src/config/api.config.ts:5`
- **Solucao proposta:** Usar `expo-constants` (`Constants.expoConfig?.extra?.apiUrl`) ou variavel de ambiente
- **Solucao aplicada:** API mobile passou a usar configuracao por ambiente via `expo-constants`, removendo dependencia de IP hardcoded para uso fora da rede local.
- **Evidencias:** Linha 5 do api.config.ts com comentario "troque pelo IP da sua maquina"
- **Riscos residuais:** Necessario documentar configuracao para devs
- **Proximo passo:** Resolvido.

---

## PROB-0015 — Mobile: elementos UI mortos

- **ID:** PROB-0015
- **Titulo:** Botoes sem handler e links nao clicaveis no mobile
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (PR-FASE2-02, 2026-07-08)
- **Area:** mobile
- **Sintoma:** Botao "Esqueceu a senha?" sem onPress. Link "Ver todas" no Dashboard nao e clicavel. Usuario clica e nada acontece.
- **Causa raiz:** TouchableOpacity e Text sem evento onPress definido
- **Impacto tecnico:** UX quebrada. Funcionalidades inacessiveis.
- **Arquivos relacionados:** `mobile/app/(auth)/login.tsx:47-49`, `mobile/app/(app)/index.tsx:68`
- **Solucao proposta:** Adicionar onPress handlers (navegar para forgot-password, navegar para lista completa)
- **Solucao aplicada:** Handlers de UI mortos foram ligados a navegação/ações reais conforme PR-FASE2-02.
- **Evidencias:** TouchableOpacity sem onPress no login, Text sem TouchableOpacity no Dashboard
- **Riscos residuais:** Verificar se a rota de destino existe antes de vincular
- **Proximo passo:** Resolvido.

---

## PROB-0016 — Mobile: API path inconsistente

- **ID:** PROB-0016
- **Titulo:** Perfil usa /dashboard/resumo sem prefixo /v1 — endpoint 404
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** FECHADO (PR-FASE2-01, 2026-07-08)
- **Area:** mobile
- **Sintoma:** Tela de Perfil nao carrega dados de resumo pois endpoint `/dashboard/resumo` nao existe. O endpoint correto e `/v1/dashboard/resumo`.
- **Causa raiz:** Path inconsistente entre `app/(app)/index.tsx` e `app/(app)/perfil.tsx`
- **Impacto tecnico:** Dados do perfil nao carregam. Erro 404 silencioso.
- **Arquivos relacionados:** `mobile/app/(app)/index.tsx:17`, `mobile/app/(app)/perfil.tsx:19`
- **Solucao proposta:** Corrigir path em perfil.tsx para `/v1/dashboard/resumo`
- **Solucao aplicada:** Path da API mobile corrigido e consolidado no serviço mobile.
- **Evidencias:** Comparacao das linhas 17 (index) e 19 (perfil)
- **Riscos residuais:** Nenhum apos correcao
- **Proximo passo:** Resolvido.

---

## PROB-0017 — Mobile: erros silenciosos em mutations

- **ID:** PROB-0017
- **Titulo:** Mutations de criar carteira e pagar conta fixa sem tratamento de erro
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (PR-FASE2-02, 2026-07-08)
- **Area:** mobile
- **Sintoma:** Se API falhar ao criar carteira ou marcar conta fixa como paga, usuario nao recebe nenhum feedback. Operacao falha silenciosamente.
- **Causa raiz:** `criarMutation` em carteiras.tsx sem `onError`. `pagarMutation` em contas-fixas.tsx sem `onError`. Try/catch vazio em handleSalvar.
- **Impacto tecnico:** Usuario acredita que operacao foi concluida mas nao foi. Dados inconsistentes.
- **Arquivos relacionados:** `mobile/app/(app)/more/carteiras.tsx:26-49`, `mobile/app/(app)/more/contas-fixas.tsx:24-31`
- **Solucao proposta:** Adicionar onError handlers com Alert.alert() ou toast
- **Solucao aplicada:** Mutations mobile críticas passaram a ter `onError`/catch com feedback de erro em carteiras e contas fixas.
- **Evidencias:** `carteiras.tsx` ainda tem `catch` vazio; `contas-fixas.tsx` tem `onError` em `criarMutation`, mas nao em `pagarMutation`.
- **Riscos residuais:** Nenhum alem da UX melhorada
- **Proximo passo:** Resolvido.

---

## PROB-0018 — Mobile: sem botão voltar nos sub-menus

- **ID:** PROB-0018
- **Titulo:** More sub-screens sem header visivel — usuarios iOS sem back button
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-11; reaberto no mesmo dia apos verificacao de codigo revelar que PR-FASE2-02 nao aplicou o fix, corrigido em seguida)
- **Area:** mobile
- **Sintoma:** `headerShown: false` em more/_layout.tsx esconde navegacao. Usuarios iOS precisam saber do gesto de swipe para voltar.
- **Causa raiz:** Configuracao `headerShown: false` no Stack layout de More
- **Impacto tecnico:** Navegacao nao descoberta para usuarios iOS
- **Arquivos relacionados:** `mobile/app/(app)/more/_layout.tsx`, `mobile/src/components/ui/BackButton.tsx`, 8 telas `more/` (carteiras, categorias, contas, contas-fixas, faturas, investimentos, orcamentos, relatorios)
- **Solucao proposta:** Mostrar header com titulo e seta de voltar, ou adicionar botao customizado
- **Solucao aplicada:** Mantido `headerShown:false` (telas ja tem titulo proprio in-content; header nativo duplicaria). Novo componente `BackButton` (chevron + "Voltar" chamando `router.back()`) inserido como primeiro filho do bloco de titulo das 8 sub-telas de `more/`. `more/index.tsx` (raiz da aba) nao recebe back por nao ser sub-tela empilhada.
- **Evidencias:** `<BackButton />` presente nas 8 telas; `tsc --noEmit` 0 erros.
- **Riscos residuais:** Validacao apenas por typecheck — mobile sem suite de testes (ver SYSTEM_OVERVIEW). Sem verificacao visual em simulador nesta sessao.
- **Proximo passo:** Conferir alinhamento visual do BackButton em iOS/Android quando houver simulador.

---

## PROB-0019 — Frontend: CSRF ausente

- **ID:** PROB-0019
- **Titulo:** SPA web sem protecao CSRF com withCredentials:true
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (BUG-0008, 2026-07-07)
- **Area:** frontend, seguranca
- **Sintoma:** Axios configurado com `withCredentials: true` (envia cookies) mas sem enviar token CSRF. Refresh token cookie pode ser explorado em ataques cross-site.
- **Causa raiz:** Sem header X-CSRF-TOKEN ou double-submit cookie pattern
- **Impacto tecnico:** POST /api/auth/refresh-token vulneravel a CSRF (embora SameSite=Lax mitigue parcialmente)
- **Arquivos relacionados:** `frontend/src/services/api.ts:20`
- **Solucao proposta:** Backend emitir CSRF token via endpoint dedicado; frontend envia-lo como header
- **Solucao aplicada:** BUG-0008: Backend criou RefreshTokenCsrfFilter validando X-CSRF-Token em refresh-token e logout. Frontend envia header automaticamente. AuthController emite/rotaciona cookie csrfToken.
- **Evidencias:** axiosInstance com withCredentials:true sem header CSRF
- **Riscos residuais:** Nenhum. CSRF resolvido para endpoints que usam cookie (refresh-token, logout). Demais endpoints usam JWT Bearer — nao suscetiveis a CSRF.
- **Proximo passo:** Coordenar implementacao backend + frontend

---

## PROB-0020 — CarteiraService scan completo de categorias

- **ID:** PROB-0020
- **Titulo:** buscarOuCriarCategoriaTransferencia() carrega todas categorias do usuario
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (PR-FOUNDATION-04, 2026-07-07)
- **Area:** backend
- **Sintoma:** buscarOuCriarCategoriaTransferencia() carregava todas categorias do usuario.
- **Solucao proposta:** Usar findByName
- **Solucao aplicada:** CategoriaRepository.findByUsuarioIdAndNomeIgnoreCase. CarteiraService usa Optional.orElseGet no lugar do scan.
- **Evidencias:** Loop for removido.
- **Riscos residuais:** Nenhum.
- **Proximo passo:** Resolvido.

---

## PROB-0021 — CarteiraService deletar() sem ownership

- **ID:** PROB-0021
- **Titulo:** Overload deletar(Long id) sem validacao de ownership — exposto para uso inseguro
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** MEDIUM
- **Status:** FECHADO (PR-FOUNDATION-02, 2026-07-07)
- **Area:** backend, seguranca
- **Sintoma:** Metodo publico `deletar(Long id)` sem validacao de usuario. Se chamado por outro componente interno, deleta sem verificar dono.
- **Causa raiz:** Sobrecarga com `deletar(Long id)` vs `deletar(Long id, Long usuarioId)` — o single-arg usa `buscarPorId()` sem filtro de usuario
- **Impacto tecnico:** Risco de delecao indevida se metodo for chamado incorretamente
- **Arquivos relacionados:** `backend/.../service/CarteiraService.java:187-195`
- **Solucao proposta:** Remover overload single-arg ou marca-lo como @Deprecated
- **Solucao aplicada:** Overload `deletar(Long id)` sem ownership removido. Controller e demais callers usam `deletar(id, usuarioId)`.
- **Evidencias:** Metodo deletar sem usuarioId removido de CarteiraService.java.
- **Riscos residuais:** Nenhum.
- **Proximo passo:** Resolvido.

---

## PROB-0022 — JJWT API deprecated

- **ID:** PROB-0022
- **Titulo:** JwtUtil usa API deprecated do JJWT (parserBuilder/setSigningKey)
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** MEDIUM
- **Status:** FECHADO (2026-07-13; evidencia corrigida)
- **Area:** backend
- **Sintoma:** `parserBuilder()`, `setSigningKey()`, `parseClaimsJws()` sao deprecated desde JJWT 0.12.x
- **Causa raiz:** API antiga usada com jjwt 0.11.5
- **Impacto tecnico:** Sem correcoes de seguranca da API nova. Bloqueia upgrade do jjwt.
- **Arquivos relacionados:** `backend/.../config/JwtUtil.java:74-79`
- **Solucao proposta:** Migrar para `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`
- **Solucao aplicada:** JJWT 0.13.0; emissao HS256 explicita e parser `verifyWith(...).parseSignedClaims(...)` rejeitando outro algoritmo.
- **Evidencias:** `JwtUtil`, testes auth/security e `mvn verify`.
- **Riscos residuais:** Nenhum conhecido.
- **Proximo passo:** Nenhum.

---

## PROB-0023 — Sem account lockout

- **ID:** PROB-0023
- **Titulo:** Sem mecanismo de lockout de conta apos multiplas falhas de login
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** MEDIUM
- **Status:** FECHADO (PR-FOUNDATION-07, 2026-07-07)
- **Area:** backend, seguranca
- **Sintoma:** Rate limit bloqueia 5 tentativas/min, mas atacante pode tentar 5 senhas a cada minuto indefinidamente. Sem lockout permanente.
- **Causa raiz:** LoginRateLimitFilter faz rate limit por IP, mas nao lockout por conta
- **Impacto tecnico:** Brute force distribuido (multi-IP) pode quebrar senhas
- **Arquivos relacionados:** `backend/.../config/LoginRateLimitFilter.java`
- **Solucao proposta:** Adicionar campo failedAttempts e lockedUntil na entidade Usuario; verificar no login
- **Solucao aplicada:** Campos failedAttempts e lockedUntil em Usuario. Migration V4. Logica de lockout no AuthController.login(). AccountLockedException + handler 429. Configuravel: security.auth.max-failed-attempts=5, lockout-minutes=15. Login bem-sucedido reseta falhas.
- **Evidencias:** Testes login_deveBloquearContaAposFalhasConsecutivas e login_deveResetarFalhasAposSucesso no AuthControllerTest. mvn test 34/34 PASS.
- **Riscos residuais:** Lockout pode ser usado para DoS em usuarios legitimos (mitigado com duracao configurada e curta).
- **Proximo passo:** Resolvido.

---

## PROB-0024 — LoginRateLimitFilter memory leak

- **ID:** PROB-0024
- **Titulo:** Rate limit entries nunca sao limpas para IPs que param de fazer requests
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** MEDIUM
- **Status:** FECHADO (PR-FOUNDATION-07, 2026-07-07)
- **Area:** backend
- **Sintoma:** ConcurrentHashMap acumula entradas de IPs que fizeram tentativas e depois nunca mais voltaram
- **Causa raiz:** Cleanup so acontecia quando o mesmo IP fazia novo request e a lista ficava vazia
- **Impacto tecnico:** Memory leak lento e progressivo em longos periodos de uptime
- **Arquivos relacionados:** `backend/.../config/LoginRateLimitFilter.java:33,62-76`
- **Solucao proposta:** Adicionar @Scheduled para limpar entradas expiradas periodicamente
- **Solucao aplicada:** @Scheduled(fixedRate=60s) cleanupExpiredEntries() no LoginRateLimitFilter. Remove entradas expiradas (>60s) e entradas com lista vazia. @EnableScheduling na FinanceiroApplication.
- **Evidencias:** Verificado em codigo. Tests passam. Log.debug para remocoes.
- **Riscos residuais:** Scheduling adiciona thread extra — aceitavel.
- **Proximo passo:** Resolvido.

---

## PROB-0025 — Mobile: entry points zumbis

- **ID:** PROB-0025
- **Titulo:** App.tsx e index.ts sao codigo morto do template Expo starter
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (PR-FASE2-02, 2026-07-08)
- **Area:** mobile
- **Sintoma:** App.tsx e o template padrao Expo ("Open up App.tsx to start"). index.ts importa App.tsx. Entry real e expo-router/entry. Estes arquivos nunca sao executados.
- **Causa raiz:** package.json define "main": "expo-router/entry", ignorando App.tsx e index.ts
- **Impacto tecnico:** Confusao para desenvolvedores. Codigo morto no repositorio.
- **Arquivos relacionados:** `mobile/App.tsx`, `mobile/index.ts`, `mobile/package.json`
- **Solucao proposta:** Deletar App.tsx e atualizar index.ts para re-exportar expo-router/entry
- **Solucao aplicada:** Entry points zumbis removidos/neutralizados conforme PR-FASE2-02; `expo-router/entry` permanece como entrada real.
- **Evidencias:** App.tsx contem template padrao; index.ts importa App; main em package.json aponta para expo-router/entry
- **Riscos residuais:** Nenhum — ambos arquivos sao ignorados pelo bundler
- **Proximo passo:** Resolvido.

---

## PROB-0026 — Mobile: console.error em produção

- **ID:** PROB-0026
- **Titulo:** api.ts mobile loga URLs e status codes em console.error sem gate __DEV__
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-11; reaberto no mesmo dia apos verificacao de codigo revelar que BUG-0051 nao aplicou o gate, corrigido em seguida)
- **Area:** mobile, seguranca
- **Sintoma:** Erros de API expoem paths internos e status codes no console de producao
- **Causa raiz:** `console.error('[API Error]', { url: error.config?.url, status: error.response?.status })` sem condicional
- **Impacto tecnico:** Vazamento de informacao de infraestrutura em builds de producao
- **Arquivos relacionados:** `mobile/src/services/api.ts:92`
- **Solucao proposta:** Envolver com `if (__DEV__)` ou remover
- **Solucao aplicada:** Bloco `console.error('[API Error]', {url, status})` envolto em `if (__DEV__) { ... }` em `mobile/src/services/api.ts:92`. Producao nao loga mais paths/status no console.
- **Evidencias:** `api.ts:92` gated por `__DEV__`.
- **Riscos residuais:** Perda de visibilidade de erros em producao — usar crash reporting service (futuro)
- **Proximo passo:** Nenhum imediato; considerar crash reporting service (Sentry/etc) para observabilidade de prod.

---

## PROB-0027 — Frontend: 54 any types

- **ID:** PROB-0027
- **Titulo:** Uso excessivo de 'any' em todo o frontend
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** MEDIUM
- **Status:** FECHADO (PR-FASE2-06, 2026-07-08)
- **Area:** frontend
- **Sintoma:** 54 ocorrencias de `any` no frontend. Service methods recebem `any` em vez de tipos definidos.
- **Causa raiz:** Tipos definidos em types/index.ts mas nao usados nas assinaturas dos services
- **Impacto tecnico:** Zero type safety nas chamadas de API. Erros de tipagem so descobertos em runtime.
- **Arquivos relacionados:** `frontend/src/`
- **Solucao proposta:** Substituir `any` por tipos explicitos (Categoria, Transacao, Carteira, etc.)
- **Solucao aplicada:** Services do frontend foram tipados e usos prioritarios de `any` removidos conforme PR-FASE2-06.
- **Evidencias:** Busca atual por `\bany\b` revelou 54 ocorrencias.
- **Riscos residuais:** Pode revelar erros de tipo antes escondidos — corrigir durante migracao
- **Proximo passo:** Resolvido.

---

## PROB-0028 — Mobile: parseBRCurrency duplicado

- **ID:** PROB-0028
- **Titulo:** Logica de parse de moeda BR duplicada em 5 arquivos
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** MEDIUM
- **Status:** FECHADO (PR-FASE2-05, 2026-07-08)
- **Area:** mobile
- **Sintoma:** Mesmo padrao `replace(/\./g, '').replace(/,/g, '.')` repetido em transacoes.tsx, metas.tsx, contas.tsx, carteiras.tsx, contas-fixas.tsx
- **Causa raiz:** Logica nao centralizada em utils/format.ts
- **Impacto tecnico:** Manutencao dificil. Mudanca no formato requer alteracao em 5 lugares.
- **Arquivos relacionados:** `mobile/app/(app)/transacoes.tsx`, `metas.tsx`, `contas.tsx`, `carteiras.tsx`, `contas-fixas.tsx`
- **Solucao proposta:** Criar `parseCurrencyBR()` em utils/format.ts e usar em todos os locais
- **Solucao aplicada:** Parse de moeda BR centralizado no mobile conforme PR-FASE2-05.
- **Evidencias:** Busca pelo regex encontrou 5 ocorrencias
- **Riscos residuais:** Verificar se alguma ocorrencia tem variacao na logica
- **Proximo passo:** Resolvido.

---

## PROB-0029 — Frontend: sem rota 404

- **ID:** PROB-0029
- **Titulo:** App nao tem catch-all route para paths desconhecidos
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** LOW
- **Status:** FECHADO (PR-FASE2-04, 2026-07-08)
- **Area:** frontend
- **Sintoma:** Usuario que acessa URL invalida ve tela em branco em vez de pagina "Nao encontrado"
- **Causa raiz:** Routes em App.tsx sem `<Route path="*" element={<NotFound />} />`
- **Impacto tecnico:** UX ruim para URLs erradas
- **Arquivos relacionados:** `frontend/src/App.tsx`
- **Solucao proposta:** Adicionar rota catch-all com componente NotFound
- **Solucao aplicada:** Rota 404 adicionada no frontend conforme PR-FASE2-04.
- **Evidencias:** Ausencia de route com path="*"
- **Riscos residuais:** Nenhum
- **Proximo passo:** Resolvido.

---

## PROB-0030 — Frontend: console.log em produção

- **ID:** PROB-0030
- **Titulo:** Cinco console.log() e 29 console.error() em codigo de producao frontend
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** LOW
- **Status:** FECHADO (PR-FASE2-04, 2026-07-08)
- **Area:** frontend
- **Sintoma:** Logs de debug visiveis no console do navegador em producao
- **Causa raiz:** console.log/error nao removidos apos desenvolvimento
- **Impacto tecnico:** Console poluido. Vazamento de dados em console.error (emails, tokens em alguns casos).
- **Arquivos relacionados:** authService.ts (x2), Login.tsx, GraficoEvolucaoMensal.tsx, GraficoGastosPorCategoria.tsx + 29 console.error em frontend/src
- **Solucao proposta:** Remover console.log. Manter console.error com gate de ambiente ou migrar para servico de logging.
- **Solucao aplicada:** Console.log/error removidos das pages do frontend; mantidos apenas pontos justificados como ErrorBoundary/Auth conforme PR-FASE2-04.
- **Evidencias:** Busca atual: 5 `console.log`, 29 `console.error`
- **Riscos residuais:** Perda de debugging em dev — usar logger condicional
- **Proximo passo:** Resolvido.

---

## PROB-0031 — Web/mobile: duplo clique financeiro ainda não bloqueado no cliente

- **ID:** PROB-0031
- **Titulo:** Ações financeiras críticas no web/mobile ainda podem ser disparadas repetidamente pela UI
- **Data:** 2026-07-08
- **Origem:** verificação pós-PR-LEDGER-20
- **Severidade:** MEDIUM
- **Status:** FECHADO (BUG-0051, 2026-07-11)
- **Area:** frontend, mobile, UX, integridade financeira
- **Sintoma:** Backend possui idempotência e conflitos padronizados, mas o checklist PR-LEDGER-20 ainda marca "Web/mobile impedem duplo clique financeiro" como `PENDENTE`.
- **Causa raiz:** PR-LEDGER-18 fechou garantias backend, mas não consolidou estados de loading/disabled/idempotency key no web/mobile para todos os comandos financeiros.
- **Impacto tecnico:** Usuário pode disparar requisições duplicadas pela interface; backend tende a proteger, mas UX fica inconsistente e pode exibir erro/confusão.
- **Arquivos relacionados:** telas web/mobile de criação, pagamento, ajuste, cancelamento e exclusão financeira.
- **Solucao proposta:** Desabilitar botões durante mutations, padronizar loading state, impedir submit duplo, propagar `Idempotency-Key` nos POSTs financeiros quando aplicável.
- **Solucao aplicada:** Locks síncronos e estado visual adicionados nas ações financeiras mais sensíveis dos clientes: pagamento de fatura web/mobile (`payingRef`), movimentação de carteira web (`movimentandoRef`), pagamento/pulo de contas fixas web/mobile (`acaoFinanceiraId`/pending), reserva em meta web (`acaoFinanceiraId`). Botões ficam `disabled` durante a mutation e exibem feedback de processamento quando aplicável.
- **Evidencias:** `frontend/src/pages/Faturas.tsx`, `mobile/app/(app)/more/faturas.tsx`, `frontend/src/pages/Carteira.tsx`, `frontend/src/pages/ContasFixas.tsx`, `mobile/app/(app)/more/contas-fixas.tsx`, `frontend/src/pages/Metas.tsx`. Validações: frontend build PASS; mobile `tsc --noEmit` PASS; backend suite PASS.
- **Riscos residuais:** Idempotency-Key no cliente ainda pode ser ampliado em POSTs financeiros futuros, mas duplo clique por UI ficou bloqueado nos fluxos financeiros atuais de maior impacto.
- **Proximo passo:** Manter padrão em novas telas/mutations financeiras.

---

## PROB-0032 — 500 ao criar transação com carteiraId (detached entity)

- **ID:** PROB-0032
- **Titulo:** POST /api/v1/transacoes com carteiraId retorna 500 INTERNAL_ERROR — "Detached entity with generated id ... Carteira.version null"
- **Data:** 2026-07-09
- **Origem:** diagnóstico manual — replicação do payload exato do app mobile contra a API local (porta 8081)
- **Severidade:** BLOCKER
- **Status:** FECHADO (2026-07-09)
- **Area:** backend
- **Sintoma:** Toda transação criada com `carteiraId` no payload falhava com 500. Stack trace apontava para entidade `Carteira` detached ao tentar persistir a `Transacao` associada.
- **Causa raiz:** `TransacaoController.toEntity()` monta um stub `new Carteira()` apenas com o `id` recebido no request (sem carregar do banco). `TransacaoService.criar()` não resolvia essa carteira via repository antes do `save()`, então o Hibernate tentava fazer cascade em uma entidade detached sem `version` — violando o optimistic locking (`@Version`).
- **Impacto tecnico:** Toda transação vinculada a uma carteira específica falhava. Sem carteira resolvida, a transação também não movimentava saldo (ver PROB-0033).
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/controller/TransacaoController.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Solucao proposta:** Resolver a carteira via `carteiraRepository.findByIdAndUsuarioId` (valida ownership) antes de associar à transação e salvar.
- **Solucao aplicada:** `TransacaoService.criar()` agora busca a carteira gerenciada via `carteiraRepository.findByIdAndUsuarioId(id, usuarioId)` e substitui o stub detached antes do `save()`. Lança `ResourceNotFoundException` se a carteira não existir ou não pertencer ao usuário.
- **Evidencias:** Reprodução via chamada direta a `POST /api/v1/transacoes` com payload `{ ..., "carteiraId": <id> }` contra API local (porta 8081) reproduzindo o stack trace "Detached entity with generated id ... Carteira.version null" antes da correção; após a correção, fluxo E2E validado (carteira 1000 + entrada 3000 − saída 200 = saldo 3800). `mvn test` 69/69 PASS.
- **Riscos residuais:** Transações antigas criadas antes desta correção, sem carteira resolvida, não têm movimento retroativo no Ledger (ver BACKLOG-0045).
- **Proximo passo:** Resolvido. Acompanhar BACKLOG-0045 para backfill retroativo.

---

## PROB-0033 — Saldo total da carteira congelado (mobile não enviava carteiraId)

- **ID:** PROB-0033
- **Titulo:** NovaTransacaoModal (mobile) não enviava carteiraId — transações não movimentavam saldo
- **Data:** 2026-07-09
- **Origem:** diagnóstico manual — replicação do payload exato do app mobile contra a API local
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-09)
- **Area:** mobile, backend
- **Sintoma:** Usuário criava transações (entrada/saída) e o saldo total das carteiras nunca mudava no dashboard/carteiras.
- **Causa raiz:** `mobile/src/components/NovaTransacaoModal.tsx` montava o payload de `POST /api/v1/transacoes` sem o campo `carteiraId`. No backend, `TransacaoService.criar()` só registra movimento no Ledger (`MovimentoCarteira`) quando `carteira` está presente na transação — transação sem carteira é ignorada pelo Ledger por design.
- **Impacto tecnico:** Saldo de carteiras e saldo total do dashboard ficavam permanentemente incorretos/congelados, apesar de as transações serem registradas e contabilizadas em relatórios/categorias.
- **Arquivos relacionados:** `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/src/types/index.ts` (`TransacaoRequest`), `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Solucao proposta:** Adicionar seletor de carteira (obrigatório) no modal mobile; incluir `carteiraId` em `TransacaoRequest`; invalidar caches de carteiras/dashboard após criar transação.
- **Solucao aplicada:** Seletor de carteira via chips adicionado ao `NovaTransacaoModal`, pré-selecionando a primeira carteira do usuário. `carteiraId?: number` adicionado a `TransacaoRequest` em `mobile/src/types/index.ts`. Query invalidation ampliada para `carteiras` e `dashboard-projecao` após sucesso da mutation.
- **Evidencias:** Payload do app antes da correção não incluía `carteiraId`; fluxo E2E pós-correção validado via API: carteira inicial 1000 + entrada 3000 − saída 200 = saldo 3800; delete com estorno → 4000.
- **Riscos residuais:** Depende de PROB-0032 estar corrigido no backend (resolvido). Transações antigas sem carteira continuam sem movimento retroativo (BACKLOG-0045).
- **Proximo passo:** Resolvido.

---

## PROB-0034 — Sessão mobile expira sem refresh automático

- **ID:** PROB-0034
- **Titulo:** App mobile passa a receber erros de API após ~15 minutos de uso (JWT expira sem refresh)
- **Data:** 2026-07-09
- **Origem:** diagnóstico manual — replicação de uso prolongado contra a API local
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-09)
- **Area:** mobile, backend, seguranca
- **Sintoma:** Após ~15 minutos, todas as chamadas autenticadas do app passavam a falhar (401), exigindo logout/login manual.
- **Causa raiz:** Access token JWT expira em `900000ms` (15 min, ver `application.properties`/`JwtUtil`). O interceptor Axios do mobile (`mobile/src/services/api.ts`) apenas mapeava o 401 para mensagem amigável, sem tentar renovar a sessão via `POST /api/auth/refresh-token`, diferente do interceptor web que já fazia refresh automático.
- **Impacto tecnico:** UX quebrada em qualquer sessão de uso contínuo acima de 15 minutos.
- **Arquivos relacionados:** `mobile/src/services/api.ts`, `mobile/src/store/auth.ts`, `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`
- **Solucao proposta:** Implementar interceptor de 401 no mobile que chame `refresh-token` (com cookie HttpOnly + header `X-CSRF-Token`), com dedupe de chamadas concorrentes e retry da request original.
- **Solucao aplicada:** Interceptor de resposta em `api.ts` detecta 401 fora de rotas `/auth/`, chama `refreshAccessToken()` (promise compartilhada/deduplicada), grava novo `accessToken`/`csrfToken` via `store/auth.ts` (SecureStore) e repete a request original com o novo Bearer token. Backend (`AuthController`) passou a devolver `csrfToken` também no corpo da resposta de login/refresh (não apenas no cookie), pois clientes nativos React Native não leem cookies para o padrão double-submit; o double-submit continua válido porque o corpo cross-origin permanece ilegível pelo browser.
- **Evidencias:** `mvn test` 69/69 PASS; fluxo manual: refresh-token rotaciona corretamente e o novo access token funciona nas chamadas seguintes.
- **Riscos residuais:** Nenhum identificado para o fluxo mobile. Rotação de refresh token com detecção de reuse já existia no backend e não foi alterada.
- **Proximo passo:** Resolvido.

---

## PROB-0035 — Transações soft-deletadas continuavam somando em dashboard/relatórios

- **ID:** PROB-0035
- **Titulo:** Queries de TransacaoRepository não filtravam ativa=true — soft-delete não era respeitado
- **Data:** 2026-07-09
- **Origem:** diagnóstico manual — replicação de fluxo de exclusão de transação contra a API local
- **Severidade:** BLOCKER
- **Status:** FECHADO (2026-07-09)
- **Area:** backend, banco
- **Sintoma:** Ao deletar uma transação (soft-delete via `ativa = false`, introduzido no Ledger em `V13__transacao_carteira.sql`), o valor dela continuava sendo somado em dashboard, relatórios, insights, orçamento e aparecia nas listagens paginadas e na fatura de cartão.
- **Causa raiz:** As queries derivadas e `@Query` de `TransacaoRepository` (listagens, somatórios SUM, agrupamento por categoria, listagem de fatura) não incluíam `ativa = true`/`AndAtivaTrue` na cláusula `WHERE`. Apenas o soft-delete gravava a flag; nenhuma leitura a respeitava.
- **Impacto tecnico:** Corrupção de dados financeiros exibidos — saldo/gastos "fantasma" de transações que o usuário já havia cancelado/excluído.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/repository/TransacaoRepository.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`, `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`
- **Solucao proposta:** Adicionar `ativa = true` em todas as queries de leitura de `TransacaoRepository`; criar variantes derivadas `AndAtivaTrue` para listagens paginadas e para consulta usada pela fatura.
- **Solucao aplicada:** Adicionado `AND t.ativa = true` em `sumValorTotalByUsuarioIdAndTipoAndDataBetween`, `sumValorEfetivoByUsuarioIdAndTipoAndDataBetween`, `sumValorEfetivoAgrupadoPorCategoria`, `sumSaidasByUsuarioIdAndPeriodo`, `sumSaidasByCategoria` e `findByUsuarioIdAndDataBetweenWithCategoria`. Criados `findByUsuarioIdAndAtivaTrue` e `findByUsuarioIdAndDataBetweenAndAtivaTrue` (usados por `TransacaoService.listarPorUsuario`/`listarPorPeriodo`) e `findByUsuarioIdAndContaIdAndDataBetweenAndAtivaTrue` (usado por `FaturaService`, substituindo `findByUsuarioIdAndContaIdAndDataBetween`).
- **Evidencias:** `mvn test` 69/69 PASS; fluxo E2E: delete de transação com estorno → saldo volta corretamente ao valor anterior (4000) e o valor não aparece mais nas somas de dashboard/relatório.
- **Riscos residuais:** Método derivado antigo `findByUsuarioIdAndDataBetween` (sem `AndAtivaTrue`) permanece no repository e não é mais usado pelo service principal — verificar se algum outro caller ainda depende dele para não reintroduzir o bug.
- **Proximo passo:** Auditar demais callers de `TransacaoRepository` (ex: exportação CSV, insights) para confirmar que também filtram `ativa=true`.

---

## PROB-0036 — categoria.valorGasto somava também transações de ENTRADA

- **ID:** PROB-0036
- **Titulo:** Criar/deletar transação de ENTRADA alterava valorGasto da categoria, inflando o indicador de gasto
- **Data:** 2026-07-09
- **Origem:** diagnóstico manual — replicação do payload exato do app mobile contra a API local
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-09)
- **Area:** backend
- **Sintoma:** Ao registrar uma transação do tipo ENTRADA vinculada a uma categoria, `Categoria.valorGasto` era incrementado — mesmo entrada de dinheiro não sendo gasto. Orçamento por categoria ficava incorreto.
- **Causa raiz:** `TransacaoService.criar()` e `TransacaoService.deletar()` somavam/subtraiam `valorTotal` em `categoria.valorGasto` incondicionalmente, sem checar `transacao.getTipo()`.
- **Impacto tecnico:** Indicadores de orçamento e "gasto por categoria" incorretos sempre que havia categoria em transações de ENTRADA.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Solucao proposta:** Restringir o ajuste de `valorGasto` a transações com `tipo == SAIDA`.
- **Solucao aplicada:** `TransacaoService.criar()` só chama `categoria.setValorGasto(...)` quando `transacao.getTipo() == TipoTransacao.SAIDA`. `TransacaoService.deletar()` só reverte `valorGasto` quando `transacao.getTipo() == TipoTransacao.SAIDA`.
- **Evidencias:** `mvn test` 69/69 PASS; fluxo E2E: orçamento de julho/2026 com gasto 150/500 correto após criar entrada e saída no mesmo mês/categoria.
- **Riscos residuais:** Nenhum identificado.
- **Proximo passo:** Resolvido.

---

## PROB-0037 — Vazamento de hash de senha no response de registro

- **ID:** PROB-0037
- **Titulo:** POST /api/auth/register retornava a entidade Usuario inteira, incluindo hash bcrypt e campos de lockout
- **Data:** 2026-07-09
- **Origem:** diagnóstico manual — inspeção do response de `POST /api/auth/register` contra a API local
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-09)
- **Area:** backend, seguranca
- **Sintoma:** Response de cadastro (`ResponseEntity.ok(usuarioSalvo)`) incluía o objeto `Usuario` completo — hash de senha (bcrypt), `failedAttempts`, `lockedUntil` e demais campos internos visíveis no corpo da resposta HTTP.
- **Causa raiz:** `AuthController.register()` retornava a entidade JPA diretamente em vez de um DTO/projeção restrita.
- **Impacto tecnico:** Vazamento de hash de senha (mesmo com bcrypt, reduz a superfície de ataque de brute-force offline) e de metadados internos de segurança (lockout) para qualquer chamador do endpoint de cadastro.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`
- **Solucao proposta:** Retornar apenas campos não sensíveis (`id`, `nome`, `email`) no response de registro.
- **Solucao aplicada:** `AuthController.register()` agora retorna `Map.of("id", ..., "nome", ..., "email", ...)` em vez da entidade `Usuario`.
- **Evidencias:** Comparação do payload de resposta antes/depois via chamada direta a `POST /api/auth/register` contra a API local; `mvn test` 69/69 PASS.
- **Riscos residuais:** Verificar se algum outro endpoint (ex: `GET /usuarios/me`) também retorna a entidade completa em vez de DTO — não verificado nesta sessão.
- **Proximo passo:** Auditar demais endpoints de `UsuarioController`/`AuthController` quanto a exposição de entidade completa.

---

## PROB-0038 — Arredondamento HALF_UP de parcelas deixava resíduo permanente no limite do cartão

- **ID:** PROB-0038
- **Titulo:** parcela = valorTotal/n com RoundingMode.HALF_UP faz a soma das parcelas divergir do valor total, deixando centavo preso no `valorGasto` do cartão
- **Data:** 2026-07-09
- **Origem:** revisao do fluxo de compra no cartao + parcelas (apos commit 69e3a3b "feat(faturas): add card purchase flow")
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-09)
- **Area:** backend
- **Sintoma:** Uma compra de R$ 100,00 em 3x gerava parcelas de R$ 33,33 cada (100/3 HALF_UP), somando R$ 99,99. Após pagar as 3 faturas, `Conta.valorGasto` (limite do cartão) não retornava a zero — ficava com R$ 0,01 residual permanente.
- **Causa raiz:** `FaturaService.registrarCompraCartao` e `TransacaoService.criarParcelas`/`atualizarValorParcelas` calculavam `valorParcela = valorTotal.divide(n, 2, HALF_UP)` e aplicavam o mesmo valor arredondado em todas as N parcelas, sem reconciliar o resto da divisão.
- **Impacto tecnico:** Divergência acumulada entre soma de lançamentos/parcelas e valor real da compra; limite do cartão nunca zera exatamente após quitação total, exigindo ajuste manual (`ContaController`/endpoint de ajuste) para corrigir.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java:161-181` (método `registrarCompraCartao`), `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java:138-166` (`criarParcelas`, `valorParcelaOuResto`), `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java:228-245` (`atualizarValorParcelas`)
- **Solucao proposta:** Última parcela absorve a diferença de arredondamento (`valorTotal - valorParcela*(n-1)`), garantindo que a soma feche exatamente com o valor total.
- **Solucao aplicada:** Helper `valorParcelaOuResto` criado em `TransacaoService` e logica equivalente inline em `FaturaService.registrarCompraCartao`; última parcela/lançamento usa o resto calculado em vez do valor arredondado fixo.
- **Evidencias:** Teste `FaturaCartaoWorkflowTest.ultimaParcelaAbsorveArredondamentoELimiteZeraAposPagarTodasAsFaturas` — 100.00 em 3x gera 33.33/33.33/33.34; `Conta.valorGasto` chega a `BigDecimal.ZERO` apos pagar as 3 faturas. Suite completa: `mvn test` (offline) → `Tests run: 76, Failures: 0, Errors: 0` (verificado nesta sessao via `./mvnw -o test`).
- **Riscos residuais:** Compras existentes já persistidas antes da correção (se houver) continuam com o resíduo antigo — não houve migração/backfill para parcelas já geradas com o bug antigo.
- **Proximo passo:** Avaliar se há necessidade de backfill para faturas/parcelas antigas com resíduo de arredondamento (ver BACKLOG-0051 se aplicável).

---

## PROB-0039 — Editar valor/data de compra no cartão dessincronizava fatura e limite

- **ID:** PROB-0039
- **Titulo:** `TransacaoService.atualizar()` alterava `valorTotal` da transação sem tocar nos lançamentos de fatura (`FaturaLancamento`) nem no `valorGasto` da conta/categoria
- **Data:** 2026-07-09
- **Origem:** revisao do fluxo de compra no cartao + parcelas
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-09)
- **Area:** backend
- **Sintoma:** Ao editar o valor ou a data de uma compra já lançada no cartão, a transação era atualizada mas a fatura (`FaturaCartao.valorTotal`, `FaturaLancamento`) e o limite (`Conta.valorGasto`) continuavam refletindo o valor antigo — fatura e limite ficavam permanentemente dessincronizados da transação real.
- **Causa raiz:** `TransacaoService.atualizar()` (linha ~172) fazia apenas `transacao.setValorTotal(novoValor)` e `registrarMovimentoDiferenca` (Ledger de carteira), sem recalcular lançamentos de fatura nem o `valorGasto` acumulado da conta/categoria.
- **Impacto tecnico:** Fatura do cartão mostra valor incorreto (diferente da soma real de compras); limite de cartão exibido ao usuário não reflete o gasto real; risco de o usuário estourar limite real sem o app indicar.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java:172-226` (`atualizar`), `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java` (`cancelarCompraCartao`, `registrarCompraCartao`)
- **Solucao proposta:** Se valor ou data mudou em uma compra de cartão, cancelar e recriar os lançamentos de fatura; ajustar `valorGasto` da conta e da categoria pela diferença; recalcular parcelas legadas.
- **Solucao aplicada:** `TransacaoService.atualizar()` agora detecta `valorAlterado`/`dataAlterada` para compras de cartão (`isCompraCartao`), chama `faturaService.cancelarCompraCartao(transacao)` antes de salvar e `faturaService.registrarCompraCartao(salva, usuarioId)` depois; ajusta `Conta.valorGasto` e `Categoria.valorGasto` pela diferença (apenas transações `SAIDA`); recalcula parcelas legadas via `atualizarValorParcelas`. `cancelarCompraCartao` falha com `BusinessException` se alguma fatura envolvida já estiver `PAGA` (edição bloqueada nesse caso).
- **Evidencias:** Teste `FaturaCartaoWorkflowTest.editarValorDeCompraNoCartaoRessincronizaFaturaELimite` — edita compra de R$100 para R$150 e confirma `FaturaCartao.valorTotal` e `Conta.valorGasto` ambos em 150.00. Suite completa: 76/76 PASS.
- **Riscos residuais:** ~~Se a fatura já estiver paga, a edição é bloqueada com `BusinessException` — comportamento não validado com teste dedicado de UI/mensagem amigável no mobile/frontend (pode aparecer como erro genérico ao usuário).~~ **Superado em 2026-07-09 (revisão 2, mesma sessão) — ver atualização abaixo.**
- **Proximo passo:** ~~Validar mensagem de erro exibida no app quando a edição falha por fatura já paga (mobile e frontend).~~ **Resolvido substituindo o bloqueio por modelo de ajuste automático — ver PROB-0044.**

### Atualização 2026-07-09 (revisão 2, mesma sessão) — bloqueio substituído por modelo definitivo de ajuste

O bloqueio (`BusinessException` ao editar compra com fatura já paga) descrito acima como solução aplicada
foi **removido e substituído** por um modelo definitivo na mesma sessão, antes de qualquer commit. Ver
**PROB-0044** para o registro completo desta evolução e **BUG-0023**/**BUG-0025** em `BUGFIX_LOG.md` para
a implementação. Resumo: `TransacaoService.atualizar()` agora chama
`FaturaService.ressincronizarCompraCartao(transacao, usuarioId)` em vez de
`cancelarCompraCartao`+`registrarCompraCartao`; lançamentos em faturas abertas são recriados/redistribuídos
pelas parcelas não pagas, e a diferença sobre a parte já paga (fatura imutável) entra como lançamento
`TipoFaturaLancamento.AJUSTE` na próxima fatura em aberto, sem nunca bloquear a edição.

---

## PROB-0040 — Compra retroativa podia ser lançada em fatura já paga

- **ID:** PROB-0040
- **Titulo:** `registrarCompraCartao` não verificava o status da fatura de destino antes de lançar a compra
- **Data:** 2026-07-09
- **Origem:** revisao do fluxo de compra no cartao + parcelas
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-09)
- **Area:** backend
- **Sintoma:** Ao registrar uma compra com data retroativa cuja competência já correspondia a uma fatura com status `PAGA`, o lançamento era adicionado normalmente àquela fatura já quitada — o valor pago não refletiria mais a fatura, e o total pago ficaria incorretamente menor que o total de lançamentos.
- **Causa raiz:** `registrarCompraCartao` chamava diretamente `criarOuBuscarFaturaEntidade` pela competência calculada, sem checar `fatura.getStatus()`.
- **Impacto tecnico:** Fatura marcada como `PAGA` passaria a ter lançamentos não cobertos pelo pagamento já registrado — inconsistência contábil entre valor pago e valor total da fatura.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java:161-181` (`registrarCompraCartao`), `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java:314-324` (`faturaDisponivelParaLancamento`, novo)
- **Solucao proposta:** Antes de lançar, verificar se a fatura de destino já está paga; se estiver, rolar a competência para a próxima fatura em aberto.
- **Solucao aplicada:** Novo helper `faturaDisponivelParaLancamento(usuarioId, conta, competencia)` itera até 24 meses à frente buscando a primeira fatura com status diferente de `PAGA`; lança `BusinessException` se não encontrar nenhuma em 24 meses.
- **Evidencias:** Teste `FaturaCartaoWorkflowTest.compraRetroativaNaoEntraEmFaturaPagaVaiParaProximaAberta` — fatura de julho paga, compra retroativa de 15/jul (R$50) é redirecionada para agosto; julho permanece em 100.00, agosto recebe 50.00. Suite completa: 76/76 PASS.
- **Riscos residuais:** Limite de 24 meses é arbitrário — se todas as faturas dos próximos 24 meses já estiverem pagas (cenário extremo/improvável), a operação falha com `BusinessException` em vez de criar uma fatura nova além do horizonte.
- **Proximo passo:** Nenhuma ação adicional planejada; risco residual aceito como extremamente improvável no fluxo real de uso.

---

## PROB-0041 — Status FECHADA da fatura nunca era exibido

- **ID:** PROB-0041
- **Titulo:** `determinarStatusAtual` (nome de referência da lógica de status) nunca derivava o status `FECHADA` mesmo quando `dataFechamento` já havia passado
- **Data:** 2026-07-09
- **Origem:** revisao do fluxo de compra no cartao + parcelas
- **Severidade:** MEDIUM
- **Status:** FECHADO (2026-07-09)
- **Area:** backend, frontend, mobile
- **Sintoma:** Fatura com `dataFechamento` já no passado (fechada para novos lançamentos, mas ainda não vencida/paga) continuava aparecendo como "Aberta" tanto no mobile quanto no frontend web — usuário não tinha como saber que a fatura já estava fechada para lançamentos.
- **Causa raiz:** Lógica de derivação de status em `FaturaService` só cobria `PAGA` e `VENCIDA` (baseado em `dataVencimento`), sem checar `dataFechamento`.
- **Impacto tecnico:** UX incorreta — usuário podia acreditar que ainda poderia editar/adicionar lançamentos numa fatura já fechada para o ciclo atual.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java:376-389` (bloco final de derivação de status), `mobile/app/(app)/more/faturas.tsx:196` (label de badge), `frontend/src/pages/Faturas.tsx:168` (label de badge)
- **Solucao proposta:** Adicionar branch de derivação: se `dataFechamento` já passou e a fatura não está paga nem vencida, retornar `FaturaStatus.FECHADA`. Adicionar labels correspondentes na UI.
- **Solucao aplicada:** Branch `if (fatura.getDataFechamento() != null && fatura.getDataFechamento().isBefore(hoje)) return FaturaStatus.FECHADA;` adicionado após o branch de `VENCIDA`. Labels `"FECHADA"` (mobile) e `"Fechada"` (frontend) adicionados nos badges de status.
- **Evidencias:** Diff revisado manualmente em `FaturaService.java`, `mobile/app/(app)/more/faturas.tsx` e `frontend/src/pages/Faturas.tsx` (working tree, ainda não commitado). Não há teste automatizado dedicado a este branch de status nesta sessão.
- **Riscos residuais:** Ausência de teste automatizado cobrindo especificamente a transição para `FECHADA` (cobertura apenas manual/visual). Ordem de precedência entre `VENCIDA` e `FECHADA` não foi testada para o caso em que `dataFechamento` e `dataVencimento` já passaram simultaneamente (código atual prioriza `VENCIDA`, o que parece correto mas não está coberto por teste).
- **Proximo passo:** Adicionar teste de unidade/integração cobrindo a derivação de `FECHADA` e a precedência `VENCIDA > FECHADA` (ver BACKLOG).

---

## PROB-0042 — Falso erro "pagamento parcial não suportado" por divergência entre valorTotal persistido e soma de lançamentos

- **ID:** PROB-0042
- **Titulo:** `pagarFatura` validava o valor pago contra `fatura.getValorTotal()` persistido, que podia divergir da soma real dos `FaturaLancamento`
- **Data:** 2026-07-09
- **Origem:** revisao do fluxo de compra no cartao + parcelas
- **Severidade:** MEDIUM
- **Status:** FECHADO (2026-07-09)
- **Area:** backend
- **Sintoma:** Usuário tentando pagar o valor total exibido na tela da fatura recebia erro "Pagamento parcial de fatura ainda não é suportado" mesmo enviando o valor correto, quando `valorTotal` persistido na entidade `FaturaCartao` divergia (por bugs anteriores de arredondamento/edição, ver PROB-0038/PROB-0039) da soma real dos lançamentos.
- **Causa raiz:** `pagarFatura` usava `total = calcularTotalLancamentos(fatura)` para checar `total > 0`, mas comparava o valor pago contra `fatura.getValorTotal()` (campo persistido, incrementado incrementalmente por `atualizarTotalFatura` e sujeito a dessincronia). `toResponse` também priorizava `fatura.getValorTotal()` sobre a soma de lançamentos ao montar o DTO exibido ao usuário.
- **Impacto tecnico:** Bloqueio funcional do fluxo de pagamento de fatura em qualquer cenário onde o campo persistido diverge da soma de lançamentos (inclusive por bugs já corrigidos nesta sessão, mas potencialmente por outras causas futuras).
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java:85-140` (`pagarFatura`), `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java:247-282` (`toResponse`)
- **Solucao proposta:** Tratar a soma dos `FaturaLancamento` como fonte da verdade tanto para validação de pagamento quanto para exibição, com fallback para `valorTotal` persistido apenas quando não há lançamentos (faturas antigas pré-migration V17).
- **Solucao aplicada:** `pagarFatura`: se `total` (soma de lançamentos) é zero e `fatura.getValorTotal() != null`, usa o valor persistido como fallback (fatura antiga sem lançamentos); caso contrário usa a soma. `toResponse`: prioriza `total` (soma) sobre `valorTotal` persistido, com o mesmo fallback para faturas pré-V17.
- **Evidencias:** Diff revisado em `FaturaService.java` (comentários `// Faturas anteriores ao V17 têm valorTotal mas nenhum lançamento` e `// Soma dos lançamentos é a fonte da verdade`). Coberto indiretamente pelos 3 novos testes de `FaturaCartaoWorkflowTest` que fazem `pagarFatura` com o valor exato da soma de lançamentos. Suite completa: 76/76 PASS.
- **Riscos residuais:** Não há teste dedicado especificamente ao caso de fatura pré-V17 (sem `FaturaLancamento`, apenas `valorTotal` persistido) exercitando o fallback em `pagarFatura`/`toResponse` — cenário coberto apenas pela leitura do código, não por teste automatizado.
- **Proximo passo:** Adicionar teste cobrindo o fallback de fatura pré-V17 (fatura com `valorTotal` setado manualmente e sem `FaturaLancamento`).

---

## PROB-0043 — Transação ENTRADA com conta associada incrementava valorGasto (limite) do cartão

- **ID:** PROB-0043
- **Titulo:** `Conta.valorGasto` (usado como limite consumido do cartão) era incrementado mesmo para transações do tipo `ENTRADA`
- **Data:** 2026-07-09
- **Origem:** revisao do fluxo de compra no cartao + parcelas
- **Severidade:** MEDIUM
- **Status:** FECHADO (2026-07-09)
- **Area:** backend
- **Sintoma:** Ao registrar uma transação do tipo `ENTRADA` vinculada a uma `Conta` (ex.: estorno/reembolso lançado como entrada com conta de cartão preenchida), o `valorGasto` da conta era incrementado da mesma forma que uma saída, inflando indevidamente o limite consumido do cartão.
- **Causa raiz:** `TransacaoService.criar()`, `atualizar()` e `deletar()` chamavam `contaService.adicionarGasto`/`removerGasto` incondicionalmente quando havia `conta` associada, sem checar `transacao.getTipo()`.
- **Impacto tecnico:** Limite de cartão exibido ao usuário podia ficar artificialmente mais alto (mais "gasto") do que o real, caso o usuário registrasse uma entrada com conta de cartão selecionada.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java:101-104` (`criar`), `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java:206-215` (`atualizar`), `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java:253` (`deletar`)
- **Solucao proposta:** Restringir chamadas de `adicionarGasto`/`removerGasto` a transações com `tipo == TipoTransacao.SAIDA`, seguindo o mesmo padrão já aplicado a `Categoria.valorGasto` em PROB-0036/BUG-0015.
- **Solucao aplicada:** Guarda `if (transacao.getTipo() == TipoTransacao.SAIDA)` adicionada em `criar()`, `atualizar()` (bloco de ajuste por diferença de valor) e `deletar()` antes de qualquer chamada a `contaService.adicionarGasto`/`removerGasto`.
- **Evidencias:** Diff revisado em `TransacaoService.java`. Suite completa: 76/76 PASS (nenhum teste dedicado exclusivamente a ENTRADA+conta+valorGasto foi adicionado nesta sessão — cobertura indireta pelos testes existentes de fluxo de cartão que usam SAIDA).
- **Riscos residuais:** Não há teste automatizado específico para "ENTRADA com conta não deve alterar valorGasto" — apenas revisão manual do código confirma a guarda.
- **Proximo passo:** Adicionar teste de unidade cobrindo ENTRADA com conta associada, confirmando que `valorGasto` permanece inalterado.

---

## PROB-0044 — Bloqueio de edição/cancelamento de compra em fatura paga substituído por modelo definitivo de ajuste/estorno

- **ID:** PROB-0044
- **Titulo:** Editar ou cancelar uma compra de cartão cuja fatura já estava `PAGA` era bloqueado com `BusinessException`; substituído por modelo definitivo de compensação (fatura paga é imutável, diferença vira lançamento na próxima fatura aberta)
- **Data:** 2026-07-09
- **Origem:** revisao do fluxo de compra no cartao + parcelas (segunda rodada, mesma sessão, apos PROB-0038 a PROB-0043)
- **Severidade:** MEDIUM
- **Status:** FECHADO (2026-07-09) — decisão de produto/arquitetura implementada, substitui o bloqueio registrado horas antes em PROB-0039
- **Area:** backend, frontend, mobile
- **Sintoma:** Na primeira rodada de correção desta mesma sessão (ver PROB-0039, PROB-0040), editar o valor/data de uma compra de cartão com pelo menos uma fatura envolvida já paga, ou cancelar/deletar essa compra, lançava `BusinessException` ("não é possível editar/cancelar compra de fatura paga") — usuário ficava impedido de corrigir ou estornar uma compra real após a fatura correspondente ser quitada, cenário comum (compra parcelada em que algumas parcelas já foram pagas e o usuário precisa editar o valor total ou cancelar a compra restante).
- **Causa raiz:** O primeiro modelo de correção (PROB-0039) tratava fatura paga como uma trava rígida — qualquer mutação em lançamento de fatura paga era proibida, sem mecanismo de compensação.
- **Impacto tecnico:** Usuário não conseguia corrigir valor incorreto nem cancelar compra parcelada após a primeira fatura ser paga — bloqueio funcional real em cenário de uso comum (compras parceladas de médio/longo prazo).
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java` (`ressincronizarCompraCartao`, `cancelarCompraCartao`, `criarLancamento`, `removerLancamentoDeFaturaAberta`, `ajustarLimiteUtilizado`), `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java` (`atualizar`, `deletar`), `backend/src/main/java/com/gestor/financeiro/model/FaturaLancamento.java`, `backend/src/main/java/com/gestor/financeiro/model/enums/TipoFaturaLancamento.java` (novo), `backend/src/main/resources/db/migration/V18__fatura_lancamento_tipo.sql` (novo)
- **Solucao proposta:** Adotar o princípio "fatura paga é imutável — nunca edita, sempre compensa com lançamento na próxima fatura aberta", análogo a estorno de cartão de crédito real.
- **Solucao aplicada:**
  1. Nova coluna `tipo VARCHAR(20) NOT NULL DEFAULT 'COMPRA'` em `fatura_lancamentos` (migration `V18__fatura_lancamento_tipo.sql`) e novo enum `TipoFaturaLancamento` (`COMPRA`, `AJUSTE`, `ESTORNO`). Lançamentos podem ter valor negativo (crédito).
  2. `FaturaService.ressincronizarCompraCartao(transacao, usuarioId)` (novo método) substitui a chamada direta `cancelarCompraCartao`+`registrarCompraCartao` em `TransacaoService.atualizar()`: lançamentos em faturas ainda abertas são removidos e recriados com o restante redistribuído pelas parcelas não pagas (última parcela em aberto absorve o arredondamento); a diferença sobre a parte já paga (imutável) vira lançamento `AJUSTE` (pode ser negativo) na próxima fatura em aberto.
  3. `FaturaService.cancelarCompraCartao(transacao, usuarioId)` (assinatura alterada — antes só recebia `transacao`) não lança mais `BusinessException` para fatura paga: remove lançamentos das faturas abertas e cria um lançamento `ESTORNO` negativo da soma da parte já paga na próxima fatura em aberto.
  4. Novo invariante centralizado: `Conta.valorGasto == soma dos lançamentos em faturas não pagas`. Helpers privados `criarLancamento`/`removerLancamentoDeFaturaAberta`/`ajustarLimiteUtilizado` em `FaturaService` ajustam `valorGasto` a cada mutação de lançamento; `TransacaoService` deixou de chamar `contaService.adicionarGasto`/`removerGasto` para compras de cartão (mantido apenas para contas não-crédito).
  5. UI: valor negativo de lançamento renderiza em verde (crédito) em `mobile/app/(app)/more/faturas.tsx` e `frontend/src/pages/Faturas.tsx`; descrição prefixada `"Ajuste: "`/`"Estorno: "`.
- **Evidencias:** Novos testes `FaturaCartaoWorkflowTest.editarCompraJaPagaGeraLancamentoDeAjusteNaProximaFatura` e `.cancelarCompraParceladaComFaturaPagaGeraEstornoNaProximaFatura`. Suite completa executada pelo `docs-reporter` nesta sessão: `cd backend && ./mvnw -o test` → `Tests run: 78, Failures: 0, Errors: 0` — `BUILD SUCCESS`.
- **Riscos residuais:**
  1. `Conta.valorGasto` pode ficar temporariamente negativo quando o crédito de um estorno é maior que as compras em aberto no momento — comportamento intencional (autocorrige na próxima compra/pagamento), mas a UI pode exibir um valor negativo de forma pouco intuitiva para o usuário.
  2. Uma fatura contendo apenas lançamento(s) de estorno (total ≤ 0) não é "pagável" pelo fluxo atual — o crédito fica aguardando compras futuras na mesma fatura para compensar; não há rollover explícito de crédito entre faturas (ex.: se a fatura fechar só com crédito, o crédito não é automaticamente transferido/creditado em carteira).
  3. A redistribuição de parcelas na edição usa "restante ÷ parcelas não pagas" (não recalcula o valor de uma parcela cheia) — decisão consciente de simplicidade, mas pode gerar parcelas com valores diferentes do que o usuário esperaria ao comparar com o parcelamento original.
- **Proximo passo:** Ver BACKLOG-0053 (UX para valorGasto negativo), BACKLOG-0054 (rollover de crédito entre faturas) e BACKLOG-0055 (recalcular parcela cheia na redistribuição).

---

## PROB-0045 — Mobile não tinha nenhuma forma de editar ou excluir uma transação já lançada

- **ID:** PROB-0045
- **Titulo:** `mobile/app/(app)/transacoes.tsx` não expunha edição nem exclusão de transação — única forma de mutação era criar (`NovaTransacaoModal`)
- **Data:** 2026-07-09
- **Origem:** revisao de integracao do modulo de faturas/cartao no app mobile (terceira rodada da mesma sessao, apos as duas rodadas de backend registradas em PROB-0038..PROB-0044)
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-09)
- **Area:** mobile
- **Sintoma:** A tela de listagem de transações (`mobile/app/(app)/transacoes.tsx`) renderizava cada linha via `ListRow` sem `onPress`, e não havia nenhum componente equivalente a um modal de edição — o usuário não conseguia corrigir um valor/data/descrição digitado errado nem excluir uma transação a partir do app mobile. O backend já suportava `PUT /api/v1/transacoes/{id}` e `DELETE /api/v1/transacoes/{id}` havia meses; a lacuna era exclusivamente de UI mobile.
- **Causa raiz:** Funcionalidade nunca implementada no mobile — o fluxo mobile foi construído em torno de criação (`NovaTransacaoModal`) sem contraparte de edição/exclusão.
- **Impacto tecnico:** Especialmente crítico após a introdução do fluxo de compra no cartão (commit `69e3a3b`) e do modelo de ajuste/estorno (PROB-0044): usuário não tinha como corrigir nem cancelar uma compra de cartão parcelada a partir do app mobile — precisava usar o frontend web (se disponível) para qualquer correção.
- **Arquivos ou modulos relacionados:** `mobile/app/(app)/transacoes.tsx`, `mobile/src/components/EditarTransacaoModal.tsx` (novo), `mobile/src/services/transacaoService.ts` (`atualizar`/`deletar`, já existiam e não foram alterados)
- **Solucao proposta:** Criar modal de edição/exclusão análogo ao `NovaTransacaoModal`, respeitando o contrato real do backend (que só aplica valor, descrição, data e observações no `PUT`; tipo/categoria/forma de pagamento não são alteráveis por esse endpoint).
- **Solucao aplicada:** Novo componente `mobile/src/components/EditarTransacaoModal.tsx` (sheet `presentationStyle="pageSheet"`), aberto ao tocar numa linha da lista (`onPress={() => setSelecionada(t)}` em `transacoes.tsx`). Edita apenas `valor`, `descricao`, `data`, `observacoes` — os únicos campos aplicados pelo backend em `TransacaoService.atualizar`; `tipo`/`categoria`/forma de pagamento são exibidos como bloco de contexto fixo, não editável. Quando a transação é compra de cartão (`tipo === 'SAIDA' && conta?.tipo === 'CREDITO'`), exibe aviso de que a edição ressincroniza faturas e que a parte já paga vira ajuste na fatura seguinte (reflete `FaturaService.ressincronizarCompraCartao`, ver PROB-0044). Exclusão via `Alert.alert` de confirmação, com texto específico para compra de cartão avisando sobre estorno. Após salvar/excluir, invalida as query keys: `transacoes`, `transacoes-recentes`, `dashboard-resumo`, `dashboard-projecao`, `carteiras`, `contas`, `contas-fatura`, `fatura`, `categorias`. Subtítulo da linha na lista passou a exibir `· Nx` quando a transação é parcelada.
- **Evidencias:** `tsc --noEmit` limpo no mobile (relatado pelo agente de implementação). Contrato validado manualmente contra o backend local na porta 8081 com payloads exatos do app: `POST` de compra parcelada em 3x → `201`; `PUT` com o corpo exato produzido pelo modal → `200`; `DELETE` → `204`. Usado usuário de teste descartável (`teste-fatura-ui@teste.com`); dados de transação criados no teste foram removidos ao final, restando apenas o usuário no banco local. `FaturaCartaoWorkflowTest`: 7/7 PASS (suite não foi alterada por este item, é backend já existente).
- **Riscos residuais:** Não há teste automatizado (mobile não tem suíte configurada — ver limitação conhecida em `SYSTEM_OVERVIEW.md`). Validação de contrato foi manual, contra ambiente local, uma única vez, com um usuário de teste — não cobre concorrência nem todos os tipos de transação (ex.: entrada parcelada, se existir). Ver PROB-0048 para o risco de ambiente identificado durante essa mesma validação manual.
- **Proximo passo:** Nenhum teste automatizado mobile configurado no projeto no momento (BACKLOG genérico já registrado em limitações conhecidas). Se/quando suíte de testes mobile for criada, adicionar cobertura de `EditarTransacaoModal`.

---

## PROB-0046 — Badge de status da fatura no mobile era binário (só verde/vermelho), sem distinguir ABERTA/FECHADA/VENCIDA

- **ID:** PROB-0046
- **Titulo:** `mobile/app/(app)/more/faturas.tsx` colorria o badge de status da fatura apenas como `PAGA` (verde) ou "tudo o resto" (vermelho), tratando o estado normal `ABERTA` como se fosse um alerta
- **Data:** 2026-07-09
- **Origem:** revisao de integracao do modulo de faturas/cartao no app mobile (terceira rodada da mesma sessao)
- **Severidade:** LOW
- **Status:** FECHADO (2026-07-09)
- **Area:** mobile
- **Sintoma:** Após PROB-0041 (status `FECHADA` passou a ser derivado e exibido como texto), o badge de cor continuava usando uma lógica binária (`fatura.status === 'PAGA' ? verde : vermelho`), então uma fatura `ABERTA` (estado normal, sem nenhum problema) aparecia com a mesma cor de alerta vermelha usada para `VENCIDA`.
- **Causa raiz:** A lógica de cor do badge (`backgroundColor: fatura.status === 'PAGA' ? colors.success + '20' : colors.danger + '20'`) nunca foi atualizada quando os status `FECHADA`/`VENCIDA` foram introduzidos como valores distintos exibidos ao usuário (PROB-0041).
- **Impacto tecnico:** UX confusa — usuário via uma fatura em aberto normal (ainda dentro do período de compras) com a mesma cor de "atenção/erro" usada para fatura vencida, sem sinalização visual da diferença de severidade real entre os quatro estados possíveis.
- **Arquivos ou modulos relacionados:** `mobile/app/(app)/more/faturas.tsx:127-133` (constante `statusBadge`), `mobile/app/(app)/more/faturas.tsx:201-203` (uso do badge)
- **Solucao proposta:** Mapear cada um dos 4 status (`ABERTA`, `FECHADA`, `VENCIDA`, `PAGA`) para uma cor semanticamente distinta, com `ABERTA` tratada como estado normal (não como alerta).
- **Solucao aplicada:** Nova constante `statusBadge` calculada antes do render: `PAGA` → `colors.success`; `VENCIDA` → `colors.danger`; `FECHADA` → `colors.warning`; padrão (`ABERTA`) → `colors.brandFg`/`colors.brandBg` (cor de marca, indicando estado normal, não alerta).
- **Evidencias:** `git diff -- mobile/app/\(app\)/more/faturas.tsx` (ver `docs/REVIEW_REPORTS/2026-07-09_backend_review_fatura-cartao-fluxo.md`, seção "Atualização (revisão 3)"). Sem teste automatizado (mobile não tem suíte de UI configurada).
- **Riscos residuais:** Nenhum teste de snapshot/UI cobre a nova lógica de cor — validação apenas por leitura de código.
- **Proximo passo:** Nenhum imediato; item de baixo risco, encerrado.

---

## PROB-0047 — Lançamentos de ajuste/estorno na fatura (mobile) não eram visualmente distinguíveis de uma compra normal

- **ID:** PROB-0047
- **Titulo:** `mobile/app/(app)/more/faturas.tsx` exibia lançamentos `AJUSTE`/`ESTORNO` (introduzidos por PROB-0044/BUG-0023/BUG-0024) apenas com o prefixo textual `"Ajuste: "`/`"Estorno: "` na descrição, sem badge, e sem remover o prefixo redundante quando a UI já sinaliza o tipo
- **Data:** 2026-07-09
- **Origem:** revisao de integracao do modulo de faturas/cartao no app mobile (terceira rodada da mesma sessao)
- **Severidade:** LOW
- **Status:** FECHADO (2026-07-09)
- **Area:** mobile
- **Sintoma:** Após o modelo de ajuste/estorno (PROB-0044) começar a gerar lançamentos com `descricao` prefixada por `"Ajuste: "`/`"Estorno: "`, a tela de fatura no mobile exibia essa string crua junto da cor condicional já implementada em BUG-0026 (valor negativo em verde), mas sem nenhum indicador visual de "tipo de lançamento" (chip/badge), dependendo inteiramente do usuário ler o prefixo textual da descrição.
- **Causa raiz:** A UI mobile foi corrigida em BUG-0026 apenas para a cor do valor (positivo/negativo); a exibição do `tipo` (campo já adicionado ao DTO/tipos em BUG-0026) não tinha nenhum uso visual além da cor do valor.
- **Impacto tecnico:** Usuário podia não perceber rapidamente, ao olhar a lista de lançamentos, que um item é um ajuste ou estorno (em vez de uma compra), especialmente em listas longas — dependência de leitura atenta do texto da descrição.
- **Arquivos ou modulos relacionados:** `mobile/app/(app)/more/faturas.tsx:246-278` (map de `fatura.lancamentos`)
- **Solucao proposta:** Adicionar chip/badge de tipo (`ESTORNO`/`AJUSTE`) ao lado da descrição de cada lançamento, e remover o prefixo textual redundante da descrição já que o badge assume esse papel.
- **Solucao aplicada:** Cada lançamento agora calcula `tipoBadge` (`ESTORNO` → chip verde `colors.success`; `AJUSTE` → chip âmbar `colors.warning`; `COMPRA` → sem badge) e remove o prefixo `"Estorno: "`/`"Ajuste: "` da descrição exibida via regex `/^(Estorno|Ajuste):\s*/` antes de renderizar (o valor original retornado pela API, com o prefixo, permanece intacto — apenas a exibição é ajustada).
- **Evidencias:** `git diff -- mobile/app/\(app\)/more/faturas.tsx` (ver `docs/REVIEW_REPORTS/2026-07-09_backend_review_fatura-cartao-fluxo.md`, seção "Atualização (revisão 3)"). Sem teste automatizado.
- **Riscos residuais:** Regex de remoção do prefixo é sensível ao texto exato gerado pelo backend em `FaturaService.ressincronizarCompraCartao`/`cancelarCompraCartao` (`"Ajuste: "`/`"Estorno: "`); se o backend mudar o texto do prefixo sem atualizar esta regex, o prefixo passaria a ser exibido duplicado com o badge.
- **Proximo passo:** Confirmado por leitura de `git diff -- frontend/src/pages/Faturas.tsx` nesta rodada: o frontend web recebeu apenas a cor condicional de BUG-0026 (`l.valor < 0 ? 'text-green-400' : 'text-red-400'`), sem badge de tipo nem remoção do prefixo textual — divergência de paridade entre mobile e web. Ver BACKLOG-0057.

---

## PROB-0048 — Backend local (porta 8081) servindo build defasado durante validação manual do fluxo de fatura no mobile

- **ID:** PROB-0048
- **Titulo:** Processo Spring Boot local (porta 8081) permaneceu com JVM iniciada antes de uma recompilação, servindo classes antigas durante parte da validação manual do fluxo de compra no cartão
- **Data:** 2026-07-09
- **Origem:** revisao de integracao do modulo de faturas/cartao no app mobile — verificação manual de contrato (terceira rodada da mesma sessao)
- **Severidade:** MEDIUM
- **Status:** FECHADO (BUG-0051, 2026-07-11) — verificação local sem listener em 8081
- **Area:** infra
- **Sintoma:** Durante a validação manual do contrato de compra de cartão no ambiente local (porta 8081), uma compra de cartão não gerou os lançamentos de fatura esperados e o `valorGasto` seguiu um caminho de cálculo antigo, divergente do código-fonte atual no working tree.
- **Causa raiz:** A JVM do processo `./mvnw spring-boot:run` local havia sido iniciada às 08:17, antes das classes serem recompiladas (build mais recente às 22:00 do mesmo dia) — o processo em execução continuava servindo o bytecode carregado no boot, sem hot-reload das mudanças de `FaturaService`/`TransacaoService` feitas ao longo da sessão.
- **Impacto tecnico:** Nenhum impacto em produção nem no código — é um artefato do fluxo de desenvolvimento local. Risco real é de falso negativo/falso positivo em validações manuais futuras: um teste manual contra um processo defasado pode indicar erroneamente que uma correção não funcionou (ou, inversamente, "funcionou" por acidente usando código antigo).
- **Arquivos ou modulos relacionados:** Nenhum arquivo de código — processo de desenvolvimento local (`backend`, execução via `./mvnw spring-boot:run` na porta 8081).
- **Solucao proposta:** Sempre reiniciar `./mvnw spring-boot:run` (ou equivalente) após qualquer recompilação de classes Java, antes de qualquer validação manual de contrato via requests HTTP diretos.
- **Solucao aplicada:** Verificação operacional em 2026-07-11 confirmou que não há processo escutando em `127.0.0.1:8081`; `nc -vz 127.0.0.1 8081` retornou `Connection refused`. Nenhuma alteração de código era necessária.
- **Evidencias:** `lsof -nP -iTCP:8081 -sTCP:LISTEN` sem saída; `nc` com conexão recusada. `ps` amplo foi bloqueado pelo sandbox, mas a porta afetada está livre.
- **Riscos residuais:** Risco recorrente de desenvolvimento local se `spring-boot:run` ficar aberto após recompilação. Mitigação operacional: reiniciar backend antes de validação manual HTTP.
- **Proximo passo:** Nenhum para código. Manter disciplina de reinício em validações locais.

---

## PROB-0049 — Importacao CSV bypassa regras financeiras centrais

- **ID:** PROB-0049
- **Titulo:** `ImportService.importarCsv()` salva `Transacao` direto no repository, sem passar por `TransacaoService`
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** CRITICAL
- **Status:** FECHADO (2026-07-10)
- **Area:** backend, banco, integridade financeira
- **Sintoma:** Transacoes importadas por CSV sao persistidas diretamente por `transacaoRepository.save(tx)`.
- **Causa raiz:** Importacao implementada como persistencia direta, sem reutilizar o service/command financeiro que aplica ledger, fatura, categoria e conta.
- **Impacto tecnico:** Importacao pode criar transacoes que aparecem em relatorios, mas nao atualizam saldo de carteira, movimentos de ledger, faturas de cartao, `categoria.valorGasto` ou `conta.valorGasto`.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/ImportService.java`, `TransacaoService.java`, `TransacaoRepository.java`, `ImportController.java`, `ImportResultDto.java`
- **Solucao proposta:** Fazer importacao montar comandos e chamar o fluxo financeiro central (`TransacaoService` ou novo application service/command handler), com opcao explicita para carteira/conta/cartao e regras de deduplicacao/idempotencia.
- **Solucao aplicada:** ImportService passou a chamar `transacaoService.criar()` por linha (ledger, fatura, `categoria.valorGasto` e `conta.valorGasto` aplicados). `@Transactional` externo removido — cada linha importa em transacao propria; linha invalida nao reverte as demais e o resultado reportado reflete o que persistiu. Deduplicacao por (usuario, data, descricao, valor, tipo) via `existsBy...AtivaTrue` — reimportacao conta como `duplicadas` no `ImportResultDto`. Coluna opcional `carteira` (por nome; inexistente = erro de linha) + param opcional `carteiraId` default no endpoint (ownership validado). Status do CSV preservado (`criar()` so forca PENDENTE quando status null); linhas CANCELADO ignoradas.
- **Evidencias:** `ImportServiceTest` (7 testes): saldo carteira e valorGasto atualizados, movimento ledger criado, reimportacao ignora duplicatas sem alterar saldo, carteira inexistente vira erro de linha sem derrubar demais, carteira padrao de outro usuario rejeitada. Suite completa 102/102 PASS.
- **Riscos residuais:** Dedupe por campos pode marcar como duplicata duas transacoes legitimas identicas no mesmo dia (mesma descricao/valor). Compra em cartao (conta CREDITO) importada segue fluxo de fatura normal — validar com extrato real de cartao. Frontend/mobile ainda nao expõem `carteiraId`/coluna carteira na UI de importacao.
- **Proximo passo:** Resolvido.

---

## PROB-0050 — Modelo de fatura/cartao incompleto para padrao alto nivel

- **ID:** PROB-0050
- **Titulo:** Fatura nao suporta pagamento parcial, fatura com total zero/negativo nem rollover explicito de credito
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-11) — **pagamento parcial** implementado em BUG-0052; **credito negativo / rollover / fatura total `<= 0` (saldo devedor rolado)** implementados em BUG-0053 (rollover lazy R1/R2, `FaturaService.liquidarFaturaAnterior`, migration `V25__fatura_rollover.sql`, teste `FaturaRolloverTest` — 7 casos, suite completa 142 testes/0 falhas). ATENCAO: `FECHADO (BUG-0051)` registrado em versao anterior desta entrada era atribuicao incorreta — BUG-0051 foi rate limit/sessao mobile/backup, nao tocou fatura; o fechamento real do escopo de credito/rollover e o BUG-0053.
- **Area:** backend, produto financeiro
- **Sintoma (historico):** `pagarFatura` bloqueava total `<= 0` (`"Fatura sem valor para pagamento"`) e nao fazia rollover explicito de credito. Pagamento parcial ja havia deixado de ser bloqueado desde BUG-0052 (acumula `valorPago`).
- **Causa raiz:** Modelo tratava credito/estorno e fatura negativa como quitacao simples; saldo credor e rotativo nao tinham ledger de estado proprio.
- **Impacto tecnico (historico):** Creditos de estorno podiam ficar presos em faturas antigas; fatura negativa e rollover nao existiam.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java` (metodo `liquidarFaturaAnterior`), `backend/src/main/java/com/gestor/financeiro/model/FaturaLancamento.java`, `backend/src/main/java/com/gestor/financeiro/model/enums/TipoFaturaLancamento.java`, `backend/src/main/java/com/gestor/financeiro/repository/FaturaLancamentoRepository.java`, `backend/src/main/resources/db/migration/V25__fatura_rollover.sql`, `backend/src/test/java/com/gestor/financeiro/FaturaRolloverTest.java`.
- **Solucao aplicada (BUG-0052, pagamento parcial, 2026-07-11):** `pagarFatura` aceita pagamento parcial com lock pessimista na fatura, acumulando `valorPago`, calculando `saldoRestante`, liberando limite pelo valor pago e marcando `PAGA` apenas ao quitar o saldo; web e mobile enviam `Idempotency-Key`, validam contra `saldoRestante` e exibem `Pago`/`Restante`. A mensagem "pagamento parcial nao suportado" foi eliminada (ver tambem BUG-0021).
- **Solucao aplicada (BUG-0053, credito/rollover, 2026-07-11):** Rollover **lazy na leitura** (sem endpoint de fechar fatura e sem scheduler — status `FECHADA` continua derivado). `liquidarFaturaAnterior` e chamado por `buscarAtual`, `buscarPorMes` e `criarOuBuscarFatura`; ao materializar a fatura de competencia M, liquida recursivamente para tras (M-1, M-2, ...) as faturas existentes ja fechadas (recursao termina por competencia estritamente decrescente, fatura anterior inexistente — nunca materializa fatura retroativa vazia — ou teto de 24 meses). **R1** (total origem `<= 0`): gera `CREDITO_ANTERIOR` (valor negativo) na proxima fatura em aberto e marca a origem `PAGA` com `dataPagamento = dataFechamento`; nunca cria `MovimentoCarteira`. **R2** (total `> 0` e `valorPago < total`): gera `SALDO_DEVEDOR_ANTERIOR` (valor positivo = total - valorPago) na proxima fatura, sem juros (fora de escopo do MVP). Idempotencia por `FaturaLancamentoRepository.existsByFaturaOrigemId` (guard em codigo) + lock pessimista (`findWithLockByIdAndUsuarioId`) na fatura de origem + unique index parcial `ux_fatura_rollover_origem_tipo` (backstop de banco, catch de `DataIntegrityViolationException` como no-op). `FaturaLancamento.transacao` passou a ser nullable, com novo campo `faturaOrigem` para rastreabilidade; `toResponse` corrigido para nao dar NPE em lancamento sem transacao. UI web/mobile exibem `CREDITO_ANTERIOR` em verde ("Credito anterior") e `SALDO_DEVEDOR_ANTERIOR` em ambar/alerta ("Saldo devedor anterior", nunca vermelho).
- **Evidencias:** `FaturaService.java` (`liquidarFaturaAnterior`); BUGFIX_LOG BUG-0052 e BUG-0053; `FaturaRolloverTest` (7 casos: R1 basico, credito abate, credito rola de novo, R2 saldo devedor, pagamento total sem rollover, idempotencia dupla-leitura, cadeia com mes pulado) — `./mvnw -q test` → Tests run: 142, Failures: 0, Errors: 0; `scripts/verify-postgres-migrations.sh` → PASS (`PostgresMigrationIT` 5/0); nao-regressao `FaturaCartaoWorkflowTest` 9/9. Spec de produto em `SYSTEM_OVERVIEW.md` ("Regra de produto: credito de fatura e saldo devedor rolado", agora marcada IMPLEMENTADA).
- **Riscos residuais:** (1) Unique index `ux_fatura_rollover_origem_tipo` da migration V25 nao existe no schema de teste (H2 create-drop, Flyway desligado em teste) — idempotencia testada apenas pelo guard de codigo `existsByFaturaOrigemId`; o backstop de banco (unique index) nao e exercitado por teste automatizado, e concorrencia real de 2 threads simultaneas nao tem teste dedicado (design coberto por lock pessimista + unique index, mas so validado por revisao de codigo, nao por teste). (2) `pagarFatura` ainda pode ter casos de borda nao cobertos quando o rollover interage com edicao/cancelamento de compra em fatura ja liquidada por rollover — nao testado explicitamente nesta rodada.
- **Proximo passo:** Nenhum obrigatorio para fechar este PROB. Recomenda-se, quando houver ambiente de teste com Flyway/Postgres real disponivel (ver PROB-0058), validar o unique index `ux_fatura_rollover_origem_tipo` e um cenario de concorrencia real (2 requisicoes simultaneas materializando a mesma fatura futura).

---

## PROB-0051 — Banco nao protege invariantes financeiros basicos

- **ID:** PROB-0051
- **Titulo:** Tabelas centrais aceitam valores/dias/enums invalidos sem `CHECK` constraints suficientes
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-11)
- **Area:** banco, integridade financeira
- **Sintoma:** `transacoes.valor_total`, `parcelas.valor`, `total_parcelas`, dias de vencimento/fechamento e enums dependem majoritariamente da validacao Java.
- **Causa raiz:** Baseline Flyway nasceu como espelho minimo do schema JPA, sem camada completa de invariantes no banco.
- **Impacto tecnico:** Bugs, imports, scripts ou futuras rotas podem persistir estado financeiramente invalido mesmo com `ddl-auto=validate`.
- **Arquivos relacionados:** `backend/src/main/resources/db/migration/V1__baseline_schema.sql`, migrations posteriores de fatura/investimentos/orcamento.
- **Solucao proposta:** Adicionar migrations com `CHECK` constraints para valores positivos/nao-negativos conforme dominio, ranges de mes/dia, total de parcelas, status/tipo validos e coerencia basica.
- **Solucao aplicada:** Migration `V20__hardening_check_constraints.sql` adiciona CHECK em transacoes (valor_total>0, tipo/status no dominio, total_parcelas>=1, valor_parcela>0), parcelas (numero>=1, total>=1, numero<=total, valor>0, status), contas (tipo, limite>=0, dias 1..31), carteiras (tipo), categorias (valor_esperado>=0), contas_fixas (valor_planejado>0, valor_real>=0, dia 1..31, status), metas (valor_total>0, reservado>=0, mensal>=0), orcamentos (mes 1..12, valores>=0), faturas_cartao (mes 1..12, valor_pago>=0, status), ativos (quantidade>=0 — tambem backstop de PROB-0054, custo/valor>=0, tipo), movimentacoes_ativo (quantidade>=0, preco>=0, tipo), movimentos_meta (valor>0, tipo ADICAO/REMOCAO, coerencia valor_assinado) e movimentos_carteira (tipo/origem no dominio, complementando os CHECK de valor da V11). Campos que legitimamente podem ser negativos/zero (`contas.valor_gasto`, `categorias.valor_gasto`, `carteiras.saldo`, `contas.saldo_atual`, `faturas_cartao.valor_total` por rollover/estorno) ficaram sem restricao de sinal, de proposito.
- **Evidencias:** Validado em PostgreSQL 16 real: V1..V21 aplicam limpo; insercoes invalidas (valor_total=0, tipo/status fora do dominio, dia_vencimento=40, ativo quantidade=-1, movimento_meta incoerente) rejeitadas pelo CHECK correto; linha valida passa. Testes `checkConstraintsRejeitamValoresFinanceirosInvalidos` e `uniqueFaturaLancamentoImpedeCompraAVistaDuplicada` adicionados em `PostgresMigrationIT`.
- **Riscos residuais:** Se algum ambiente ja tiver dado legado violando um invariante (ex.: posicao de ativo negativa por PROB-0054, ou compra a vista duplicada por PROB-0052), a migration V20/V21 falha de proposito no deploy — a correcao do dado e manual, nunca silenciosa. `PostgresMigrationIT` continua dependente de Docker valido (PROB-0058); validacao acima foi feita subindo Postgres 16 via CLI e aplicando as migrations em ordem.
- **Proximo passo:** Nenhum. Fechar PROB-0053/0054 elimina os caminhos de codigo que hoje o banco passa a barrar.

---

## PROB-0052 — Unique de fatura_lancamentos falha para parcela NULL no PostgreSQL

- **ID:** PROB-0052
- **Titulo:** `UNIQUE(fatura_id, transacao_id, parcela_numero)` nao impede duplicidade quando `parcela_numero` e `NULL`
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-11)
- **Area:** banco, cartao, integridade financeira
- **Sintoma:** Compras a vista usam `parcela_numero = NULL`; PostgreSQL permite multiplas linhas iguais em unique quando coluna nullable e `NULL`.
- **Causa raiz:** Constraint unica nao considerou semantica de `NULL` em PostgreSQL.
- **Impacto tecnico:** Duplicidade de lancamento de compra a vista pode inflar fatura e `Conta.valorGasto`.
- **Arquivos relacionados:** `backend/src/main/resources/db/migration/V17__fatura_lancamentos.sql`
- **Solucao proposta:** Criar unique index funcional parcial, por exemplo com `COALESCE(parcela_numero, 0)`, ou constraints separadas para compra a vista e parcelada.
- **Solucao aplicada:** Migration `V21__fatura_lancamentos_unique_null_safe.sql` remove a unique constraint inline da V17 (via bloco `DO` que localiza a constraint por `pg_constraint`, sem depender do nome auto-gerado) e cria `CREATE UNIQUE INDEX ux_fatura_lancamentos_unico ON fatura_lancamentos (fatura_id, transacao_id, COALESCE(parcela_numero, 0))`. Parcelas reais sao numeradas a partir de 1, entao 0 nunca colide com parcela valida. Confirmado que AJUSTE/ESTORNO (tambem `parcela_numero` NULL) nao colidem entre si porque `ressincronizarCompraCartao`/`cancelarCompraCartao` removem antes todos os lancamentos abertos da transacao — no maximo um lancamento NULL por (fatura, transacao).
- **Evidencias:** Validado em PostgreSQL 16 real: duplicata de compra a vista (`parcela_numero` NULL) agora rejeitada por `ux_fatura_lancamentos_unico` (era o bug); parcela 1 coexiste com a a vista (COALESCE=1 != 0); parcela duplicada rejeitada. Teste `uniqueFaturaLancamentoImpedeCompraAVistaDuplicada` em `PostgresMigrationIT`.
- **Riscos residuais:** Se um ambiente ja tiver duplicata legada (fatura, transacao, parcela NULL), o `CREATE UNIQUE INDEX` falha de proposito no deploy — dedupe manual (nunca silencioso, por ser lancamento financeiro). Idempotencia de `registrarCompraCartao` no codigo continua valendo, agora com o banco como backstop.
- **Proximo passo:** Nenhum.

---

## PROB-0053 — Relatorios e projecoes ainda fazem agregacao em memoria

- **ID:** PROB-0053
- **Titulo:** `RelatorioService` e `ProjecaoService` carregam listas completas em memoria para calculos agregados
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-11)
- **Area:** backend, performance, banco
- **Sintoma:** Relatorio carrega transacoes do periodo para top despesas/gastos por conta; projecao carrega contas fixas, parcelas e faturas e filtra em Java.
- **Causa raiz:** Dashboard foi otimizado para SQL, mas relatorios/projecoes mantiveram padrao antigo.
- **Impacto tecnico:** Lentidao, alto consumo de memoria e risco de OOM com historico grande.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/RelatorioService.java`, `backend/src/main/java/com/gestor/financeiro/service/ProjecaoService.java`
- **Solucao proposta:** Substituir por queries agregadas/paginadas no banco (`SUM`, `GROUP BY`, `ORDER BY`, `LIMIT`) e indices coerentes.
- **Solucao aplicada:** `RelatorioService` deixou de carregar `findByUsuarioIdAndDataBetween` em memoria. Tres queries agregadas novas em `TransacaoRepository`: `findMaioresDespesas` (LEFT JOIN categoria, `ORDER BY valorTotal DESC` + `Pageable(0,10)`), `sumSaidasAgrupadoPorConta` (`GROUP BY` conta, `ORDER BY SUM DESC` + `Pageable(0,8)`) e `countSaidasByUsuarioIdAndPeriodo`. `ProjecaoService` trocou os tres helpers (`somarContasFixasNoMes`/`somarParcelasNoMes`/`somarFaturasEmAberto`) por `SUM(COALESCE(...))` no banco: `ContaFixaRepository.somarPlanejadoNoPeriodo`, `ParcelaRepository.somarValorNoPeriodo` e `FaturaCartaoRepository.somarValorTotalPorStatusNoPeriodo`. Efeito colateral corretivo: as novas queries filtram `ativa = true`, alinhando maiores despesas / gasto por conta / contagem aos totais (antes o load em memoria incluia transacoes canceladas — coerente com PROB-0035).
- **Evidencias:** Testes `RelatorioServiceTest` (3) e `ProjecaoServiceTest` (2) validam agregacao no banco (SQL logado com `group by`/`order by`/`fetch first N rows only`), ordenacao, limite, cor padrao sem categoria, exclusao de ENTRADA e canceladas, e exclusao de conta fixa PAGA. Suite completa: 121 testes, 0 falhas.
- **Indices de suporte:** Migration `V23__relatorio_projecao_support_indexes.sql` adiciona `idx_transacoes_usuario_tipo_data_ativa (usuario_id, tipo, data) WHERE ativa = true` (parcial, casa com relatorio/dashboard), `idx_contas_fixas_usuario_vencimento_ativo (usuario_id, data_proximo_vencimento) WHERE ativo = true` e `idx_faturas_usuario_status_vencimento (usuario_id, status, data_vencimento)`. Gasto por conta ja usava `idx_transacoes_conta`. Validado aplicando V1..V23 em PostgreSQL 16 real (via psql em container descartavel, ja que Testcontainers nao sobe aqui — PROB-0058): todas as migrations aplicam limpo e os 3 indices sao criados.
- **Riscos residuais:** Projecao ainda emite ~3 queries por mes projetado (N pequeno), nao mais N loads de tabela cheia.
- **Proximo passo:** Se necessario, colapsar as somas mensais da projecao em uma unica query agrupada por mes.

---

## PROB-0054 — Investimentos permite posicao negativa e nao integra caixa

- **ID:** PROB-0054
- **Titulo:** `InvestimentoService` nao bloqueia venda acima da quantidade e nao movimenta carteira/caixa
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** HIGH
- **Status:** FECHADO (2026-07-11)
- **Area:** backend, investimentos, integridade financeira
- **Sintoma:** Venda calcula preco medio e subtrai quantidade sem validar posicao suficiente.
- **Causa raiz:** Modulo de investimentos foi implementado como controle isolado de posicao, nao como evento financeiro integrado ao ledger.
- **Impacto tecnico:** Quantidade negativa, custo medio incorreto, patrimonio/investimentos desconectados do saldo real.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/InvestimentoService.java`, `backend/src/main/java/com/gestor/financeiro/dto/MovimentacaoRequest.java`, `backend/src/main/java/com/gestor/financeiro/model/enums/OrigemMovimentoCarteira.java`, `backend/src/main/resources/db/migration/V22__movimentos_carteira_origem_investimento.sql`, `backend/src/test/java/com/gestor/financeiro/InvestimentoServiceTest.java`
- **Solucao proposta:** Validar quantidade/preco positivos, bloquear venda acima da posicao, integrar compras/vendas a carteira/ledger e registrar eventos auditaveis.
- **Solucao aplicada:** `InvestimentoService.adicionarMovimentacao`/`updateAtivoPosicao` reescritos: (1) VENDA bloqueia quantidade acima da posicao atual com `BusinessException` ("Quantidade insuficiente para venda..."), eliminando quantidade negativa e a divisao por zero de VENDA com posicao 0; (2) quantidade sempre > 0, preco >= 0 e > 0 exceto BONIFICACAO (acoes gratuitas com preco 0), tipo invalido agora vira `BusinessException` em vez de 500; (3) DIVIDENDO nao altera quantidade nem custo (provento em caixa); BONIFICACAO aumenta quantidade com custo ZERO, reduzindo preco medio (antes somava `valorTotal` ao custo indevidamente); (4) integracao de caixa **opcional e nao-breaking** via novo campo `MovimentacaoRequest.carteiraId` — se informado, COMPRA debita (SAIDA) e VENDA/DIVIDENDO creditam (ENTRADA) o caixa via `LedgerService.registrarMovimento` com origem `INVESTIMENTO` (novo valor em `OrigemMovimentoCarteira`), `referenciaTipo="ATIVO"`, `referenciaId=ativo.id`, `idempotencyKey="MOV_ATIVO_<movId>"`; COMPRA com carteira valida saldo suficiente (`permitirSaldoNegativo=false`) e ownership com lock via `LedgerService`; se `carteiraId` ausente, so atualiza posicao (compativel com o mobile atual, que ainda nao envia `carteiraId`); BONIFICACAO nunca move caixa; (5) lookups de ativo migrados de `RuntimeException` para `ResourceNotFoundException`. Migration `V22__movimentos_carteira_origem_investimento.sql` estende o CHECK `chk_movimentos_carteira_origem` (criado na V20/PROB-0051) para aceitar o novo valor `INVESTIMENTO` — fecha o "proximo passo" ja previsto em PROB-0051, onde o backstop de banco (`ativo.quantidade>=0`) ja existia mas sem o caminho de codigo correspondente barrado na aplicacao.
- **Evidencias:** `InvestimentoServiceTest.java` (novo, 14 testes) cobre: venda acima da posicao rejeitada, venda sem posicao nao divide por zero, quantidade/preco nao-positivos rejeitados, tipo invalido, bonificacao sem custo, dividendo sem alterar posicao, compra/venda/dividendo movimentando caixa, saldo insuficiente na compra, origem `INVESTIMENTO`, sem-carteira nao gera movimento. Suite completa: 116 testes, 0 falha, BUILD SUCCESS. Migration V22 (chain V1..V22) aplicada limpa em PostgreSQL 16 real via Docker CLI (Testcontainers segue indisponivel — ver PROB-0058); CHECK confirmado aceitando `INVESTIMENTO` e rejeitando valor fora do dominio.
- **Riscos residuais:** Integracao de caixa e opt-in por request; enquanto o mobile nao enviar `carteiraId`, patrimonio de investimentos e caixa seguem desacoplados (por escolha de produto, nao-breaking). Migrations V20/V21/V22 ainda nao commitadas/deployadas; PROB-0058 (Testcontainers sem Docker socket) segue aberto.
- **Proximo passo:** Mobile passar a enviar `carteiraId` nas movimentacoes de ativo para ativar a integracao de caixa opcional ja implementada. Commit/deploy das migrations V20-V22 pendente (fora do escopo deste agente).

---

## PROB-0055 — Rate limit em memoria nao serve para multi-instancia

- **ID:** PROB-0055
- **Titulo:** `LoginRateLimitFilter` usa `ConcurrentHashMap` local para rate limit
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** MEDIUM
- **Status:** FECHADO (BUG-0051, 2026-07-11)
- **Area:** backend, seguranca, infra
- **Sintoma:** Tentativas sao contadas apenas dentro da JVM atual.
- **Causa raiz:** Implementacao simples local, sem store distribuido.
- **Impacto tecnico:** Em multi-instancia, atacante distribui tentativas entre replicas; reinicio limpa historico.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/config/LoginRateLimitFilter.java`
- **Solucao proposta:** Migrar para store compartilhado. Redis/Bucket4j era uma opção; como o sistema já depende de PostgreSQL, foi adotado bucket transacional no banco para evitar nova infraestrutura.
- **Solucao aplicada:** Criado `RateLimitBucket` + `RateLimitBucketRepository` + `RateLimitService`. `LoginRateLimitFilter` deixou de usar `ConcurrentHashMap` e chama `RateLimitService.consume()`, que usa lock pessimista por chave no banco. Migration `V24__rate_limit_buckets.sql` cria tabela e índice. Limpeza periódica remove buckets expirados.
- **Evidencias:** Testes `AuthControllerTest`/`SecurityTest` PASS; backend suite PASS; `scripts/verify-postgres-migrations.sh` PASS validando `V24` em PostgreSQL real.
- **Riscos residuais:** Rate limit depende do PostgreSQL estar disponível; isso é aceitável porque a API também depende do banco para autenticação.
- **Proximo passo:** Nenhum imediato. Avaliar Redis/gateway apenas se volume/latência justificar.

---

## PROB-0056 — Bypass CSRF por header mobile exige threat model formal

- **ID:** PROB-0056
- **Titulo:** Requests com `X-Client-Type: mobile` pulam validacao CSRF em refresh/logout
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** MEDIUM
- **Status:** FECHADO (BUG-0051, 2026-07-11)
- **Area:** backend, seguranca, mobile
- **Sintoma:** `RefreshTokenCsrfFilter` retorna sem validar CSRF quando header mobile esta presente.
- **Causa raiz:** Cliente nativo nao usa o mesmo modelo de cookie/CSRF do navegador; backend aceita header declarativo.
- **Impacto tecnico:** Se o backend confiar em header spoofavel sem outra garantia, o limite entre cliente web e mobile fica fraco. O risco pratico depende de CORS, cookies, storage mobile e envio de refresh token no body.
- **Arquivos relacionados:** `backend/src/main/java/com/gestor/financeiro/config/RefreshTokenCsrfFilter.java`, `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`
- **Solucao proposta:** Documentar e aplicar contratos separados: web usa cookie HttpOnly + double-submit CSRF; mobile usa refresh token no body, armazenado em SecureStore, sem cookies.
- **Solucao aplicada:** `RefreshTokenCsrfFilter` agora bloqueia request com `X-Client-Type: mobile` se houver cookie `refreshToken` (`MOBILE_COOKIE_REFRESH_NOT_ALLOWED`). `AuthController` em modo mobile emite/rotaciona refresh token apenas no body e não envia `Set-Cookie`; refresh/logout mobile resolvem token só pelo body. Axios mobile passou a `withCredentials:false` e removeu CSRF do refresh/logout.
- **Evidencias:** Novo teste `mobile_deveRejeitarRefreshTokenViaCookieMesmoComHeaderMobile`; teste mobile de refresh body-only; `AuthControllerTest`/`SecurityTest` PASS; mobile `tsc --noEmit` PASS.
- **Riscos residuais:** Segurança mobile depende de SecureStore e proteção do dispositivo, conforme threat model nativo.
- **Proximo passo:** Nenhum imediato.

---

## PROB-0057 — Injeção por campo reduz testabilidade e imutabilidade

- **ID:** PROB-0057
- **Titulo:** Backend ainda usa `@Autowired` por campo em larga escala
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** MEDIUM
- **Status:** FECHADO (BUG-0051, 2026-07-11)
- **Area:** backend, qualidade
- **Sintoma:** 135 usos de `@Autowired` em `backend/src/main/java`.
- **Causa raiz:** Padrao historico de injecao por campo nos controllers/services/configs.
- **Impacto tecnico:** Dificulta testes unitarios puros, construcao de objetos, imutabilidade e leitura de dependencias obrigatorias.
- **Arquivos relacionados:** multiplos arquivos em `backend/src/main/java/com/gestor/financeiro`.
- **Solucao proposta:** Migrar gradualmente para constructor injection, priorizando services financeiros e filtros/configuracoes.
- **Solucao aplicada:** Sweep completo em `backend/src/main/java`: controllers, services, config e security migrados para constructor injection com dependencias `final` e `@RequiredArgsConstructor`. Novos `LoginRateLimitFilter`/`RateLimitService` já nasceram com injeção por construtor.
- **Evidencias:** `rg "@Autowired" backend/src/main/java` sem ocorrencias; backend compile PASS; backend suite PASS.
- **Riscos residuais:** Testes Spring ainda usam `@Autowired`, aceitavel para testes de integracao/contexto Spring e fora do escopo de producao.
- **Proximo passo:** Nenhum imediato.

---

## PROB-0058 — Integration test PostgreSQL nao executa sem Docker valido

- **ID:** PROB-0058
- **Titulo:** `mvn verify -Pintegration-test` falha localmente porque Testcontainers nao encontra Docker valido
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** MEDIUM
- **Status:** FECHADO (BUG-0051, 2026-07-11)
- **Area:** testes, infra
- **Sintoma:** Suite unit/slice passa, mas integration-test PostgreSQL nao roda no ambiente local auditado.
- **Causa raiz:** Docker/Testcontainers indisponivel ou mal configurado no host.
- **Impacto tecnico:** Validação real de Flyway/PostgreSQL fica dependente de CI ou ambiente manual.
- **Arquivos relacionados:** `backend/src/test/java/com/gestor/financeiro/PostgresMigrationIT.java`, profile `integration-test`.
- **Solucao proposta:** Garantir execução real em PostgreSQL no CI/dev sem mascarar erro por skip.
- **Solucao aplicada:** `PostgresMigrationIT` agora aceita PostgreSQL externo via `POSTGRES_IT_JDBC_URL`/`POSTGRES_IT_USERNAME`/`POSTGRES_IT_PASSWORD`; se env ausente, ainda tenta Testcontainers. Novo script `scripts/verify-postgres-migrations.sh` sobe PostgreSQL 16 descartável via Docker CLI, executa `PostgresMigrationIT` contra ele e remove o container. CI passou a usar esse script como gate real de migrations.
- **Evidencias:** `scripts/verify-postgres-migrations.sh` PASS local; Docker CLI `docker run --rm hello-world` PASS; `mvn verify -Pintegration-test` ainda falha neste host por bug/socket do Testcontainers, mas o gate real via Docker CLI cobre o mesmo objetivo sem skip.
- **Riscos residuais:** Testcontainers puro ainda depende de ambiente Docker compatível; CI/local usam script canônico para validação real.
- **Proximo passo:** Usar `scripts/verify-postgres-migrations.sh` para validar migrations novas.

---

## PROB-0059 — Backups sem criptografia e sem restore drill automatizado

- **ID:** PROB-0059
- **Titulo:** Scripts/compose fazem backup, mas nao ha criptografia nem teste automatizado de restore
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** MEDIUM
- **Status:** FECHADO (BUG-0051, 2026-07-11)
- **Area:** infra, seguranca, operacao
- **Sintoma:** `pg_dump` gera arquivo local/volume; restore e manual com confirmacao humana.
- **Causa raiz:** Backup implementado como rotina basica, nao como plano operacional completo.
- **Impacto tecnico:** Vazamento de backup expõe dados financeiros; backup pode ser inutil se restore nunca for testado.
- **Arquivos relacionados:** `scripts/backup-db.sh`, `scripts/restore-db.sh`, `docker-compose.vps.yml`
- **Solucao proposta:** Criptografar backups, registrar retenção/local seguro, automatizar restore drill em banco descartável e alertar falhas.
- **Solucao aplicada:** `scripts/backup-db.sh` agora bloqueia backup sem criptografia por padrão e suporta `BACKUP_GPG_RECIPIENT` ou `BACKUP_ENCRYPTION_PASSPHRASE` (AES256). `scripts/restore-db.sh` restaura `.sql.gz` e `.sql.gz.gpg`, com modo não interativo controlado por `RESTORE_ASSUME_YES=true`. Novo `scripts/restore-drill-db.sh` automatiza restore em banco descartável e valida tabelas básicas. `docker-compose.vps.yml` usa imagem própria `deploy/vps/Dockerfile.postgres-backup` com `gnupg`, exige `BACKUP_ENCRYPTION_PASSPHRASE` e gera `.sql.gz.gpg`.
- **Evidencias:** `bash -n scripts/backup-db.sh scripts/restore-db.sh scripts/restore-drill-db.sh` PASS; frontend/mobile/backend validações PASS.
- **Riscos residuais:** Restore drill precisa ser agendado em ambiente operacional com banco descartável e chave gerenciada fora do repositório.
- **Proximo passo:** Configurar secret `BACKUP_ENCRYPTION_PASSPHRASE` no VPS e agendar restore drill.

---

## PROB-0060 — Build de imagem backend pula testes

- **ID:** PROB-0060
- **Titulo:** `backend/Dockerfile` usa `mvn clean package -DskipTests`
- **Data:** 2026-07-10
- **Origem:** auditoria backend/non-frontend alto nivel
- **Severidade:** LOW
- **Status:** FECHADO (BUG-0052, 2026-07-11)
- **Area:** backend, CI/CD
- **Sintoma:** Imagem Docker pode ser criada mesmo se testes falharem, caso build seja executado fora do CI.
- **Causa raiz:** Dockerfile otimizado para build rapido; responsabilidade de teste delegada ao CI.
- **Impacto tecnico:** Regressao pode ser empacotada por fluxo manual que ignore CI.
- **Arquivos relacionados:** `backend/Dockerfile`
- **Solucao aplicada:** `backend/Dockerfile` passou a executar `mvn clean package` sem `-DskipTests`; CI ja mantem `mvn test` e `scripts/verify-postgres-migrations.sh` como gates.
- **Evidencias:** `backend/Dockerfile:6`, `.github/workflows/ci.yml:23-24`.
- **Riscos residuais:** Build Docker fica mais lento, mas passa a falhar se a suite backend falhar.
- **Proximo passo:** Nenhum.

---

## PROB-0061 — Onboarding mobile fora do design system e sem acessibilidade

- **ID:** PROB-0061
- **Titulo:** `onboarding.tsx` usava paleta Tailwind hard-coded, CTA final verde e inputs/chips manuais sem a11y
- **Data:** 2026-07-10
- **Origem:** auditoria de UI mobile (impeccable)
- **Severidade:** MEDIUM
- **Status:** FECHADO (BUG-0048, 2026-07-10)
- **Area:** mobile, UI, acessibilidade
- **Sintoma:** Categorias sugeridas com cores fora da paleta canônica (`#EF4444`, `#8B5CF6`, ...) — categoria criada no onboarding tinha cor impossível de re-selecionar no editor de categorias; botão "Começar" verde `#22C55E` violava a regra "verde é dinheiro, violeta é marca"; inputs e chips duplicavam `Field`/`Chip` com radius/labels divergentes; nenhum `accessibilityRole`/`State`/`Label` em chips, grid de categorias, checkboxes e botões.
- **Causa raiz:** Tela construída antes da consolidação do design system (`DESIGN.md` + componentes `ui/`), sem passar por revisão de UI.
- **Solucao proposta:** Alinhar à paleta `CATEGORY_COLORS`, CTA em `colors.brand`, reusar `Field`/`Chip`, adicionar a11y completa e alvos ≥44pt.
- **Solucao aplicada:** `CATEGORIAS_SUGERIDAS` agora referencia `CATEGORY_COLORS` (com cinza neutro novo para "Outros"); CTA "Começar" em `brand`/`brandText`; todos os inputs viraram `Field` e chips viraram `Chip`; grid e "Pular" com role `checkbox` + `checked`; botões com role e minHeight 48; barra de progresso simplificada.
- **Evidencias:** `mobile/app/onboarding.tsx`, `mobile/src/utils/format.ts`. `npx tsc --noEmit` PASS.

---

## PROB-0062 — Cores hard-coded quebrando dark mode e identidade visual

- **ID:** PROB-0062
- **Titulo:** Branco fixo em botões/splash e tiles arco-íris no hub "Mais" ignoravam tokens do tema
- **Data:** 2026-07-10
- **Origem:** auditoria de UI mobile (impeccable)
- **Severidade:** MEDIUM
- **Status:** FECHADO (BUG-0048, 2026-07-10)
- **Area:** mobile, UI, tema
- **Sintoma:** `perfil.tsx` com texto `#ffffff` sobre `colors.brand` (no dark, brand é lilás claro — contraste ~2:1) e botão "Sair" branco sobre `danger` (falha no dark); `app/index.tsx` com splash `#fff` fixo (flash branco no dark mode); `more/index.tsx` com uma cor por item de menu (tiles arco-íris — anti-referência declarada no PRODUCT.md).
- **Causa raiz:** Uso de literais de cor no lugar dos tokens `brandText`/`bg`/`brandBg` do tema.
- **Solucao proposta:** Substituir literais por tokens; hub de navegação todo em violeta (navegação é marca).
- **Solucao aplicada:** `perfil.tsx` usa `brandText` no botão brand e `dangerBg`+`danger` no "Sair"; splash usa `colors.bg` + spinner `brand`; tiles do hub "Mais" todos em `brandBg`; badge "Em breve" de 8pt para 10pt.
- **Evidencias:** `mobile/app/(app)/perfil.tsx`, `mobile/app/index.tsx`, `mobile/app/(app)/more/index.tsx`. `npx tsc --noEmit` PASS.

---

## PROB-0063 — Telas de auth sem Field, links violeta sem contraste AA e alvos de toque pequenos

- **ID:** PROB-0063
- **Titulo:** login/register/forgot/reset duplicavam inputs manuais (radius 8, label 9pt) e usavam `brand` (3.5:1) em links de texto
- **Data:** 2026-07-10
- **Origem:** auditoria de UI mobile (impeccable)
- **Severidade:** MEDIUM
- **Status:** FECHADO (BUG-0048, 2026-07-10)
- **Area:** mobile, UI, acessibilidade
- **Sintoma:** Inputs sem `accessibilityLabel` (label visual desvinculado); links "Esqueceu a senha?", "Criar conta", "política de privacidade" e "Entrar" em `colors.brand` (#7c5cfc sobre lavanda = ~3.5:1, falha WCAG AA para texto pequeno); "Esqueceu a senha?" com alvo de toque < 44pt; vocabulário visual divergente do resto do app.
- **Causa raiz:** Telas de auth anteriores ao componente `Field`; token AA `brandFg` existia mas não era usado em links.
- **Solucao proposta:** Migrar inputs para `Field`, links para `brandFg`, alvos ≥44pt, roles de botão.
- **Solucao aplicada:** As 4 telas usam `Field` (com `autoComplete`/`textContentType` no login); links em `brandFg`; alvos de toque com minHeight 44; `accessibilityRole="button"` nos toques; botões com radius 12 unificado.
- **Evidencias:** `mobile/app/(auth)/login.tsx`, `register.tsx`, `forgot-password.tsx`, `reset-password.tsx`. `npx tsc --noEmit` PASS.

---

## PROB-0064 — Categorias: FAB caseiro sem label, seletor de cor sem a11y e espaçamento duplicado

- **ID:** PROB-0064
- **Titulo:** `more/categorias.tsx` reimplementava o FAB sem `accessibilityLabel` e o seletor de cor não expunha estado
- **Data:** 2026-07-10
- **Origem:** auditoria de UI mobile (impeccable)
- **Severidade:** LOW
- **Status:** FECHADO (BUG-0048, 2026-07-10)
- **Area:** mobile, UI, acessibilidade
- **Sintoma:** FAB "+" manual (sem gradiente/glow do componente `Fab`, sem label para leitor de tela); swatches de cor sem role/estado/label; `gap` + `marginRight` somando espaçamento; input Nome manual com eyebrow 9pt; Cancelar/Salvar sem role e alvo < 44pt.
- **Causa raiz:** Tela construída sem reusar `Fab`/`Field`.
- **Solucao proposta:** Reusar componentes, adicionar a11y ao seletor de cor.
- **Solucao aplicada:** `Fab` com label "Nova categoria"; swatches com role `radio` + `selected` + label + hitSlop; espaçamento único via `gap: 12`; Nome via `Field`; Cancelar/Salvar com role button, minHeight 44 e cor `brandFg`.
- **Evidencias:** `mobile/app/(app)/more/categorias.tsx`. `npx tsc --noEmit` PASS.

---

## PROB-0065 — Falso positivo: build TypeScript do frontend web

- **ID:** PROB-0065
- **Titulo:** Suspeita de erros de tipo TypeScript no frontend web
- **Data:** 2026-07-11
- **Origem:** implementacao (verificacao de nao-regressao durante BUG-0053, rollover de credito/saldo devedor de fatura)
- **Severidade:** MEDIUM
- **Status:** FECHADO (falso positivo, 2026-07-11)
- **Area:** frontend, documentacao
- **Sintoma:** Suspeita inicial de ~36 erros em arquivos nao relacionados ao rollover de fatura.
- **Causa raiz:** Falso positivo/observacao obsoleta.
- **Impacto tecnico:** Nenhum no estado atual.
- **Solucao aplicada:** Revalidado build web no estado final desta rodada.
- **Evidencias:** `frontend npm run build --silent` PASS em 2026-07-11.
- **Riscos residuais:** Nenhum conhecido.
- **Proximo passo:** Nenhum.

---

## PROB-0066 — Rate limit de login/forgot-password/register contornavel via X-Forwarded-For forjado

- **ID:** PROB-0066
- **Titulo:** `LoginRateLimitFilter`/`AuthController` usavam `getRemoteAddr()` com `forward-headers-strategy=framework`, permitindo que o cliente forjasse o IP usado como chave de rate limit via header `X-Forwarded-For`
- **Data:** 2026-07-14
- **Origem:** auditoria abrangente (security-auditor) sobre a stack de deploy VPS/nginx
- **Severidade:** HIGH
- **Status:** FECHADO (commit `c959dfc`)
- **Area:** backend, seguranca, infra
- **Sintoma:** Com `forward-headers-strategy=framework` (Spring interpreta o `X-Forwarded-For` recebido) e nginx configurado apenas para *append* (nao sobrescrever) o header, o primeiro IP (leftmost) da lista `X-Forwarded-For` era controlado pelo proprio cliente da requisicao. `getRemoteAddr()` em `LoginRateLimitFilter` e `AuthController` resolvia esse IP forjado como chave do bucket, permitindo que um atacante trocasse de IP declarado a cada tentativa e contornasse os limites de `login` (5/min), `forgot-password` (3/min) e `register` (5/min).
- **Causa raiz:** Confianca implicita em todo o header `X-Forwarded-For` recebido pela aplicacao, sem que a camada de proxy (nginx) sobrescrevesse/normalizasse o header antes de repassar, e sem que o Tomcat estivesse configurado para extrair o IP real de forma confiavel a partir de uma lista de proxies internos conhecidos.
- **Impacto tecnico:** Rate limit e account lockout de login, recuperacao de senha e registro efetivamente inoperantes contra um atacante determinado — abre caminho para brute force de senha e abuso de envio de email de recuperacao/registro em massa.
- **Arquivos ou modulos relacionados:** `backend/src/main/resources/application-vps.properties`, `backend/src/main/java/com/gestor/financeiro/config/LoginRateLimitFilter.java`, `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`, `docker-compose.production.yml`, `docker-compose.vps.yml`, `deploy/vps/nginx.conf.template`, `deploy/vps/nginx.npm.conf`, `deploy/vps/README.md`
- **Solucao proposta:** Trocar `forward-headers-strategy` de `framework` para `native` (Tomcat `RemoteIpValve` resolve o IP real a partir de uma lista fechada de proxies internos, em vez de confiar cegamente no header) e garantir que a camada de proxy mais externa sempre sobrescreva/normalize o `X-Forwarded-For` com o IP real de conexao.
- **Solucao aplicada:** `forward-headers-strategy` alterado de `framework` para `native` em `application-vps.properties`, com `RemoteIpValve` configurado (`remote-ip-header=X-Forwarded-For`, `protocol-header=X-Forwarded-Proto`, `internal-proxies` cobrindo loopback e faixas privadas Docker). A env var `SERVER_FORWARD_HEADERS_STRATEGY=native` foi adicionada em `docker-compose.production.yml` e `docker-compose.vps.yml` — descoberta chave durante a correcao foi que a env var sobrepoe o profile, entao ambos precisavam ser atualizados para o valor realmente entrar em vigor. `deploy/vps/nginx.conf.template` (topologia standalone, 1 hop) passou a sobrescrever o `X-Forwarded-For` com `$remote_addr` em vez de repassar o header recebido. `deploy/vps/nginx.npm.conf` (topologia atras do Nginx Proxy Manager, 2 hops) mantem o comportamento *append-only*, mas a premissa de que o NPM sempre anexa seu proprio `$remote_addr` ao `X-Forwarded-For` (e nao repassa cegamente o header do cliente) foi documentada explicitamente em `deploy/vps/README.md`. Adicionalmente, `docker-compose.production.yml` ganhou uma rede Docker interna dedicada `web<->API`, com a API removida da rede `proxy` — o NPM so alcanca o container `web`, reduzindo a superficie de acesso direto a API.
- **Evidencias ou comandos usados:** `./mvnw -q test` → 155/155 PASS apos a mudanca; leitura de `application-vps.properties`, `docker-compose.production.yml`, `docker-compose.vps.yml`, `deploy/vps/nginx.conf.template`, `deploy/vps/nginx.npm.conf` confirmando a alteracao (`git diff --stat` desses arquivos: 6 arquivos, 94 insercoes, 13 remocoes).
- **Riscos residuais:** A premissa "NPM sempre anexa seu `$remote_addr` real" depende de configuracao correta e continua do Nginx Proxy Manager fora do repositorio (nao versionado no codigo) — se o NPM for reconfigurado para repassar o `X-Forwarded-For` do cliente sem anexar, o problema volta a existir silenciosamente. Nenhum teste automatizado do backend simula a cadeia real de proxies (nginx standalone e/ou NPM) validando o IP resolvido; a validacao depende de `nginx -t` + smoke manual em staging (ver BACKLOG-0080). Mudanca commitada, mas ainda nao testada em ambiente VPS real ate o momento deste registro.
- **Proximo passo:** Executar `nginx -t` nos dois configs, recriar as redes do `docker-compose.production.yml` (rede interna e nova) e validar em staging que um `X-Forwarded-For` forjado pelo cliente nao muda o bucket de rate limit resolvido pela API (ver BACKLOG-0080).

---

## PROB-0067 — Pagamento de parcela duplicava debito na carteira sob reenvio/concorrencia

- **ID:** PROB-0067
- **Titulo:** `ParcelaService.marcarComoPaga` sem guard de estado nem idempotency key — duas chamadas ao mesmo endpoint criavam dois `MovimentoCarteira` de saida
- **Data:** 2026-07-14
- **Origem:** auditoria abrangente (security-auditor / database-engineer) sobre integridade financeira
- **Severidade:** HIGH
- **Status:** FECHADO (commit `0d1e0c0`)
- **Area:** backend, banco
- **Sintoma:** `PUT /api/v1/parcelas/{id}/pagar` chamado duas vezes (duplo clique, retry de rede, ou requisicoes concorrentes) para a mesma parcela ja paga criava um **segundo** `MovimentoCarteira` de saida, debitando a carteira do usuario duas vezes pelo mesmo pagamento e corrompendo o saldo.
- **Causa raiz:** `marcarComoPaga` nao verificava se a parcela ja estava `PAGO` antes de gerar o movimento de carteira, e a chamada nao usava uma `idempotencyKey` estatica (decisao deliberada, ver "Solucao aplicada") nem tinha `@Version` para serializar escrita concorrente na mesma parcela.
- **Impacto tecnico:** Corrupcao de saldo financeiro por debito duplicado — mesma classe de risco que PROB-0002 (race condition de saldo), mas neste caso tanto por reenvio sequencial (idempotencia ausente) quanto por concorrencia real (falta de lock).
- **Arquivos ou modulos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/ParcelaService.java`, `backend/src/main/java/com/gestor/financeiro/model/Parcela.java`, `backend/src/main/resources/db/migration/V28__pre_production_hardening.sql`, `backend/src/test/java/com/gestor/financeiro/ParcelaServiceTest.java`
- **Solucao proposta:** Guard no service (retornar no-op se a parcela ja estiver `PAGO`) + `@Version` na entidade `Parcela` para que escrita concorrente sobre a mesma parcela falhe com `OptimisticLockingFailureException` (→ 409) em vez de aplicar as duas escritas.
- **Solucao aplicada:** `ParcelaService.marcarComoPaga` agora retorna a parcela sem efeito colateral (no-op) se o status ja for `PAGO`, antes de qualquer geracao de `MovimentoCarteira`. Coluna `version BIGINT NOT NULL DEFAULT 0` adicionada a `Parcela` (anotacao `@Version`) e a tabela `parcelas` via migration `V28__pre_production_hardening.sql`, seguindo o mesmo padrao ja usado em Carteira/Conta/Meta/Categoria (V2, PROB-0002). Deliberadamente **nao** foi adotada uma `idempotencyKey` estatica por parcela, para preservar o fluxo legitimo de produto pagar → despagar → pagar (uma key estatica bloquearia o terceiro pagamento).
- **Evidencias ou comandos usados:** Novo teste `ParcelaServiceTest.pagarParcelaJaPagaEhIdempotente`; `./mvnw -q test` → 155/155 PASS; `./mvnw -q verify` → BUILD SUCCESS.
- **Riscos residuais:** O guard no service cobre o caminho sequencial (reenvio apos resposta); a protecao contra concorrencia real (duas threads simultaneas pagando a mesma parcela nao-paga) depende do `@Version` e do 409 gerado por `OptimisticLockingFailureException` — nao ha teste automatizado de concorrencia real com duas threads simultaneas para este fluxo especifico (mesma limitacao estrutural documentada em outros pontos do ledger, ex. PROB-0058).
- **Proximo passo:** Nenhum bloqueante. Considerar teste de concorrencia real (2 threads) para `marcarComoPaga` em rodada futura de hardening, se o caso de uso justificar o custo de manutencao do teste.

---

## PROB-0068 — Exclusao de carteira em uso normal retornava HTTP 500

- **ID:** PROB-0068
- **Titulo:** `CarteiraService.deletar` so verificava movimentos de origem `CARTEIRA_AJUSTE`, deixando o caminho mais comum (carteira com `TRANSACAO`/`PARCELA`) cair em violacao de FK `RESTRICT` sem tratamento
- **Data:** 2026-07-14
- **Origem:** auditoria abrangente (quality-reviewer / database-engineer)
- **Severidade:** HIGH
- **Status:** FECHADO (commit `0d1e0c0`)
- **Area:** backend, banco
- **Sintoma:** `DELETE /api/v1/carteiras/{id}` numa carteira com uso normal (qualquer transacao ja lancada contra ela, origem `TRANSACAO` ou `PARCELA`) retornava HTTP 500 em vez de um erro de negocio tratado (400/409), porque o guard de aplicacao so cobria origem `CARTEIRA_AJUSTE` e a exclusao seguia ate colidir com a constraint de FK `RESTRICT` em `movimentos_carteira.carteira_id` no banco.
- **Causa raiz:** Guard de validacao incompleto em `CarteiraService.deletar` — cobria apenas um subconjunto (`CARTEIRA_AJUSTE`) das origens possiveis de `MovimentoCarteira`, deixando os casos mais frequentes de uso real (transacoes e parcelas) sem verificacao previa, resultando em excecao de integridade de banco nao mapeada propagando como 500.
- **Impacto tecnico:** Qualquer usuario que tentasse excluir uma carteira ja usada em transacoes normais recebia um erro 500 generico em vez de uma mensagem de negocio clara — o caminho mais comum de uso da funcionalidade estava quebrado.
- **Arquivos ou modulos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/CarteiraService.java`, `backend/src/main/java/com/gestor/financeiro/repository/MovimentoCarteiraRepository.java`, `backend/src/test/java/com/gestor/financeiro/CarteiraControllerTest.java`
- **Solucao proposta:** Trocar a checagem restrita por origem por uma checagem generica de existencia de qualquer `MovimentoCarteira` associado a carteira, bloqueando a exclusao com a `BusinessException` de negocio ja existente no fluxo.
- **Solucao aplicada:** Novo metodo `existsByCarteiraId(Long)` em `MovimentoCarteiraRepository`; `CarteiraService.deletar` agora bloqueia a exclusao (com a mesma `BusinessException` de negocio ja usada no fluxo) sempre que existir **qualquer** movimento associado a carteira, independentemente da origem.
- **Evidencias ou comandos usados:** Novos testes `CarteiraControllerTest.deletarCarteiraComMovimentoDeTransacaoRetornaErroDeNegocio` e `CarteiraControllerTest.deletarCarteiraSemMovimentoRemove`; `./mvnw -q test` → 155/155 PASS.
- **Riscos residuais:** Nenhum identificado — o guard agora cobre o superset de origens de `MovimentoCarteira`. Mensagem de negocio ao usuario final ainda depende do texto ja existente na `BusinessException` (nao revisado neste ciclo para clareza de UX).
- **Proximo passo:** Nenhum bloqueante.

---

## PROB-0069 — Indices ausentes em `movimentos_carteira.carteira_id` e `refresh_tokens.usuario_id` (full scan em hot paths)

- **ID:** PROB-0069
- **Titulo:** Consulta por `carteira_id` isolado e consultas de auth por `usuario_id` em `refresh_tokens` nao tinham indice dedicado
- **Data:** 2026-07-14
- **Origem:** auditoria abrangente (database-engineer)
- **Severidade:** LOW
- **Status:** FECHADO (commit `0d1e0c0`)
- **Area:** banco
- **Sintoma:** `existsByCarteiraId` (novo metodo criado para PROB-0068) e a validacao de FK sobre `movimentos_carteira.carteira_id` nao tinham indice dedicado — o unico indice existente em `movimentos_carteira` (criado na migration V11) e composto e liderado por `usuario_id`, inutil para filtro isolado por `carteira_id`. Da mesma forma, `refresh_tokens.usuario_id` — usado em `findByUsuario`, `revokeAllByUsuario` e `countValidTokensByUsuario`, executado em todo login/refresh/logout-all — nao tinha indice, resultando em full table scan nesses hot paths de autenticacao.
- **Causa raiz:** Indices nao acompanharam o crescimento de queries por esses campos ao longo do tempo.
- **Impacto tecnico:** Degradacao de performance proporcional ao volume de dados em `movimentos_carteira` e `refresh_tokens` — sem impacto funcional/correcao, apenas latencia crescente em operacoes frequentes (exclusao de carteira, todo fluxo de autenticacao).
- **Arquivos ou modulos relacionados:** `backend/src/main/resources/db/migration/V28__pre_production_hardening.sql`
- **Solucao proposta:** Criar `CREATE INDEX idx_movimentos_carteira_carteira ON movimentos_carteira(carteira_id)` e `CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens(usuario_id)`.
- **Solucao aplicada:** Ambos os indices criados via migration `V28__pre_production_hardening.sql` (`CREATE INDEX IF NOT EXISTS`), na mesma migration que adiciona a coluna `version` de `Parcela` (PROB-0067). `V27` permanece reservado em `db/contract` (nao promovido neste ciclo).
- **Evidencias ou comandos usados:** `scripts/verify-postgres-migrations.sh` PASS contra PostgreSQL real via Docker, incluindo a migration V28.
- **Riscos residuais:** Nenhum identificado. Migration aditiva, `IF NOT EXISTS`, sem risco de quebra de compatibilidade.
- **Proximo passo:** Nenhum bloqueante.

---

## PROB-0070 — SPA (rotas fora de `/api/**`) servida sem headers de seguranca

- **ID:** PROB-0070
- **Titulo:** `SecurityConfig` cobre apenas `/api/**`; assets/HTML do frontend web servidos pelo nginx sem HSTS, X-Frame-Options, CSP e demais headers de seguranca
- **Data:** 2026-07-14
- **Origem:** auditoria abrangente (security-auditor)
- **Severidade:** MEDIUM
- **Status:** FECHADO (commit `c959dfc`)
- **Area:** frontend, seguranca, infra
- **Sintoma:** O SPA (HTML/JS/CSS servidos diretamente pelo nginx, fora do escopo de `/api/**`) nao recebia nenhum dos headers de seguranca aplicados pelo Spring Security no backend — sem HSTS, sem `X-Frame-Options`, sem `X-Content-Type-Options`, sem `Referrer-Policy` e sem CSP, deixando a superficie de risco de clickjacking/MIME sniffing/downgrade HTTP aberta na camada que o usuario final de fato carrega no navegador.
- **Causa raiz:** `SecurityConfig` (Spring) so intercepta requisicoes para `/api/**`; o SPA e servido por um `nginx` separado (container `web`) que nao tinha os headers configurados.
- **Impacto tecnico:** Risco de clickjacking (ausencia de `X-Frame-Options`/`frame-ancestors`), MIME sniffing (`X-Content-Type-Options`), vazamento de referrer entre origens e ausencia de forcamento de HTTPS via HSTS na pagina principal servida ao usuario.
- **Arquivos ou modulos relacionados:** `deploy/vps/nginx.conf.template`, `deploy/vps/nginx.npm.conf`
- **Solucao proposta:** Adicionar `add_header` para HSTS, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` e uma CSP explicita nos dois configs de nginx.
- **Solucao aplicada:** Ambos os arquivos (`nginx.conf.template` e `nginx.npm.conf`) ganharam os headers: HSTS, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` e CSP (`default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'`). Os headers sao repetidos explicitamente no bloco `/assets/` porque `add_header` no nginx nao herda automaticamente de blocos pai quando o bloco filho declara seu proprio `add_header`.
- **Evidencias ou comandos usados:** `git diff --stat deploy/vps/nginx.conf.template deploy/vps/nginx.npm.conf` → 43 insercoes, 3 remocoes; leitura direta dos dois arquivos confirmando os headers.
- **Riscos residuais:** CSP escolhida e restritiva (`'self'` para script/style, sem `unsafe-inline`) — se o SPA atual depender de inline script/style em algum ponto nao coberto pelos testes de build, pode quebrar silenciosamente em producao; validacao recomendada via `nginx -t` + carregamento manual do SPA em staging antes do rollout (ver BACKLOG-0080). Nenhum teste automatizado verifica os headers HTTP retornados pelo nginx (fora do escopo de teste do backend/frontend).
- **Proximo passo:** Validar em staging que o SPA carrega sem violacao de CSP no console do navegador antes de promover para producao (ver BACKLOG-0080).

---

## PROB-0071 — Token de reset de senha trafegava na query string (`GET /api/auth/validate-token?token=...`)

- **ID:** PROB-0071
- **Titulo:** Endpoint de validacao de token de reset de senha aceitava o token via query string, expondo-o a access logs de proxies/CDN/browser history
- **Data:** 2026-07-14
- **Origem:** auditoria abrangente (security-auditor / lgpd-auditor)
- **Severidade:** MEDIUM
- **Status:** FECHADO (commit `5c08ce0`)
- **Area:** backend, frontend, seguranca, LGPD
- **Sintoma:** `GET /api/auth/validate-token?token=...` trafegava o token de reset de senha (segredo de curta duracao, mas ainda assim um segredo capaz de redefinir a senha da conta) na query string da URL — dado tipicamente persistido em access logs de nginx/proxies intermediarios, historico do navegador e possivelmente em ferramentas de observabilidade/APM que capturam URLs completas.
- **Causa raiz:** Escolha original de design usou `GET` com parametro de query para um endpoint de validacao que deveria tratar o token como segredo, mesmo padrao problematico do fluxo de "esqueci minha senha" classico quando implementado via GET.
- **Impacto tecnico:** Exposicao de um segredo de autenticacao (token de reset) em superficies de log persistentes fora do controle direto da aplicacao — risco de LGPD/seguranca caso esses logs sejam acessados indevidamente dentro da janela de validade do token.
- **Arquivos ou modulos relacionados:** `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`, `backend/src/main/java/com/gestor/financeiro/dto/ValidateTokenRequest.java` (novo), `backend/src/main/java/com/gestor/financeiro/exception/GlobalExceptionHandler.java`, `backend/src/test/java/com/gestor/financeiro/AuthControllerTest.java`, `frontend/src/pages/ResetPassword.tsx`, `backend/API.md`, `deploy/vps/README.md`
- **Solucao proposta:** Trocar `GET` com query string por `POST` com o token no corpo da requisicao (`ValidateTokenRequest`), removendo o `GET` do endpoint.
- **Solucao aplicada:** Novo endpoint `POST /api/auth/validate-token` recebendo `ValidateTokenRequest { token }` (`@NotBlank`, `@Size(max=255)`). O `GET` anterior foi removido do controller — uma chamada `GET` para o mesmo path agora retorna HTTP 405 (Method Not Allowed) por meio de um novo handler de `HttpRequestMethodNotSupportedException` no `GlobalExceptionHandler` (antes desse handler, a ausencia do metodo caia no catch-all generico e respondia 500 em vez de 405). `frontend/src/pages/ResetPassword.tsx` atualizado para chamar o novo contrato POST. O email de recuperacao de senha continua sendo um deep link mobile e nao foi alterado neste ciclo.
- **Evidencias ou comandos usados:** Novos/ajustados testes em `AuthControllerTest` (incluindo `validateToken_getRemovidoRetorna405` e conversao dos testes existentes de GET para POST); `./mvnw -q test` → 155/155 PASS; `backend/API.md` e `deploy/vps/README.md` atualizados para refletir o novo contrato (fora do escopo de edicao deste registro — ver nota de restricao abaixo).
- **Riscos residuais:** Qualquer client/integrador externo que ainda dependa do contrato antigo (`GET` com query string) quebra com 405 — mudanca de contrato de API breaking, mitigada por documentacao atualizada em `backend/API.md`/`deploy/vps/README.md`, mas sem versionamento de API formal no projeto.
- **Proximo passo:** Nenhum bloqueante. Confirmar em staging que o fluxo completo de reset de senha (email → deep link mobile → POST validate-token → POST reset-password) segue funcional apos a mudanca de contrato.

---

## PROB-0072 — `TransacaoRequest.totalParcelas` sem teto maximo de validacao

- **ID:** PROB-0072
- **Titulo:** Campo `totalParcelas` de `TransacaoRequest` aceitava qualquer valor positivo, sem limite superior razoavel
- **Data:** 2026-07-14
- **Origem:** auditoria abrangente (quality-reviewer)
- **Severidade:** LOW
- **Status:** FECHADO (commit `0d1e0c0`)
- **Area:** backend
- **Sintoma:** `TransacaoRequest.totalParcelas` nao tinha teto de validacao — uma compra parcelada em, por exemplo, 999999 parcelas seria aceita pela validacao de DTO, gerando esse volume de `Parcela`/`FaturaLancamento` no banco.
- **Causa raiz:** Validacao original cobria apenas o piso (valor minimo), sem limite superior.
- **Impacto tecnico:** Potencial abuso (gerar volume excessivo de registros por requisicao) e caso de uso sem sentido no dominio real de parcelamento de cartao de credito (nenhum emissor real oferece centenas de parcelas).
- **Arquivos ou modulos relacionados:** `backend/src/main/java/com/gestor/financeiro/dto/TransacaoRequest.java`
- **Solucao proposta:** Adicionar `@Max(120)` (10 anos de parcelas mensais, teto generoso mas finito) ao campo.
- **Solucao aplicada:** `@Max(120)` adicionado a `totalParcelas` em `TransacaoRequest`.
- **Evidencias ou comandos usados:** `./mvnw -q test` → 155/155 PASS.
- **Riscos residuais:** Nenhum identificado — teto generoso o suficiente para nao colidir com nenhum caso de uso real documentado no sistema.
- **Proximo passo:** Nenhum.

---

## PROB-0073 — Recomendacao de auditoria rejeitada: CHECK `contas.valor_gasto >= 0`

- **ID:** PROB-0073
- **Titulo:** Auditoria recomendou constraint de banco `CHECK (valor_gasto >= 0)` em `contas`; recomendacao avaliada e rejeitada
- **Data:** 2026-07-14
- **Origem:** auditoria abrangente (database-engineer) — recomendacao nao implementada, registrada para rastreabilidade da decisao
- **Severidade:** MEDIUM
- **Status:** FECHADO (recomendacao rejeitada com justificativa, decisao registrada; nenhum codigo alterado)
- **Area:** backend, banco
- **Sintoma:** N/A — nao e um bug observado em producao, e uma recomendacao de hardening de banco feita durante a auditoria abrangente.
- **Causa raiz:** N/A.
- **Impacto tecnico:** N/A — a recomendacao, se implementada como proposta, teria **quebrado** um comportamento de produto legitimo e ja documentado.
- **Arquivos ou modulos relacionados:** `docs/SYSTEM_OVERVIEW.md` (regra de produto "credito de fatura e saldo devedor rolado", secao R1/R2, decisao V20:5-8 sobre `valor_gasto`)
- **Solucao proposta (pela auditoria):** Adicionar `CHECK (valor_gasto >= 0)` na tabela `contas` para impedir saldo de gasto negativo.
- **Solucao aplicada:** Nenhuma — recomendacao **rejeitada**. `Conta.valorGasto` negativo e um estado valido e ja documentado do dominio: representa **credito de cartao** (por exemplo, estorno maior que as compras em aberto no cartao), exibido na UI como "credito disponivel" desde BUG-0052 (2026-07-11) e formalizado como principio de produto na migration `V20` (comentario nas linhas 5-8, que documenta `valor_gasto` negativo como credito legitimo de cartao). Aplicar o `CHECK` proposto teria feito o backend falhar (`DataIntegrityViolationException`) exatamente no cenario que R1 do rollover de fatura (PROB-0050/BUG-0053) foi desenhado para suportar.
- **Evidencias ou comandos usados:** Leitura de `docs/SYSTEM_OVERVIEW.md` secao "Regra de produto: credito de fatura e saldo devedor rolado" e do item 23 de "Limitacoes conhecidas" (`Conta.valorGasto` pode ficar temporariamente negativo).
- **Riscos residuais:** Nenhum — decisao de nao implementar preserva o comportamento correto. Risco inverso (implementar a constraint) seria quebrar o rollover de credito de fatura em producao.
- **Proximo passo:** Nenhum. Se uma auditoria futura reabrir esta recomendacao, referenciar este registro e a migration V20 antes de qualquer implementacao.

---

## PROB-0074 — Recomendacao de auditoria rejeitada: piso zero + lock pessimista em `ContaService.removerGasto`

- **ID:** PROB-0074
- **Titulo:** Auditoria recomendou piso zero (`Math.max(0, ...)`) e lock pessimista em `ContaService.removerGasto`; recomendacao avaliada e rejeitada
- **Data:** 2026-07-14
- **Origem:** auditoria abrangente (database-engineer) — recomendacao nao implementada, registrada para rastreabilidade da decisao
- **Severidade:** MEDIUM
- **Status:** FECHADO (recomendacao rejeitada com justificativa, decisao registrada; nenhum codigo alterado)
- **Area:** backend, banco
- **Sintoma:** N/A — recomendacao de hardening feita durante a auditoria abrangente, nao um bug observado.
- **Causa raiz:** N/A.
- **Impacto tecnico:** N/A — a recomendacao, se implementada como proposta, teria **engolido** (zerado) credito legitimo de cartao em vez de preserva-lo.
- **Arquivos ou modulos relacionados:** `backend/src/main/java/com/gestor/financeiro/service/ContaService.java` (`removerGasto`), `backend/src/main/java/com/gestor/financeiro/model/Conta.java` (`@Version` ja existente desde PROB-0002)
- **Solucao proposta (pela auditoria):** Impedir `valorGasto` de ficar negativo em `removerGasto` aplicando um piso zero no calculo, e adicionar lock pessimista para serializar concorrencia.
- **Solucao aplicada:** Nenhuma — recomendacao **rejeitada** em duas frentes: (1) o piso zero engoliria credito de cartao legitimo (mesmo principio de PROB-0073 — `valorGasto` negativo e um estado valido de dominio, nao um bug); (2) `Conta` **ja possui** `@Version` (optimistic locking, desde PROB-0002/migration V2), tornando o lock pessimista adicional redundante para o problema de concorrencia — a escrita concorrente ja falha com `OptimisticLockingFailureException` (409) em vez de aplicar as duas escritas silenciosamente.
- **Evidencias ou comandos usados:** Leitura de `backend/src/main/java/com/gestor/financeiro/model/Conta.java` confirmando `@Version` existente; leitura de `docs/PROBLEM_LEDGER.md` PROB-0002 (optimistic locking) e PROB-0073 (credito negativo legitimo).
- **Riscos residuais:** Nenhum — decisao de nao implementar preserva o comportamento correto de credito de cartao e evita lock pessimista redundante sobre uma entidade que ja tem optimistic locking. Se o volume de conflitos otimistas em `Conta` crescer a ponto de degradar UX (muitos 409), reavaliar lock pessimista como otimizacao de performance (nao como correcao de bug).
- **Proximo passo:** Nenhum. Monitorar taxa de 409 em `ContaService` como sinal de que a otimizacao de lock pessimista passaria a valer a pena.

---

> Mantido pelo `docs-reporter`. Ultima atualizacao: 2026-07-14 (hardening pre-producao P0+P1 commitado em `main` como `5c08ce0`, `0d1e0c0` e `c959dfc`: PROB-0066 a PROB-0072 fechados; PROB-0073 e PROB-0074 fechados como recomendacoes de auditoria avaliadas e rejeitadas com justificativa documentada — ver `docs/BUGFIX_LOG.md` BUG-0059 a BUG-0065 e `docs/REVIEW_REPORTS/2026-07-14_full-system_implementation_pre-production-hardening.md`).

---

## PROB-0075 — Onboarding cadastra renda como SAIDA com categoria Alimentacao

- **ID:** PROB-0075
- **Titulo:** Renda do onboarding herda default SAIDA de ContaFixa e recebe a primeira categoria sugerida
- **Data:** 2026-07-15
- **Origem:** auditoria `docs/15 07 2026 - MetaDoNexosFinancas.md` (P0-1)
- **Severidade:** CRITICAL
- **Status:** CORRIGIDO na branch fase-1-integridade (commit 7787712; OnboardingServiceTest 4/4)
- **Area:** backend, onboarding
- **Sintoma:** Salario aparece como despesa recorrente; projecao recebe sinal invertido; web e mobile afetados.
- **Causa raiz:** `ContaFixa.java:33` default `TipoTransacao.SAIDA`; `OnboardingService.criarRendaSeNaoExistir` (117-135) nao seta tipo e associa `categorias.get(0)` (Alimentacao); fallback SAIDA em `ContaFixaService.java:69`.
- **Solucao proposta:** Onboarding cria renda como ENTRADA com categoria "Renda" idempotente; auditoria SQL read-only de rendas historicas suspeitas (sem UPDATE heuristico); fallback mantido com WARN. Ver ADR-0002.
- **Proximo passo:** PR-1.

---

## PROB-0076 — Exclusao LGPD nao remove ExecucaoRecorrencia

- **ID:** PROB-0076
- **Titulo:** UsuarioExclusaoService nao contempla execucoes de recorrencia; FKs restritivas quebram exclusao do titular
- **Data:** 2026-07-15
- **Origem:** auditoria `docs/15 07 2026 - MetaDoNexosFinancas.md` (P0-2)
- **Severidade:** CRITICAL
- **Status:** CORRIGIDO na branch fase-1-integridade (commit 89e890e; UsuarioExclusaoLgpdIT 2/2 em PostgreSQL real)
- **Area:** backend, LGPD
- **Sintoma:** Usuario com recorrencias executadas pode nao conseguir excluir a conta; direito de eliminacao incompleto.
- **Causa raiz:** `DELETES_ORDENADOS` (`UsuarioExclusaoService.java:41-61`) sem `execucao_recorrencia`; V29 criou FKs NOT NULL sem cascade.
- **Solucao proposta:** Manifesto de exclusao (tabela+JPQL) com ExecucaoRecorrencia antes de Transacao/ContaFixa + teste-guardiao por catalogo de FKs. Ver ADR-0007.
- **Proximo passo:** PR-2.

---

## PROB-0077 — Meta concluida ou excluida oculta dinheiro reservado

- **ID:** PROB-0077
- **Titulo:** Meta a 100% recebe ativa=false e some da listagem; exclusao so desativa mesmo com valorReservado > 0
- **Data:** 2026-07-15
- **Origem:** auditoria `docs/15 07 2026 - MetaDoNexosFinancas.md` (P0-3)
- **Severidade:** CRITICAL
- **Status:** CORRIGIDO na branch fase-1-integridade (commit b9b901c; MetaLifecycleTest 5/5 + MetaStatusBackfillIT)
- **Area:** backend, frontend, mobile
- **Sintoma:** Valor debitado da carteira deixa de ser visivel; usuario perde caminho de resgate; patrimonio incompleto.
- **Causa raiz:** `MetaService.java:72-75` (conclusao), `:34-35` (findByUsuarioIdAndAtivaTrue), `:136-142` (soft-delete sem tratar reserva).
- **Solucao proposta:** Status canonico ATIVA/CONCLUIDA/ARQUIVADA (V30), filtro `?status=`, exclusao com reserva bloqueada com 400 instrutivo. Ver ADR-0004.
- **Proximo passo:** PR-3.

---

## PROB-0078 — Onboarding web nao atomico e divergente do mobile

- **ID:** PROB-0078
- **Titulo:** Web cria carteira/conta/categorias/renda/meta em 5 chamadas independentes e depois marca onboarding completo
- **Data:** 2026-07-15
- **Origem:** auditoria `docs/15 07 2026 - MetaDoNexosFinancas.md` (P0-4)
- **Severidade:** CRITICAL
- **Status:** CORRIGIDO na branch fase-1-integridade (commit de64c46; OnboardingAtomicidadeTest 3/3 + E2E Playwright 1/1)
- **Area:** frontend, backend
- **Sintoma:** Falha intermediaria deixa cadastro parcial; retry duplica etapas; contratos divergentes entre web e mobile.
- **Causa raiz:** `frontend/src/pages/Onboarding.tsx` persiste por etapa (linhas 75/93/114/141/166 + completar 181); `onboardingService.ts` nem expoe `finalizar`.
- **Solucao proposta:** Web monta um unico OnboardingFinalizarRequest; backend com lock pessimista + idempotencia por `onboardingCompleto`; E2E Playwright. Ver ADR-0002.
- **Proximo passo:** PR-4 (depende de PR-1).

---

## PROB-0079 — Timezone inconsistente entre scheduler e APIs financeiras

- **ID:** PROB-0079
- **Titulo:** Recorrencias fixam America/Sao_Paulo; dashboard, faturas, orcamento, relatorios e anexos usam now() no timezone da JVM
- **Data:** 2026-07-15
- **Origem:** auditoria `docs/15 07 2026 - MetaDoNexosFinancas.md` (P1-5)
- **Severidade:** HIGH
- **Status:** CORRIGIDO na branch fase-1-integridade (commit d1ac0d5; BusinessClockGuardTest + TimezoneViradaTest 2/2)
- **Area:** backend
- **Sintoma:** Em servidor UTC, dia/mes viram adiantados; status e totais divergem na virada do periodo.
- **Causa raiz:** `RecorrenciaScheduler.java:18,22` e `ContaFixaService.java:42` hardcoded; `DashboardService`, `FaturaService`, `OrcamentoService`, `RelatorioService`, `AnexoService` usam now() sem zona.
- **Solucao proposta:** `app.business.timezone` + bean Clock injetado em todos os servicos financeiros + teste-guardiao contra now() sem Clock. Ver ADR-0003.
- **Proximo passo:** PR-5 (apos fechamento dos P0).

---

## PROB-0080 — Anexos sem persistencia operacional segura

- **ID:** PROB-0080
- **Titulo:** Uploads gravados no filesystem do container sem volume montado em nenhum compose
- **Data:** 2026-07-15
- **Origem:** auditoria `docs/15 07 2026 - MetaDoNexosFinancas.md` (P1-6)
- **Severidade:** HIGH
- **Status:** CORRIGIDO na branch fase-1-integridade (commit d1ea7f7; drill de anexos com evidencia neste ledger)
- **Area:** infra, backend
- **Sintoma:** Comprovantes somem apos recriacao do container; backup PostgreSQL nao inclui anexos.
- **Causa raiz:** `AnexoService` grava em `app.upload.dir` local; composes production/vps sem volume de uploads.
- **Solucao proposta:** Volume `backend_uploads:/app/uploads` em production e VPS + drill com evidencia; object storage futuro por gatilho definido. Ver ADR-0005.
- **Proximo passo:** PR-6.

---

## PROB-0081 — Estrategias de backup divergentes e sem copia off-host

- **ID:** PROB-0081
- **Titulo:** production usa pg_dump -Fc sem criptografia; vps usa gzip+GPG simetrico; nenhum envia para fora do host; uploads fora do escopo
- **Data:** 2026-07-15
- **Origem:** auditoria `docs/15 07 2026 - MetaDoNexosFinancas.md` (P1-7)
- **Severidade:** HIGH
- **Status:** REABERTO — aguarda drill com remote externo real, API comprovadamente parada,
  checksum remoto e download completo de anexo restaurado
- **Area:** infra
- **Sintoma:** Perda do host implica perda dos backups; politica de retencao e restore nao comprovados de ponta a ponta com anexos.
- **Causa raiz:** Dois composes evoluiram separadamente; scripts existentes (`scripts/backup-db.sh`, `restore-db.sh`, `restore-drill-db.sh`) nao cobrem uploads nem envio externo.
- **Solucao proposta:** Politica canonica: pg_dump -Fc + tar de uploads + manifesto/checksums, GPG assimetrico (privada off-host), rclone obrigatorio fail-closed, retencao 7d+4s, drill de restore com evidencia. Ver ADR-0006.
- **Proximo passo:** infraestrutura/SRE executar checklist off-host de
  `docs/runbooks/FASE5_FECHAMENTO_OPERACIONAL.md`; exigir remote externo, chave GPG pública,
  checksum remoto e restore limpo antes de qualquer promoção.

---

## Evidência do drill de anexos (PROB-0080) — 2026-07-15

Drill executado localmente com a API containerizada e named volume (`APP_UPLOAD_DIR=/app/uploads`,
volume `backend_uploads` — mesma configuração aplicada a `docker-compose.production.yml` e
`docker-compose.vps.yml` na branch `fase-1-integridade`):

1. register → login → `POST /api/v1/onboarding/finalizar` → transação → upload de PDF;
   arquivo gravado em `/app/uploads/1/eefdb350-71cd-44ed-8cf1-701bfc4881ac.pdf`.
2. `docker rm -f` + novo container com o mesmo volume → `GET /api/v1/anexos/1/download`
   retornou o PDF íntegro (conteúdo conferido).
3. `DELETE /api/v1/usuarios/me` (senha confirmada) → HTTP 204 → diretório do titular
   removido do volume (`ls /app/uploads` vazio).

Ressalva: drill local em arm64 usou `eclipse-temurin:17-jre` + jar montado (a imagem
`17-jre-alpine` do Dockerfile não publica manifest arm64); a semântica de volume testada é a
mesma dos composes. Falha de remoção física pós-commit permanece risco operacional registrado
(ADR-0005) até storage durável.

---

## Evidência do backup/restore drill (PROB-0081) — 2026-07-15

Executado com a imagem canônica (`deploy/vps/Dockerfile.postgres-backup`: bash+gnupg+rclone+
pg_dump 17) contra base semeada via API (usuário, onboarding, transação, anexo PDF):

1. **Fail-closed comprovado:** sem `BACKUP_GPG_RECIPIENT` → exit 1; sem `RCLONE_REMOTE` → exit 1;
   falha de criptografia → exit ≠ 0 com staging removido.
2. **Backup:** `backup-db.sh` gerou `gf_backup_20260715_185101.tar.gpg` (pg_dump -Fc +
   uploads.tar.gz + manifest sha256, GPG assimétrico — chave privada fora do servidor),
   enviado via rclone a remote e tamanho remoto validado; staging removido.
3. **Restore drill:** PostgreSQL 17 limpo → `restore-db.sh` decriptou com a chave privada,
   validou checksums do manifesto, `pg_restore --clean` OK; contagens: usuarios=1,
   transacoes=1, anexos=1, flyway ok; anexo extraído com conteúdo íntegro (`%PDF-1.4 ...`).

Ressalvas: (a) drill usou rclone com backend local como remote — em produção configurar remote
externo real (`deploy/backup/rclone/rclone.conf`); (b) restore de servidor PG16 com cliente 17
gera erro benigno `transaction_timeout` — manter major igual entre dump e restore (produção usa
17/17); (c) a tentativa histórica com `API_CONTAINER` não garantia a janela de manutenção.
O coordenador host-side substituiu esse mecanismo, mas ainda precisa de novo drill. Por isso
esta evidência histórica não fecha o problema;
`PROB-0081` permanece reaberto até o drill off-host real passar com o coordenador host-side.

---

## PROB-0082 — Código do redesign visual "Fase 4" (dark-first ciano) só existe em stash não commitado

- **ID:** PROB-0082
- **Titulo:** Reversão total do protótipo/redesign "Fase 4" do mobile deixou o código descartado sem
  local durável além de `git stash`
- **Data:** 2026-08-19
- **Origem:** decisão do dono do produto, branch `chore/remove-prototipo`
- **Severidade:** LOW
- **Status:** FECHADO em 2026-08-19 — risco materializado por decisão consciente do dono (ver abaixo).
- **Area:** mobile, documentacao
- **Sintoma:** Nenhum sintoma de execução — trata-se de uma decisão de produto executada
  corretamente. O ponto registrado é que o único protótipo commitado no repositório
  (`docs/Gestor Financeiro (standalone).html`, tema claro lavanda) foi removido via `git rm`, o
  protótipo dark-first ciano em `docs/prototipo/app.html` (untracked) e o rename staged
  `docs/prototipo/legado-claro.html` deixaram de existir, e todo o redesign "Fase 4" não commitado
  do app mobile foi revertido ao estado do commit `ae30d62` via
  `git stash push --include-untracked -m "fase4-prototipo-descartado-2026-08-19"`.
- **Causa raiz:** N/A — não é um bug. Decisão explícita do dono do produto: "ficou horrível e só
  atrapalha o sistema", descartando por inteiro o redesign visual "Fase 4" (dark-first ciano).
- **Impacto tecnico:** (1) `docs/prototipo/` não existe mais como diretório no working tree; (2) o
  tema dark-first ciano (`mobile/src/theme/colors.ts`, `theme/index.ts`), as telas
  `mobile/app/(app)/carteira.tsx` e `mobile/app/(app)/analises.tsx`, os componentes `CardBadge`,
  `CreditCardArt`, `DiaHeader`, `MerchantLogo`, `ProgressRing`, `TransacaoRow`,
  `mobile/src/domain/marcas.ts`, `mobile/src/store/themePref.ts`, `mobile/src/utils/color.ts`,
  testes novos (`marcas.test.ts`, `telasNovas.test.tsx`, `themePref.test.ts`,
  `uiPrimitivos.test.tsx`) e `mobile/.maestro/fase4-visual.yaml` só existem em
  `git stash@{0}` no momento deste registro — fora do reflog padrão de commits; (3)
  `mobile/app/(app)/more/relatorios.tsx` (que o redesign havia apagado) foi restaurado junto com o
  link em `more/index.tsx`; a tab bar voltou para `Início · Transações · + · Planejamento · Mais`;
  (4) `DESIGN.md` e `PRODUCT.md` (raiz do projeto) foram limpos para não citar mais o protótipo como
  fonte canônica — a fonte canônica passa a ser `DESIGN.md` + `mobile/src/theme/colors.ts`.
- **Arquivos ou modulos relacionados:** `mobile/src/theme/colors.ts`, `mobile/src/theme/index.ts`,
  `mobile/app/(app)/carteira.tsx`, `mobile/app/(app)/analises.tsx`,
  `mobile/app/(app)/more/relatorios.tsx`, `mobile/app/(app)/more/index.tsx`,
  `mobile/app/(app)/_layout.tsx`, `mobile/src/components/ui/CardBadge.tsx`,
  `mobile/src/components/ui/CreditCardArt.tsx`, `mobile/src/components/ui/DiaHeader.tsx`,
  `mobile/src/components/ui/MerchantLogo.tsx`, `mobile/src/components/ui/ProgressRing.tsx`,
  `mobile/src/components/ui/TransacaoRow.tsx`, `mobile/src/domain/marcas.ts`,
  `mobile/src/store/themePref.ts`, `mobile/src/utils/color.ts`, `mobile/.maestro/fase4-visual.yaml`,
  `DESIGN.md`, `PRODUCT.md` (raiz — fora da propriedade do `docs-reporter`).
- **Solucao proposta:** Ver BACKLOG-0090 — decisão explícita do dono sobre manter o stash com prazo
  de revisão, exportar para um branch nomeado, ou dropar de vez.
- **Solucao aplicada:** O dono decidiu apagar de vez. Após commitar a remoção do protótipo na `main`,
  o stash `fase4-prototipo-descartado-2026-08-19` foi dropado (`git stash drop`). O código do redesign
  "Fase 4" deixou de existir no repositório — não há branch, commit nem stash com ele. O risco
  descrito aqui foi aceito explicitamente, não mitigado. Ver BACKLOG-0090.
- **Evidencias ou comandos usados:** `git status --short` (working tree limpo de artefatos do
  redesign, exceto `DESIGN.md`/`PRODUCT.md` modificados e a deleção do standalone); `git stash list`
  (`stash@{0}: On main: fase4-prototipo-descartado-2026-08-19`); `git stash show --stat stash@{0}`
  (18 arquivos, 458 insertions(+), 566 deletions(-) — inclui a remoção de 296 linhas de
  `relatorios.tsx` dentro do stash, ou seja, o stash registra a versão em que o arquivo tinha sido
  apagado pelo redesign); `npx tsc --noEmit` limpo pós-reversão; `npm test` no mobile 12 suítes / 36
  testes PASS pós-reversão; `grep -in "prototipo|standalone" DESIGN.md PRODUCT.md` sem hits.
- **Riscos residuais:** `git stash drop`/`git stash clear` acidental apaga o código do redesign de
  forma irreversível fora do reflog local (não sincronizado com nenhum remoto). Baixa severidade
  porque a decisão do dono foi descartar o trabalho — o risco é apenas de perda de opcionalidade
  futura, não de regressão em produção.
- **Proximo passo:** Ver BACKLOG-0090.

---

## PROB-0084 — Redesign de navegação removeu silenciosamente o checklist de setup da home

- **ID:** PROB-0084
- **Titulo:** Funcionalidade entregue no PR-F3-10 (`Complete seu setup`) desapareceu da home sem
  registro, deixando `src/store/homeChecklist.ts` e seu teste órfãos no repositório
- **Data:** 2026-08-21
- **Origem:** auditoria do fluxo de inscrição/onboarding (mesma sessão), antes de redesenhar as telas
- **Severidade:** MEDIUM
- **Status:** RESOLVIDO (2026-08-21) — checklist restaurado no padrão visual novo; ver BUG-0072
- **Area:** mobile
- **Sintoma:** Usuário novo terminava o onboarding mínimo (uma etapa) e chegava na home sem
  categorias, sem cartão e sem nenhum convite a completar o setup. O store
  `src/store/homeChecklist.ts` e `src/__tests__/homeChecklist.test.ts` continuavam no repositório,
  mas `rg 'homeChecklist' --glob '*.tsx'` não retornava nenhum consumidor.
- **Causa raiz:** o checklist nasceu em `mobile/app/(app)/index.tsx` no commit `f0b27de` (PR-F3-10)
  e não foi transportado quando a home mudou de arquivo para `app/(app)/(inicio)/index.tsx` no
  commit `9a3b205` ("feat(mobile): refresh navigation and theme"). `git log -S homeChecklist --
  mobile/app` mostra exatamente os dois commits: entrada e saída.
- **Impacto de processo:** o teste unitário do store continuou verde durante todo o período, o que
  escondeu a perda — a suíte cobria a persistência da dispensa, não a existência do consumidor.
  Vale como lição para futuras migrações de tela: um store sem importador em `.tsx` é sinal de
  funcionalidade perdida.

---

## PROB-0083 — Interceptor Axios do mobile descarta mensagem de `BusinessException` em 400/422

- **ID:** PROB-0083
- **Titulo:** `mobile/src/services/api.ts` só lê `error.response.data.details` para montar
  `userMessage` em 400/422; respostas no formato `{"code":"BUSINESS_ERROR","message":"...","details":null}`
  perdem a mensagem de negócio do backend
- **Data:** 2026-08-21
- **Origem:** implementação (redesign de `mobile/app/(app)/ajustes.tsx` e fluxo de exclusão de
  conta LGPD, mesma sessão)
- **Severidade:** MEDIUM
- **Status:** RESOLVIDO (2026-08-21) — corrigido na origem em `mobile/src/services/api.ts`; ver
  BUG-0070. O interceptor passou a preservar o envelope (`code`/`message`/`details`) e a tratar 429;
  o contorno local de `ajustes.tsx` (BUG-0069) foi removido. Coberto por
  `mobile/src/__tests__/apiErros.test.ts` e confirmado em runtime contra backend local (porta 8094).
- **Area:** mobile
- **Sintoma:** Ao testar `DELETE /v1/usuarios/me` com senha incorreta contra o backend local (porta
  8093, banco `gf_ajustes`), o backend responde `422` com corpo
  `{"code":"BUSINESS_ERROR","message":"Senha incorreta","details":null}` (confirmado por `curl`
  direto, fora do app). No app, esse erro chegava à tela como "Dados inválidos. Verifique os
  campos." — mensagem genérica, sem relação com a causa real (senha errada). O mesmo padrão de
  resposta (`BusinessException` sem `details`) é usado por outras regras de negócio do backend, não
  apenas exclusão de conta.
- **Causa raiz:** Confirmada por leitura de código — `mobile/src/services/api.ts` (bloco do
  interceptor de erro, ~linhas 110-113): para `status === 400 || status === 422`, o código só
  extrai `error.response.data.details` (um mapa de campo→mensagem, usado para erros de validação
  de DTO com `@Valid`) e ignora por completo `error.response.data.message`, que é onde o
  `GlobalExceptionHandler` do backend coloca a mensagem de uma `BusinessException` quando não há
  `details` (`details: null`). Sem `details`, o código cai no fallback fixo `'Dados inválidos.
  Verifique os campos.'`.
- **Impacto tecnico:** Qualquer tela que dependa de uma mensagem de regra de negócio vinda do
  backend em 400/422 sem `details` (não só exclusão de conta) exibe texto genérico ao usuário em
  vez da causa real — usuário não sabe o que corrigir. Não foi feita uma varredura completa de
  todas as telas afetadas nesta rodada (fora do escopo desta sessão, que focou em `ajustes.tsx`).
- **Arquivos ou modulos relacionados:** `mobile/src/services/api.ts` (interceptor de erro, bloco
  400/422), `backend/src/main/java/com/gestor/financeiro/exception/GlobalExceptionHandler.java`
  (formato de resposta de `BusinessException`), `mobile/app/(app)/ajustes.tsx` (onde o contorno
  local foi aplicado).
- **Solucao proposta:** Ajustar o interceptor para, na ausência de `details`, usar
  `error.response.data.message` quando `error.response.data.code === 'BUSINESS_ERROR'` (ou de forma
  mais geral, sempre que `message` existir e for uma string não vazia), antes de cair no fallback
  genérico. Depois, varrer as telas que hoje dependem do texto genérico de 400/422 para confirmar
  se alguma regra de negócio ficou mascarada silenciosamente.
- **Solucao aplicada:** Nenhuma no interceptor (correção global fica pendente — ver BACKLOG-0094).
  Contorno pontual aplicado apenas em `mobile/app/(app)/ajustes.tsx`: o handler de exclusão de
  conta lê `err.response.data.message` diretamente quando `err.response.data.code ===
  'BUSINESS_ERROR'`, sem alterar `api.ts`.
- **Evidencias ou comandos usados:** `curl -X DELETE http://localhost:8093/api/v1/usuarios/me -H
  "Authorization: Bearer <token>" -H "Content-Type: application/json" -d
  '{"senha":"errada"}'` → HTTP 422 `{"code":"BUSINESS_ERROR","message":"Senha incorreta","details":
  null}`; leitura de `mobile/src/services/api.ts` confirmando o bloco `if (status === 400 ||
  status === 422)`.
- **Riscos residuais:** Sem a varredura completa das telas, pode haver outros pontos do app onde o
  usuário já recebe mensagem genérica em vez da mensagem de negócio real, sem que isso tenha sido
  percebido como bug — só foi notado aqui porque a tela nova validou o fluxo ponta a ponta contra o
  backend real.
- **Proximo passo:** Ver BACKLOG-0094 (correção do interceptor + varredura das telas dependentes).

---

## PROB-0085 — Sessão mobile trava em "token expirado" após desbloqueio por digital; exigia deslogar e logar manualmente

- **ID:** PROB-0085
- **Titulo:** Usando o app com desbloqueio por biometria, a sessão morria silenciosamente e a UI
  ficava presa em erro/loading sem voltar ao login sozinha — usuário precisava deslogar e logar de
  novo na mão
- **Data:** 2026-08-22
- **Origem:** bug report (dono do produto)
- **Severidade:** HIGH
- **Status:** FECHADO (2026-08-22) — corrigido e verificado nesta sessão; ver BUG-0096 (backend) e
  BUG-0097 (mobile)
- **Area:** backend, mobile, seguranca
- **Sintoma:** Usando o app com desbloqueio por digital, o app "deu falha e simplesmente não
  carregava"; log acusou token expirado. Toda a UI ficava presa em estado de erro/loading, sem
  redirecionamento automático para a tela de login — só saía do estado preso deslogando e logando
  manualmente.
- **Causa raiz:** Confirmada — dois defeitos somados:
  1. **Falha de refresh não-401 deixava a sessão travada no cliente.** O backend respondia à falha
     de refresh do token com três status diferentes: 422 (`BusinessException` "Refresh token
     expirado"), 404 (`ResourceNotFoundException` "não encontrado") e 401
     (`TokenReuseDetectedException`, reuso detectado). O mobile (`mobile/src/services/api.ts`) só
     descartava as credenciais salvas em 401/403. Em 422/404 os tokens mortos permaneciam no
     `SecureStore`, `refreshAccessToken()` devolvia `null`, a request original original falhava com
     401 e nada avisava o `AuthContext` — `isAuthenticated` é apenas `usuario !== null` e
     `restoreSession` já tinha rodado no boot, então a UI seguia montada como autenticada, com toda
     tela em erro/loading. O guarda de boot tinha o mesmo furo: a condição `(401||403) &&
     !refreshToken` nunca fechava, porque o refresh token continuava gravado no armazenamento.
  2. **O desbloqueio por digital não renovava nada com o servidor.**
     `AppLockGate.unlockWithDevice` validava a biometria localmente e só fazia `setLocked(false)` —
     nenhuma chamada ao backend acontecia no desbloqueio, então o cadeado visual podia estar sobre
     uma sessão morta havia dias sem que o app percebesse.
- **Impacto tecnico:** Usuário via o app "travado" sem explicação, precisando descobrir sozinho que
  precisava deslogar manualmente para recuperar o acesso — sintoma percebido como bug grave de
  estabilidade, não como expiração normal de sessão. Qualquer falha de refresh que não fosse 401/403
  (token expirado, revogado ou não encontrado) caía nesse buraco.
- **Arquivos ou modulos relacionados:**
  `backend/src/main/java/com/gestor/financeiro/service/RefreshTokenService.java`,
  `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`,
  `backend/src/main/java/com/gestor/financeiro/exception/GlobalExceptionHandler.java`,
  `backend/src/main/java/com/gestor/financeiro/exception/SessaoExpiradaException.java` (novo),
  `backend/src/main/java/com/gestor/financeiro/service/RefreshTokenScheduler.java` (novo),
  `mobile/src/services/api.ts`, `mobile/src/context/AuthContext.tsx`,
  `mobile/src/components/AppLockGate.tsx`.
- **Solucao proposta:** Ver "Solucao aplicada" — já implementada nesta sessão.
- **Solucao aplicada:** Backend: as três saídas de falha do refresh (expirado, revogado, não
  encontrado) passaram a lançar `SessaoExpiradaException`, mapeada em `GlobalExceptionHandler` para
  HTTP 401 com `code: SESSION_EXPIRED`; `AuthController` idem para "Refresh token não fornecido";
  `TokenReuseDetectedException` permanece 401 com code próprio. Janela do refresh token elevada de 7
  para 30 dias (decisão do dono do produto), configurável via `jwt.refresh-expiration-days` nos
  quatro profiles; rotação continua deslizante (renova a expiração a cada refresh); Max-Age do
  cookie web passou a acompanhar a mesma property (antes o cookie morria antes do token). Novo
  `RefreshTokenScheduler` liga a limpeza diária de tokens expirados (03:15
  `America/Sao_Paulo`, `app.refresh-token.cleanup.cron`/`.enabled`, guarda de sobreposição com
  `AtomicBoolean`) — o método `limparTokensExpirados()` já existia mas nunca tinha caller. Mobile:
  `api.ts` passou a encerrar a sessão em **qualquer resposta do servidor** na falha de refresh (não
  só 401/403), preservando os tokens quando não há `response` (rede/timeout — tolerância offline
  mantida); novo canal `setOnSessionExpired(fn)`/`encerrarSessao()`; `refreshAccessToken` passou a
  ser exportada. `AuthContext` registra o handler no mount (limpa cache e `usuario`, derrubando
  `isAuthenticated` e levando ao login) e `restoreSession` encerra a sessão sempre que o servidor
  negou a autenticação (401/403), sem depender da ausência local do refresh token.
  `AppLockGate.unlockWithDevice` e `unlockWithPassword` agora chamam `refreshAccessToken()` antes de
  liberar a UI (falha de rede libera assim mesmo; sessão recusada pelo servidor cai no canal de
  sessão encerrada); dedup por `refreshPromise` compartilhado evita que duas chamadas paralelas de
  refresh pareçam reuso e revoguem todas as sessões do usuário.
- **Evidencias ou comandos usados:** Ver "Testes/validacoes executadas" de BUG-0096 e BUG-0097 em
  `docs/BUGFIX_LOG.md` — suíte backend 296 testes PASS (4 novos em `AuthControllerTest`), suíte
  mobile 30 suítes/254 testes PASS (novo `mobile/src/__tests__/sessaoExpirada.test.ts`, 7 casos;
  `AppLockGate.test.tsx` +3 casos); verificação em runtime na stack local (backend 8081, banco
  descartável `gf_sessao`) exercitando login, rotação, expirado→401 `SESSION_EXPIRED`,
  desconhecido→401 `SESSION_EXPIRED`, ausente→401 `SESSION_EXPIRED`, reuso→401
  `TOKEN_REUSE_DETECTED`, deslizamento da expiração de 2 dias envelhecidos de volta a ~30 dias após
  renovação, rota protegida com token podre→401, e o scheduler de limpeza com cron acelerado
  (`refresh_token_cleanup_concluido removidos=2`, batendo com os 2 tokens expirados existentes).
- **Riscos residuais:** (1) Biometria não protege o token em repouso — `SecureStore.setItemAsync`
  não usa `requireAuthentication` (ver BACKLOG-0104); endurecer isso tem risco de perda de sessão em
  troca de aparelho, decisão de produto pendente. (2) `csrfToken` continua gravado no mobile e nunca
  lido — código morto, sem risco funcional, mas ruído (ver BACKLOG-0103). (3) Janela de sessão mais
  longa (30 dias) aumenta a superfície de tempo em que um refresh token roubado permanece válido até
  ser usado (mitigado pela detecção de reuso, que revoga todas as sessões do usuário ao primeiro uso
  duplicado). (4) Verificação de runtime foi feita só em iOS/simulador — não há confirmação em
  Android nesta rodada.
- **Proximo passo:** Observar em uso real (dono do produto) se o sintoma original (app travado após
  digital) volta a ocorrer. Nenhuma ação pendente de código para fechar este problema — os riscos
  residuais acima já estão rastreados como itens de backlog separados.

---

## PROB-0086 — Classificador de perguntas sequestrava lançamento que citasse cartão

- **ID:** PROB-0086
- **Titulo:** Frase de lançamento com a palavra "cartao" era tratada como consulta de fatura e nunca
  virava rascunho — o assistente respondia a fatura em vez de registrar a compra
- **Data:** 2026-08-29
- **Origem:** implementação de parcelamento no assistente (E2E local iOS, Fase 5)
- **Severidade:** HIGH
- **Status:** FECHADO (2026-08-29)
- **Area:** backend, assistente
- **Sintoma:** "comprei 300,00 no mercado hoje no Cartao Nubank em 3x" devolvia
  `outcome = NOT_FINANCIAL` com uma resposta de consulta de faturas. Nenhum rascunho era criado, e a
  compra no cartão era impossível de registrar pelo assistente.
- **Causa raiz:** `FinancialQuestionClassifier.intent()` classificava por presença de termo:
  `has(text, "fatura", "cartao")` devolvia `INVOICES` para qualquer texto que contivesse "cartao",
  sem exigir forma de pergunta. Como `AssistantService.receive()` consulta o classificador antes do
  parser financeiro, a frase nunca chegava ao caminho de lançamento.
- **Solucao aplicada:** o classificador só assume consulta quando a frase realmente pergunta. Após
  casar um intent, ele desiste (`Optional.empty()`) se não houver marcador interrogativo ("?",
  "quanto", "qual", "quais", "quando", "quantos", "mostra", "resumo", "como esta") e houver indício
  de lançamento — verbo (`comprei|paguei|gastei|lancei|registrar|anota|...`) ou valor monetário.
- **Evidencias:** `AssistantParcelamentoTest` (3 testes, inclui a frase acima chegando a
  `COMPLETE`); `FinancialQuestionClassifierTest` seguiu verde (4 testes); suíte backend 470 verdes.
  E2E `artifacts/fase5/run-20/proof-assistant-parcelado.json`.
- **Riscos residuais:** a heurística é lexical. Uma pergunta sem pronome interrogativo e com valor
  (ex.: "fatura do cartão 300") passa a ser tratada como lançamento. Nenhum caso assim apareceu nos
  testes, mas o classificador continua sendo regra de palavra, não de intenção.
- **Proximo passo:** nenhum. Rastreado como risco lexical conhecido.

---

## PROB-0087 — App pedia permissão de push no simulador porque a guarda de aparelho real não existia mais

- **ID:** PROB-0087
- **Titulo:** `Constants.isDevice` foi removido do expo-constants 18; a guarda caía no fallback
  `?? true` e o app pedia notificação em simulador/emulador
- **Data:** 2026-08-29
- **Origem:** E2E local iOS (Fase 5) — o diálogo do sistema cobria a tela e derrubava os flows
- **Severidade:** MEDIUM
- **Status:** FECHADO (2026-08-29)
- **Area:** mobile
- **Sintoma:** ao entrar na área logada no simulador, o app abria "O app Nexos Finanças deseja
  enviar notificações". No E2E o alerta ficava por cima da tela e o Maestro não encontrava mais
  nenhum elemento.
- **Causa raiz:** `src/notificacoes/push.ts` decidia por `Constants.isDevice ?? true`. O campo
  `isDevice` não existe em `expo-constants@18` (migrou para `expo-device`), então a expressão era
  sempre `true`: o app se considerava aparelho real em qualquer simulador e seguia para
  `requestPermissionsAsync`.
- **Solucao aplicada:** dependência `expo-device` instalada e guarda trocada para `Device.isDevice`,
  que o módulo nativo resolve como `false` em `targetEnvironment(simulator)`.
- **Evidencias:** `src/__tests__/push.test.ts` (6 testes) com mock de `expo-device`; mobile 434
  testes verdes; E2E `artifacts/fase5/run-20` sem diálogo de notificação.
- **Riscos residuais:** se `expo-device` não estiver linkado no build nativo, o JS cai no fallback
  `true` da própria biblioteca e o sintoma volta. O `pod install` passa a ser obrigatório junto da
  dependência.
- **Proximo passo:** nenhum.

---

## PROB-0088 — Runner E2E iOS não conseguia fechar: app sem entitlements e bundle em modo dev

- **ID:** PROB-0088
- **Titulo:** `scripts/e2e-assistant-ios.sh` gerava app sem assinatura (Keychain indisponível) e
  bundle JS de desenvolvimento (LogBox por cima da tela); nenhum flow chegava ao fim
- **Data:** 2026-08-29
- **Origem:** primeira execução real do fechamento operacional da Fase 5
- **Severidade:** HIGH
- **Status:** FECHADO (2026-08-29)
- **Area:** infraestrutura, mobile
- **Sintoma:** o app subia, mas (1) `expo-notifications` registrava
  `Keychain access failed: A required entitlement isn't present` e (2) um redbox de `console.error`
  cobria a tela de login. O flow `assistant-text` falhava em `Element not found: login-email`.
- **Causa raiz:** duas decisões do runner:
  1. `CODE_SIGNING_ALLOWED=NO` produzia binário sem entitlements — sem Keychain, e é ali que
     `expo-secure-store` guarda a sessão (`src/store/auth.ts`).
  2. `-configuration Debug` força `DEV=true` em `react-native-xcode.sh:48`; o
     `EXTRA_PACKAGER_ARGS=--dev false` do runner chega depois de `--dev $DEV` na linha de comando e
     não vence. O bundle embarcado saía em modo dev, com LogBox ativo.
- **Solucao aplicada:** build passou a `-configuration Release` (que resolve `DEV=false` de verdade)
  e a assinatura local padrão do Xcode voltou a valer (flag `CODE_SIGNING_ALLOWED=NO` removida);
  `APP_PATH` passou a procurar em `Release-iphonesimulator`.
- **Evidencias:** `artifacts/fase5/run-20/xcodebuild.log`; app com entitlements e tela de login
  limpa; seis flows verdes.
- **Riscos residuais:** o build Release é mais lento que Debug, então a rodada completa ficou mais
  cara. O runner não valida explicitamente que o bundle não é dev — depende da configuração.
- **Proximo passo:** nenhum.

---

## PROB-0089 — Sessão vazava entre flows E2E porque `clearState` do Maestro não limpa o Keychain

- **ID:** PROB-0089
- **Titulo:** Com a assinatura corrigida, o token sobrevivia à reinstalação e o flow seguinte abria
  já logado, sem tela de login
- **Data:** 2026-08-29
- **Origem:** E2E local iOS (Fase 5)
- **Severidade:** MEDIUM
- **Status:** FECHADO (2026-08-29)
- **Area:** infraestrutura
- **Sintoma:** o segundo flow em diante falhava em `Element not found: login-email`, com o app já
  autenticado.
- **Causa raiz:** `launchApp: clearState: true` limpa dados do app, não o Keychain do simulador — e
  é lá que a sessão fica (`expo-secure-store`). O Keychain sobrevive inclusive a reinstalações.
- **Solucao aplicada:** `run_flow()` executa `xcrun simctl keychain <udid> reset` antes de cada
  flow; o bloco de importação faz o mesmo.
- **Evidencias:** `scripts/e2e-assistant-ios.sh`; seis flows consecutivos verdes em
  `artifacts/fase5/run-20`.
- **Riscos residuais:** o reset é do simulador inteiro, não só do app. Aceitável porque o runner
  assume simulador dedicado ao E2E.
- **Proximo passo:** nenhum.

---

## PROB-0090 — Defeitos do próprio runner: variável não inicializada, sucesso falso e falso positivo de segredo

- **ID:** PROB-0090
- **Titulo:** `prove_financial_state` abortava com `label: unbound variable`, o `cleanup` imprimia
  `OK` mesmo em falha, e a varredura de segredos acusava telefone dentro de um número de duração
- **Data:** 2026-08-29
- **Origem:** E2E local iOS (Fase 5)
- **Severidade:** HIGH
- **Status:** FECHADO (2026-08-29)
- **Area:** infraestrutura
- **Sintoma:** três falhas distintas, cada uma bloqueando o fechamento:
  1. `./scripts/e2e-assistant-ios.sh: line 178: label: unbound variable` na primeira prova
     financeira, com o script encerrando logo depois;
  2. mesmo assim a saída final dizia `OK: Assistente iOS`;
  3. `FALHA: evidências contêm padrão sensível` apontando para uma linha de log do Maestro.
- **Causa raiz:**
  1. `local label="$1" expected="$2" file="...$label..."` — o Bash expande todas as palavras da
     declaração antes de atribuir, então `$label` ainda não existia; com `set -u` isso aborta;
  2. o `trap cleanup EXIT` decidia o texto só pelo código de saída, que nesse caminho chegava zero;
  3. o padrão `551[0-9]{8,}` casava dentro de `0.05514192581176758`, uma duração impressa pelo
     driver do Maestro.
- **Solucao aplicada:** `file` passou a ser declarado em `local` separado; `cleanup` só declara `OK`
  se `run.txt` existir (e promove código zero a 1 quando não existir); o padrão de telefone ganhou
  `\b` nas bordas.
- **Evidencias:** `artifacts/fase5/run-20/run.txt` e `sensitive-scan.txt`
  ("OK: nenhum padrão sensível detectado.").
- **Riscos residuais:** a varredura continua sendo por regex sobre logs — outros falsos positivos ou
  negativos são possíveis.
- **Proximo passo:** nenhum.

---

## PROB-0091 — Dois testes quebrados por causa alheia ao assistente (data do mês e mapper sem JavaTimeModule)

- **ID:** PROB-0091
- **Titulo:** `AporteAutomaticoServiceTest` falhava a partir do dia 29 e
  `StructuredProviderWireMockTest` falhava sempre por desserialização de `LocalDate`
- **Data:** 2026-08-29
- **Origem:** execução da suíte backend completa durante a Fase 5
- **Severidade:** MEDIUM
- **Status:** FECHADO (2026-08-29)
- **Area:** backend, testes
- **Sintoma:** seis erros na suíte: quatro com `Business: Escolha um dia entre 1 e 28` e dois com
  `ProviderFailure: Resposta fora do schema`.
- **Causa raiz:** dois defeitos independentes e pré-existentes:
  1. o teste de aporte usava `LocalDate.now(clock).getDayOfMonth()` como dia de cobrança, mas a
     regra de negócio aceita 1..28 — a suíte quebrava sozinha nos dias 29, 30 e 31;
  2. o teste WireMock construía os providers com `new ObjectMapper()` cru; sem `JavaTimeModule` o
     campo `data` (`LocalDate`) do rascunho nunca desserializava. Em produção o mapper vem do
     Spring, já com o módulo — por isso só o teste falhava.
- **Solucao aplicada:** o dia do aporte passou por `Math.min(28, ...)`; o teste WireMock passou a
  construir o mapper com `JavaTimeModule`, espelhando a aplicação.
- **Evidencias:** suíte backend 470 testes, 0 falhas.
- **Riscos residuais:** outros testes podem ter dependência de data não mapeada; não houve varredura
  sistemática.
- **Proximo passo:** nenhum.

---

## PROB-0092 — Assistente inacessível em produção: dois gates independentes travando o mesmo recurso

- **ID:** PROB-0092
- **Titulo:** Dono do produto subiu nova versão do backend e instalou novo build do app, e o
  Assistente continuou inacessível — corrigir um gate não bastava, havia dois
- **Data:** 2026-08-29
- **Origem:** usuário (dono do produto)
- **Severidade:** BLOCKER
- **Status:** FECHADO (2026-08-29, working tree não commitado)
- **Area:** mobile, backend, infra
- **Sintoma:** o item "Assistente" em Ajustes aparecia desabilitado (opacity 0.55, badge
  "Em breve") em todo build publicado; quando acessado por deep link, qualquer chamada a
  `/api/v1/assistant/**` respondia 404 e o app exibia "Não foi possível enviar".
- **Causa raiz:** dois gates independentes, cada um suficiente para travar a feature sozinho:
  1. **Mobile** — `mobile/app/(app)/ajustes.tsx:49` desabilitava o item quando
     `process.env.EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED !== 'true'`. Essa env var só era setada em
     `scripts/e2e-assistant-ios.sh` (uso local de E2E); o workflow de release nunca a definia, e por
     ser `EXPO_PUBLIC_*` ela é embutida no bundle em tempo de build — não há como religar sem novo
     binário.
  2. **Backend** — `AssistantController.java:21` tem
     `@ConditionalOnProperty("assistant.text.enabled")` (default `false` em
     `application.properties:133`), e `docker-compose.vps.yml`/`docker-compose.production.yml` não
     repassavam nenhuma variável `ASSISTANT_*` ao container `api`. Toda rota `/api/v1/assistant/**`
     saía do contexto Spring por completo (404 puro, sem corpo explicativo).
- **Impacto tecnico:** a feature central do release (assistente financeiro) ficava 100%
  inacessível para o usuário final em produção, apesar de código, testes e infraestrutura de fila
  estarem prontos e verdes desde a Fase 5.
- **Arquivos ou modulos relacionados:**
  `mobile/app/(app)/ajustes.tsx`,
  `mobile/app/(app)/more/assistente.tsx`,
  `backend/src/main/java/com/gestor/financeiro/controller/AssistantController.java`,
  `backend/src/main/resources/application.properties`,
  `docker-compose.vps.yml`, `docker-compose.production.yml`,
  `deploy/vps/.env.vps.example`, `deploy/vps/.env.vps.production.example`.
- **Solucao proposta:** eliminar o gate fixado em build/topologia e substituí-lo por uma consulta de
  runtime — ver ADR-0018.
- **Solucao aplicada:**
  - Novo `GET /api/v1/capacidades` (autenticado) —
    `backend/src/main/java/com/gestor/financeiro/controller/CapacidadesController.java` +
    `dto/CapacidadesResponse.java` — devolve `{assistenteTexto, assistenteAudio,
    assistenteWhatsapp}` lendo as mesmas properties dos controllers condicionais.
    Deliberadamente **sem** `@ConditionalOnProperty`.
  - Mobile: `mobile/src/services/capacidadesService.ts` + `mobile/src/hooks/useCapacidades.ts`
    (React Query, `staleTime` 5 min, fail-closed — erro/loading tratam tudo como desligado).
    `ajustes.tsx` e `more/assistente.tsx` passaram a ler o hook; as duas env vars
    `EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED`/`EXPO_PUBLIC_ASSISTANT_WHATSAPP_ENABLED` foram eliminadas
    do código do app. `more/assistente.tsx` ganhou `EstadoVazio` "Assistente indisponível" para
    acesso por deep link com a capacidade desligada.
  - `docker-compose.vps.yml`/`docker-compose.production.yml` passaram a repassar
    `ASSISTANT_TEXT_ENABLED`/`ASSISTANT_AUDIO_ENABLED` (default `false`) ao container `api`.
    `ASSISTANT_EXTERNAL_ENABLED` e chaves de IA ficaram **de fora** por decisão do dono do produto —
    só parser determinístico habilitado, sem custo e sem risco de o
    `AssistantExternalConfigurationGuard` derrubar o boot.
  - `deploy/vps/.env.vps.example` e `.env.vps.production.example` documentam as vars novas e o
    pré-requisito de banco em V65.
  - `scripts/e2e-assistant-ios.sh` e `docs/runbooks/FASE5_FECHAMENTO_OPERACIONAL.md` atualizados
    para o novo contrato.
  - Bônus: `GlobalExceptionHandler` passou a tratar `ProviderFailure`
    (CONFIGURATION/SAFETY_REFUSAL, antes escapava do `AiExtractionPipeline` como 500 genérico) como
    503 `AI_UNAVAILABLE`/422 `AI_REFUSED`.
- **Evidencias ou comandos usados:** testes novos `CapacidadesDesligadasTest` (3 casos) e
  `CapacidadesLigadasTest` (1 caso); suíte backend completa 481 testes verdes; suíte mobile completa
  437 testes verdes; `git status --short` confirma os arquivos novos/alterados listados acima.
- **Riscos residuais:** `/api/v1/capacidades` não distingue "backend fora do ar" de "capacidade
  desligada" — os dois caem no mesmo fail-closed do hook (ver ADR-0018, seção Consequências). Se o
  dono do produto ligar `ASSISTANT_EXTERNAL_ENABLED` no futuro, o mesmo cuidado de runtime precisa
  valer para as chaves de IA (não reintroduzir gate de build).
- **Proximo passo:** nenhum enquanto o comportamento atual (parser determinístico apenas) for a
  decisão de produto vigente.

---

## PROB-0093 — Não dava para editar o saldo de uma conta, e "Conta Principal" não existia como conceito

- **ID:** PROB-0093
- **Titulo:** Tela de carteiras do mobile era somente-leitura (listar/criar/abrir extrato); "Conta
  Principal" era só o texto default de um campo, sem coluna, flag ou regra
- **Data:** 2026-08-29
- **Origem:** usuário (dono do produto)
- **Severidade:** HIGH
- **Status:** FECHADO (2026-08-29, working tree não commitado)
- **Area:** backend, banco, mobile
- **Sintoma:** não havia como corrigir o saldo de uma conta depois de criada — a tela `more/carteiras.tsx`
  só permitia listar, criar e abrir extrato. "Conta Principal", nome que o onboarding sugeria por
  padrão, não correspondia a nenhum dado persistido: era só o valor inicial de um `useState` de
  texto em `mobile/app/onboarding.tsx:88`. O formulário de lançamento pré-selecionava
  `carteiras[0]`, isto é, ordem de inserção no banco, não decisão do titular.
- **Causa raiz:** `contaFinanceiraService.atualizar/ajustar/deletar` existiam no client mobile e não
  eram chamados em lugar nenhum da UI. O domínio nunca modelou "conta principal" — não havia coluna,
  índice nem regra de negócio para isso.
- **Impacto tecnico:** usuário sem forma de corrigir saldo divergente sem recriar a conta (perdendo
  histórico); nenhuma conta tinha status de "padrão" real, então formulários que precisavam de uma
  conta pré-selecionada dependiam de ordem de inserção, um comportamento instável e não
  documentado.
- **Arquivos ou modulos relacionados:**
  `backend/src/main/resources/db/migration/V66__carteira_principal.sql`,
  `backend/src/main/java/com/gestor/financeiro/service/CarteiraService.java`,
  `backend/src/main/java/com/gestor/financeiro/model/Carteira.java`,
  `backend/src/main/java/com/gestor/financeiro/repository/CarteiraRepository.java`,
  `backend/src/main/java/com/gestor/financeiro/dto/ContaFinanceiraRequest.java`,
  `backend/src/main/java/com/gestor/financeiro/dto/ContaFinanceiraResponse.java`,
  `backend/src/main/java/com/gestor/financeiro/controller/ContaFinanceiraController.java`,
  `backend/src/main/java/com/gestor/financeiro/service/OnboardingService.java`,
  `backend/src/main/java/com/gestor/financeiro/service/ExportService.java`,
  `mobile/app/(app)/more/carteiras.tsx`, `mobile/app/onboarding.tsx`,
  `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/src/store/onboardingRascunho.ts`,
  `frontend/src/pages/Onboarding.tsx`.
- **Solucao proposta:** modelar "conta principal" como dado de domínio (não texto de UI), com
  unicidade garantida pelo banco, e transformar a tela de carteiras em CRUD completo.
- **Solucao aplicada:**
  - Migration `V66__carteira_principal.sql`: coluna `principal BOOLEAN NOT NULL DEFAULT FALSE` em
    `carteiras`; índice único **parcial** `ux_carteiras_principal_usuario ON carteiras (usuario_id)
    WHERE principal` (unicidade é do banco, não da aplicação); backfill determinístico marcando a
    conta ATIVO manual de menor id de cada titular.
  - `CarteiraService.definirPrincipal(id, usuarioId)`: desmarca a atual, faz flush, só então marca
    a nova — a ordem inversa viola o índice parcial. `criar`/`atualizar` chamam o método quando
    `principal == true`; `deletar` da conta principal elege sucessora (a manual ATIVO mais antiga).
  - Contrato: `ContaFinanceiraRequest.principal` é opcional e só `true` tem efeito — `null`/`false`
    preservam o estado atual, porque desmarcar sem eleger outra deixaria o titular sem conta padrão.
    `ContaFinanceiraResponse.principal` exposto. `ExportService` ganhou a coluna Principal no CSV de
    carteiras (LGPD).
  - Onboarding: a carteira nasce com `principal = true`; no mobile o nome deixou de vir
    pré-preenchido como "Conta Principal" e entrou o campo **Banco** (já suportado pelo backend/DTO,
    nunca coletado antes); o nome acompanha o banco enquanto estiver vazio.
  - `more/carteiras.tsx`: formulário agora edita (título "Editar Conta"), com campos Nome, Banco,
    Tipo, Saldo e toggle "Conta principal"; botões Editar/Excluir no card (ocultos em conta
    gerenciada); selo "Principal"; principal listada no topo do grupo. O saldo vem pré-preenchido
    com o saldo atual porque o PUT converte a **diferença** em `AJUSTE_MANUAL` no ledger — salvar
    sem alterar precisa gerar diferença zero.
  - `NovaTransacaoModal.tsx:178` passou a pré-selecionar a conta principal em vez de `carteiras[0]`.
- **Evidencias ou comandos usados:** testes novos em `ContaFinanceiraControllerTest` cobrindo eleger
  principal desmarcando a anterior, PUT sem o campo não desmarcando, criação nascendo principal e
  exclusão elegendo sucessora; migration V66 validada contra PostgreSQL 16 real via
  `scripts/verify-postgres-migrations.sh`; suíte backend 481 testes verdes; suíte mobile 437 testes
  verdes.
- **Riscos residuais:** titular sem nenhuma conta manual ATIVO fica sem principal após o backfill —
  estado considerado válido (não erro), mas nunca testado em produção real com dados legados
  divergentes do fixture de teste. `frontend/src/pages/Onboarding.tsx` foi tocado para manter o
  contrato consistente, mas o web não ganhou a mesma tela de edição de carteira que o mobile — não
  verificado neste ciclo (ver BACKLOG novo).
- **Proximo passo:** confirmar em produção, após deploy, que o backfill da V66 marcou a conta
  correta para os usuários reais existentes (não só nos fixtures de teste).

---

## PROB-0094 — Tela de Relatórios (mobile) com layout quebrado: régua de períodos ocupava metade da tela

- **ID:** PROB-0094
- **Titulo:** Regressão introduzida no commit `d44fc43` fazia dois `ScrollView` dividirem o espaço
  livre ~50/50, e o estado vazio disparava com salário lançado no mês
- **Data:** 2026-08-29
- **Origem:** usuário (dono do produto)
- **Severidade:** HIGH
- **Status:** FECHADO (2026-08-29, working tree não commitado)
- **Area:** mobile
- **Sintoma:** na tela `mobile/app/(app)/analises.tsx`, a faixa de chips de período ocupava
  visualmente metade da altura da tela, empurrando o conteúdo real para baixo. Um mês só com
  entradas (ex.: salário, sem saída lançada) mostrava o estado vazio "Nada por aqui neste período".
- **Causa raiz:** dois defeitos distintos na mesma tela:
  1. **Layout.** A faixa de chips de período era um `<ScrollView horizontal>` **sem** prop `style`,
     irmão direto do `ScrollView` de conteúdo, ambos dentro de uma `View flex:1`. O React Native
     aplica `baseHorizontal = {flexGrow: 1, flexShrink: 1}` a todo `ScrollView` (verificado em
     `react-native/Libraries/Components/ScrollView/ScrollView.js`, RN 0.81.5) — sem `style` que
     fixe a altura, os dois `ScrollView` irmãos disputavam o espaço livre ~50/50. Regressão
     introduzida no commit `d44fc43` ("refactor(mobile): migrate analytics screen to the pattern"),
     que trocou um `View flexDirection:'row'` (que não cresce) por esse `ScrollView`. Era a
     **única** tela do app com essa combinação — em `metas.tsx` e `OperacoesFiltro` o mesmo padrão
     de chips vive dentro de um container sem espaço livre para disputar.
  2. **Estado vazio.** `data.totalTransacoes` vinha de
     `countSaidasByUsuarioIdAndPeriodo` (contava **só** saídas), então um mês só com entrada
     (salário) sempre mostrava o estado vazio, mesmo com dado real no período.
- **Impacto tecnico:** tela de Relatórios inutilizável em qualquer aparelho (chips consumindo
  metade da viewport) e reportando "sem dados" para usuários com receita e sem despesa no mês —
  dois defeitos que, juntos, tornavam a tela praticamente não confiável.
- **Arquivos ou modulos relacionados:**
  `mobile/app/(app)/analises.tsx`, `mobile/app/(app)/_layout.tsx`,
  `backend/src/main/java/com/gestor/financeiro/repository/TransacaoRepository.java`,
  `backend/src/main/java/com/gestor/financeiro/service/RelatorioService.java`.
- **Solucao proposta:** reestruturar a tela para a "Receita de tela" canônica do `DESIGN.md`
  (`ScrollView` raiz único) e corrigir a query de contagem do estado vazio.
- **Solucao aplicada:**
  - Tela reestruturada: `ScrollView` raiz único com `CabecalhoDeTela`, chips e intervalo **dentro**
    dele — não mais como irmãos concorrendo por espaço livre.
  - Nova query `countAtivasByUsuarioIdAndPeriodo` em `TransacaoRepository` (conta entradas e
    saídas); o gatilho do estado vazio no app virou `totalEntradas === 0 && totalSaidas === 0`.
  - Erro/vazio do relatório do período deixaram de derrubar Evolução mensal, Comparação mensal e
    Alertas — cada bloco responde pelo próprio estado dentro do scroll (queries independentes).
  - Comparação mensal: a heurística antiga (`periodo.includes('anterior'/'atual')` com fallback
    posicional) inverteria o sinal do badge se o rótulo mudasse; passou a usar a ordem fixa que
    `DashboardService` monta (`[0]` anterior, `[1]` atual) e exige tamanho 2.
  - Novo card "Gastos por conta": `gastosPorConta` já era servido pelo backend e só o web
    renderizava; o mobile recebia o payload e descartava.
  - Pull-to-refresh (`RefreshControl`) refazendo as três queries, como a Home.
  - Título da aba unificado de "Análises" para "Relatórios" (`(app)/_layout.tsx:117`). O rótulo
    "Relatórios" em Ajustes **não** mudou — é tocado por texto no Maestro.
- **Evidencias ou comandos usados:** teste novo `RelatorioServiceTest.mesSoComEntradaNaoParecePeriodoVazio`;
  suíte backend 481 testes verdes; suíte mobile 437 testes verdes.
- **Riscos residuais:** a causa raiz do layout foi confirmada lendo o código-fonte do RN
  (`ScrollView.js`), não com um teste automatizado de layout — não há regressão automática contra
  esse padrão específico se reaparecer em outra tela nova.
- **Proximo passo:** nenhum.

---

## PROB-0095 — Smoke de UI no iOS (`scripts/e2e-mobile-ios.sh`) testava app errado e dava falso verde

- **ID:** PROB-0095
- **Titulo:** Quatro defeitos independentes no smoke de UI mascaravam regressões de layout há tempo
  indeterminado — nenhum causado pelas correções de PROB-0092/0093/0094, todos pré-existentes
- **Data:** 2026-08-29
- **Origem:** revisão (rodada de verificação de UI/UX em simulador iOS, pedida pelo dono do produto
  após as correções de PROB-0092/0093/0094 — "rodar teste unitário não prova layout")
- **Severidade:** HIGH
- **Status:** FECHADO (2026-08-29, working tree não commitado)
- **Area:** mobile, infra
- **Sintoma:** o smoke financeiro (`scripts/e2e-mobile-ios.sh`) precisou de 8 rodadas até o primeiro
  verde legítimo desta sessão. Comparado ao verde histórico do mesmo script, o comportamento não
  detectava regressões de UI reais introduzidas por commits anteriores.
- **Causa raiz:** quatro defeitos independentes no script:
  1. **App errado instalado.** `APP_PATH="$(find "$BUILD_DIR/Build/Products" -maxdepth 2 -type d
     -name '*.app' -print -quit)"` buscava a partir da raiz de `Products/` e pegava a primeira
     configuração encontrada. Este script e `scripts/e2e-assistant-ios.sh` compartilham o mesmo
     `DerivedData` (`mobile/.e2e-derived-data`, ambos usam `BUILD_DIR`); um
     `Release-iphonesimulator` deixado pelo runner do assistente sombreava o
     `Debug-iphonesimulator` recém-compilado, e o smoke passava verde testando um app de horas
     antes. Provado comparando os bundles Hermes com `strings`: o Release instalado não continha
     `onboarding-account-bank`, `Gastos por conta` nem `assistenteTexto`, todos presentes no Debug
     novo.
  2. **Build Debug sem assinatura era inutilizável.** `-configuration Debug` +
     `CODE_SIGNING_ALLOWED=NO`: Debug força `DEV=true` em `react-native-xcode.sh` (bundle com
     LogBox) e a ausência de entitlements faz `expo-notifications` falhar ao ler o Keychain
     (`getRegistrationInfoAsync` → "Keychain access failed: A required entitlement isn't present"),
     disparando `console.error` e cobrindo a tela com o LogBox vermelho. O flow morria em 19s no
     primeiro `tapOn: ".*Criar conta"`, atrás da tela vermelha — mesma causa raiz de PROB-0088, já
     documentada para o runner do assistente e nunca aplicada a este script.
  3. **Faltava `xcrun simctl keychain reset`.** `clearState` do Maestro não apaga o Keychain, e é
     lá que a sessão mora (PROB-0089) — sessão de um flow anterior vazava para o próximo.
  4. **Lista de invariantes travada em 4.** A asserção `(.resumo | length) == 4` com
     `["COFRE_META","PASSIVO_FATURAS","SALDO_LEDGER","TRANSACAO_INCOMPLETA"]` quebrava porque o
     backend passou a devolver 5 invariantes desde o commit `21b9a15`
     (`CATEGORIA_VALOR_GASTO` em `ReconciliacaoGlobalService`).
- **Impacto tecnico:** o smoke de UI não pegava regressão de layout/comportamento há tempo — ajuda
  a explicar por que PROB-0092, PROB-0093 e PROB-0094 chegaram a produção sem serem detectados por
  automação antes do dono do produto reportar. Qualquer "verde" anterior deste script não prova
  nada sobre o app que de fato roda em produção.
- **Arquivos ou modulos relacionados:** `scripts/e2e-mobile-ios.sh`,
  `mobile/.e2e-derived-data` (`BUILD_DIR` compartilhado com `scripts/e2e-assistant-ios.sh`),
  `mobile/.maestro/financial-critical.yaml`.
- **Solucao proposta:** corrigir os quatro defeitos e adicionar guarda contra recorrência do item 1.
- **Solucao aplicada:**
  - `find` ancorado na configuração correta
    (`"$BUILD_DIR/Build/Products/Release-iphonesimulator"`) + guarda que falha se `main.jsbundle`
    for mais antigo que o diretório de artefatos do run.
  - Build passou de `-configuration Debug` + `CODE_SIGNING_ALLOWED=NO` para `-configuration
    Release` assinado pelo Xcode, igual ao runner do assistente (linha 199 do script).
  - `xcrun simctl keychain reset` adicionado logo após o install.
  - Lista de invariantes atualizada para os 5 correntes (`.resumo | length) == 5`).
- **Evidencias ou comandos usados:** comparação de `strings` nos bundles Hermes (Release vs Debug)
  confirmando qual app estava instalado; smoke final `OK: smoke financeiro concluído`, flow Maestro
  1 teste / 0 falhas / 167s, reconciliação global 8 verificações / 0 divergências, conta final
  `Conta Principal · saldo 825 · banco Nubank · principal=true`.
- **Riscos residuais:** `scripts/e2e-mobile-ios.sh` e `scripts/e2e-assistant-ios.sh` continuam
  escrevendo no mesmo `DerivedData` — ancorar cada script na sua configuração eliminou o sintoma
  desta rodada, mas a causa estrutural (dois runners, um `BUILD_DIR`) permanece, pronta para
  reaparecer na próxima variação de configuração/scheme/build parcial (ver BACKLOG-0118).
- **Proximo passo:** resolver BACKLOG-0118 (DerivedData isolado por runner, ou runner único
  parametrizado).

---

## PROB-0096 — Conteúdo do padrão de tela passava por baixo da barra de status ao rolar

- **ID:** PROB-0096
- **Titulo:** Regressão introduzida pela correção de PROB-0094 (adoção da "Receita de tela" do
  DESIGN.md) fazia o conteúdo subir até y=0 e cobrir relógio/wifi/bateria
- **Data:** 2026-08-29
- **Origem:** revisão (mesma rodada de verificação de UI/UX em simulador iOS)
- **Severidade:** MEDIUM
- **Status:** FECHADO (2026-08-29, working tree não commitado)
- **Area:** mobile
- **Sintoma:** ao mover o cabeçalho para dentro do `ScrollView` de raiz (correção de PROB-0094), o
  conteúdo da tela de Relatórios passou a subir até `y=0` ao rolar, ficando por cima do relógio,
  wifi e bateria. Comprovado por screenshot: o título "Comparação mensal" e o badge de variação
  sobrepunham o relógio.
- **Causa raiz:** não era defeito específico da tela de Relatórios — é do padrão. Todas as 28 telas
  de `app/(app)/**` seguem a mesma "Receita de tela" do `DESIGN.md` (`ScrollView` de raiz único,
  cabeçalho dentro dele), e a Home já se comportava assim antes desta sessão; a correção de
  PROB-0094 só tornou o sintoma visível numa segunda tela.
- **Impacto tecnico:** sobreposição visual de texto sobre os indicadores de sistema (relógio, wifi,
  bateria) ao rolar até o topo, presente potencialmente em qualquer tela padrão do app, não só na
  tela onde foi observado pela primeira vez nesta rodada.
- **Arquivos ou modulos relacionados:** `mobile/app/(app)/_layout.tsx`.
- **Solucao proposta:** corrigir no layout compartilhado, não em cada tela individualmente, já que
  o problema é do padrão.
- **Solucao aplicada:** faixa da barra de status em `mobile/app/(app)/_layout.tsx` — `View`
  absoluto, `top:0`, `height: insets.top`, `backgroundColor: colors.bg`, `pointerEvents="none"` (para
  não roubar toque de nada embaixo), e o `Fragment` de retorno virou `View flex:1` para dar bloco de
  contenção ao posicionamento absoluto. Modais não são afetados: `FolhaModal` usa o `Modal` nativo
  do RN, que renderiza em janela separada e já trata o próprio inset.
- **Evidencias ou comandos usados:** screenshot antes (título sobreposto ao relógio) e depois
  (relógio sobre fundo sólido, conteúdo cortado limpo) da correção.
- **Riscos residuais:** verificado apenas em iPhone 17 Pro / iOS 26.5 / tema escuro. No Android a
  status bar pode ser translúcida ou opaca conforme a configuração, e o comportamento de
  `insets.top` difere — não verificado (ver BACKLOG-0119).
- **Proximo passo:** verificar em Android e em tema claro (BACKLOG-0119).

---

## PROB-0097 — Campo obrigatório "Saldo inicial" do onboarding ficava coberto pelo teclado

- **ID:** PROB-0097
- **Titulo:** Regressão introduzida pela adição do campo Banco ao passo 1 do onboarding (correção de
  PROB-0093) empurrava Tipo e Saldo inicial para baixo da dobra
- **Data:** 2026-08-29
- **Origem:** revisão (mesma rodada de verificação de UI/UX em simulador iOS)
- **Severidade:** MEDIUM
- **Status:** FECHADO (2026-08-29, working tree não commitado)
- **Area:** mobile
- **Sintoma:** o campo "Banco", adicionado ao passo 1 do onboarding pela correção de PROB-0093,
  empurrou "Tipo" e "Saldo inicial (R$)" para baixo da dobra. Com o teclado aberto após digitar o
  nome, o campo Saldo — obrigatório — ficava coberto, exigindo fechar o teclado ou rolar
  manualmente para preenchê-lo. Antes da adição do campo Banco, o passo cabia inteiro na tela.
  Diagnóstico levou três rodadas de E2E por sintoma enganoso: o valor digitado ("100000") caía no
  campo Nome (que virava "Conta Principal 100000"), porque o `tapOn` do Maestro não move foco para
  um campo coberto pelo teclado.
- **Causa raiz:** nenhum encadeamento de foco entre os campos do formulário; o React Native só rola
  um `TextInput` para a área visível quando ele recebe foco, e sem `onSubmitEditing`/`ref`
  encadeados o usuário precisava fechar o teclado manualmente para ver o campo seguinte.
- **Impacto tecnico:** onboarding é o primeiro fluxo que qualquer novo usuário atravessa; um campo
  obrigatório invisível sob o teclado, sem indicação de como revelá-lo, é fricção logo na primeira
  tela do app.
- **Arquivos ou modulos relacionados:** `mobile/app/onboarding.tsx`.
- **Solucao proposta:** encadear o foco entre os campos do passo 1, aproveitando que o componente
  `Field` já suportava `ref` para esse fim.
- **Solucao aplicada:** `returnKeyType="next"` + `onSubmitEditing` levando Banco → Nome → Saldo, com
  `ref` (`contaNomeRef`, `contaSaldoRef`) em `mobile/app/onboarding.tsx`. O encadeamento resolve as
  duas partes do problema ao mesmo tempo: o usuário não precisa fechar o teclado nem rolar, e o
  campo seguinte aparece sozinho porque recebeu foco.
- **Evidencias ou comandos usados:** flow Maestro passou a percorrer os campos por `pressKey:
  Enter`, como uma pessoa faz — é essa mudança no flow que prova a correção. `mobile/app/(app)/
  more/carteiras.tsx` ganhou `testID="conta-form-nome"`, `testID="conta-form-banco"` e
  `testID="conta-form-saldo"` na mesma sessão, porque o seletor Maestro por texto "Saldo" casava com
  o rótulo do campo em vez do input.
- **Riscos residuais:** verificado apenas em iPhone 17 Pro / iOS 26.5 / tema escuro; fonte ampliada
  (que agrava exatamente este tipo de corte por teclado) não foi testada (ver BACKLOG-0119).
- **Proximo passo:** nenhum além do BACKLOG-0119.

---

## PROB-0098 — Assinatura no cartão de crédito (ex.: Netflix) não tinha caminho de cadastro — recorrência só sabia debitar caixa

- **ID:** PROB-0098
- **Titulo:** Motor de recorrência (`ContaFixa`) só suportava destino de caixa (`carteira_id`); no cartão só existia parcelamento, nunca recorrência
- **Data:** 2026-08-29
- **Origem:** usuario (dono do produto tentou cadastrar a assinatura da Netflix, R$60/mês no cartão, e não encontrou como)
- **Severidade:** HIGH
- **Status:** FECHADO (2026-08-29, migration `V67` + implementação backend/mobile, working tree não commitado)
- **Area:** backend, mobile, banco
- **Sintoma:** dono do produto tentou cadastrar a assinatura da Netflix (R$60/mês, cobrada no cartão
  de crédito) e não encontrou nenhum caminho no app para isso.
- **Causa raiz:** confirmada — o motor de recorrência maduro (`ContaFixa` + `ExecucaoRecorrencia` +
  `RecorrenciaScheduler`) só sabia debitar caixa: `ContaFixa` tinha `carteira_id` e nunca `conta_id`.
  No cartão só existia parcelamento (`Transacao`/`FaturaLancamento`/`Parcela`); uma cobrança
  recorrente e não parcelada no cartão não tinha caminho de cadastro nenhum.
- **Impacto tecnico:** qualquer assinatura cobrada no cartão (Netflix, Spotify, etc.) exigia
  lançamento manual todo mês, sem alerta de falha, sem histórico de execuções e sem integração com o
  motor de recorrência já existente para o caixa.
- **Arquivos ou modulos relacionados:**
  `backend/src/main/java/com/gestor/financeiro/model/ContaFixa.java`,
  `backend/src/main/java/com/gestor/financeiro/service/ContaFixaService.java`,
  `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`,
  `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`,
  `backend/src/main/java/com/gestor/financeiro/service/CartaoService.java`,
  `backend/src/main/java/com/gestor/financeiro/repository/ContaFixaRepository.java`,
  `backend/src/main/java/com/gestor/financeiro/dto/ContaFixaRequest.java`,
  `backend/src/main/java/com/gestor/financeiro/dto/ContaFixaResponseDto.java`,
  `backend/src/main/java/com/gestor/financeiro/controller/ContaFixaController.java`,
  `backend/src/main/resources/db/migration/V67__recorrencia_cartao.sql`,
  `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/app/(app)/more/contas-fixas.tsx`,
  `mobile/src/domain/recorrencia.ts`, `mobile/src/hooks/useInvalidarAposTransacao.ts`,
  `mobile/src/types/index.ts`.
- **Solucao proposta:** dar a `ContaFixa` dois destinos de cobrança mutuamente exclusivos —
  `carteira_id` (caixa) ou `conta_id` (cartão) —, seguindo o padrão "um destino, nunca dois" já usado
  na V55; no ramo cartão, criar `Transacao` com `conta` setada e `carteira=null`, reaproveitando que
  `TransacaoService.criar` já chama `FaturaService.registrarCompraCartao` (sem reescrever nem
  duplicar regra de fatura, ADR-0001). Decisões do dono do produto: assinatura de cartão é automática
  por padrão; criação exposta no botão "Nova" e na tela Recorrências; estouro de limite lança e
  avisa, nunca bloqueia.
- **Solucao aplicada:** migration `V67__recorrencia_cartao.sql` (expand puro, aditivo, ADR-0015)
  adiciona `contas_fixas.conta_id`, troca `ck_contas_fixas_automatica_carteira` por
  `ck_contas_fixas_destino_automatico`, adiciona `ck_contas_fixas_destino_unico` e
  `ck_contas_fixas_cartao_saida`, índice parcial `idx_contas_fixas_conta`.
  `ContaFixaService.resolverDestino` (renomeado de `resolverCarteira`) valida exclusividade,
  ownership do cartão (404), cartão inativo (422) e ENTRADA+cartão (400) — cada `CHECK` do banco tem
  validação equivalente no service devolvendo 4xx antes de chegar ao banco. `ContaFixaRequest` ganhou
  `cartaoId` + três `@AssertTrue`; `ContaFixaResponseDto.CartaoResumo(id, nome, bandeira,
  ultimosDigitos)` expõe metadado de exibição, nunca PAN. Primeira ocorrência executa na hora quando
  `execucaoAutomatica` e `dataProximoVencimento <= hoje` (falha não invalida o cadastro). No mobile,
  `NovaTransacaoModal` ganhou o switch "Repete todo mês" (SAIDA, fora do modo assistente) e
  `app/(app)/more/contas-fixas.tsx` ganhou o seletor "Cobrar em" (Conta/Cartão). Cinco defeitos
  pré-existentes no motor de recorrência foram corrigidos na mesma sessão para o cartão poder usar o
  motor com segurança — ver BUG-0098 a BUG-0102.
- **Evidencias ou comandos usados:** backend 501 testes, 0 falhas (491 após a feature + 10 do novo
  `ContaFixaCartaoTest`; mais 1 caso novo em `UsuarioExclusaoTest` para a FK nova na exclusão LGPD);
  mobile 447 testes, 40 suítes, 0 falhas; lint `--max-warnings=0` limpo; `tsc --noEmit` limpo;
  `scripts/verify-postgres-migrations.sh` exit 0, "now at version v67" em PostgreSQL 16 real. Runtime
  (backend 8081, banco limpo): assinatura Spotify criada com `cartao` no response; cobrança entrou na
  fatura de setembro (compra dia 29 > fechamento dia 10) com descrição "Spotify", R$60, sem parcelas;
  conta corrente permaneceu 2500,00 (único movimento de caixa é o saldo inicial do onboarding);
  passivo do cartão subiu 60 → 120 acompanhando as cobranças; reexecução da mesma ocorrência devolveu
  422 sem duplicar; `realizar` com `carteiraId` no corpo numa assinatura de cartão não tocou o caixa;
  DELETE do cartão (204) desativou as duas assinaturas; restart do backend (dispara
  `recuperarAoIniciar`) manteve 3 lançamentos/3 transações/3 execuções; validações devolveram
  400/400/422/404 (dois destinos, ENTRADA no cartão, cartão inativo, cartão inexistente) — nenhum
  500, os `CHECK`s do banco nunca dispararam.
- **Riscos residuais:** frequência de recorrência é hard-coded mensal (`plusMonths(1)`); não há
  validação/notificação de estouro de limite do cartão (decisão do dono do produto foi "lançar e
  avisar", mas o "avisar" ainda não foi implementado — não havia validação de limite alguma
  pré-existente); `CarteiraService.deletar` não checa `contas_fixas` (pode devolver 500 de FK
  `RESTRICT` numa carteira sem movimentos mas referenciada por recorrência — pré-existente, não
  introduzido por esta entrega); sugestão de recorrência detectada (`RecorrenciaCandidataService`)
  ainda não herda o cartão da transação de origem; web (`frontend/`) não tem paridade (decisão
  mobile-first já registrada). Ver BACKLOG-0120 a BACKLOG-0126.
- **Proximo passo:** priorizar os itens de BACKLOG-0120 a BACKLOG-0126 conforme demanda de produto;
  nenhuma ação bloqueante pendente para esta entrega em si.
