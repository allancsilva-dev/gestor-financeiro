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
- **Riscos residuais:** Concorrencia real testada apenas com H2; PostgreSQL validation pendente (sem Docker Compose).
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
- **Riscos residuais:** Validação de startup com PostgreSQL limpo não executada por ausência de Docker Compose. Migration gerada a partir de entidades JPA — validar correspondência com BD existente se houver.
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
- **Status:** ABERTO
- **Area:** mobile
- **Sintoma:** Usuario precisa fazer login toda vez que abre o app. Sessao nao sobrevive a restart, crash ou kill do processo.
- **Causa raiz:** `store/auth.ts` armazena token em variavel de modulo (`let _accessToken`). `expo-secure-store` instalado mas nunca importado ou usado.
- **Impacto tecnico:** Experiencia de usuario degradada. Impossivel manter sessao entre usos do app.
- **Arquivos relacionados:** `mobile/src/store/auth.ts:1-2`, `mobile/src/context/AuthContext.tsx:31`
- **Solucao proposta:** Implementar persistencia com expo-secure-store. Adicionar useEffect no AuthContext para restaurar token no startup.
- **Solucao aplicada:** pendente
- **Evidencias:** Comentario `// TODO fase 2: persistir com expo-secure-store` + variavel `_accessToken` em modulo
- **Riscos residuais:** Secure Store tem limitacoes de tamanho (~2KB); tokens JWT cabem. Chave compartilhada entre apps do mesmo dev no iOS.
- **Proximo passo:** Implementar get/set/delete no auth store usando expo-secure-store

---

## PROB-0014 — Mobile: IP hardcoded

- **ID:** PROB-0014
- **Titulo:** URL da API hardcoded com IP fixo 192.168.15.3:8081
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** ABERTO
- **Area:** mobile
- **Sintoma:** App so funciona na rede especifica do desenvolvedor. Inutilizavel em producao ou em outra rede.
- **Causa raiz:** `const BASE_URL = 'http://192.168.15.3:8081/api'` hardcoded
- **Impacto tecnico:** App quebrado em qualquer ambiente que nao seja a maquina de dev
- **Arquivos relacionados:** `mobile/src/config/api.config.ts:5`
- **Solucao proposta:** Usar `expo-constants` (`Constants.expoConfig?.extra?.apiUrl`) ou variavel de ambiente
- **Solucao aplicada:** pendente
- **Evidencias:** Linha 5 do api.config.ts com comentario "troque pelo IP da sua maquina"
- **Riscos residuais:** Necessario documentar configuracao para devs
- **Proximo passo:** Migrar para `expo-constants` com fallback documentado

---

## PROB-0015 — Mobile: elementos UI mortos

- **ID:** PROB-0015
- **Titulo:** Botoes sem handler e links nao clicaveis no mobile
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** ABERTO
- **Area:** mobile
- **Sintoma:** Botao "Esqueceu a senha?" sem onPress. Link "Ver todas" no Dashboard nao e clicavel. Usuario clica e nada acontece.
- **Causa raiz:** TouchableOpacity e Text sem evento onPress definido
- **Impacto tecnico:** UX quebrada. Funcionalidades inacessiveis.
- **Arquivos relacionados:** `mobile/app/(auth)/login.tsx:47-49`, `mobile/app/(app)/index.tsx:68`
- **Solucao proposta:** Adicionar onPress handlers (navegar para forgot-password, navegar para lista completa)
- **Solucao aplicada:** pendente
- **Evidencias:** TouchableOpacity sem onPress no login, Text sem TouchableOpacity no Dashboard
- **Riscos residuais:** Verificar se a rota de destino existe antes de vincular
- **Proximo passo:** Implementar handlers de navegacao

---

## PROB-0016 — Mobile: API path inconsistente

- **ID:** PROB-0016
- **Titulo:** Perfil usa /dashboard/resumo sem prefixo /v1 — endpoint 404
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** CRITICAL
- **Status:** ABERTO
- **Area:** mobile
- **Sintoma:** Tela de Perfil nao carrega dados de resumo pois endpoint `/dashboard/resumo` nao existe. O endpoint correto e `/v1/dashboard/resumo`.
- **Causa raiz:** Path inconsistente entre `app/(app)/index.tsx` e `app/(app)/perfil.tsx`
- **Impacto tecnico:** Dados do perfil nao carregam. Erro 404 silencioso.
- **Arquivos relacionados:** `mobile/app/(app)/index.tsx:17`, `mobile/app/(app)/perfil.tsx:19`
- **Solucao proposta:** Corrigir path em perfil.tsx para `/v1/dashboard/resumo`
- **Solucao aplicada:** pendente
- **Evidencias:** Comparacao das linhas 17 (index) e 19 (perfil)
- **Riscos residuais:** Nenhum apos correcao
- **Proximo passo:** Corrigir path no perfil.tsx

