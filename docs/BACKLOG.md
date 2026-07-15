# Backlog — Gestor Financeiro

Registro de proximos passos e itens nao tratados agora, descobertos em revisoes, auditorias e
implementacoes. Mantido pelo `docs-reporter`. Complementa `docs/PROXIMOS_PASSOS.md`.

## Padrao obrigatorio de implementacao

Todo item deve ser resolvido pela causa raiz, com desenho coerente com a arquitetura, contrato explicito, migracao segura quando aplicavel, testes proporcionais ao risco e observabilidade. Nao aceitar como conclusao: `--force` sem analise, bypass de seguranca, suppressions para esconder erro, pin arbitrario de dependencia, duplicacao de regra financeira, estado inconsistente temporario ou ajuste exclusivo para fazer teste/build passar. Excecao tecnica exige decisao registrada, risco residual, mitigacao, prazo e responsavel.

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
- **Status:** FECHADO (2026-07-13)

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
- **Status:** FECHADO (JJWT 0.13.0, 2026-07-13)

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
- **Atualizacao 2026-07-11:** frente mobile parcialmente concluida: validação centralizada de email/senha/dia, datas `DD/MM/AAAA` agora validam calendario real, auth mobile valida email antes de login/reset, perfil usa regra de senha do backend, onboarding valida numeros finitos/dia/data e cartões exigem dias de fechamento/vencimento validos. `cd mobile && npm run lint` PASS. Frontend web segue fora do escopo desta correção.
- **Atualizacao 2026-07-13:** web recebeu schemas Zod tipados e auth integrado, incluindo regra real de senha e aceite da politica. Formularios financeiros web ainda precisam migrar para os schemas antes do fechamento.
- **Atualizacao 2026-07-13:** formularios financeiros web migrados para schemas Zod centralizados: transacoes, carteiras, categorias, contas fixas, cartoes, investimentos, metas, orcamentos, faturas e onboarding. Payloads usam dados normalizados do `safeParse`; erros por campo incluem foco e atributos ARIA. `npm test`, `npm run lint`, `npm run build` e `npx tsc --noEmit` PASS.
- **Status:** FECHADO

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
- **Status:** FECHADO (Jest/RNTL, 11 testes iniciais e CI, 2026-07-13)

---

## BACKLOG-0027 — Aumentar cobertura de testes backend

- **Titulo:** Escrever testes unitarios e de integracao para services e controllers
- **Prioridade:** P2
- **Area:** backend
- **Motivo:** Cobertura precisava de gate mensuravel; descricao antiga de seis arquivos estava obsoleta.
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Testes para todos os services (Carteira, Meta, Dashboard, Conta, ContaFixa, Categoria, Parcela); coverage > 70%
- **Risco se ficar pendente:** Bugs em regras de negocio nao detectados
- **Status:** FECHADO (JaCoCo 74% global elegivel; servicos criticos >=85%; CI bloqueante, 2026-07-13)

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
- **Status:** FECHADO (SMTP validado com GreenMail e fallback seguro, 2026-07-13)

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
- **Status:** FECHADO (BUG-0052, 2026-07-11)

---

## BACKLOG-0035 — Adicionar pull-to-refresh no Dashboard mobile

- **Titulo:** RefreshControl no ScrollView do Dashboard
- **Prioridade:** P3
- **Area:** mobile
- **Motivo:** Sem mecanismo de reload alem de sair e voltar da tela
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Pull-to-refresh atualiza dados do dashboard
- **Risco se ficar pendente:** Dados stale sem forma facil de atualizar
- **Status:** FECHADO (BUG-0052, 2026-07-11)

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
- **Status:** FECHADO (BUG-0051, 2026-07-11)

---

## BACKLOG-0045 — Backfill retroativo do Ledger para transações antigas sem carteira

- **Titulo:** Rodar/estender `LedgerBackfillService` para cobrir transações criadas antes da correção do BUG-0011/BUG-0012
- **Prioridade:** P1
- **Area:** backend, banco
- **Motivo:** Antes de 2026-07-09, transações com `carteiraId` falhavam (BUG-0011) e o app mobile nem enviava `carteiraId` (BUG-0012). Transações criadas nesse período não têm `MovimentoCarteira` correspondente no Ledger, mesmo que a carteira exista — o saldo materializado da carteira pode não refletir o histórico real de transações antigas.
- **Dependencias:** BUG-0011 e BUG-0012 corrigidos (concluído em 2026-07-09). `LedgerBackfillService` já existe (PR-LEDGER-05) mas foi desenhado para backfill inicial de carteiras sem movimento nenhum, não para reconciliar transações específicas sem carteira.
- **Criterio de aceite:** Levantamento de quantas transações ativas existem sem `carteira_id` e sem movimento correspondente; decisão documentada (backfill automático vs. correção manual assistida); se aplicado, reconciliação (`LedgerReconciliationService`) retorna `OK` para todas as carteiras afetadas.
- **Risco se ficar pendente:** Saldo de carteiras de usuários com uso anterior a 2026-07-09 pode ficar permanentemente divergente do histórico real de transações.
- **Decisao documentada (2026-07-11):** Definição de "órfã" = transação ativa, com `carteira_id`, que NÃO é compra de cartão (SAIDA em conta CREDITO vai para fatura, não movimenta carteira) e sem `MovimentoCarteira` de origem `TRANSACAO`. Como `carteira.saldo` só é incrementado pelo `LedgerService`, uma órfã não passou pelo ledger E não alterou o saldo — o que gera dois cenários opostos por carteira:
  - **Cenário A** (saldo já reflete a órfã, setado por código pré-ledger): hoje `DIVERGENTE`, com `saldoMaterializado - saldoLedger == impactoAssinadoDasOrfas`. Correção segura = criar 1 `MovimentoCarteira` por órfã **sem** tocar no saldo (movimento-only), fazendo o ledger convergir → `OK`.
  - **Cenário B** (saldo NÃO reflete a órfã): hoje `OK` mas subestimado, `saldoMaterializado - saldoLedger == 0` com `impactoOrfas != 0`. Corrigir exigiria mexer no saldo (decisão de produto) → deixado para **revisão manual**, nunca automático.
  - Regra do automático: só age quando `S - L == O` por carteira; qualquer outro caso é reportado como `REVISAO_MANUAL` e não é alterado. Backfill cego (mexer em todas) corromperia um dos dois cenários.
- **Solucao aplicada (parcial, 2026-07-11):** (1) `scripts/diagnose-ledger-backfill.sql` — levantamento read-only (5 consultas). (2) `TransacaoRepository.findOrfasSemMovimentoByUsuarioId` (LEFT JOIN em conta p/ não descartar órfãs sem conta). (3) `LedgerBackfillService.reconciliarTransacoesOrfasUsuario(usuarioId, dryRun)` — scenario-aware, idempotente (idempotency key `ledger-backfill-transacao-{id}` + naturalmente idempotente pela própria query). (4) Endpoints self-scoped: `GET /api/v1/carteiras/minhas/backfill-orfas/diagnostico` (dry-run) e `POST /api/v1/carteiras/minhas/backfill-orfas` (aplica). (5) `LedgerBackfillOrfasTest` (6 testes: cenário A entrada/saída, cenário B manual, idempotência, compra-cartão excluída, isolamento por usuário) — reconciliação retorna `OK` após backfill do cenário A.
- **Pendente:** Executar o diagnóstico contra o PostgreSQL real da VPS (dados de produção não acessíveis do ambiente de dev), registrar os números e, se houver carteiras `RECONCILIAVEL`, aplicar. Carteiras `REVISAO_MANUAL`/órfãs sem carteira (consulta 4 do SQL) continuam decisão manual. Antes de qualquer `--apply`, exigir backup criptografado, restore drill, relatório dry-run versionado e reconciliação final `OK`, conforme auditoria `REVIEW_REPORTS/2026-07-13_full-system_mvp-high-level-readiness-audit.md`.
- **Status:** PARCIAL (código, decisão e testes concluídos em 2026-07-11; execução operacional na VPS pendente)

