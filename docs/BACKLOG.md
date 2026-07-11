# Backlog — Gestor Financeiro

Registro de proximos passos e itens nao tratados agora, descobertos em revisoes, auditorias e
implementacoes. Mantido pelo `docs-reporter`. Complementa `docs/PROXIMOS_PASSOS.md`.

---

## BACKLOG-0001 — Migrar para migrations versionadas (Flyway)

- **Titulo:** Substituir ddl-auto=update por Flyway/Liquibase
- **Prioridade:** P0
- **Area:** backend, banco
- **Motivo:** ddl-auto=update em producao e risco de perda de dados. Necessario versionamento de schema.
- **Dependencias:** PROB-0006 resolvido (ddl-auto alterado para validate/none)
- **Criterio de aceite:** Migrations versionadas rodando em dev e prod; ddl-auto=validate ou none
- **Risco se ficar pendente:** Perda de dados em deploy, schema drift entre ambientes
- **Status:** FECHADO (PR-FOUNDATION-01, 2026-07-07)

---

## BACKLOG-0002 — Implementar @Version em entidades financeiras

- **Titulo:** Adicionar optimistic locking em Carteira, Meta e Conta
- **Prioridade:** P0
- **Area:** backend
- **Motivo:** Race conditions em operacoes de saldo/valor podem corromper dados financeiros
- **Dependencias:** PROB-0002 resolvido
- **Criterio de aceite:** @Version nas entidades; OptimisticLockException tratada com retry ou mensagem
- **Risco se ficar pendente:** Corrupcao de saldo em uso concorrente
- **Status:** FECHADO (PR-FOUNDATION-03, 2026-07-07)

---

## BACKLOG-0003 — Corrigir queries massivas (findAll sem filtro)

- **Titulo:** Substituir findAll() por queries filtradas em ParcelaService e ContaFixaService
- **Prioridade:** P0
- **Area:** backend
- **Motivo:** findAll() carrega todos os registros do banco — OOM em producao
- **Dependencias:** PROB-0003 resolvido
- **Criterio de aceite:** Queries com WHERE filtrando por status e data
- **Risco se ficar pendente:** Crash da aplicacao com volume de dados
- **Status:** FECHADO (PR-FOUNDATION-04, 2026-07-07)

---

## BACKLOG-0004 — Agregacoes SQL no DashboardService

- **Titulo:** Substituir agregacao em memoria (Stream) por queries SUM no banco
- **Prioridade:** P0
- **Area:** backend
- **Motivo:** Carregar entidades completas so para somar e ineficiente e nao escala
- **Dependencias:** PROB-0004 resolvido
- **Criterio de aceite:** Queries JPQL com SUM; Dashboard responde em < 500ms com 100k transacoes
- **Risco se ficar pendente:** Dashboard lento ou OOM com muitos dados
- **Status:** FECHADO (PR-FOUNDATION-04, 2026-07-07)

---

## BACKLOG-0005 — Persistir token mobile com expo-secure-store

- **Titulo:** Implementar persistencia de sessao no mobile
- **Prioridade:** P0
- **Area:** mobile
- **Motivo:** Usuario perde sessao toda vez que abre o app
- **Dependencias:** Resolver PROB-0013
- **Criterio de aceite:** Token armazenado no SecureStore; sessao restaurada no cold start; sem flash de login
- **Risco se ficar pendente:** Experiencia de usuario inaceitavel
- **Status:** FECHADO (PR-FASE2-01, 2026-07-08)

---

## BACKLOG-0006 — Configurar URL da API mobile por ambiente

- **Titulo:** Substituir IP hardcoded por configuracao de ambiente no mobile
- **Prioridade:** P0
- **Area:** mobile
- **Motivo:** IP fixo quebra app em qualquer rede que nao a do dev
- **Dependencias:** Resolver PROB-0014
- **Criterio de aceite:** URL da API configurada via expo-constants ou env var
- **Risco se ficar pendente:** App inutilizavel fora da rede do dev
- **Status:** FECHADO (PR-FASE2-01, 2026-07-08)

---

## BACKLOG-0007 — Fortalecer politica de senha

- **Titulo:** Implementar validacao de complexidade de senha
- **Prioridade:** P0
- **Area:** backend, seguranca
- **Motivo:** Minimo de 6 caracteres sem requisitos de complexidade para app financeiro
- **Dependencias:** PROB-0007 resolvido
- **Criterio de aceite:** Min 8 chars, ao menos 1 letra e 1 digito, aplicado em registro e reset de senha
- **Risco se ficar pendente:** Contas vulneraveis a ataques de forca bruta
- **Status:** FECHADO (PR-FOUNDATION-07, 2026-07-07)

---

## BACKLOG-0008 — Expandir rate limiting

- **Titulo:** Adicionar rate limit em register, reset-password e validate-token
- **Prioridade:** P1
- **Area:** backend, seguranca
- **Motivo:** Endpoints criticos sem protecao contra abuso
- **Dependencias:** PROB-0008 resolvido
- **Criterio de aceite:** Rate limit ativo nos 3 endpoints; respostas 429 com Retry-After
- **Risco se ficar pendente:** Abuso de API, contas fake, token enumeration
- **Status:** FECHADO (PR-FOUNDATION-05, 2026-07-07)

---

## BACKLOG-0009 — Implementar CSRF protection no frontend web

- **Titulo:** Adicionar token CSRF no frontend web
- **Prioridade:** P1
- **Area:** frontend, seguranca
- **Motivo:** withCredentials:true envia cookies sem protecao CSRF
- **Dependencias:** Endpoint backend para emitir token CSRF
- **Criterio de aceite:** Token CSRF enviado como header em toda request state-changing
- **Risco se ficar pendente:** POST /api/auth/refresh-token vulneravel a CSRF
- **Status:** FECHADO (BUG-0008, 2026-07-07)

---

## BACKLOG-0010 — Adicionar @Transactional sistematicamente

- **Titulo:** Adicionar @Transactional em todos os metodos write dos services
- **Prioridade:** P1
- **Area:** backend
- **Motivo:** Operacoes de escrita sem garantia de atomicidade
- **Dependencias:** PROB-0012 resolvido
- **Criterio de aceite:** Todo metodo publico de create/update/delete com @Transactional
- **Risco se ficar pendente:** Inconsistencia de dados em falhas parciais
- **Status:** FECHADO (PR-FOUNDATION-03, 2026-07-07)

---

## BACKLOG-0011 — Corrigir defaults inseguros de dev

- **Titulo:** Remover senha DB '1234' e JWT secret default do application.properties
- **Prioridade:** P1
- **Area:** backend, seguranca
- **Motivo:** Fallbacks inseguros se env vars nao setadas
- **Dependencias:** PROB-0009 parcial
- **Criterio de aceite:** Decidir se ambiente dev deve falhar sem DB_PASSWORD/JWT_SECRET ou manter defaults locais documentados
- **Risco se ficar pendente:** Seguranca comprometida em configuracao padrao
- **Status:** ABERTO