---

## PROB-0017 — Mobile: erros silenciosos em mutations

- **ID:** PROB-0017
- **Titulo:** Mutations de criar carteira e pagar conta fixa sem tratamento de erro
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** PARCIAL
- **Area:** mobile
- **Sintoma:** Se API falhar ao criar carteira ou marcar conta fixa como paga, usuario nao recebe nenhum feedback. Operacao falha silenciosamente.
- **Causa raiz:** `criarMutation` em carteiras.tsx sem `onError`. `pagarMutation` em contas-fixas.tsx sem `onError`. Try/catch vazio em handleSalvar.
- **Impacto tecnico:** Usuario acredita que operacao foi concluida mas nao foi. Dados inconsistentes.
- **Arquivos relacionados:** `mobile/app/(app)/more/carteiras.tsx:26-49`, `mobile/app/(app)/more/contas-fixas.tsx:24-31`
- **Solucao proposta:** Adicionar onError handlers com Alert.alert() ou toast
- **Solucao aplicada:** parcial — criar conta fixa ja possui `onError`; criar carteira e pagar conta fixa ainda nao.
- **Evidencias:** `carteiras.tsx` ainda tem `catch` vazio; `contas-fixas.tsx` tem `onError` em `criarMutation`, mas nao em `pagarMutation`.
- **Riscos residuais:** Nenhum alem da UX melhorada
- **Proximo passo:** Implementar `onError` restante em carteira e pagamento de conta fixa

---

## PROB-0018 — Mobile: sem botão voltar nos sub-menus

- **ID:** PROB-0018
- **Titulo:** More sub-screens sem header visivel — usuarios iOS sem back button
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** HIGH
- **Status:** ABERTO
- **Area:** mobile
- **Sintoma:** `headerShown: false` em more/_layout.tsx esconde navegacao. Usuarios iOS precisam saber do gesto de swipe para voltar.
- **Causa raiz:** Configuracao `headerShown: false` no Stack layout de More
- **Impacto tecnico:** Navegacao nao descoberta para usuarios iOS
- **Arquivos relacionados:** `mobile/app/(app)/more/_layout.tsx`
- **Solucao proposta:** Mostrar header com titulo e seta de voltar, ou adicionar botao customizado
- **Solucao aplicada:** pendente
- **Evidencias:** Stack.Screen com headerShown:false
- **Riscos residuais:** Nenhum com header padrao
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
- **Status:** ABERTO
- **Area:** backend
- **Sintoma:** `parserBuilder()`, `setSigningKey()`, `parseClaimsJws()` sao deprecated desde JJWT 0.12.x
- **Causa raiz:** API antiga usada com jjwt 0.11.5
- **Impacto tecnico:** Sem correcoes de seguranca da API nova. Bloqueia upgrade do jjwt.
- **Arquivos relacionados:** `backend/.../config/JwtUtil.java:74-79`
- **Solucao proposta:** Migrar para `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`
- **Solucao aplicada:** pendente
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
- **Status:** ABERTO
- **Area:** mobile
- **Sintoma:** App.tsx e o template padrao Expo ("Open up App.tsx to start"). index.ts importa App.tsx. Entry real e expo-router/entry. Estes arquivos nunca sao executados.
- **Causa raiz:** package.json define "main": "expo-router/entry", ignorando App.tsx e index.ts
- **Impacto tecnico:** Confusao para desenvolvedores. Codigo morto no repositorio.
- **Arquivos relacionados:** `mobile/App.tsx`, `mobile/index.ts`, `mobile/package.json`
- **Solucao proposta:** Deletar App.tsx e atualizar index.ts para re-exportar expo-router/entry
- **Solucao aplicada:** pendente
- **Evidencias:** App.tsx contem template padrao; index.ts importa App; main em package.json aponta para expo-router/entry
- **Riscos residuais:** Nenhum — ambos arquivos sao ignorados pelo bundler
- **Proximo passo:** Limpar entry points

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
- **Status:** ABERTO
- **Area:** frontend
- **Sintoma:** 54 ocorrencias de `any` no frontend. Service methods recebem `any` em vez de tipos definidos.
- **Causa raiz:** Tipos definidos em types/index.ts mas nao usados nas assinaturas dos services
- **Impacto tecnico:** Zero type safety nas chamadas de API. Erros de tipagem so descobertos em runtime.
- **Arquivos relacionados:** `frontend/src/`
- **Solucao proposta:** Substituir `any` por tipos explicitos (Categoria, Transacao, Carteira, etc.)
- **Solucao aplicada:** pendente
- **Evidencias:** Busca atual por `\bany\b` revelou 54 ocorrencias.
- **Riscos residuais:** Pode revelar erros de tipo antes escondidos — corrigir durante migracao
- **Proximo passo:** Tipar metodos de service prioritariamente