---

## BACKLOG-0046 — Remover arquivo de swap do vim commitável no repositório

- **Titulo:** Excluir `mobile/src/services/.api.ts.swp` e garantir `.gitignore` cobre `*.swp`
- **Prioridade:** P3
- **Area:** mobile, documentacao
- **Motivo:** Arquivo `mobile/src/services/.api.ts.swp` (swap de edição do vim) apareceu como untracked no repositório durante a sessão de 2026-07-09. Não deve ser versionado.
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Arquivo removido do working tree; `*.swp` adicionado ao `.gitignore` se ainda não estiver coberto.
- **Risco se ficar pendente:** Baixo — poluição do repositório, risco de commit acidental de arquivo temporário.
- **Status:** FECHADO (BUG-0052, 2026-07-11)

---

## BACKLOG-0047 — Auditar demais endpoints quanto a exposição de entidade JPA completa

- **Titulo:** Verificar se outros controllers retornam entidade completa (`Usuario`, etc.) em vez de DTO
- **Prioridade:** P2
- **Area:** backend, seguranca
- **Motivo:** BUG-0016 (PROB-0037) confirmou que `POST /api/auth/register` vazava hash bcrypt e campos de lockout por retornar a entidade `Usuario` diretamente. Não foi feita uma varredura sistemática nos demais endpoints (ex: `GET /usuarios/me`, endpoints de perfil) para confirmar que todos usam DTO/projeção.
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Levantamento de todos os `ResponseEntity.ok(entidade)` no código de `controller/`; endpoints que retornam entidade JPA com campos sensíveis (senha, tokens, lockout) convertidos para DTO.
- **Risco se ficar pendente:** Possível vazamento adicional de dados sensíveis (PII/segurança) em endpoints não revisados.
- **Status:** FECHADO (DTOs tipados + ArchUnit recursivo, 2026-07-13)

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
- **Status:** FECHADO (BUG-0052, 2026-07-11)
- **Nota (2026-07-09, revisão 2, mesma sessão):** o modelo de edição/cancelamento de compra evoluiu (ver PROB-0044/BACKLOG-0052) para permitir compensação via lançamento `AJUSTE`/`ESTORNO` mesmo com fatura paga. Naquele momento, `pagarFatura` ainda exigia o valor exato da fatura e este item foi mantido aberto.
- **Nota (2026-07-09, revisão 3, mesma sessão):** identificado efeito colateral distinto deste item — o mesmo texto de erro do backend também aparece por divergência de corrida (total da fatura muda entre o fetch da tela e o toque em "Pagar Fatura"), não apenas por pagamento parcial intencional. Tratamento de UX para esse caso específico registrado separadamente em BACKLOG-0056.
- **Nota (2026-07-11):** pagamento parcial implementado em BUG-0052; `valorPago` acumula e a fatura só vira `PAGA` quando o saldo restante é quitado.

---

## BACKLOG-0050 — Avaliar aposentadoria da tabela Parcela legada para compras no cartão

- **Titulo:** `Parcela` (legada) e `FaturaLancamento` coexistem para compras parceladas no cartão — avaliar unificação
- **Prioridade:** P2
- **Area:** backend, banco
- **Motivo:** Revisão de 2026-07-09 (BUG-0017/BUG-0018) identificou que compras de cartão geram registros redundantes em duas tabelas: `Parcela` (modelo legado, vencimento começando 1 mês após a compra) e `FaturaLancamento` (modelo atual usado pelo cálculo de fatura desde a migration V17). Ambas precisaram ser corrigidas separadamente para o mesmo bug de arredondamento (`valorParcelaOuResto` em `TransacaoService` e lógica equivalente em `FaturaService`), aumentando a superfície de manutenção e risco de dessincronia futura.
- **Dependencias:** Levantamento de quem consome `Parcela` hoje (endpoints, telas mobile/frontend, relatórios) antes de qualquer remoção; migration de dados se decidido migrar histórico existente.
- **Criterio de aceite:** Decisão documentada — manter as duas tabelas (com justificativa) ou depreciar `Parcela` para compras de cartão em favor exclusivo de `FaturaLancamento`, com plano de migração se aplicável. Para promover a V27: backup e restore drill aprovados, maintenance job `card-schedule` com zero `sem_lancamento_canonico`, relatório versionado e validação pós-migration no PostgreSQL da VPS.
- **Risco se ficar pendente:** Bugs que afetam o cálculo de parcelas (como arredondamento) precisam ser corrigidos em dois lugares distintos; risco de corrigir um e esquecer o outro em manutenções futuras.
- **Status:** PARCIAL (Release A concluida; V27 staged para Release B apos auditoria VPS, 2026-07-13)

---

## BACKLOG-0051 — Backfill de resíduo de arredondamento em parcelas/faturas antigas

- **Titulo:** Avaliar se compras parceladas de cartão criadas antes da correção de BUG-0017 têm resíduo de arredondamento (limite não zera exatamente)
- **Prioridade:** P2
- **Area:** backend, banco
- **Motivo:** A correção de PROB-0038/BUG-0017 (última parcela absorve o arredondamento) só se aplica a compras criadas/editadas a partir de 2026-07-09. Compras parceladas já persistidas antes dessa data (se houver em ambiente de produção/staging) mantêm o resíduo de centavos no `valorGasto` mesmo após quitação total das faturas.
- **Dependencias:** Confirmar se há dados reais em produção anteriores a esta correção (o ambiente local de desenvolvimento não representa produção).
- **Criterio de aceite:** Levantamento de compras parceladas existentes com `SUM(parcelas.valor) != transacao.valorTotal`; script de reconciliação ajustando a última parcela/lançamento de cada compra afetada, se necessário.
- **Risco se ficar pendente:** Usuários com compras parceladas antigas podem ver limite de cartão com centavos residuais que nunca zeram mesmo após pagar tudo.
- **Diagnostico local (2026-07-11):** `scripts/diagnose-rounding-residue-backfill.sql` rodado no Postgres local (`gestor_financeiro`) retornou 0 transações com resíduo em `parcelas`, 0 em `fatura_lancamentos` seguros e 0 casos manuais com `AJUSTE`/`ESTORNO`/rollover.
- **Solucao aplicada (2026-07-11):** `ParcelamentoRoundingBackfillService` com diagnóstico dry-run e correção idempotente self-scoped (`GET /api/v1/transacoes/minhas/backfill-arredondamento/diagnostico`, `POST /api/v1/transacoes/minhas/backfill-arredondamento`). A correção ajusta a última `Parcela` e o último `FaturaLancamento` `COMPRA` seguro; recalcula `FaturaCartao.valorTotal` pela diferença; ajusta `Conta.valorGasto` somente se a fatura não estiver `PAGA`; pula faturas com lançamentos não-`COMPRA` para revisão manual.
- **Status:** FECHADO (BUG-0054, 2026-07-11)

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
- **Status:** FECHADO (BUG-0052, 2026-07-11)