---

## BACKLOG-0012 — Corrigir configuracoes de producao

- **Titulo:** Fix cookie.secure, CORS fallback e ddl-auto em prod properties
- **Prioridade:** P1
- **Area:** backend, seguranca
- **Motivo:** Configuracoes de producao com valores inseguros ou ausentes
- **Dependencias:** PROB-0005, PROB-0006, PROB-0010 resolvidos
- **Criterio de aceite:** cookie.secure=true, CORS sem fallback, ddl-auto=validate em prod
- **Risco se ficar pendente:** Cookies inseguros, CORS permissivo, schema drift
- **Status:** FECHADO (PR-FOUNDATION-05, 2026-07-07)

---

## BACKLOG-0013 — Remover PII de logs

- **Titulo:** Mascarar email e remover token de reset dos logs
- **Prioridade:** P1
- **Area:** backend, LGPD
- **Motivo:** Logs contem email (PII) e token de reset (seguranca)
- **Dependencias:** PROB-0011 resolvido
- **Criterio de aceite:** Emails mascarados (j***@d***.com); token nunca logado nem em DEBUG
- **Risco se ficar pendente:** Violacao LGPD; vazamento de token
- **Status:** FECHADO (PR-FOUNDATION-05, 2026-07-07)

---

## BACKLOG-0014 — Corrigir elementos UI mortos no mobile

- **Titulo:** Adicionar handlers em botoes e links sem acao no mobile
- **Prioridade:** P1
- **Area:** mobile
- **Motivo:** Botoes que parecem clicaveis mas nao fazem nada
- **Dependencias:** Resolver PROB-0015
- **Criterio de aceite:** "Esqueceu a senha" navega para forgot-password; "Ver todas" navega para lista
- **Risco se ficar pendente:** UX quebrada, frustracao do usuario
- **Status:** FECHADO (PR-FASE2-02, 2026-07-08)

---

## BACKLOG-0015 — Remover entry points zumbis do mobile

- **Titulo:** Deletar App.tsx (template Expo) e corrigir index.ts
- **Prioridade:** P1
- **Area:** mobile
- **Motivo:** Codigo morto causando confusao
- **Dependencias:** Resolver PROB-0025
- **Criterio de aceite:** App.tsx removido; index.ts limpo ou re-exportando expo-router
- **Risco se ficar pendente:** Confusao para devs — "qual entry point esta sendo usado?"
- **Status:** FECHADO (PR-FASE2-02, 2026-07-08)

---

## BACKLOG-0016 — Corrigir API path inconsistente no mobile

- **Titulo:** Corrigir /dashboard/resumo para /v1/dashboard/resumo no perfil.tsx
- **Prioridade:** P0
- **Area:** mobile
- **Motivo:** Endpoint 404 — dados nao carregam na tela de perfil
- **Dependencias:** Resolver PROB-0016
- **Criterio de aceite:** Perfil carrega dados do dashboard corretamente
- **Risco se ficar pendente:** Tela de perfil quebrada
- **Status:** FECHADO (PR-FASE2-01, 2026-07-08)

---

## BACKLOG-0017 — Tratar erros em mutations mobile

- **Titulo:** Adicionar onError em mutations de carteira e contas-fixas
- **Prioridade:** P1
- **Area:** mobile
- **Motivo:** Falhas silenciosas — usuario nao sabe que operacao falhou
- **Dependencias:** Resolver restante de PROB-0017
- **Criterio de aceite:** Toda mutation com onError que mostra Alert ou toast
- **Risco se ficar pendente:** Usuario acredita que operacao foi concluida mas nao foi
- **Status:** FECHADO (PR-FASE2-02, 2026-07-08)

---

## BACKLOG-0018 — Centralizar parseCurrencyBR no mobile

- **Titulo:** Extrair logica de parse de moeda BR para utils/format.ts
- **Prioridade:** P2
- **Area:** mobile
- **Motivo:** Codigo duplicado em 5 arquivos
- **Dependencias:** Resolver PROB-0028
- **Criterio de aceite:** Funcao parseCurrencyBR exportada de format.ts; 5 arquivos importam dela
- **Risco se ficar pendente:** Manutencao fragil — bug de parse precisa ser corrigido em 5 lugares
- **Status:** FECHADO (PR-FASE2-05, 2026-07-08)

---

## BACKLOG-0019 — Migrar JwtUtil para API nao-deprecated do JJWT

- **Titulo:** Upgrade do jjwt para 0.12.x e uso da nova API de parser
- **Prioridade:** P2
- **Area:** backend
- **Motivo:** API atual deprecated; upgrade necessario para correcoes de seguranca
- **Dependencias:** Resolver PROB-0022
- **Criterio de aceite:** jjwt 0.12.x; `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`
- **Risco se ficar pendente:** Sem patches de seguranca do jjwt
- **Status:** ABERTO

---

## BACKLOG-0020 — Adicionar account lockout

- **Titulo:** Implementar bloqueio de conta apos N falhas consecutivas de login
- **Prioridade:** P2
- **Area:** backend, seguranca
- **Motivo:** Rate limit por IP nao protege contra ataque distribuido
- **Dependencias:** PROB-0023 resolvido
- **Criterio de aceite:** Conta bloqueada por limite configuravel de falhas; default atual 5 falhas por 15min; mensagem clara ao usuario
- **Risco se ficar pendente:** Senhas vulneraveis a brute force multi-IP
- **Status:** FECHADO (PR-FOUNDATION-07, 2026-07-07)

---

## BACKLOG-0021 — Limpeza periodica do rate limit map

- **Titulo:** Adicionar @Scheduled para limpar entradas expiradas do ConcurrentHashMap
- **Prioridade:** P2
- **Area:** backend
- **Motivo:** Memory leak lento de entradas de IPs que nunca mais fazem request
- **Dependencias:** PROB-0024 resolvido
- **Criterio de aceite:** Scheduled task limpa entradas expiradas a cada 60s
- **Risco se ficar pendente:** Memory leak em uptime prolongado
- **Status:** FECHADO (PR-FOUNDATION-07, 2026-07-07)

---

## BACKLOG-0022 — Remover dead code e imports nao usados

- **Titulo:** Limpeza de arquivos e imports nao utilizados no frontend e mobile
- **Prioridade:** P3
- **Area:** frontend, mobile
- **Motivo:** Codigo morto polui repositorio e confunde devs
- **Dependencias:** Nenhuma
- **Criterio de aceite:** GraficoComparacaoMensal removido ou integrado; mobile App.tsx removido; imports unused removidos; dependencias nao usadas removidas do package.json
- **Risco se ficar pendente:** Build levemente maior; confusao para novos devs
- **Status:** FECHADO (PR-FASE2-04, 2026-07-08)

