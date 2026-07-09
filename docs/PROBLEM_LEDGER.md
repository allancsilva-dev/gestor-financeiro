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

> Mantido pelo `docs-reporter`. Ultima atualizacao: 2026-07-09 (correções pós-diagnóstico manual via replicação de payloads mobile — ver BUGFIX_LOG BUG-0011 a BUG-0016).