---

## BACKLOG-0054 — Rollover explícito de crédito entre faturas quando fatura contém apenas estorno

- **Titulo:** Definir comportamento quando uma fatura fecha contendo apenas lançamento(s) de estorno (total ≤ 0)
- **Prioridade:** P2
- **Area:** backend
- **Motivo:** No modelo implementado em 2026-07-09 (PROB-0044), uma fatura cujo total é ≤ 0 (só contém estorno) não é "pagável" pelo fluxo atual de `pagarFatura` — o crédito fica aguardando compras futuras na mesma fatura para compensar. Não há rollover explícito desse crédito para a fatura seguinte nem para a carteira do usuário.
- **Dependencias:** Nenhuma. Decisão de produto **travada em 2026-07-11** — ver spec "Regra de produto: credito de fatura e saldo devedor rolado" em `SYSTEM_OVERVIEW.md` (regra R1).
- **Decisao documentada (2026-07-11):** crédito de fatura total ≤ 0 vira **crédito do cartão** e é carregado para a próxima fatura (lançamento `CREDITO_ANTERIOR`, valor negativo) até zerar; NÃO vira saldo em carteira. Fatura de origem fecha `PAGA`. Detalhe e exemplos em `SYSTEM_OVERVIEW.md`. Converge com BACKLOG-0059 (implementado junto).
- **Criterio de aceite:** Conforme spec — fatura ≤ 0 fecha `PAGA` e gera crédito rolado; crédito abate a próxima fatura e some ao zerar; testes cobrem o ciclo.
- **Risco se ficar pendente:** Crédito de estorno pode ficar "preso" numa fatura antiga sem nunca ser aplicado, se o usuário não fizer novas compras naquele cartão.
- **Status:** FECHADO (BUG-0053, 2026-07-11) — implementado via rollover lazy `FaturaService.liquidarFaturaAnterior` (regra R1), migration `V25__fatura_rollover.sql`, testes em `FaturaRolloverTest`. Ver PROB-0050 e BACKLOG-0059.

---

## BACKLOG-0055 — Recalcular parcela cheia na redistribuição de edição de compra parcelada

- **Titulo:** Avaliar se a redistribuição de "restante ÷ parcelas não pagas" ao editar compra parcelada deveria recalcular o valor de parcela completo
- **Prioridade:** P3
- **Area:** backend
- **Motivo:** `FaturaService.ressincronizarCompraCartao` (2026-07-09, BUG-0023) redistribui o valor restante (novo total menos o que já foi pago) apenas entre as parcelas ainda não pagas, dividindo igualmente entre elas — não recalcula o valor "cheio" de uma parcela como se todo o parcelamento tivesse sido refeito desde o início. Decisão consciente de simplicidade, mas pode gerar parcelas com valores que divergem do que o usuário esperaria comparando com o parcelamento original.
- **Dependencias:** Nenhuma tecnica bloqueante; decisão de produto sobre qual comportamento é mais intuitivo para o usuário.
- **Criterio de aceite:** Decisão documentada — manter a redistribuição simples atual, ou implementar recálculo completo do parcelamento (com possível impacto em parcelas já pagas, o que exigiria tratamento adicional de fatura imutável).
- **Risco se ficar pendente:** Baixo — comportamento atual é funcional e testado; risco é de estranheza do usuário ao comparar valores de parcela antes/depois da edição.
- **Status:** FECHADO (BUG-0052, 2026-07-11)

---

## BACKLOG-0056 — Refetch/retry automático quando total da fatura muda entre carregar a tela e tocar em "Pagar Fatura"

- **Titulo:** Mensagem de erro do backend "Pagamento parcial de fatura ainda não é suportado" aparece ao usuário por divergência de corrida (total mudou entre o fetch da tela e o toque em Pagar Fatura), não por pagamento parcial real
- **Prioridade:** P2
- **Area:** frontend, mobile
- **Motivo:** Cenário original obsoleto após BUG-0052 (2026-07-11). O `handlePagar` de `mobile/app/(app)/more/faturas.tsx` e `frontend/src/pages/Faturas.tsx` **não** envia mais `fatura.valorTotal` cheio: agora envia o valor digitado, validado localmente contra `saldoRestante = valorTotal - valorPago`, e exibe `Pago`/`Restante`. A mensagem "pagamento parcial não suportado" foi eliminada (BUG-0021 + BUG-0052) e o `pagarFatura` aceita pagamento parcial.
- **Dependencias:** Nenhuma.
- **Criterio de aceite:** N/A — o sintoma descrito (mensagem confusa por envio de total cheio) não reproduz mais.
- **Risco se ficar pendente:** Resíduo de corrida: se um `ESTORNO` reduzir o total entre o fetch e o toque, o valor digitado pode exceder o novo `saldoRestante` e o backend responde `"Valor de pagamento maior que o saldo restante"` (mensagem clara, não mais "parcial não suportado"). Refetch/retry automático fica como melhoria opcional de UX, não bug.
- **Status:** FECHADO (BUG-0052, 2026-07-11) — cenário original resolvido; resíduo de corrida rebaixado a melhoria opcional.

---

## BACKLOG-0057 — Paridade mobile/web no badge de tipo de lançamento (ajuste/estorno) da fatura

- **Titulo:** Frontend web (`frontend/src/pages/Faturas.tsx`) não recebeu o badge de tipo (`AJUSTE`/`ESTORNO`) nem a remoção do prefixo textual da descrição, implementados apenas no mobile em 2026-07-09 (PROB-0047/BUG-0029)
- **Prioridade:** P3
- **Area:** frontend
- **Motivo:** Verificação de `git diff -- frontend/src/pages/Faturas.tsx` nesta sessão (2026-07-09) confirma que o frontend web só tem a cor condicional do valor (herdada de BUG-0026) — a descrição de lançamentos `AJUSTE`/`ESTORNO` continua exibida com o prefixo textual cru (`"Ajuste: "`/`"Estorno: "`) e sem nenhum chip/badge indicando o tipo, diferente do mobile.
- **Dependencias:** Nenhuma tecnica bloqueante; depende apenas de replicar a lógica já implementada no mobile (`mobile/app/(app)/more/faturas.tsx`) para o componente React equivalente.
- **Criterio de aceite:** `frontend/src/pages/Faturas.tsx` exibe o mesmo badge de tipo (`ESTORNO`/`AJUSTE`) e remove o prefixo textual redundante da descrição, com paridade visual em relação ao mobile.
- **Risco se ficar pendente:** Inconsistência de UX entre mobile e web para o mesmo dado (usuário que usa os dois clientes vê apresentações diferentes do mesmo lançamento).
- **Status:** FECHADO (BUG-0052, 2026-07-11)

---

## BACKLOG-0058 — Refatorar importacao CSV para usar fluxo financeiro central