---

## BACKLOG-0023 — Tipar services do frontend

- **Titulo:** Substituir `any` por tipos explicitos nos metodos de service do frontend
- **Prioridade:** P2
- **Area:** frontend
- **Motivo:** 54 ocorrencias de `any` removem type safety
- **Dependencias:** Resolver PROB-0027
- **Criterio de aceite:** Zero any nos arquivos de service; parametros tipados com interfaces do types/index.ts
- **Risco se ficar pendente:** Erros de tipo so descobertos em runtime
- **Status:** FECHADO (PR-FASE2-06, 2026-07-08)

---

## BACKLOG-0024 — Adicionar validacao de formularios

- **Titulo:** Implementar validacao client-side nos formularios web e mobile
- **Prioridade:** P2
- **Area:** frontend, mobile
- **Motivo:** Formularios sem validacao de email, valores negativos, tamanhos maximos, datas invalidas
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Email validado com regex; campos numericos validados (min, max, positivo); datas validadas; feedback visual de erro
- **Risco se ficar pendente:** Erros de API evitaveis; UX ruim
- **Status:** ABERTO

---

## BACKLOG-0025 — Adicionar acessibilidade

- **Titulo:** Implementar ARIA labels, roles e keyboard navigation
- **Prioridade:** P2
- **Area:** frontend, mobile
- **Motivo:** Zero acessibilidade em todo o sistema
- **Dependencias:** Nenhuma
- **Criterio de aceite:** aria-label em botoes e inputs; role em componentes customizados; keyboard nav em dropdowns; accessibilityLabel no mobile
- **Risco se ficar pendente:** Sistema inacessivel para usuarios com leitores de tela
- **Status:** FECHADO (PR-FASE2-08, 2026-07-08)

---

## BACKLOG-0026 — Implementar testes no mobile

- **Titulo:** Configurar e escrever testes unitarios e de integracao no mobile
- **Prioridade:** P2
- **Area:** mobile
- **Motivo:** Zero testes no mobile — sem cobertura de regressao
- **Dependencias:** Configurar Jest/RNTL no package.json mobile
- **Criterio de aceite:** Testes para auth store, api service, componentes principais; scripts test e lint no package.json
- **Risco se ficar pendente:** Bugs de regressao nao detectados
- **Status:** ABERTO

---

## BACKLOG-0027 — Aumentar cobertura de testes backend

- **Titulo:** Escrever testes unitarios e de integracao para services e controllers
- **Prioridade:** P2
- **Area:** backend
- **Motivo:** Apenas 6 arquivos de teste cobrindo auth, transacoes, security
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Testes para todos os services (Carteira, Meta, Dashboard, Conta, ContaFixa, Categoria, Parcela); coverage > 70%
- **Risco se ficar pendente:** Bugs em regras de negocio nao detectados
- **Status:** ABERTO

---

## BACKLOG-0028 — Configurar CI/CD

- **Titulo:** Pipeline de build, test e deploy automatizado
- **Prioridade:** P2
- **Area:** infra
- **Motivo:** Build e deploy manuais — propenso a erro humano
- **Dependencias:** Testes implementados (BACKLOG-0026, BACKLOG-0027)
- **Criterio de aceite:** Pipeline GitHub Actions: build → test → lint em PRs
- **Risco se ficar pendente:** Deploys manuais com testes esquecidos
- **Status:** FECHADO (PR-FASE3-01, 2026-07-08)

---

## BACKLOG-0029 — Health check de banco no Actuator

- **Titulo:** Adicionar health indicator para conectividade PostgreSQL
- **Prioridade:** P3
- **Area:** backend, infra
- **Motivo:** /actuator/health nao verifica banco — falsos positivos
- **Dependencias:** spring-boot-starter-actuator ja incluso
- **Criterio de aceite:** Health endpoint retorna status do banco; readiness probe funcional
- **Risco se ficar pendente:** App considerado healthy mesmo com banco fora do ar
- **Status:** FECHADO (PR-FOUNDATION-06, DataSourceHealthIndicator configurado)

---

## BACKLOG-0030 — Implementar email real

- **Titulo:** Substituir EmailService stub por envio real via SMTP
- **Prioridade:** P2
- **Area:** backend
- **Motivo:** Recuperacao de senha apenas loga no console — nao funcional
- **Dependencias:** Configuracao SMTP; PROB-0011 resolvido primeiro (remover token do log)
- **Criterio de aceite:** Email enviado via SMTP configurado; fallback para log em dev
- **Risco se ficar pendente:** Usuarios nao conseguem resetar senha
- **Status:** ABERTO

---

## BACKLOG-0031 — Adicionar rota 404 no frontend

- **Titulo:** Criar componente NotFound e rota catch-all
- **Prioridade:** P3
- **Area:** frontend
- **Motivo:** URLs invalidas mostram tela em branco
- **Dependencias:** Resolver PROB-0029
- **Criterio de aceite:** Rota `*` renderiza NotFound com link para Dashboard ou Login
- **Risco se ficar pendente:** UX ruim para URLs erradas
- **Status:** FECHADO (PR-FASE2-04, 2026-07-08)

---

## BACKLOG-0032 — Remover console.log do frontend

- **Titulo:** Limpar console.log e console.error residuais
- **Prioridade:** P3
- **Area:** frontend
- **Motivo:** Logs de debug em producao
- **Dependencias:** Resolver PROB-0030
- **Criterio de aceite:** Zero console.log; console.error apenas em ErrorBoundary ou logger condicional
- **Risco se ficar pendente:** Console poluido; dados vazados em logs
- **Status:** FECHADO (PR-FASE2-04, 2026-07-08)

---

## BACKLOG-0033 — Adicionar confirmPassword no registro

- **Titulo:** Validar confirmacao de senha no backend e frontend
- **Prioridade:** P2
- **Area:** backend, frontend
- **Motivo:** Usuario pode digitar senha errada sem perceber — conta inacessivel
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Campo confirmPassword no DTO RegisterRequest; validacao de igualdade no backend e frontend
- **Risco se ficar pendente:** Contas perdidas por typo na senha
- **Status:** FECHADO (PR-FASE2-07, 2026-07-08)

---

## BACKLOG-0034 — Adicionar confirmacao de logout no mobile

- **Titulo:** Dialog de confirmacao antes de logout no perfil.tsx
- **Prioridade:** P3
- **Area:** mobile
- **Motivo:** Logout imediato sem confirmacao — acionamento acidental
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Alert.alert com "Tem certeza?" antes de executar logout
- **Risco se ficar pendente:** Logout acidental
- **Status:** ABERTO

---

## BACKLOG-0035 — Adicionar pull-to-refresh no Dashboard mobile

