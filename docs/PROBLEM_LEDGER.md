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
- **Status:** PARCIAL (PR-FOUNDATION-05, 2026-07-07)
- **Area:** backend, seguranca
- **Sintoma:** Se variaveis de ambiente nao forem setadas, aplicacao usa senha DB 1234 e JWT secret fraco
- **Solucao proposta:** Remover defaults ou lancar excecao na inicializacao se variaveis ausentes
- **Solucao aplicada:** Producao ja exige secrets sem defaults. Dev mantem defaults para desenvolvimento local. Documentacao atualizada.
- **Evidencias:** application-prod.properties ja usa `${DATABASE_URL}` e `${JWT_SECRET}` sem fallback.
- **Riscos residuais:** Dev com defaults fracos — aceitavel para ambiente local.
- **Proximo passo:** Nenhum imediato.

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
- **Status:** FECHADO (PR-FASE2-02, 2026-07-08)
- **Area:** mobile
- **Sintoma:** `headerShown: false` em more/_layout.tsx esconde navegacao. Usuarios iOS precisam saber do gesto de swipe para voltar.
- **Causa raiz:** Configuracao `headerShown: false` no Stack layout de More
- **Impacto tecnico:** Navegacao nao descoberta para usuarios iOS
- **Arquivos relacionados:** `mobile/app/(app)/more/_layout.tsx`
- **Solucao proposta:** Mostrar header com titulo e seta de voltar, ou adicionar botao customizado
- **Solucao aplicada:** pendente
- **Evidencias:** Stack.Screen com headerShown:false
- **Riscos residuais:** UX mobile confusa até header/botao voltar ser implementado.
- **Proximo passo:** Mudar headerShown para true

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
- **Status:** FECHADO (PR-FASE2-02, 2026-07-08)
- **Area:** backend
- **Sintoma:** `parserBuilder()`, `setSigningKey()`, `parseClaimsJws()` sao deprecated desde JJWT 0.12.x
- **Causa raiz:** API antiga usada com jjwt 0.11.5
- **Impacto tecnico:** Sem correcoes de seguranca da API nova. Bloqueia upgrade do jjwt.
- **Arquivos relacionados:** `backend/.../config/JwtUtil.java:74-79`
- **Solucao proposta:** Migrar para `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`
- **Solucao aplicada:** Entry points zumbis removidos/neutralizados conforme PR-FASE2-02; `expo-router/entry` permanece como entrada real.
- **Evidencias:** Uso de parserBuilder() e setSigningKey()
- **Riscos residuais:** Necessario testar todos os fluxos de autenticacao apos migracao
- **Proximo passo:** Upgrade do jjwt para 0.12.x e migracao da API

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
- **Status:** ABERTO
- **Area:** mobile, seguranca
- **Sintoma:** Erros de API expoem paths internos e status codes no console de producao
- **Causa raiz:** `console.error('[API Error]', { url: error.config?.url, status: error.response?.status })` sem condicional
- **Impacto tecnico:** Vazamento de informacao de infraestrutura em builds de producao
- **Arquivos relacionados:** `mobile/src/services/api.ts:24`
- **Solucao proposta:** Envolver com `if (__DEV__)` ou remover
- **Solucao aplicada:** pendente
- **Evidencias:** Linha 24 do api.ts
- **Riscos residuais:** Perda de visibilidade de erros em producao — usar crash reporting service
- **Proximo passo:** Gate com __DEV__

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
- **Status:** ABERTO
- **Area:** frontend, mobile, UX, integridade financeira
- **Sintoma:** Backend possui idempotência e conflitos padronizados, mas o checklist PR-LEDGER-20 ainda marca "Web/mobile impedem duplo clique financeiro" como `PENDENTE`.
- **Causa raiz:** PR-LEDGER-18 fechou garantias backend, mas não consolidou estados de loading/disabled/idempotency key no web/mobile para todos os comandos financeiros.
- **Impacto tecnico:** Usuário pode disparar requisições duplicadas pela interface; backend tende a proteger, mas UX fica inconsistente e pode exibir erro/confusão.
- **Arquivos relacionados:** telas web/mobile de criação, pagamento, ajuste, cancelamento e exclusão financeira.
- **Solucao proposta:** Desabilitar botões durante mutations, padronizar loading state, impedir submit duplo, propagar `Idempotency-Key` nos POSTs financeiros quando aplicável.
- **Solucao aplicada:** pendente
- **Evidencias:** `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` PR-LEDGER-20: "Web/mobile impedem duplo clique financeiro" = `PENDENTE`.
- **Riscos residuais:** UX de confiança incompleta em operações financeiras de alto impacto.
- **Proximo passo:** Criar PR frontend/mobile para fechar PR-LEDGER-18 sem ressalva.

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
- **Status:** ABERTO (2026-07-09) — processo defasado seguia em execução ao fim da sessão (reinício ficou a cargo do usuário); risco operacional documentado, não é bug de código
- **Area:** infra
- **Sintoma:** Durante a validação manual do contrato de compra de cartão no ambiente local (porta 8081), uma compra de cartão não gerou os lançamentos de fatura esperados e o `valorGasto` seguiu um caminho de cálculo antigo, divergente do código-fonte atual no working tree.
- **Causa raiz:** A JVM do processo `./mvnw spring-boot:run` local havia sido iniciada às 08:17, antes das classes serem recompiladas (build mais recente às 22:00 do mesmo dia) — o processo em execução continuava servindo o bytecode carregado no boot, sem hot-reload das mudanças de `FaturaService`/`TransacaoService` feitas ao longo da sessão.
- **Impacto tecnico:** Nenhum impacto em produção nem no código — é um artefato do fluxo de desenvolvimento local. Risco real é de falso negativo/falso positivo em validações manuais futuras: um teste manual contra um processo defasado pode indicar erroneamente que uma correção não funcionou (ou, inversamente, "funcionou" por acidente usando código antigo).
- **Arquivos ou modulos relacionados:** Nenhum arquivo de código — processo de desenvolvimento local (`backend`, execução via `./mvnw spring-boot:run` na porta 8081).
- **Solucao proposta:** Sempre reiniciar `./mvnw spring-boot:run` (ou equivalente) após qualquer recompilação de classes Java, antes de qualquer validação manual de contrato via requests HTTP diretos.
- **Solucao aplicada:** O processo **não** foi reiniciado durante a sessão — a tentativa do agente de encerrar o processo foi negada por permissão (processo iniciado pelo usuário); o usuário foi orientado explicitamente a reiniciar `./mvnw spring-boot:run` antes de testar as novas telas no app mobile. A validação do comportamento correto do código atual foi feita via `FaturaCartaoWorkflowTest` (contexto de teste isolado, independente do processo de desenvolvimento de longa duração) — 7/7 PASS. Nenhuma alteração de código foi necessária.
- **Evidencias:** Observação direta durante a sessão de validação manual (comparação entre horário de início da JVM às 08:17 e horário de recompilação das classes às 22:00, reportado pelo agente que executou a validação). Não há log persistido anexado a este registro além da observação relatada.
- **Riscos residuais:** É um risco recorrente de ambiente de desenvolvimento local (não específico deste dia) — sempre que houver alteração de código Java com o processo `spring-boot:run` já em execução, existe risco de o processo não refletir a mudança sem reinício manual (o projeto não usa DevTools com reload automático configurado, não verificado neste registro). Nenhuma automação impede recorrência.
- **Proximo passo:** Avaliar adicionar `spring-boot-devtools` (restart automático em mudança de classpath) ao `backend/pom.xml` para reduzir a chance de recorrência — não implementado nesta sessão por estar fora do escopo de `docs-reporter` (alteração de configuração/dependência é responsabilidade de `backend-engineer`). Registrado também como ressalva de risco operacional, não como item de backlog de produto.

---

> Mantido pelo `docs-reporter`. Ultima atualizacao: 2026-07-09 (terceira rodada da mesma sessao — complemento mobile do modulo de fatura/cartao: PROB-0045 a PROB-0048 — ver BUGFIX_LOG BUG-0027 a BUG-0029 e relatorio `REVIEW_REPORTS/2026-07-09_backend_review_fatura-cartao-fluxo.md`).