- **Titulo:** `ImportService` deve criar transacoes pelo mesmo caminho de `TransacaoService`
- **Prioridade:** P0
- **Area:** backend, banco, integridade financeira
- **Motivo:** PROB-0049 — importacao bypassa ledger/fatura/categoria/conta.
- **Dependencias:** Definir contrato de importacao: carteira obrigatoria/opcional, conta/cartao, deduplicacao e mapeamento de categoria.
- **Criterio de aceite:** CSV importado gera os mesmos efeitos de uma transacao criada via API normal; testes cobrem transacao com carteira, sem carteira, cartao de credito, categoria e erro parcial; nenhuma linha e persistida por `transacaoRepository.save` direto fora do fluxo central.
- **Risco se ficar pendente:** Dados importados podem corromper saldos e relatorios.
- **Status:** FECHADO (PROB-0049, 2026-07-10) — `ImportService` chama `transacaoService.criar()` por linha.

---

## BACKLOG-0059 — Formalizar modelo completo de fatura, credito e pagamento parcial

- **Titulo:** Definir e implementar comportamento de fatura com credito negativo, fatura total `<= 0` e rollover (pagamento parcial JA feito em BUG-0052)
- **Prioridade:** P1
- **Area:** backend, produto financeiro
- **Motivo:** PROB-0050 — **pagamento parcial ja implementado** (BUG-0052, 2026-07-11: `pagarFatura` acumula `valorPago`, calcula `saldoRestante`, marca `PAGA` ao quitar). Restam os casos de credito: fatura total `<= 0` (so estorno), saldo credor e rollover para a proxima fatura/carteira. Escopo restante convergente com BACKLOG-0054.
- **Dependencias:** Decisao de produto **travada em 2026-07-11** — spec completa (R1 credito, R2 saldo devedor rolado, mapeamento no Ledger, novos `TipoFaturaLancamento` `CREDITO_ANTERIOR`/`SALDO_DEVEDOR_ANTERIOR`) em `SYSTEM_OVERVIEW.md`. Rotativo/juros fora de escopo do MVP.
- **Criterio de aceite:** Conforme spec em `SYSTEM_OVERVIEW.md` — testes para fatura zerada/negativa (credito rolado), saldo devedor rolado no fechamento parcial, rollover idempotente e pagamento total; UI/clientes exibem credito e saldo devedor rolados sem ambiguidade. (Pagamento parcial e total ja cobertos por testes de BUG-0052.)
- **Risco se ficar pendente:** Creditos de estorno podem ficar presos numa fatura e usuarios podem nao entender saldos do cartao.
- **Status:** FECHADO (BUG-0053, 2026-07-11) — R1 (credito rolado) e R2 (saldo devedor rolado) implementados via `FaturaService.liquidarFaturaAnterior` (rollover lazy na leitura, sem endpoint de fechar fatura nem scheduler), enum `TipoFaturaLancamento` com `CREDITO_ANTERIOR`/`SALDO_DEVEDOR_ANTERIOR`, migration `V25__fatura_rollover.sql` (unique index parcial de idempotencia), UI web/mobile atualizadas. Testes: `FaturaRolloverTest` (7 casos) + suite completa 142/0 falhas; `scripts/verify-postgres-migrations.sh` PASS com PostgreSQL real de teste. Ver PROB-0050 e BACKLOG-0054.

---

## BACKLOG-0060 — Adicionar constraints financeiras no PostgreSQL

- **Titulo:** Criar migration de hardening com `CHECK` constraints para tabelas financeiras
- **Prioridade:** P0
- **Area:** banco, backend
- **Motivo:** PROB-0051 — invariantes vivem so no Java em varias tabelas centrais.
- **Dependencias:** Levantamento de dados existentes para evitar migration quebrar banco com registros legados invalidos.
- **Criterio de aceite:** Migrations adicionam constraints para valores positivos/nao-negativos, ranges de mes/dia, total de parcelas, enum/status valido e coerencia basica; testes PostgreSQL cobrem constraints; dados legados tratados por backfill ou migration defensiva.
- **Risco se ficar pendente:** Qualquer bug/import/script pode persistir estado financeiro invalido.
- **Status:** FECHADO (PROB-0051, 2026-07-11) — `V20__hardening_check_constraints.sql`; validado em PostgreSQL 16 real + testes em `PostgresMigrationIT`.

---

## BACKLOG-0061 — Corrigir unicidade de fatura_lancamentos com parcela NULL

- **Titulo:** Substituir unique vulneravel a `NULL` por indice funcional/parcial robusto
- **Prioridade:** P0
- **Area:** banco, cartao
- **Motivo:** PROB-0052 — PostgreSQL permite duplicidade quando `parcela_numero` e `NULL`.
- **Dependencias:** Verificar se ja existem duplicidades em dados reais antes de aplicar constraint.
- **Criterio de aceite:** Migration impede duplicidade de compra a vista e parcelada; teste PostgreSQL tenta inserir duplicata com `parcela_numero NULL` e falha; codigo continua idempotente.
- **Risco se ficar pendente:** Compra a vista duplicada pode inflar fatura/limite.
- **Status:** FECHADO (PROB-0052, 2026-07-11) — `V21__fatura_lancamentos_unique_null_safe.sql` com indice funcional `COALESCE(parcela_numero, 0)`; validado em PostgreSQL 16 real.

---

## BACKLOG-0062 — Otimizar RelatorioService e ProjecaoService com SQL agregado

- **Titulo:** Remover agregacoes em memoria de relatorios/projecoes
- **Prioridade:** P1
- **Area:** backend, banco, performance
- **Motivo:** PROB-0053 — relatorios e projecoes carregam listas completas e filtram em Java.
- **Dependencias:** Definir DTOs/projections de repository e indices necessarios.
- **Criterio de aceite:** Top despesas, gastos por conta, contas fixas, parcelas e faturas futuras calculadas por queries agregadas/paginadas; teste com volume representativo; endpoints mantem contrato atual.
- **Risco se ficar pendente:** Lentidao/OOM com historico grande.
- **Status:** FECHADO (2026-07-11) — `RelatorioService`/`ProjecaoService` migrados para queries agregadas (`SUM`/`GROUP BY`/`ORDER BY`/`Pageable`); contrato dos endpoints mantido; indices de suporte na `V23__relatorio_projecao_support_indexes.sql`; testes `RelatorioServiceTest` + `ProjecaoServiceTest`. Ver PROB-0053.

---

## BACKLOG-0063 — Redesenhar modulo de investimentos com integridade de posicao e caixa

- **Titulo:** Bloquear venda acima da posicao e integrar compra/venda de ativos com carteira/ledger
- **Prioridade:** P1
- **Area:** backend, investimentos, ledger
- **Motivo:** PROB-0054 — investimento hoje e controle isolado, com risco de quantidade negativa e caixa inconsistente.
- **Dependencias:** Decidir se investimentos usam carteira especifica, carteira de corretora ou novo subledger.
- **Criterio de aceite:** Quantidade/preco positivos; venda acima da posicao retorna erro; compra debita carteira; venda credita carteira; eventos de investimento auditaveis; testes cobrem compra, venda total, venda parcial e erro de venda excedente.
- **Risco se ficar pendente:** Patrimonio reportado diverge do dinheiro real.
- **Status:** FECHADO (PROB-0054, 2026-07-11) — `InvestimentoService` reescrito (venda bloqueada acima da posicao, quantidade/preco validados, tipo invalido tratado); integracao de caixa implementada como opt-in via `MovimentacaoRequest.carteiraId` + `LedgerService` com origem `INVESTIMENTO` (nova migration V22); 14 testes novos em `InvestimentoServiceTest`. Decidiu-se por integracao opcional/nao-breaking em vez de carteira obrigatoria — mobile ainda precisa passar a enviar `carteiraId` para ativar o efeito de caixa (ver proximo passo de PROB-0054).