- **Titulo:** RefreshControl no ScrollView do Dashboard
- **Prioridade:** P3
- **Area:** mobile
- **Motivo:** Sem mecanismo de reload alem de sair e voltar da tela
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Pull-to-refresh atualiza dados do dashboard
- **Risco se ficar pendente:** Dados stale sem forma facil de atualizar
- **Status:** ABERTO

---

## BACKLOG-0036 — Onboarding financeiro guiado

- **Titulo:** Implementar wizard de onboarding pos-registro para configuracao inicial
- **Prioridade:** P0 (Fase 1)
- **Area:** backend, frontend, mobile
- **Motivo:** Usuario novo cai em telas vazias sem orientacao. Necessario guiar configuracao inicial de carteira, conta, categorias, renda e meta.
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Wizard multi-step com 6 passos (carteira, conta, categorias, renda opcional, meta opcional, confirmacao); flag onboardingCompleto no backend; redirect automatico pos-login; web e mobile implementados
- **Risco se ficar pendente:** Abandono do app por falta de orientacao
- **Status:** FECHADO (PR-FASE1-01, 2026-07-07)

---

## BACKLOG-0037 — Orçamento mensal por categoria

- **Titulo:** Implementar orcamento mensal com limites por categoria e progresso
- **Prioridade:** P0 (Fase 1)
- **Area:** backend, frontend, mobile
- **Motivo:** Usuario precisa planejar gastos mensais e acompanhar progresso por categoria
- **Dependencias:** Nenhuma
- **Criterio de aceite:** CRUD de orcamento mensal; limites por categoria; barra de progresso com cores (verde/amarelo/vermelho); navegacao entre meses; calculo automatico de gasto real via agregacao de transacoes; web e mobile implementados
- **Risco se ficar pendente:** Sem controle de gastos planejados vs realizados
- **Status:** FECHADO (PR-FASE1-02, 2026-07-07)

---

## BACKLOG-0038 — Recorrência real com pular mês e vínculo transação

- **Titulo:** Implementar pularMes, reativar e vínculo conta_fixa_id na transação
- **Prioridade:** P0 (Fase 1)
- **Area:** backend, frontend, mobile
- **Motivo:** Contas fixas precisam diferenciar previsto/confirmado/pago/atrasado/pulado para projeção financeira precisa
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Endpoint pularMes avança vencimento sem criar transação; reativar restaura conta inativa; transação criada ao pagar vincula conta_fixa_id (FK); botão Pular Mês no web e mobile
- **Risco se ficar pendente:** Impossibilidade de ignorar mês específico de conta recorrente
- **Status:** FECHADO (PR-FASE1-03, 2026-07-07)

---

## BACKLOG-0039 — Cartão de crédito e fatura

- **Titulo:** Implementar modelo de faturas de cartão de crédito
- **Prioridade:** P0 (Fase 1)
- **Area:** backend, frontend, mobile
- **Motivo:** Cartão de crédito tratado apenas como Conta simples. Necessário modelar faturas mensais com fechamento, vencimento e pagamento.
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Entidade FaturaCartao vinculada a Conta (CREDITO); fatura criada automaticamente com transações do período; endpoint pagarFatura cria transação de pagamento; visualização de lançamentos por fatura; navegação entre meses; web e mobile
- **Risco se ficar pendente:** Impossibilidade de controlar faturas de cartão com precisão
- **Status:** FECHADO (PR-FASE1-04, 2026-07-07)

---

## BACKLOG-0040 — Projeção de caixa

- **Titulo:** Implementar projeção de saldo futuro com base em contas fixas e parcelas
- **Prioridade:** P0 (Fase 1)
- **Area:** backend, frontend, mobile
- **Motivo:** Usuário precisa saber risco de saldo negativo nos próximos meses
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Endpoint GET /dashboard/projecao?meses=6; calcula saldo final mês a mês subtraindo contas fixas pendentes e parcelas futuras; tabela no web e lista no mobile
- **Risco se ficar pendente:** Usuário sem visibilidade de problemas futuros de caixa
- **Status:** FECHADO (PR-FASE1-05, 2026-07-07)

---

## BACKLOG-0041 — Relatórios e filtros por período

- **Titulo:** Implementar relatórios com filtro por período, gastos por categoria, formato de pagamento e maiores despesas
- **Prioridade:** P0 (Fase 1)
- **Area:** backend, frontend, mobile
- **Motivo:** Usuário precisa analisar finanças em qualquer período, não apenas mês atual
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Endpoint GET /relatorios com filtro inicio/fim; resposta inclui KPIs, gastos por categoria, gastos por conta e top 10 maiores despesas; tela web com date pickers e cards; tela mobile com filtros de data
- **Risco se ficar pendente:** Sem capacidade de análise histórica
- **Status:** FECHADO (PR-FASE1-06, 2026-07-07)

---

## BACKLOG-0042 — Exportação de dados (CSV)

- **Titulo:** Implementar exportação CSV de transações, categorias, contas e dados completos
- **Prioridade:** P0 (Fase 1)
- **Area:** backend, frontend, mobile
- **Motivo:** LGPD exige portabilidade de dados. Usuário precisa poder exportar seu histórico financeiro.
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Endpoints CSV para transações (com filtro), categorias, contas e completo; botões de download no web; opção de exportar no mobile via Share/Link
- **Risco se ficar pendente:** Não conformidade LGPD, falta de confiança do usuário
- **Status:** FECHADO (PR-FASE1-07, 2026-07-07)

---

## BACKLOG-0043 — Impedir duplo clique em ações financeiras no web/mobile

- **Titulo:** Padronizar loading/disabled/idempotency key nos comandos financeiros do cliente
- **Prioridade:** P1
- **Area:** frontend, mobile, UX, integridade financeira
- **Motivo:** PR-LEDGER-18 deixou backend protegido, mas web/mobile ainda não bloqueiam duplo clique financeiro de forma uniforme.
- **Dependencias:** Resolver PROB-0031
- **Criterio de aceite:** Botões de criar/pagar/estornar/ajustar/cancelar/excluir ficam disabled durante mutation; usuário recebe feedback visual; POSTs financeiros críticos enviam `Idempotency-Key` quando aplicável; testes ou validação manual documentada.
- **Risco se ficar pendente:** Requisições duplicadas, mensagens de erro confusas e menor confiança em operações financeiras.
- **Status:** ABERTO

---

## BACKLOG-0045 — Backfill retroativo do Ledger para transações antigas sem carteira