---

## PROB-0028 — Mobile: parseBRCurrency duplicado

- **ID:** PROB-0028
- **Titulo:** Logica de parse de moeda BR duplicada em 5 arquivos
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** MEDIUM
- **Status:** ABERTO
- **Area:** mobile
- **Sintoma:** Mesmo padrao `replace(/\./g, '').replace(/,/g, '.')` repetido em transacoes.tsx, metas.tsx, contas.tsx, carteiras.tsx, contas-fixas.tsx
- **Causa raiz:** Logica nao centralizada em utils/format.ts
- **Impacto tecnico:** Manutencao dificil. Mudanca no formato requer alteracao em 5 lugares.
- **Arquivos relacionados:** `mobile/app/(app)/transacoes.tsx`, `metas.tsx`, `contas.tsx`, `carteiras.tsx`, `contas-fixas.tsx`
- **Solucao proposta:** Criar `parseCurrencyBR()` em utils/format.ts e usar em todos os locais
- **Solucao aplicada:** pendente
- **Evidencias:** Busca pelo regex encontrou 5 ocorrencias
- **Riscos residuais:** Verificar se alguma ocorrencia tem variacao na logica
- **Proximo passo:** Centralizar em format.ts

---

## PROB-0029 — Frontend: sem rota 404

- **ID:** PROB-0029
- **Titulo:** App nao tem catch-all route para paths desconhecidos
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** LOW
- **Status:** ABERTO
- **Area:** frontend
- **Sintoma:** Usuario que acessa URL invalida ve tela em branco em vez de pagina "Nao encontrado"
- **Causa raiz:** Routes em App.tsx sem `<Route path="*" element={<NotFound />} />`
- **Impacto tecnico:** UX ruim para URLs erradas
- **Arquivos relacionados:** `frontend/src/App.tsx`
- **Solucao proposta:** Adicionar rota catch-all com componente NotFound
- **Solucao aplicada:** pendente
- **Evidencias:** Ausencia de route com path="*"
- **Riscos residuais:** Nenhum
- **Proximo passo:** Criar componente NotFound e adicionar rota

---

## PROB-0030 — Frontend: console.log em produção

- **ID:** PROB-0030
- **Titulo:** Cinco console.log() e 29 console.error() em codigo de producao frontend
- **Data:** 2026-07-06
- **Origem:** auditoria completa do sistema
- **Severidade:** LOW
- **Status:** ABERTO
- **Area:** frontend
- **Sintoma:** Logs de debug visiveis no console do navegador em producao
- **Causa raiz:** console.log/error nao removidos apos desenvolvimento
- **Impacto tecnico:** Console poluido. Vazamento de dados em console.error (emails, tokens em alguns casos).
- **Arquivos relacionados:** authService.ts (x2), Login.tsx, GraficoEvolucaoMensal.tsx, GraficoGastosPorCategoria.tsx + 29 console.error em frontend/src
- **Solucao proposta:** Remover console.log. Manter console.error com gate de ambiente ou migrar para servico de logging.
- **Solucao aplicada:** pendente
- **Evidencias:** Busca atual: 5 `console.log`, 29 `console.error`
- **Riscos residuais:** Perda de debugging em dev — usar logger condicional
- **Proximo passo:** Limpeza de console.log; avaliar console.error caso a caso

---

> Mantido pelo `docs-reporter`. Ultima atualizacao: 2026-07-06 (auditoria completa do sistema).