---

## BACKLOG-0064 — Migrar rate limit para store distribuido

- **Titulo:** Substituir `ConcurrentHashMap` local por Redis/Bucket4j ou gateway rate limit
- **Prioridade:** P2
- **Area:** backend, seguranca, infra
- **Motivo:** PROB-0055 — rate limit atual nao escala para multi-instancia.
- **Dependencias:** Escolha de Redis/gateway e estrategia de chave por IP/email/rota.
- **Criterio de aceite:** Rate limit consistente entre replicas; reinicio de uma instancia nao zera tentativas; testes cobrem 429 e headers; fallback operacional documentado.
- **Risco se ficar pendente:** Brute force fica mais facil em escala horizontal.
- **Status:** FECHADO (BUG-0051, 2026-07-11) — rate limit persistido em `rate_limit_buckets` com lock pessimista; ver PROB-0055.

---

## BACKLOG-0065 — Documentar e testar contrato de sessao mobile

- **Titulo:** Separar contrato web cookie+CSRF de contrato mobile token no body/secure storage
- **Prioridade:** P2
- **Area:** backend, mobile, seguranca
- **Motivo:** PROB-0056 — bypass CSRF por header mobile precisa threat model explicito.
- **Dependencias:** Confirmar storage mobile real e comportamento CORS/preflight em producao.
- **Criterio de aceite:** Documento de threat model; testes backend para web sem CSRF (403), web com CSRF (200), mobile com contrato oficial (200), request spoofado fora do contrato (bloqueado); clientes alinhados.
- **Risco se ficar pendente:** Ambiguidade de seguranca entre navegador e app nativo.
- **Status:** FECHADO (BUG-0051, 2026-07-11) — contrato web cookie+CSRF e mobile body+SecureStore separado; ver PROB-0056.

---

## BACKLOG-0066 — Migrar services financeiros para constructor injection

- **Titulo:** Reduzir `@Autowired` por campo, priorizando services/filtros financeiros
- **Prioridade:** P3
- **Area:** backend, qualidade
- **Motivo:** PROB-0057 — 135 usos de field injection reduzem testabilidade.
- **Dependencias:** Nenhuma; pode ser feito incrementalmente junto dos fixes.
- **Criterio de aceite:** Services tocados em fixes passam para construtor; novos services nao usam field injection; padrao documentado.
- **Risco se ficar pendente:** Manutencao e testes ficam mais dificeis, sem impacto funcional imediato.
- **Status:** FECHADO (BUG-0051, 2026-07-11) — `backend/src/main/java` sem `@Autowired`; produção usa constructor injection.

---

## BACKLOG-0067 — Garantir execution real de Testcontainers/PostgreSQL

- **Titulo:** Corrigir ambiente/CI para `mvn verify -Pintegration-test` rodar sempre
- **Prioridade:** P1
- **Area:** testes, infra
- **Motivo:** PROB-0058 — integration-test PostgreSQL falhou localmente por Docker invalido.
- **Dependencias:** Docker funcional no ambiente ou CI com Testcontainers habilitado.
- **Criterio de aceite:** `PostgresMigrationIT` roda em CI e pelo menos um ambiente local documentado; falha por Docker indisponivel fica clara; migrations novas exigem teste PostgreSQL.
- **Risco se ficar pendente:** Schema pode quebrar em PostgreSQL real apesar de testes unitarios passarem.
- **Status:** FECHADO (BUG-0051, 2026-07-11) — `scripts/verify-postgres-migrations.sh` roda `PostgresMigrationIT` contra PostgreSQL Docker real e CI usa o script.

---

## BACKLOG-0068 — Criptografar backups e automatizar restore drill

- **Titulo:** Transformar backup de banco em rotina operacional verificavel
- **Prioridade:** P1
- **Area:** infra, seguranca, operacao
- **Motivo:** PROB-0059 — backup existe, mas sem criptografia e sem validacao automatizada de restore.
- **Dependencias:** Definir destino seguro, chave de criptografia e retencao.
- **Criterio de aceite:** Backups criptografados; restore periodico em banco descartavel; log/alerta de falha; runbook de recuperacao; teste de restore documentado.
- **Risco se ficar pendente:** Vazamento de dados financeiros ou backup inutil em incidente.
- **Status:** FECHADO (BUG-0051, 2026-07-11) — backup criptografado por padrão, restore `.gpg` e restore drill automatizado.

---

## BACKLOG-0069 — Definir politica de build Docker com testes

- **Titulo:** Decidir se Dockerfile deve rodar testes ou depender obrigatoriamente do CI
- **Prioridade:** P3
- **Area:** backend, CI/CD
- **Motivo:** PROB-0060 — imagem backend usa `-DskipTests`.
- **Dependencias:** Fluxo de deploy oficial.
- **Criterio de aceite:** Politica documentada; se deploy manual for permitido, build deve barrar testes falhos ou exigir flag explicita; se CI for gate unico, pipeline deve impedir deploy de imagem sem suite verde.
- **Risco se ficar pendente:** Imagem com regressao pode ser empacotada fora do CI.
- **Status:** FECHADO (BUG-0052, 2026-07-11)

---

## BACKLOG-0070 — Falso positivo: build TypeScript do frontend web

- **Titulo:** Verificar suspeita de build TypeScript quebrado do frontend web
- **Prioridade:** P1
- **Area:** frontend
- **Motivo:** Suspeita registrada durante BUG-0053 indicava ~36 erros pre-existentes, mas revalidacao direta nesta rodada mostrou que o build atual fecha.
- **Evidencia:** `frontend npm run build --silent` PASS em 2026-07-11.
- **Status:** FECHADO (falso positivo, 2026-07-11)

---

## BACKLOG-0071 — Liberar build nativo e alinhar Expo