- **Titulo:** Rodar/estender `LedgerBackfillService` para cobrir transações criadas antes da correção do BUG-0011/BUG-0012
- **Prioridade:** P1
- **Area:** backend, banco
- **Motivo:** Antes de 2026-07-09, transações com `carteiraId` falhavam (BUG-0011) e o app mobile nem enviava `carteiraId` (BUG-0012). Transações criadas nesse período não têm `MovimentoCarteira` correspondente no Ledger, mesmo que a carteira exista — o saldo materializado da carteira pode não refletir o histórico real de transações antigas.
- **Dependencias:** BUG-0011 e BUG-0012 corrigidos (concluído em 2026-07-09). `LedgerBackfillService` já existe (PR-LEDGER-05) mas foi desenhado para backfill inicial de carteiras sem movimento nenhum, não para reconciliar transações específicas sem carteira.
- **Criterio de aceite:** Levantamento de quantas transações ativas existem sem `carteira_id` e sem movimento correspondente; decisão documentada (backfill automático vs. correção manual assistida); se aplicado, reconciliação (`LedgerReconciliationService`) retorna `OK` para todas as carteiras afetadas.
- **Risco se ficar pendente:** Saldo de carteiras de usuários com uso anterior a 2026-07-09 pode ficar permanentemente divergente do histórico real de transações.
- **Status:** ABERTO

---

## BACKLOG-0046 — Remover arquivo de swap do vim commitável no repositório

- **Titulo:** Excluir `mobile/src/services/.api.ts.swp` e garantir `.gitignore` cobre `*.swp`
- **Prioridade:** P3
- **Area:** mobile, documentacao
- **Motivo:** Arquivo `mobile/src/services/.api.ts.swp` (swap de edição do vim) apareceu como untracked no repositório durante a sessão de 2026-07-09. Não deve ser versionado.
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Arquivo removido do working tree; `*.swp` adicionado ao `.gitignore` se ainda não estiver coberto.
- **Risco se ficar pendente:** Baixo — poluição do repositório, risco de commit acidental de arquivo temporário.
- **Status:** ABERTO

---

## BACKLOG-0047 — Auditar demais endpoints quanto a exposição de entidade JPA completa

- **Titulo:** Verificar se outros controllers retornam entidade completa (`Usuario`, etc.) em vez de DTO
- **Prioridade:** P2
- **Area:** backend, seguranca
- **Motivo:** BUG-0016 (PROB-0037) confirmou que `POST /api/auth/register` vazava hash bcrypt e campos de lockout por retornar a entidade `Usuario` diretamente. Não foi feita uma varredura sistemática nos demais endpoints (ex: `GET /usuarios/me`, endpoints de perfil) para confirmar que todos usam DTO/projeção.
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Levantamento de todos os `ResponseEntity.ok(entidade)` no código de `controller/`; endpoints que retornam entidade JPA com campos sensíveis (senha, tokens, lockout) convertidos para DTO.
- **Risco se ficar pendente:** Possível vazamento adicional de dados sensíveis (PII/segurança) em endpoints não revisados.
- **Status:** ABERTO

---

## BACKLOG-0048 — Efeitos visuais do protótipo aplicados ao mobile (Entrance, FloatEmoji, Fab gradiente)

- **Titulo:** Portar efeitos de entrada (`gf-rise`/`gf-pop`) e emoji flutuante (`gf-float`) do protótipo standalone para o app mobile
- **Prioridade:** P2
- **Area:** mobile, documentacao
- **Motivo:** Alinhar a experiência visual do app Expo com o protótipo de referência (`docs/Gestor Financeiro (standalone).html`), conforme direção de design registrada em `mobile-first-prototype-redesign` (memória do usuário).
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Componentes `Entrance` (stagger de entrada, respeita `Reduce Motion`) e `FloatEmoji` criados e aplicados em home/metas/transações; `Fab` com gradiente violeta `#7c5cfc`→`#8b2fff` e glow.
- **Risco se ficar pendente:** N/A — já implementado nesta sessão.
- **Status:** FECHADO (2026-07-09)
- **Evidencias:** `mobile/src/components/ui/Entrance.tsx` (novo), `mobile/src/components/ui/FloatEmoji.tsx` (novo), `mobile/src/components/ui/Fab.tsx`, aplicados em `mobile/app/(app)/index.tsx`, `mobile/app/(app)/metas.tsx`, `mobile/app/(app)/transacoes.tsx`. Não validado com teste automatizado (mobile sem suíte de testes configurada — ver limitação conhecida em `SYSTEM_OVERVIEW.md`).

---

## BACKLOG-0049 — Avaliar suporte a pagamento parcial de fatura de cartão

- **Titulo:** Decidir se `pagarFatura` deve passar a aceitar pagamento parcial (hoje bloqueado por design)
- **Prioridade:** P3
- **Area:** backend, frontend, mobile
- **Motivo:** Durante a revisão do fluxo de cartão/faturas de 2026-07-09 (PROB-0042/BUG-0021), confirmou-se que o bloqueio de pagamento parcial é uma decisão consciente de design, não um bug — mas não há registro formal do trade-off nem plano de quando/se isso deveria mudar.
- **Dependencias:** Nenhuma tecnica bloqueante; decisão de produto sobre se pagamento parcial de fatura faz sentido no modelo atual (fatura sem parcelamento de dívida rotativa/juros).
- **Criterio de aceite:** Decisão documentada em `SYSTEM_OVERVIEW.md` (mantida como está, ou especificação de como pagamento parcial funcionaria: saldo remanescente, juros, rollover para próxima fatura).
- **Risco se ficar pendente:** Baixo — comportamento atual é intencional e testado; risco é apenas de retrabalho futuro sem contexto se a decisão não estiver registrada.
- **Status:** ABERTO
- **Nota (2026-07-09, revisão 2, mesma sessão):** o modelo de edição/cancelamento de compra evoluiu (ver PROB-0044/BACKLOG-0052) para permitir compensação via lançamento `AJUSTE`/`ESTORNO` mesmo com fatura paga. Isso é ortogonal a este item — `pagarFatura` continua exigindo o valor exato da fatura (sem pagamento parcial); mantido `ABERTO` conforme orientação explícita de manter esta decisão em aberto.
- **Nota (2026-07-09, revisão 3, mesma sessão):** identificado efeito colateral distinto deste item — o mesmo texto de erro do backend também aparece por divergência de corrida (total da fatura muda entre o fetch da tela e o toque em "Pagar Fatura"), não apenas por pagamento parcial intencional. Tratamento de UX para esse caso específico registrado separadamente em BACKLOG-0056.

---

## BACKLOG-0050 — Avaliar aposentadoria da tabela Parcela legada para compras no cartão

