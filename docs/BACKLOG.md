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

> Mantido pelo `docs-reporter`. Ultima atualizacao: 2026-07-09 (correções pós-diagnóstico manual e efeitos visuais do protótipo — ver BUGFIX_LOG BUG-0011 a BUG-0016).