- **Prioridade:** P0
- **Area:** mobile, release
- **Motivo:** Android falha com React Native 0.81.5, Reanimated 4.5.1 e Worklets 0.10.2 incompatíveis; `expo-doctor` aprova apenas 16/18 checks.
- **Criterio de aceite:** `expo-doctor` 18/18; `tsc`, Jest, Android debug/release e iOS release PASS; `usesCleartextTraffic` removido do schema Expo e proibido em produção; smoke em device real documentado.
- **Atualizacao 2026-07-13:** causa raiz corrigida sem bypass: `nativewind` estava declarado sem configuração/uso e seu peer amplo resolveu Reanimated `4.5.1`/Worklets `0.10.2`, incompatíveis com RN `0.81.5`. Stack CSS dormente removida; Expo/Router/Linking e peers React web alinhados ao SDK 54; Reanimated `4.1.7` + Worklets `0.5.1` declarados nas versões suportadas; `expo-system-ui` adicionado para cumprir `userInterfaceStyle`; `usesCleartextTraffic` removido do app config. CI mobile agora executa `expo-doctor`.
- **Evidencias 2026-07-13:** instalação limpa `npm ci` PASS; `expo-doctor` 18/18; TypeScript PASS; Jest 11/11; export web PASS; prebuild limpo PASS; Android `assembleDebug` + `assembleRelease` PASS; iOS Release arm64 para destino genérico e Release para Simulator PASS; app Release abriu no iPhone 17 Simulator sem crash e exibiu login. Manifest release Android não contém `usesCleartextTraffic`; somente manifests debug gerados permitem HTTP local. ATS iOS mantém `NSAllowsArbitraryLoads=false`.
- **Pendente:** smoke em hardware Android/iOS físico. Nenhum device estava conectado ao host (`adb devices` vazio; Xcode listou apenas Mac e simuladores). Vulnerabilidades runtime permanecem isoladas no BACKLOG-0072 e não foram tratadas nesta etapa.
- **Status:** PARCIAL — correção e gates automatizados concluídos; hardware físico ainda impede satisfazer integralmente o critério de aceite.

---

## BACKLOG-0072 — Eliminar vulnerabilidades de dependencias

- **Prioridade:** P0
- **Area:** frontend, mobile, backend, seguranca
- **Motivo:** `npm audit --omit=dev` reportou 5 vulnerabilidades web e 24 mobile, incluindo high em dependências diretas e critical transitiva.
- **Criterio de aceite:** zero critical/high de runtime; exceções exclusivamente de toolchain possuem análise, mitigação, prazo e owner; `axios`/router corrigidos; SCA npm e Maven bloqueante no CI; builds/testes continuam verdes.
- **Atualizacao 2026-07-13:** frontend confirmado com zero vulnerabilidades; mobile atualizou `axios` e transitivas compatíveis, reduzindo de 1 critical/4 high para zero critical/high. Os 14 moderate e 1 low restantes pertencem ao toolchain Expo/RN e foram analisados em `docs/SECURITY_DEPENDENCY_RISK_REGISTER.md`; correção automática exigiria upgrade major para Expo 57 e reabriria a matriz nativa estabilizada no BACKLOG-0071. Backend atualizou Spring Boot `3.5.7 -> 3.5.16`, PostgreSQL JDBC `42.7.8 -> 42.7.13`, Tomcat `10.1.48 -> 10.1.57` e Log4j2 API/bridge `2.24.3 -> 2.25.5`; Spring Framework, Security e Jackson acompanham a matriz gerenciada do Boot. CI agora bloqueia npm runtime em high/critical e Maven em CVSS >= 7 via OWASP Dependency-Check 12.2.2, com erro do scanner também bloqueante.
- **Evidencias 2026-07-13:** `npm audit --omit=dev --audit-level=high` PASS web/mobile; web lint/build/test PASS; mobile `expo-doctor`, TypeScript e Jest PASS; backend `clean verify` PASS; OWASP Dependency-Check contra NVD atualizada PASS com zero dependências em CVSS >= 7. `NVD_API_KEY` permanece recomendada para acelerar a primeira sincronização, não sendo requisito funcional do scanner. CI remoto e proveniência do SHA pertencem ao BACKLOG-0073.
- **Status:** FECHADO — zero critical/high de runtime, risco exclusivo de toolchain formalizado e gates SCA bloqueantes implementados.

---

## BACKLOG-0073 — Consolidar main, CI e proveniencia do release

- **Prioridade:** P0
- **Area:** repositorio, CI/CD, release
- **Motivo:** baseline aprovada localmente contém 41 arquivos modificados e 18 untracked; CI remoto não foi confirmado.
- **Criterio de aceite:** working tree limpa; commits revisáveis; CI remoto verde no SHA candidato; imagens/APKs/IPAs identificam o mesmo SHA; deploy registra versão, migration e rollback.
- **Atualizacao mobile 2026-07-13:** criado `mobile-release.yml`, acionado somente apos CI verde em `main` ou manualmente, com checkout do SHA aprovado, Android Release, iOS Simulator Release, nomes de artefato contendo SHA e validação obrigatória dos secrets Sentry. Build local confirmou Android `assembleRelease` e iOS Simulator Release. Registro completo em `REVIEW_REPORTS/2026-07-13_mobile-release-hardening-implementation.md`.
- **Commit:** `2db9b58` (`feat(mobile): harden release readiness`).
- **Pendente:** CI remoto verde, artefatos publicados pelo GitHub Actions, assinatura/store e smoke em hardware. Deploy/backend/web não pertencem a esta implementação mobile.
- **Status:** PARCIAL — proveniencia e gates mobile implementados e commitados; evidencia remota pendente.

---

## BACKLOG-0074 — Corrigir drift e exposicao do Actuator na VPS

- **Prioridade:** P1
- **Area:** backend, infra, seguranca
- **Motivo:** health anônimo implantado retornou banco, disco e componentes apesar da configuração esperada `when-authorized`/`never`.
- **Criterio de aceite:** identificar profile/config efetivos; health anônimo retorna somente status; detalhes exigem autenticação ou rede interna; teste automatizado e smoke externo comprovam; deploy/runbook documentam profile ativo.
- **Status:** ABERTO — auditoria MVP 2026-07-13.

---

## BACKLOG-0075 — Automatizar jornadas criticas web e mobile

- **Prioridade:** P1
- **Area:** testes, frontend, mobile, backend
- **Motivo:** backend possui cobertura forte, mas 15 testes web e 11 mobile não validam jornadas financeiras completas.
- **Criterio de aceite:** E2E de cadastro/onboarding, transação/saldo, fatura, conta fixa, meta, sessão/refresh/logout/reset, importação/exportação e anexo; smoke Android/iOS; execução bloqueante no CI e contra staging.
- **Atualizacao mobile 2026-07-13:** workflow protegido `mobile-maestro.yml` executa Android e iOS contra staging no SHA informado. Flows nativos cobrem login, navegação para recuperação e política/consentimento antes do cadastro. CLI Maestro fixada com checksum do instalador; resultados JUnit e diagnosticos são artifacts.
- **Pendente:** executar o workflow com staging/secrets reais e ampliar cobertura para onboarding, transação/saldo, fatura, conta fixa, meta, sessão/logout, importação/exportação e anexo. Web fora do escopo desta rodada.
- **Status:** PARCIAL — infraestrutura e smokes mobile implementados; jornadas financeiras e execução remota pendentes.

---

## BACKLOG-0076 — Validar recuperacao de senha ponta a ponta

- **Prioridade:** P1
- **Area:** backend, mobile, frontend, operacao
- **Motivo:** SMTP e deep link possuem código/testes isolados, mas entrega real no ambiente implantado não foi comprovada.
- **Criterio de aceite:** provedor SMTP configurado; SPF/DKIM verificados; reset web/mobile em staging/produção controlada; token expira, é single-use e não aparece em logs; falha de entrega gera alerta sem enumeração de usuário.
- **Atualizacao mobile 2026-07-13:** Maestro agora prova que a recuperação é acessível a partir do login e renderiza o campo de e-mail. Erros de login, solicitação e redefinição passaram a ser anunciados por leitor de tela.
- **Pendente:** entrega SMTP real, abertura do deep link, expiração/single-use e login com nova senha em staging/produção controlada.
- **Status:** PARCIAL — navegação e acessibilidade mobile automatizadas; recuperação ponta a ponta não comprovada.