- **Titulo:** `Parcela` (legada) e `FaturaLancamento` coexistem para compras parceladas no cartão — avaliar unificação
- **Prioridade:** P2
- **Area:** backend, banco
- **Motivo:** Revisão de 2026-07-09 (BUG-0017/BUG-0018) identificou que compras de cartão geram registros redundantes em duas tabelas: `Parcela` (modelo legado, vencimento começando 1 mês após a compra) e `FaturaLancamento` (modelo atual usado pelo cálculo de fatura desde a migration V17). Ambas precisaram ser corrigidas separadamente para o mesmo bug de arredondamento (`valorParcelaOuResto` em `TransacaoService` e lógica equivalente em `FaturaService`), aumentando a superfície de manutenção e risco de dessincronia futura.
- **Dependencias:** Levantamento de quem consome `Parcela` hoje (endpoints, telas mobile/frontend, relatórios) antes de qualquer remoção; migration de dados se decidido migrar histórico existente.
- **Criterio de aceite:** Decisão documentada — manter as duas tabelas (com justificativa) ou depreciar `Parcela` para compras de cartão em favor exclusivo de `FaturaLancamento`, com plano de migração se aplicável.
- **Risco se ficar pendente:** Bugs que afetam o cálculo de parcelas (como arredondamento) precisam ser corrigidos em dois lugares distintos; risco de corrigir um e esquecer o outro em manutenções futuras.
- **Status:** ABERTO

---

## BACKLOG-0051 — Backfill de resíduo de arredondamento em parcelas/faturas antigas

- **Titulo:** Avaliar se compras parceladas de cartão criadas antes da correção de BUG-0017 têm resíduo de arredondamento (limite não zera exatamente)
- **Prioridade:** P2
- **Area:** backend, banco
- **Motivo:** A correção de PROB-0038/BUG-0017 (última parcela absorve o arredondamento) só se aplica a compras criadas/editadas a partir de 2026-07-09. Compras parceladas já persistidas antes dessa data (se houver em ambiente de produção/staging) mantêm o resíduo de centavos no `valorGasto` mesmo após quitação total das faturas.
- **Dependencias:** Confirmar se há dados reais em produção anteriores a esta correção (o ambiente local de desenvolvimento não representa produção).
- **Criterio de aceite:** Levantamento de compras parceladas existentes com `SUM(parcelas.valor) != transacao.valorTotal`; script de reconciliação ajustando a última parcela/lançamento de cada compra afetada, se necessário.
- **Risco se ficar pendente:** Usuários com compras parceladas antigas podem ver limite de cartão com centavos residuais que nunca zeram mesmo após pagar tudo.
- **Status:** ABERTO

---

## BACKLOG-0052 — Decidir modelo de edição/cancelamento de compra de cartão com fatura paga

- **Titulo:** Definir se compra de cartão com fatura já paga pode ser editada/cancelada, e como
- **Prioridade:** P1
- **Area:** backend, frontend, mobile
- **Motivo:** Na primeira rodada de correção de 2026-07-09 (PROB-0039), o modelo escolhido foi bloquear qualquer edição/cancelamento de compra que envolvesse fatura já paga (`BusinessException`). Esse bloqueio se mostrou uma limitação funcional real (compra parcelada com parcela já paga não podia ser corrigida nem cancelada) e precisava de uma decisão formal de modelo antes de virar padrão definitivo.
- **Dependencias:** Nenhuma tecnica bloqueante.
- **Criterio de aceite:** Modelo definido e implementado — fatura paga tratada como imutável, com lançamento compensatório (`AJUSTE` para edição, `ESTORNO` para cancelamento) na próxima fatura em aberto, sem bloquear a operação do usuário.
- **Risco se ficar pendente:** Usuário permanentemente impedido de corrigir/cancelar compras parceladas após a primeira fatura ser paga.
- **Status:** FECHADO (2026-07-09, mesma sessão — ver PROB-0044, BUG-0023, BUG-0024)

---

## BACKLOG-0053 — UX para valorGasto negativo (crédito) do cartão

- **Titulo:** Melhorar exibição do limite do cartão quando `Conta.valorGasto` fica temporariamente negativo
- **Prioridade:** P3
- **Area:** frontend, mobile
- **Motivo:** Desde a implementação do modelo de ajuste/estorno (2026-07-09, PROB-0044/BUG-0024), `Conta.valorGasto` pode ficar negativo quando um estorno (crédito) é maior que as compras em aberto no momento — comportamento intencional que autocorrige na próxima compra/pagamento, mas a tela de contas/cartão pode exibir esse valor negativo de forma pouco intuitiva ao usuário (ex.: "limite usado: -R$100,00" sem explicação).
- **Dependencias:** Nenhuma tecnica bloqueante; depende de decisão de design de UI (mobile e frontend web).
- **Criterio de aceite:** Tela de conta/cartão exibe o `valorGasto` negativo com indicação clara de "crédito disponível" ou equivalente, em vez de apenas um número negativo sem contexto.
- **Risco se ficar pendente:** Confusão do usuário ao ver limite de cartão negativo sem explicação.
- **Status:** ABERTO

---

## BACKLOG-0054 — Rollover explícito de crédito entre faturas quando fatura contém apenas estorno

- **Titulo:** Definir comportamento quando uma fatura fecha contendo apenas lançamento(s) de estorno (total ≤ 0)
- **Prioridade:** P2
- **Area:** backend
- **Motivo:** No modelo implementado em 2026-07-09 (PROB-0044), uma fatura cujo total é ≤ 0 (só contém estorno) não é "pagável" pelo fluxo atual de `pagarFatura` — o crédito fica aguardando compras futuras na mesma fatura para compensar. Não há rollover explícito desse crédito para a fatura seguinte nem para a carteira do usuário.
- **Dependencias:** Nenhuma tecnica bloqueante; decisão de produto sobre o que fazer com crédito de estorno que não é consumido antes do fechamento da fatura.
- **Criterio de aceite:** Decisão documentada — manter como está (crédito aguarda indefinidamente na mesma fatura) ou implementar rollover automático do saldo credor para a próxima fatura/carteira.
- **Risco se ficar pendente:** Crédito de estorno pode ficar "preso" numa fatura antiga sem nunca ser aplicado, se o usuário não fizer novas compras naquele cartão.
- **Status:** ABERTO

---

## BACKLOG-0055 — Recalcular parcela cheia na redistribuição de edição de compra parcelada

- **Titulo:** Avaliar se a redistribuição de "restante ÷ parcelas não pagas" ao editar compra parcelada deveria recalcular o valor de parcela completo
- **Prioridade:** P3
- **Area:** backend
- **Motivo:** `FaturaService.ressincronizarCompraCartao` (2026-07-09, BUG-0023) redistribui o valor restante (novo total menos o que já foi pago) apenas entre as parcelas ainda não pagas, dividindo igualmente entre elas — não recalcula o valor "cheio" de uma parcela como se todo o parcelamento tivesse sido refeito desde o início. Decisão consciente de simplicidade, mas pode gerar parcelas com valores que divergem do que o usuário esperaria comparando com o parcelamento original.
- **Dependencias:** Nenhuma tecnica bloqueante; decisão de produto sobre qual comportamento é mais intuitivo para o usuário.
- **Criterio de aceite:** Decisão documentada — manter a redistribuição simples atual, ou implementar recálculo completo do parcelamento (com possível impacto em parcelas já pagas, o que exigiria tratamento adicional de fatura imutável).
- **Risco se ficar pendente:** Baixo — comportamento atual é funcional e testado; risco é de estranheza do usuário ao comparar valores de parcela antes/depois da edição.
- **Status:** ABERTO