---

## BACKLOG-0077 — Publicar politica de privacidade e fechar direitos LGPD

- **Prioridade:** P1
- **Area:** produto, frontend, mobile, backend, LGPD
- **Motivo:** consentimento versionado, exportação e exclusão existem, mas cadastro não oferece acesso real ao texto da política aceita.
- **Criterio de aceite:** política versionada e publicada; links acessíveis web/mobile antes do aceite; versão registrada corresponde ao documento; exportação/exclusão testadas ponta a ponta; revisão jurídica registrada.
- **Atualizacao mobile 2026-07-13:** política versão `2026-07` virou tela nativa acessível antes do aceite. Checkbox possui alvo de 44pt, role/state explícitos e link independente. Maestro valida abertura, versão, direitos e retorno ao cadastro.
- **Pendente:** revisão jurídica/identidade do controlador e E2E real de exportação/exclusão. Publicação web não foi alterada porque a rodada é exclusivamente mobile.
- **Status:** PARCIAL — acesso e consentimento mobile implementados; validação jurídica e direitos E2E pendentes.

---

## BACKLOG-0078 — Fechar acessibilidade e polimento de release

- **Prioridade:** P1
- **Area:** mobile, frontend, UX, acessibilidade
- **Motivo:** inputs sem labels explícitas, controles abaixo de 44pt, falta de VoiceOver/TalkBack e acabamento web de scaffold.
- **Criterio de aceite:** WCAG AA nos fluxos críticos; toque >=44pt; labels/erros/estados anunciados; VoiceOver, TalkBack, fonte ampliada e teclado web aprovados; título/favicon web corretos; warnings relevantes zerados; auditoria `impeccable` repetida.
- **Atualizacao mobile 2026-07-13:** corrigidos controles interativos abaixo de 44pt, labels de inputs diretos, checkbox de consentimento, estados/erros anunciados e dashboard com hierarquia mais sóbria, sem hero promocional. Adicionado ESLint a11y bloqueante com `--max-warnings=0`; CI preserva gate TypeScript separado. Typecheck, lint e 11 testes PASS.
- **Pendente:** auditoria manual VoiceOver/TalkBack, fonte ampliada e contraste renderizado em hardware Android/iOS. Web fora do escopo.
- **Status:** PARCIAL — correções estáticas e gates mobile concluídos; validação assistiva em devices físicos pendente.

---

## BACKLOG-0079 — Automatizar observabilidade e coerencia documental

- **Prioridade:** P2
- **Area:** operacao, qualidade, documentacao
- **Motivo:** ausência de crash reporting/SCA comprovados e drift entre código, produção, contagens de testes e documentos.
- **Criterio de aceite:** alertas de indisponibilidade/5xx; crash reporting web/mobile sem PII financeira; métricas e SLO mínimos; CI valida IDs/links/status e publica contagens reais; overview, deploy, backlog e bugfix log referenciam SHA/ambiente.
- **Atualizacao mobile 2026-07-13:** integrado `@sentry/react-native` com release SHA/ambiente, `sendDefaultPii=false`, tracing desativado e remoção defensiva de user/request/extra/dados de breadcrumbs. Upload de source maps só é configurado quando token, organização e projeto existem; release CI falha se esses valores estiverem ausentes. Este backlog, bugfix log e relatório de implementação registram a mesma baseline `807e777`.
- **Pendente:** configurar projeto/DSN/secrets externos, executar release CI e comprovar evento sem PII; alertas/SLO e observabilidade web permanecem fora desta rodada.
- **Status:** PARCIAL — instrumentação mobile implementada; operação externa não comprovada.

---

## BACKLOG-0080 — Executar gates de deploy do hardening pre-producao (P0-1/nginx/redes/smoke)

- **Prioridade:** P0
- **Area:** infra, seguranca, backend
- **Motivo:** o fix de PROB-0066/BUG-0059 (rate limit de auth contornavel via X-Forwarded-For forjado) e o fix de PROB-0070/BUG-0063 (headers de seguranca do SPA) foram commitados em `c959dfc`, mas nao foram validados na cadeia real de proxy/rede — mudanca de `forward-headers-strategy` (framework→native) e de rede Docker tem risco real de quebrar cookies/redirects se mal configurada em producao.
- **Dependencias:** deploy do commit `c959dfc`; acesso a ambiente de staging equivalente a producao (nginx standalone e/ou atras do Nginx Proxy Manager).
- **Criterio de aceite:**
  1. `nginx -t` PASS nos dois configs (`deploy/vps/nginx.conf.template` e `deploy/vps/nginx.npm.conf`);
  2. redes do `docker-compose.production.yml` recriadas (rede interna `web<->API` e nova) e confirmado que o Proxy Host do Nginx Proxy Manager aponta para o servico `GestorFinanceiro-Web` (nao mais direto para a API);
  3. smoke em staging comprovando que um `X-Forwarded-For` forjado pelo cliente **nao** muda o bucket de rate limit resolvido pela API (teste com 2+ IPs declarados falsos, mesma origem real, mesmo bucket bloqueado);
  4. smoke confirmando cookie `refreshToken` com `Secure` funcionando e **sem** loop de redirect apos a troca `forward-headers-strategy` framework→native;
  5. carregamento do SPA em staging sem violacao de CSP no console do navegador (PROB-0070).
- **Risco se ficar pendente:** o fix de rate limit (P0 de seguranca) e os headers de seguranca do SPA permanecem nao comprovados em ambiente real — risco de a correcao nao ter efeito pratico (ou pior, quebrar autenticacao) quando promovida a producao sem essa validacao.
- **Status:** ABERTO

---

## BACKLOG-0081 — Idempotencia de `InvestimentoService.adicionarMovimentacao`

- **Prioridade:** P2
- **Area:** backend
- **Motivo:** auditoria abrangente de 2026-07-14 identificou que `InvestimentoService.adicionarMovimentacao` nao usa `Idempotency-Key`, ao contrario de outros fluxos financeiros sensiveis a duplo clique/retry (ex.: pagamento de fatura, BUG-0052; pagamento de parcela, BUG-0060) — reenvio da mesma requisicao pode duplicar compra/venda/dividendo na posicao do ativo.
- **Dependencias:** nenhuma tecnica; decisao de produto sobre se o padrao `Idempotency-Key` ja usado em fatura deve se estender a investimentos.
- **Criterio de aceite:** `adicionarMovimentacao` aceita e persiste `Idempotency-Key` por requisicao; reenvio da mesma key retorna o resultado original sem duplicar a movimentacao; teste automatizado cobrindo reenvio.
- **Risco se ficar pendente:** duplo clique ou retry de rede no lancamento de uma movimentacao de investimento pode duplicar compra/venda/dividendo, distorcendo posicao e preco medio do ativo (mesma classe de risco ja corrigida em PROB-0067/BUG-0060 para parcelas).
- **Status:** ABERTO

---

## BACKLOG-0082 — Paginacao na listagem de investimentos

- **Prioridade:** P2
- **Area:** backend
- **Motivo:** auditoria abrangente de 2026-07-14 identificou que a listagem de investimentos (ativos/movimentacoes) nao e paginada, ao contrario de outras listagens do sistema — risco de payload/consulta crescer sem limite conforme o usuario acumula historico de movimentacoes.
- **Dependencias:** nenhuma tecnica; ajuste de contrato de API (`API.md`, fora da responsabilidade deste agente de documentacao) e dos clientes (web/mobile) que consomem a listagem.
- **Criterio de aceite:** endpoint de listagem de investimentos aceita `page`/`size` (ou equivalente ja usado em outras listagens do sistema); resposta inclui metadados de paginacao; clientes web/mobile atualizados para consumir paginado.
- **Risco se ficar pendente:** degradacao de performance e payload crescente para usuarios com muitas movimentacoes de investimento acumuladas.
- **Status:** ABERTO

---

## BACKLOG-0083 — `RefreshToken.toString()` pode expor PII/segredo em logs

- **Prioridade:** P2
- **Area:** backend, seguranca, LGPD
- **Motivo:** auditoria abrangente de 2026-07-14 identificou que a entidade `RefreshToken` nao tem `toString()` customizado (ou `@ToString.Exclude` no campo sensivel) — se a entidade for logada por engano (ex.: log de debug de uma entidade JPA completa, exception com objeto anexado), o hash/valor do token pode acabar em log.
- **Dependencias:** nenhuma.
- **Criterio de aceite:** `RefreshToken.toString()` (Lombok `@ToString` ou implementacao manual) exclui explicitamente o campo do token/hash; teste ou verificacao manual confirmando que `toString()` nao contem o valor sensivel.
- **Risco se ficar pendente:** vazamento de token de refresh (equivalente a sequestro de sessao) em logs de aplicacao, caso a entidade seja logada por engano em algum ponto futuro do codigo.
- **Status:** ABERTO

---

## BACKLOG-0084 — Lombok `@Data` em entidades com relacionamento bidirecional

- **Prioridade:** P2
- **Area:** backend
- **Motivo:** auditoria abrangente de 2026-07-14 identificou pares de entidades JPA com relacionamento bidirecional usando Lombok `@Data` (que gera `equals`/`hashCode`/`toString` incluindo todos os campos, inclusive os relacionamentos) — risco de recursao infinita (`StackOverflowError`) em `toString()`/`equals()`/`hashCode()` quando ambos os lados da relacao se referenciam.
- **Dependencias:** identificar exaustivamente os pares afetados (nao levantado nesta rodada de documentacao — a auditoria original apontou o padrao de risco, sem lista fechada de entidades).
- **Criterio de aceite:** entidades com relacionamento bidirecional usam `@ToString.Exclude`/`@EqualsAndHashCode.Exclude` (ou equivalente manual) no lado que fecha o ciclo; teste ou verificacao manual de que `toString()`/`equals()`/`hashCode()` nao estoura em nenhum par bidirecional do modelo.
- **Risco se ficar pendente:** `StackOverflowError` em runtime se algum caminho de codigo (log, debug, comparacao) acionar `toString()`/`equals()`/`hashCode()` num objeto com ciclo bidirecional nao protegido.
- **Status:** ABERTO

---

## BACKLOG-0085 — Revisar defaults inseguros remanescentes em `application.properties` base

- **Prioridade:** P2
- **Area:** backend, seguranca
- **Motivo:** auditoria abrangente de 2026-07-14 reapontou risco de defaults inseguros no `application.properties` base (perfil default, nao `-vps`/`-prod`). BACKLOG-0011 (fechado em 2026-07-13) ja tratou senha de DB `1234` e JWT secret default especificos; este item cobre uma revisao mais ampla de todo o `application.properties` base para confirmar que nenhum outro default sensivel (ex.: CORS, credenciais de terceiros, flags de debug) fica implicito sem documentacao ou sem exigir override explicito em producao.
- **Dependencias:** BACKLOG-0011 (relacionado, ja fechado — este item e um follow-up mais amplo, nao uma reabertura).
- **Criterio de aceite:** revisao linha a linha do `application.properties` base classificando cada default como (a) seguro para dev local, (b) exige override obrigatorio em prod (documentado), ou (c) deve ser removido; nenhum default sensivel de producao herdado silenciosamente do perfil base.
- **Risco se ficar pendente:** configuracao insegura de producao por omissao, caso um profile futuro (`-vps`/`-prod`) deixe de sobrescrever algum default sensivel do perfil base sem que isso seja percebido.
- **Status:** ABERTO

>
> Atualizacao anterior: 2026-07-10 (auditoria backend/non-frontend alto nivel: BACKLOG-0058 a BACKLOG-0069 — ver PROBLEM_LEDGER PROB-0049 a PROB-0060 e relatorio `REVIEW_REPORTS/2026-07-10_backend_nonfrontend_high-level-audit.md`).
>
> Atualizacao 2026-07-14: hardening pre-producao P0+P1 foi commitado em `main` (`5c08ce0`, `0d1e0c0`, `c959dfc`) e fechou PROB-0066 a PROB-0072 (BUG-0059 a BUG-0065); BACKLOG-0080 registra os gates de deploy pendentes (nginx/redes/smoke) e BACKLOG-0081 a BACKLOG-0085 registram os itens P2 identificados na mesma auditoria e explicitamente adiados. Ver `docs/REVIEW_REPORTS/2026-07-14_full-system_implementation_pre-production-hardening.md`.

---

## BACKLOG-0086 — Fase 0B: ADRs de dominio como bloqueio formal da Fase 2

- **Titulo:** Escrever e aprovar ADRs de conta financeira, ledger, investimentos, orcamento, competencia, liquidez, metricas oficiais e reconciliacao antes de qualquer trabalho da Fase 2
- **Prioridade:** P1
- **Area:** arquitetura, docs
- **Motivo:** A Fase 2 (verdade financeira: unificacao Conta/Carteira, lancamentos balanceados, parcelamento canonico, metricas oficiais) exige decisoes de dominio aprovadas. A Fase 0A (`docs/adr/ADR-0001..0007`) cobre apenas as decisoes necessarias para a Fase 1.
- **Dependencias:** Fechamento dos P0 (PROB-0075..0078) e da Fase 1
- **Criterio de aceite:** ADRs de dominio aceitos pelo responsavel do produto; glossario atualizado; mapeamento dados atuais -> modelo futuro; plano de migration reversivel com reconciliacao antes/depois
- **Risco se ficar pendente:** Fase 2 iniciada sem modelo aprovado repete a duplicacao de verdade financeira que motivou a auditoria
- **Status:** ABERTO — **Fase 2 nao inicia antes da aprovacao da Fase 0B**

---

## BACKLOG-0087 — Congelamento de modulos novos ate fechamento dos P0

- **Titulo:** Nenhum modulo novo (Open Finance, WhatsApp, chat, features) enquanto P0 da auditoria estiver aberto
- **Prioridade:** P0
- **Area:** processo
- **Motivo:** Regra da auditoria `docs/15 07 2026 - MetaDoNexosFinancas.md`; foco total em integridade (PR-0..PR-4 do plano Fase 1).
- **Criterio de aceite:** Congelamento termina somente apos PR-4 mergeado, suites globais verdes (backend, web, mobile, E2E) e evidencias registradas no PROBLEM_LEDGER
- **Status:** ATIVO