---

## BACKLOG-0056 — Refetch/retry automático quando total da fatura muda entre carregar a tela e tocar em "Pagar Fatura"

- **Titulo:** Mensagem de erro do backend "Pagamento parcial de fatura ainda não é suportado" aparece ao usuário por divergência de corrida (total mudou entre o fetch da tela e o toque em Pagar Fatura), não por pagamento parcial real
- **Prioridade:** P2
- **Area:** frontend, mobile
- **Motivo:** `handlePagar` em `mobile/app/(app)/more/faturas.tsx:100-109` e `frontend/src/pages/Faturas.tsx:81-89` chamam `faturaService.pagarFatura(fatura.id, fatura.valorTotal, carteiraPagamentoId)` usando o `fatura.valorTotal` que já está em memória desde o último fetch. Se, entre o fetch da tela e o toque no botão, um novo lançamento (`COMPRA`/`AJUSTE`/`ESTORNO`) alterar o total real da fatura no backend (`pagarFatura` valida contra a soma atual dos `FaturaLancamento`, ver PROB-0042/BUG-0021), o valor enviado diverge do valor real e o backend responde com a mensagem genérica de "pagamento parcial não suportado" — que é tecnicamente verdadeira (o valor enviado não bate com o total atual) mas confusa para o usuário, que não fez nenhum pagamento parcial intencional.
- **Dependencias:** Nenhuma tecnica bloqueante.
- **Criterio de aceite:** Ao receber esse erro específico do backend, o client (web e mobile) refaz o fetch da fatura e tenta o pagamento novamente automaticamente (ou, no mínimo, exibe mensagem clara de "o valor da fatura mudou, tentando novamente com o valor atualizado" em vez do texto cru do backend) — com limite de tentativas para evitar loop.
- **Risco se ficar pendente:** Usuário recebe mensagem de erro tecnicamente correta mas confusa ("pagamento parcial não suportado") em um cenário que não é pagamento parcial, e precisa descobrir sozinho que precisa recarregar a tela e tentar de novo.
- **Status:** ABERTO

---

## BACKLOG-0057 — Paridade mobile/web no badge de tipo de lançamento (ajuste/estorno) da fatura

- **Titulo:** Frontend web (`frontend/src/pages/Faturas.tsx`) não recebeu o badge de tipo (`AJUSTE`/`ESTORNO`) nem a remoção do prefixo textual da descrição, implementados apenas no mobile em 2026-07-09 (PROB-0047/BUG-0029)
- **Prioridade:** P3
- **Area:** frontend
- **Motivo:** Verificação de `git diff -- frontend/src/pages/Faturas.tsx` nesta sessão (2026-07-09) confirma que o frontend web só tem a cor condicional do valor (herdada de BUG-0026) — a descrição de lançamentos `AJUSTE`/`ESTORNO` continua exibida com o prefixo textual cru (`"Ajuste: "`/`"Estorno: "`) e sem nenhum chip/badge indicando o tipo, diferente do mobile.
- **Dependencias:** Nenhuma tecnica bloqueante; depende apenas de replicar a lógica já implementada no mobile (`mobile/app/(app)/more/faturas.tsx`) para o componente React equivalente.
- **Criterio de aceite:** `frontend/src/pages/Faturas.tsx` exibe o mesmo badge de tipo (`ESTORNO`/`AJUSTE`) e remove o prefixo textual redundante da descrição, com paridade visual em relação ao mobile.
- **Risco se ficar pendente:** Inconsistência de UX entre mobile e web para o mesmo dado (usuário que usa os dois clientes vê apresentações diferentes do mesmo lançamento).
- **Status:** ABERTO

---

## BACKLOG-0058 — Refatorar importacao CSV para usar fluxo financeiro central

- **Titulo:** `ImportService` deve criar transacoes pelo mesmo caminho de `TransacaoService`
- **Prioridade:** P0
- **Area:** backend, banco, integridade financeira
- **Motivo:** PROB-0049 — importacao bypassa ledger/fatura/categoria/conta.
- **Dependencias:** Definir contrato de importacao: carteira obrigatoria/opcional, conta/cartao, deduplicacao e mapeamento de categoria.
- **Criterio de aceite:** CSV importado gera os mesmos efeitos de uma transacao criada via API normal; testes cobrem transacao com carteira, sem carteira, cartao de credito, categoria e erro parcial; nenhuma linha e persistida por `transacaoRepository.save` direto fora do fluxo central.
- **Risco se ficar pendente:** Dados importados podem corromper saldos e relatorios.
- **Status:** ABERTO

---

## BACKLOG-0059 — Formalizar modelo completo de fatura, credito e pagamento parcial

- **Titulo:** Definir e implementar comportamento de fatura com pagamento parcial, credito negativo e rollover
- **Prioridade:** P1
- **Area:** backend, produto financeiro
- **Motivo:** PROB-0050 — modelo atual de cartao e robusto para compra/fatura simples, mas incompleto para casos reais de credito.
- **Dependencias:** Decisao de produto sobre rotativo/juros, saldo devedor, saldo credor, estorno e fechamento de fatura.
- **Criterio de aceite:** Documento de regra em `SYSTEM_OVERVIEW.md`; testes para fatura parcial, fatura zerada, fatura negativa/credito, rollover para proxima fatura e pagamento total; UI/clientes conseguem exibir os estados sem ambiguidade.
- **Risco se ficar pendente:** Creditos podem ficar presos e usuarios podem nao entender saldos do cartao.
- **Status:** ABERTO

---

## BACKLOG-0060 — Adicionar constraints financeiras no PostgreSQL

- **Titulo:** Criar migration de hardening com `CHECK` constraints para tabelas financeiras
- **Prioridade:** P0
- **Area:** banco, backend
- **Motivo:** PROB-0051 — invariantes vivem so no Java em varias tabelas centrais.
- **Dependencias:** Levantamento de dados existentes para evitar migration quebrar banco com registros legados invalidos.
- **Criterio de aceite:** Migrations adicionam constraints para valores positivos/nao-negativos, ranges de mes/dia, total de parcelas, enum/status valido e coerencia basica; testes PostgreSQL cobrem constraints; dados legados tratados por backfill ou migration defensiva.
- **Risco se ficar pendente:** Qualquer bug/import/script pode persistir estado financeiro invalido.
- **Status:** ABERTO

---

## BACKLOG-0061 — Corrigir unicidade de fatura_lancamentos com parcela NULL

- **Titulo:** Substituir unique vulneravel a `NULL` por indice funcional/parcial robusto
- **Prioridade:** P0
- **Area:** banco, cartao
- **Motivo:** PROB-0052 — PostgreSQL permite duplicidade quando `parcela_numero` e `NULL`.
- **Dependencias:** Verificar se ja existem duplicidades em dados reais antes de aplicar constraint.
- **Criterio de aceite:** Migration impede duplicidade de compra a vista e parcelada; teste PostgreSQL tenta inserir duplicata com `parcela_numero NULL` e falha; codigo continua idempotente.
- **Risco se ficar pendente:** Compra a vista duplicada pode inflar fatura/limite.
- **Status:** ABERTO

---

## BACKLOG-0062 — Otimizar RelatorioService e ProjecaoService com SQL agregado

- **Titulo:** Remover agregacoes em memoria de relatorios/projecoes
- **Prioridade:** P1
- **Area:** backend, banco, performance
- **Motivo:** PROB-0053 — relatorios e projecoes carregam listas completas e filtram em Java.
- **Dependencias:** Definir DTOs/projections de repository e indices necessarios.
- **Criterio de aceite:** Top despesas, gastos por conta, contas fixas, parcelas e faturas futuras calculadas por queries agregadas/paginadas; teste com volume representativo; endpoints mantem contrato atual.
- **Risco se ficar pendente:** Lentidao/OOM com historico grande.
- **Status:** ABERTO

---

## BACKLOG-0063 — Redesenhar modulo de investimentos com integridade de posicao e caixa

- **Titulo:** Bloquear venda acima da posicao e integrar compra/venda de ativos com carteira/ledger
- **Prioridade:** P1
- **Area:** backend, investimentos, ledger
- **Motivo:** PROB-0054 — investimento hoje e controle isolado, com risco de quantidade negativa e caixa inconsistente.
- **Dependencias:** Decidir se investimentos usam carteira especifica, carteira de corretora ou novo subledger.
- **Criterio de aceite:** Quantidade/preco positivos; venda acima da posicao retorna erro; compra debita carteira; venda credita carteira; eventos de investimento auditaveis; testes cobrem compra, venda total, venda parcial e erro de venda excedente.
- **Risco se ficar pendente:** Patrimonio reportado diverge do dinheiro real.
- **Status:** ABERTO

---

## BACKLOG-0064 — Migrar rate limit para store distribuido

- **Titulo:** Substituir `ConcurrentHashMap` local por Redis/Bucket4j ou gateway rate limit
- **Prioridade:** P2
- **Area:** backend, seguranca, infra
- **Motivo:** PROB-0055 — rate limit atual nao escala para multi-instancia.
- **Dependencias:** Escolha de Redis/gateway e estrategia de chave por IP/email/rota.
- **Criterio de aceite:** Rate limit consistente entre replicas; reinicio de uma instancia nao zera tentativas; testes cobrem 429 e headers; fallback operacional documentado.
- **Risco se ficar pendente:** Brute force fica mais facil em escala horizontal.
- **Status:** ABERTO

---

## BACKLOG-0065 — Documentar e testar contrato de sessao mobile

- **Titulo:** Separar contrato web cookie+CSRF de contrato mobile token no body/secure storage
- **Prioridade:** P2
- **Area:** backend, mobile, seguranca
- **Motivo:** PROB-0056 — bypass CSRF por header mobile precisa threat model explicito.
- **Dependencias:** Confirmar storage mobile real e comportamento CORS/preflight em producao.
- **Criterio de aceite:** Documento de threat model; testes backend para web sem CSRF (403), web com CSRF (200), mobile com contrato oficial (200), request spoofado fora do contrato (bloqueado); clientes alinhados.
- **Risco se ficar pendente:** Ambiguidade de seguranca entre navegador e app nativo.
- **Status:** ABERTO

---

## BACKLOG-0066 — Migrar services financeiros para constructor injection

- **Titulo:** Reduzir `@Autowired` por campo, priorizando services/filtros financeiros
- **Prioridade:** P3
- **Area:** backend, qualidade
- **Motivo:** PROB-0057 — 135 usos de field injection reduzem testabilidade.
- **Dependencias:** Nenhuma; pode ser feito incrementalmente junto dos fixes.
- **Criterio de aceite:** Services tocados em fixes passam para construtor; novos services nao usam field injection; padrao documentado.
- **Risco se ficar pendente:** Manutencao e testes ficam mais dificeis, sem impacto funcional imediato.
- **Status:** ABERTO

---

## BACKLOG-0067 — Garantir execution real de Testcontainers/PostgreSQL

- **Titulo:** Corrigir ambiente/CI para `mvn verify -Pintegration-test` rodar sempre
- **Prioridade:** P1
- **Area:** testes, infra
- **Motivo:** PROB-0058 — integration-test PostgreSQL falhou localmente por Docker invalido.
- **Dependencias:** Docker funcional no ambiente ou CI com Testcontainers habilitado.
- **Criterio de aceite:** `PostgresMigrationIT` roda em CI e pelo menos um ambiente local documentado; falha por Docker indisponivel fica clara; migrations novas exigem teste PostgreSQL.
- **Risco se ficar pendente:** Schema pode quebrar em PostgreSQL real apesar de testes unitarios passarem.
- **Status:** ABERTO

---

## BACKLOG-0068 — Criptografar backups e automatizar restore drill

- **Titulo:** Transformar backup de banco em rotina operacional verificavel
- **Prioridade:** P1
- **Area:** infra, seguranca, operacao
- **Motivo:** PROB-0059 — backup existe, mas sem criptografia e sem validacao automatizada de restore.
- **Dependencias:** Definir destino seguro, chave de criptografia e retencao.
- **Criterio de aceite:** Backups criptografados; restore periodico em banco descartavel; log/alerta de falha; runbook de recuperacao; teste de restore documentado.
- **Risco se ficar pendente:** Vazamento de dados financeiros ou backup inutil em incidente.
- **Status:** ABERTO

---

## BACKLOG-0069 — Definir politica de build Docker com testes

- **Titulo:** Decidir se Dockerfile deve rodar testes ou depender obrigatoriamente do CI
- **Prioridade:** P3
- **Area:** backend, CI/CD
- **Motivo:** PROB-0060 — imagem backend usa `-DskipTests`.
- **Dependencias:** Fluxo de deploy oficial.
- **Criterio de aceite:** Politica documentada; se deploy manual for permitido, build deve barrar testes falhos ou exigir flag explicita; se CI for gate unico, pipeline deve impedir deploy de imagem sem suite verde.
- **Risco se ficar pendente:** Imagem com regressao pode ser empacotada fora do CI.
- **Status:** ABERTO

---

> Mantido pelo `docs-reporter`. Ultima atualizacao: 2026-07-10 (auditoria backend/non-frontend alto nivel: BACKLOG-0058 a BACKLOG-0069 — ver PROBLEM_LEDGER PROB-0049 a PROB-0060 e relatorio `REVIEW_REPORTS/2026-07-10_backend_nonfrontend_high-level-audit.md`).
