# Backlog — Gestor Financeiro

Registro de proximos passos e itens nao tratados agora, descobertos em revisoes, auditorias e
implementacoes. Mantido pelo `docs-reporter`. Complementa `docs/PROXIMOS_PASSOS.md`.

**Revisão de estado:** 25/08/2026 (`main` em `e885ed7`). `PROXIMOS_PASSOS.md` é legado; direção
atual está em `15 07 2026 - MetaDoNexosFinancas.md`. Entradas fechadas preservam diagnóstico e
evidência histórica; trabalho executável atual é formado pelos itens `ABERTO`/`PARCIAL` restantes.

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
- **Nota (2026-08-19, BACKLOG-0091):** o caminho `docs/Gestor Financeiro (standalone).html` citado
  acima **não existe mais** no working tree — removido em 2026-08-19 (`git rm`, branch
  `chore/remove-prototipo`) por decisão do dono do produto de descartar por inteiro o protótipo
  HTML e o redesign visual "Fase 4". O arquivo permanece apenas no histórico do git (até o commit
  `ae30d62`). O componente `Fab` com gradiente violeta segue em uso — este registro descreve
  apenas o caminho morto do arquivo de referência, não uma reversão desta entrada.

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
- **Status:** FECHADO (2026-07-16; confirmado em 25/08/2026) — V36 promoveu o contract
  anteriormente staged e removeu parcela redundante de compra de cartão. `FaturaLancamento` é o
  cronograma canônico; `Parcela` permanece somente para fluxo não-cartão.

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
- **Status:** PARCIAL — quatro flows mobile, incluindo `financial-critical`, passaram localmente
  em simulador em 22/08/2026. Jornadas web, execução remota e matriz Android continuam pendentes.

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
- **Atualizacao mobile 2026-08-21:** dois avanços no direito de eliminação e no acesso à política
  para quem já é usuário (antes só era linkável na tela de cadastro, `app/(auth)/privacidade.tsx`):
  (1) `mobile/app/(app)/ajustes.tsx` ganhou um item "Política de privacidade" (rodapé "Dados e
  privacidade") navegando para a mesma tela `app/(auth)/privacidade.tsx`, agora alcançável também
  por quem já tem conta; (2) o endpoint `DELETE /v1/usuarios/me` (já existente no backend, com
  `UsuarioExclusaoLgpdIT` cobrindo a exclusão em PostgreSQL real — ver PROB-0076) passou a ser
  consumido pelo mobile pela primeira vez: novo `mobile/src/services/usuarioService.ts` e um fluxo
  de confirmação dupla (`Alert` de aviso + modal `pageSheet` pedindo a senha atual) em
  `ajustes.tsx`. Testado ponta a ponta contra backend local (porta 8093, banco `gf_ajustes`): senha
  errada → 422 `BUSINESS_ERROR`/"Senha incorreta" exibido na tela (ver BUG-0069/PROB-0083 para o
  contorno de mensagem que isso exigiu); senha certa → 204, e login subsequente com a mesma conta
  falha, confirmando exclusão real. `mobile/src/__tests__/AjustesScreen.test.tsx` (10 casos) cobre
  ambos os fluxos com mocks. Exportação de dados (`GET /v1/exportar/completo`, CSV) e importação de
  extrato (CSV) já existiam antes desta sessão — apenas relocadas visualmente para a mesma tela de
  "Dados e privacidade", sem mudança de contrato.
- **Pendente:** revisão jurídica/identidade do controlador (não é algo que `docs-reporter` ou a
  sessão de implementação possam resolver) e verificação em produção/staging real (a validação de
  2026-08-21 foi contra backend local, não contra o ambiente implantado). Publicação web da
  política de privacidade e do fluxo de exclusão de conta continuam fora do escopo mobile.
- **Status:** PARCIAL — acesso à política (cadastro e, desde 2026-08-21, também para usuário
  logado) e exclusão de conta ponta a ponta implementados e testados localmente no mobile;
  validação jurídica, publicação web equivalente e verificação em ambiente implantado pendentes.

---

## BACKLOG-0078 — Fechar acessibilidade e polimento de release

- **Prioridade:** P1
- **Area:** mobile, frontend, UX, acessibilidade
- **Motivo:** inputs sem labels explícitas, controles abaixo de 44pt, falta de VoiceOver/TalkBack e acabamento web de scaffold.
- **Criterio de aceite:** WCAG AA nos fluxos críticos; toque >=44pt; labels/erros/estados anunciados; VoiceOver, TalkBack, fonte ampliada e teclado web aprovados; título/favicon web corretos; warnings relevantes zerados; auditoria `impeccable` repetida.
- **Atualizacao mobile 2026-07-13:** corrigidos controles interativos abaixo de 44pt, labels de inputs diretos, checkbox de consentimento, estados/erros anunciados e dashboard com hierarquia mais sóbria, sem hero promocional. Adicionado ESLint a11y bloqueante com `--max-warnings=0`; CI preserva gate TypeScript separado. Typecheck, lint e 11 testes PASS.
- **Pendente:** auditoria manual VoiceOver/TalkBack, fonte ampliada e contraste renderizado em hardware Android/iOS. Web fora do escopo.
- **Status:** PARCIAL — correções estáticas e gates mobile concluídos; validação assistiva em devices físicos pendente.
- **Atualização 2026-08-22:** Durante a rodada de verificação Maestro pós-padronização visual
  (simulador iPhone 17 Pro, iOS 26.5), a acessibilidade foi verificada por dumps da árvore de
  acessibilidade (`maestro hierarchy`) — a mesma fonte que VoiceOver consome — e foi assim que
  BUG-0094 (`CardMeta`, touchable aninhado escondendo as ações "Depositar"/"Editar"/"Excluir") foi
  encontrado e confirmado corrigido. Isso não substitui a pendência original: nenhum passe com
  VoiceOver/TalkBack de fato ligado no dispositivo foi feito nesta rodada, e Reduce Motion também
  não foi exercitado. Pendência permanece igual, agora com evidência adicional de que o método de
  dump por si só já encontra defeitos reais.

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
- **Status:** FECHADO (2026-08-21; confirmado em `main` em 25/08/2026) — `POST
  /api/v1/investimentos/{ativoId}/movimentacoes` aceita o header `Idempotency-Key`
  (`InvestimentoController`, mesmo padrao de `FaturaController`). `InvestimentoService.adicionarMovimentacao`
  ganhou sobrecarga com a chave, faz exists-check por `findByUsuarioIdAndIdempotencyKey` antes de
  qualquer efeito colateral e persiste a chave em `movimentacoes_ativo.idempotency_key`. Migration
  nova `V44__movimentacao_ativo_idempotency.sql` cria a coluna e o indice unico parcial
  `ux_movimentacoes_ativo_usuario_idempotency` (molde de V11). A "protecao" anterior (chave do
  ledger/operacao derivada de `mov.getId()`) era falsa — dois cliques geravam ids diferentes e
  nunca colidiam. Clientes passaram a enviar o header: `mobile/src/services/investimentoService.ts`
  + `mobile/app/(app)/more/investimentos.tsx` (chave por abertura do formulario) e
  `frontend/src/services/investimentoService.ts` + `frontend/src/pages/Investimentos.tsx` (chave
  mantida ate a movimentacao entrar). Evidencia: `./mvnw test` 292 testes/0 falhas, incluindo
  `InvestimentoIdempotenciaPaginacaoTest` (5, novo); migration V44 validada em runtime (PostgreSQL 16
  efemero, `SPRING_PROFILES_ACTIVE=dev`, `ddl-auto=validate`, Flyway aplicou 43 migrations ate v44,
  `\d movimentacoes_ativo` mostra o indice unico parcial); contrato HTTP verificado — dois POSTs com
  o mesmo `Idempotency-Key` retornaram o mesmo `id` e uma unica movimentacao. Ver BUG-0076.
  **Ressalva:** flow Maestro `financial-critical` (que exercita este endpoint via app) nao foi
  executado nesta rodada — ver BACKLOG-0095.

---

## BACKLOG-0082 — Paginacao na listagem de investimentos

- **Prioridade:** P2
- **Area:** backend
- **Motivo:** auditoria abrangente de 2026-07-14 identificou que a listagem de investimentos (ativos/movimentacoes) nao e paginada, ao contrario de outras listagens do sistema — risco de payload/consulta crescer sem limite conforme o usuario acumula historico de movimentacoes.
- **Dependencias:** nenhuma tecnica; ajuste de contrato de API (`API.md`, fora da responsabilidade deste agente de documentacao) e dos clientes (web/mobile) que consomem a listagem.
- **Criterio de aceite:** endpoint de listagem de investimentos aceita `page`/`size` (ou equivalente ja usado em outras listagens do sistema); resposta inclui metadados de paginacao; clientes web/mobile atualizados para consumir paginado.
- **Risco se ficar pendente:** degradacao de performance e payload crescente para usuarios com muitas movimentacoes de investimento acumuladas.
- **Status:** FECHADO (2026-08-21; confirmado em `main` em 25/08/2026) — muda contrato de
  API (breaking change). `GET /api/v1/investimentos` e `GET
  /api/v1/investimentos/{ativoId}/movimentacoes` passam a devolver `Page` com
  `@PageableDefault(size = 20)` e `PaginationUtils.enforceMaxSize(pageable, 100)`, como
  `TransacaoController`. Repositorios ganharam variantes paginadas. Os dois clientes consomem
  `.content ?? []` com `size=100` (padrao ja usado em `categoriaService`/`contaFinanceiraService`):
  `mobile/src/services/investimentoService.ts` + `mobile/app/(app)/more/investimentos.tsx` e
  `frontend/src/services/investimentoService.ts` + `frontend/src/pages/Investimentos.tsx`.
  Evidencia: `./mvnw test` 292/0 falhas (`InvestimentoIdempotenciaPaginacaoTest` cobre paginacao;
  `InvestimentoCustodiaCotacaoTest` ajustado ao retorno paginado); contrato verificado —
  `GET /v1/investimentos/{id}/movimentacoes` devolve envelope `Page` (`totalElements`, `size=20`);
  `GET /v1/investimentos?size=500` e capado em `size=100`. Ver BUG-0077.
  **Pendencia explicita:** o caminho citado na auditoria original (`API.md`) e, na verdade,
  `backend/API.md` — arquivo que **nao documenta** os endpoints de investimentos (nem antes nem
  depois desta mudanca; `grep -i investimento backend/API.md` nao retorna nada). Nao foi atualizado
  por este agente — fora do escopo de arquivos que o `docs-reporter` mantem, e fora do dono correto
  (`backend/API.md` nao e `docs/`). Ver BACKLOG-0097.

---

## BACKLOG-0083 — `RefreshToken.toString()` pode expor PII/segredo em logs

- **Prioridade:** P2
- **Area:** backend, seguranca, LGPD
- **Motivo:** auditoria abrangente de 2026-07-14 identificou que a entidade `RefreshToken` nao tem `toString()` customizado (ou `@ToString.Exclude` no campo sensivel) — se a entidade for logada por engano (ex.: log de debug de uma entidade JPA completa, exception com objeto anexado), o hash/valor do token pode acabar em log.
- **Dependencias:** nenhuma.
- **Criterio de aceite:** `RefreshToken.toString()` (Lombok `@ToString` ou implementacao manual) exclui explicitamente o campo do token/hash; teste ou verificacao manual confirmando que `toString()` nao contem o valor sensivel.
- **Risco se ficar pendente:** vazamento de token de refresh (equivalente a sequestro de sessao) em logs de aplicacao, caso a entidade seja logada por engano em algum ponto futuro do codigo.
- **Correcao da descricao original (2026-08-21):** a premissa da auditoria de 2026-07-14 estava
  errada — a entidade **ja tinha** `toString()` customizado (`model/RefreshToken.java`), e era
  exatamente esse metodo o vazamento: imprimia 20 chars do hash SHA-256 do token e
  `usuario.getEmail()` (PII), alem de disparar lazy-load e poder estourar NPE no `substring`.
- **Status:** FECHADO (2026-08-21; confirmado em `main` em 25/08/2026) — `toString()` agora
  expoe so `id`, `usuarioId` (via `usuario.getId()`, que nao forca o lazy load) e
  `dataExpiracao`/`revogado`. Evidencia: `RefreshTokenToStringTest` (2, novo) dentro de `./mvnw
  test` 292/0 falhas. Ver BUG-0078.

---

## BACKLOG-0084 — Lombok `@Data` em entidades com relacionamento bidirecional

- **Prioridade:** P2
- **Area:** backend
- **Motivo:** auditoria abrangente de 2026-07-14 identificou pares de entidades JPA com relacionamento bidirecional usando Lombok `@Data` (que gera `equals`/`hashCode`/`toString` incluindo todos os campos, inclusive os relacionamentos) — risco de recursao infinita (`StackOverflowError`) em `toString()`/`equals()`/`hashCode()` quando ambos os lados da relacao se referenciam.
- **Dependencias:** identificar exaustivamente os pares afetados (nao levantado nesta rodada de documentacao — a auditoria original apontou o padrao de risco, sem lista fechada de entidades).
- **Criterio de aceite:** entidades com relacionamento bidirecional usam `@ToString.Exclude`/`@EqualsAndHashCode.Exclude` (ou equivalente manual) no lado que fecha o ciclo; teste ou verificacao manual de que `toString()`/`equals()`/`hashCode()` nao estoura em nenhum par bidirecional do modelo.
- **Risco se ficar pendente:** `StackOverflowError` em runtime se algum caminho de codigo (log, debug, comparacao) acionar `toString()`/`equals()`/`hashCode()` num objeto com ciclo bidirecional nao protegido.
- **Lista fechada (2026-08-21):** `grep mappedBy backend/src/main/java/com/gestor/financeiro/model/*.java`
  confirma exatamente 2 ciclos bidirecionais no modelo: `Ativo` ↔ `MovimentacaoAtivo`
  (`model/Ativo.java:66`) e `Transacao` ↔ `Parcela` (`model/Transacao.java:80`).
- **Status:** FECHADO (2026-08-21; confirmado em `main` em 25/08/2026) — os dois pares
  ganharam `@ToString.Exclude` + `@EqualsAndHashCode.Exclude` nos dois lados; o par de
  investimentos (`Ativo`/`MovimentacaoAtivo`) ganhou tambem `@JsonIgnoreProperties`, que nao tinha
  nenhuma protecao antes (nem no JSON) — `Transacao`↔`Parcela` ja tinha. Evidencia:
  `EntidadesBidirecionaisSemRecursaoTest` (2, novo) dentro de `./mvnw test` 292/0 falhas. Ver
  BUG-0079.

---

## BACKLOG-0085 — Revisar defaults inseguros remanescentes em `application.properties` base

- **Prioridade:** P2
- **Area:** backend, seguranca
- **Motivo:** auditoria abrangente de 2026-07-14 reapontou risco de defaults inseguros no `application.properties` base (perfil default, nao `-vps`/`-prod`). BACKLOG-0011 (fechado em 2026-07-13) ja tratou senha de DB `1234` e JWT secret default especificos; este item cobre uma revisao mais ampla de todo o `application.properties` base para confirmar que nenhum outro default sensivel (ex.: CORS, credenciais de terceiros, flags de debug) fica implicito sem documentacao ou sem exigir override explicito em producao.
- **Dependencias:** BACKLOG-0011 (relacionado, ja fechado — este item e um follow-up mais amplo, nao uma reabertura).
- **Criterio de aceite:** revisao linha a linha do `application.properties` base classificando cada default como (a) seguro para dev local, (b) exige override obrigatorio em prod (documentado), ou (c) deve ser removido; nenhum default sensivel de producao herdado silenciosamente do perfil base.
- **Risco se ficar pendente:** configuracao insegura de producao por omissao, caso um profile futuro (`-vps`/`-prod`) deixe de sobrescrever algum default sensivel do perfil base sem que isso seja percebido.
- **Status:** FECHADO (2026-08-21; confirmado em `main` em 25/08/2026) — defaults do perfil
  base (`backend/src/main/resources/application.properties`) invertidos para o lado seguro:
  `spring.jpa.show-sql=false`, `app.docs.public=false`, `management.endpoint.health.show-details=never`,
  `logging.level.com.gestor.financeiro=INFO`, `logging.level.org.springframework.security=WARN`,
  `cookie.secure=${COOKIE_SECURE:true}`, `cors.allowed.origins=${CORS_ALLOWED_ORIGINS:}` (sem
  default de dev). `application-dev.properties` ja declarava todos esses valores explicitamente,
  entao o desenvolvimento nao muda. `src/test/resources/application-test.properties` passou a
  declarar `app.docs.public=true` e `show-details=always`, que os testes de infraestrutura
  exercitam e que antes vinham herdados do base. Evidencia: `./mvnw test` 292/0 falhas. Ver
  BUG-0080.

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
- **Status:** CONCLUIDO (2026-07-15) — ADR-0008..0015 aceitos pelo responsavel do produto via
  plano da Fase 2 rev. 3 (3 rodadas de review, veredito PASS); glossario atualizado (9 metricas);
  mapeamento e plano de migracao em `docs/adr/ANEXO-fase-0b-mapeamento-dados.md`. **Codigo da
  Fase 2 foi implementado; promoção operacional permanece bloqueada por BACKLOG-0088/PROB-0081.**

---

## BACKLOG-0087 — Congelamento de modulos novos ate fechamento dos P0

- **Titulo:** Nenhum modulo novo (Open Finance, WhatsApp, chat, features) enquanto P0 da auditoria estiver aberto
- **Prioridade:** P0
- **Area:** processo
- **Motivo:** Regra da auditoria `docs/15 07 2026 - MetaDoNexosFinancas.md`; foco total em integridade (PR-0..PR-4 do plano Fase 1).
- **Criterio de aceite:** Congelamento termina somente apos PR-4 mergeado, suites globais verdes (backend, web, mobile, E2E) e evidencias registradas no PROBLEM_LEDGER
- **Status:** FECHADO (2026-08-25) — PR-0..PR-4 e as Fases 1–3 foram mergeados com evidências
  automatizadas e runtime registradas. O congelamento amplo cumpriu sua função. Open Finance,
  WhatsApp e IA continuam fora da prioridade, agora por ordem de roadmap; deploy e promoção do
  PR-F2-20 seguem bloqueados especificamente por BACKLOG-0088/PROB-0081.

---

## BACKLOG-0088 — Promover PR-F2-20 após reconciliação no clone restaurado

- **Prioridade:** P0 operacional
- **Área:** backend, banco, operação
- **Motivo:** a reconciliação global está implementada em `main`, mas não autoriza deploy antes
  da prova em dados restaurados de produção.
- **Critério de aceite:** backup off-host e restore drill de `PROB-0081` aprovados; V41 aplicada no
  clone; postflight PR-F2-19 verde; maintenance `global-reconciliation` com checksum válido, zero
  divergências e zero erros; smoke autenticado de endpoint, health e métricas após deploy.
- **Rollback:** reimplantar somente o artefato anterior; PR-F2-20 não altera schema nem dados.
- **Status:** ABERTO — implementação concluída; gate externo `PROB-0081` pendente.

## BACKLOG-0089 — Atualizar CHANGELOG.md e CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md com PR-F3-01 a PR-F3-13 (Fase 3 completa)

- **Prioridade:** P3
- **Área:** documentação
- **Motivo:** o PR-F3-01 (compromissos próximos, Fase 3), o PR-F3-02 (sugestão determinística de
  categoria, Fase 3, commit `483ef36` em `main`), o PR-F3-03 (contrato de onboarding mínimo, Fase 3,
  commit `ccd0f10` em `main`), o PR-F3-04 (fundação de drill-down, Fase 3, commit `7cc4aeb` em
  `main` — fecha o Bloco A backend da Fase 3, PR-F3-01 a PR-F3-04), o PR-F3-05 (lançamento rápido
  mobile, Fase 3, commit `413d191` em `main` — primeiro PR do Bloco B, consumo mobile), o PR-F3-06
  (visão financeira mobile, Fase 3, commit `0c892bc` em `main` — segundo PR do Bloco B, consumo
  mobile), o PR-F3-07 (home reduzida mobile, Fase 3, commit `628cf8e` em `main` — terceiro PR do
  Bloco B, consumo mobile), o PR-F3-08 (drill-down até o extrato mobile, Fase 3, commit `672d97b`
  em `main` — quarto PR do Bloco B, consumo mobile), o PR-F3-09 (onboarding mobile mínimo, Fase 3,
  commit `0849847` em `main` — quinto PR do Bloco B, consumo mobile), o PR-F3-10 (setup progressivo
  mobile, Fase 3, commit `f0b27de` em `main` — sexto PR do Bloco B, consumo mobile) e o PR-F3-11
  (modalidade imutável e histórico de metas, Fase 3, commit `6712653` em `main`, com ajuste de
  flow Maestro em `feee1cb` — sétimo PR do Bloco B; é este PR, não o PR-F3-10, quem efetivamente
  fecha o Bloco B mobile da Fase 3, PR-F3-05 a PR-F3-11) foram
  implementados e revisados, mas `docs/CHANGELOG.md` e
  `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` não puderam ser atualizados em nenhuma das
  onze rodadas porque esses dois arquivos não constam na lista de arquivos sob responsabilidade do
  `docs-reporter` — a tentativa de edição foi bloqueada pelo sistema de permissão da ferramenta (e,
  nas rodadas do PR-F3-02 em diante, a restrição foi confirmada explicitamente pelo solicitante). O
  conteúdo completo de cada entrada já foi redigido e está disponível em:
  - PR-F3-01: `docs/REVIEW_REPORTS/2026-07-17_backend_implementation_pr-f3-01-compromissos-proximos.md`
    e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-17 em "Auditoria e estado atual").
  - PR-F3-02: `docs/REVIEW_REPORTS/2026-07-17_backend_implementation_pr-f3-02-sugestao-categoria.md`
    (seção "O que ficou pendente", com blocos de texto prontos para `CHANGELOG.md` e para o
    checklist) e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-17, logo após a do PR-F3-01).
  - PR-F3-03: `docs/REVIEW_REPORTS/2026-07-17_backend_implementation_pr-f3-03-onboarding-minimo.md`
    (seção "O que ficou pendente", com blocos de texto prontos para `CHANGELOG.md` e para o
    checklist) e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-17, logo após a do PR-F3-02).
  - PR-F3-04: `docs/REVIEW_REPORTS/2026-07-17_backend_implementation_pr-f3-04-drill-down.md`
    (seção "O que ficou pendente", com blocos de texto prontos para `CHANGELOG.md` e para o
    checklist) e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-17, logo após a do PR-F3-03).
  - PR-F3-05: `docs/REVIEW_REPORTS/2026-07-19_mobile_implementation_pr-f3-05-lancamento-rapido.md`
    (seção "O que ficou pendente", com blocos de texto prontos para `CHANGELOG.md` e para o
    checklist) e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-19, logo após a do PR-F3-04).
  - PR-F3-06: `docs/REVIEW_REPORTS/2026-07-19_mobile_implementation_pr-f3-06-visao-financeira.md`
    (seção "O que ficou pendente", com blocos de texto prontos para `CHANGELOG.md` e para o
    checklist) e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-19, logo após a do PR-F3-05).
  - PR-F3-07: `docs/REVIEW_REPORTS/2026-07-19_mobile_implementation_pr-f3-07-home-reduzida.md`
    (seção "O que ficou pendente", com blocos de texto prontos para `CHANGELOG.md` e para o
    checklist) e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-19, logo após a do PR-F3-06).
  - PR-F3-08: `docs/REVIEW_REPORTS/2026-07-19_mobile_implementation_pr-f3-08-drill-down.md`
    (seção "O que ficou pendente", com blocos de texto prontos para `CHANGELOG.md` e para o
    checklist) e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-19, logo após a do PR-F3-07).
  - PR-F3-09: `docs/REVIEW_REPORTS/2026-07-19_mobile_implementation_pr-f3-09-onboarding-minimo.md`
    (seção "O que ficou pendente", com blocos de texto prontos para `CHANGELOG.md` e para o
    checklist) e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-19, logo após a do PR-F3-08).
  - PR-F3-10: `docs/REVIEW_REPORTS/2026-07-19_mobile_implementation_pr-f3-10-setup-progressivo.md`
    (seção "O que ficou pendente", com blocos de texto prontos para `CHANGELOG.md` e para o
    checklist) e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-19, logo após a do PR-F3-09).
  - PR-F3-11: `docs/REVIEW_REPORTS/2026-07-19_fullstack_implementation_pr-f3-11-modalidade-metas.md`
    (seção "O que ficou pendente", com blocos de texto prontos para `CHANGELOG.md` e para o
    checklist) e `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-19, logo após a do PR-F3-10). Este
    relatório também registra a correção de rastreabilidade: é o PR-F3-11, não o PR-F3-10, quem
    efetivamente fecha o Bloco B mobile da Fase 3 (PR-F3-05 a PR-F3-11).
  - PR-F3-12 (web mínimo com drill-down do Dashboard, Fase 3, commit `9d1e8a6` em `main` —
    primeiro PR do Bloco C, web e consolidação):
    `docs/REVIEW_REPORTS/2026-07-19_web_implementation_pr-f3-12-web-minimo.md` (seção "O que ficou
    pendente", com blocos de texto prontos para `CHANGELOG.md` e para o checklist) e
    `docs/SYSTEM_OVERVIEW.md` (entrada de 2026-07-19, logo após a do PR-F3-11). A consolidação
    dessas entradas é escopo explícito do PR-F3-13 ("Legados e documentação").
  - PR-F3-13 (legados e documentação, Fase 3 — fecha o Bloco C e a Fase 3): a rodada do PR-F3-13
    (2026-07-19) TENTOU aplicar a consolidação e confirmou que `docs/CHANGELOG.md` e
    `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` estão negados por configuração de
    permissão também para a sessão principal (erro "directory denied by permission settings"),
    não apenas para o `docs-reporter`. O texto consolidado COMPLETO das treze entradas de
    CHANGELOG (por bloco/PR, com commits) e a seção de checklist da Fase 3 UX estão prontos em
    `docs/REVIEW_REPORTS/2026-07-19_fullstack_implementation_pr-f3-13-legados-documentacao.md`
    (seção "Texto pronto para CHANGELOG/CHECKLIST") — basta colar. Glossário, BACKLOG e
    SYSTEM_OVERVIEW (sem bloqueio) já foram atualizados nas próprias rodadas.
- **Dependências:** um agente ou usuário com permissão de escrita em `docs/CHANGELOG.md` e
  `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` aplicar as onze entradas (podem ser aplicadas
  em commits separados ou no mesmo commit). Para o PR-F3-05 especificamente, recomenda-se também
  executar o Maestro `financial-critical.yaml` atualizado (simulador iOS + stack local) antes de
  marcar a entrada do checklist como totalmente concluída, dado que o flow foi alterado e ainda não
  foi rodado (ver achado #2 do relatório de revisão do PR-F3-05). Para o PR-F3-06, o PR-F3-07 e o
  PR-F3-08, recomenda-se realizar a validação visual (Maestro/simulador, tema claro/escuro) em
  conjunto com a do PR-F3-05, numa única rodada para o Bloco B até aqui (ver achados #4 e #5 do
  relatório de revisão do PR-F3-06, achados #4 e #5 do relatório de revisão do PR-F3-07, e achado
  #4 do relatório de revisão do PR-F3-08) — a prioridade dessa rodada aumentou com o PR-F3-07, pois
  a home (tela mais frequentada do app) já teve estrutura visual alterada em 3 PRs consecutivos sem
  nenhuma validação end-to-end automatizada, e aumentou ainda mais com o PR-F3-08, pois este é o
  primeiro PR do Bloco B cujo comportamento novo depende diretamente de integração real com
  `expo-router` (parâmetros de rota para drill-down), não coberta por nenhum teste unitário até
  aqui. **Para o PR-F3-09, a execução do Maestro passa a ser prioridade CRÍTICA e deve ser agendada
  antes da rodada única dos PRs anteriores (não apenas acumulada a ela)** — o onboarding foi
  reescrito do zero (wizard de 6 passos → etapa única), o Maestro ganhou um bloco novo inteiro de
  setup pós-onboarding (categoria, cartão e meta criados pelas telas normais), e nenhum teste
  unitário Jest cobre `onboarding.tsx`; este é o primeiro fluxo que todo usuário novo percorre, e uma
  falha nele bloquearia a entrada de qualquer conta nova no app (ver achado #3, severidade
  MÉDIA/ALTA, do relatório de revisão do PR-F3-09). **Para o PR-F3-10, a mesma execução de Maestro
  agendada para o PR-F3-09 deve cobrir também os três fluxos de criação contextual novos** (CTA
  "Criar cartão agora" dentro do pagamento com cartão, CTA "Criar pacote inicial (9 categorias)" e
  criação de categoria única inline no lançamento, e os CTAs "Criar primeira meta"/"Cadastrar
  recorrência" nos vazios de `metas.tsx`/`contas-fixas.tsx`) e o checklist discreto da home
  ("Complete seu setup"), nenhum dos quais foi exercitado por simulador ou Maestro nesta rodada (ver
  achado #3 do relatório de revisão do PR-F3-10). **Para o PR-F3-11, a mesma execução de Maestro
  agendada para o Bloco B deve cobrir também a criação de meta com escolha obrigatória de
  modalidade** — o flow `financial-critical.yaml` já foi corrigido para tocar "Cofre real" antes de
  preencher a meta smoke (commit de acompanhamento `feee1cb`, confirmado por leitura direta de
  `git show`/`git rev-parse HEAD` pelo `docs-reporter`), mas a rodada real de simulador continua sem
  ocorrer; esta é a rodada final que fecha a validação visual/E2E pendente de todo o Bloco B
  (PR-F3-05 a PR-F3-11, ver achado #3 do relatório de revisão do PR-F3-11).
- **Critério de aceite:**
  - `CHANGELOG.md` tem entrada `## [Fase 3 — PR-F3-01] - 2026-07-17` acima da entrada `PR-F2-20`,
    entrada `## [Fase 3 — PR-F3-02] - 2026-07-17` acima da entrada `PR-F3-01`, entrada
    `## [Fase 3 — PR-F3-03] - 2026-07-17` acima da entrada `PR-F3-02`, entrada
    `## [Fase 3 — PR-F3-04] - 2026-07-17` acima da entrada `PR-F3-03`, entrada
    `## [Fase 3 — PR-F3-05] - 2026-07-19` acima da entrada `PR-F3-04`, entrada
    `## [Fase 3 — PR-F3-06] - 2026-07-19` acima da entrada `PR-F3-05`, entrada
    `## [Fase 3 — PR-F3-07] - 2026-07-19` acima da entrada `PR-F3-06`, entrada
    `## [Fase 3 — PR-F3-08] - 2026-07-19` acima da entrada `PR-F3-07`, entrada
    `## [Fase 3 — PR-F3-09] - 2026-07-19` acima da entrada `PR-F3-08`, e entrada
    `## [Fase 3 — PR-F3-10] - 2026-07-19` acima da entrada `PR-F3-09`, e entrada
    `## [Fase 3 — PR-F3-11] - 2026-07-19` acima da entrada `PR-F3-10` (ordem cronológica inversa,
    seguindo o padrão já usado no arquivo).
  - O checklist tem seção `PR-F3-01` marcada como concluída com evidência de `./mvnw test`
    (243/243) e `./mvnw verify -Pintegration-test` (243 + 27 ITs), seção `PR-F3-02` marcada como
    concluída com evidência de `./mvnw verify -Pintegration-test` (247 unitários + 27 ITs, commit
    `483ef36`), seção `PR-F3-03` marcada como concluída com evidência de
    `./mvnw verify -Pintegration-test` (249 unitários + 27 ITs, commit `ccd0f10`), seção
    `PR-F3-04` marcada como concluída com evidência de `./mvnw verify -Pintegration-test`
    (255 unitários + 27 ITs, commit `7cc4aeb`), incluindo a observação de que fecha o Bloco A
    backend da Fase 3, seção `PR-F3-05` marcada como `PASS_COM_RESSALVA` com evidência de
    `npx tsc --noEmit` limpo e Jest 26/26 (9 suites, commit `413d191`), incluindo explicitamente os
    itens em aberto (Maestro `financial-critical.yaml` não executado, evidência visual claro/escuro
    e medição do tempo do fluxo pendentes), seção `PR-F3-06` marcada como `PASS_COM_RESSALVA` com
    evidência de `npx tsc --noEmit` limpo e Jest 26/26 (9 suites, commit `0c892bc`), incluindo
    explicitamente os itens em aberto (Maestro/simulador não executado para a tela nova, evidência
    visual claro/escuro pendente, acumulada com a mesma pendência do PR-F3-05), seção `PR-F3-07`
    marcada como `PASS_COM_RESSALVA` com evidência de `npx tsc --noEmit` limpo e Jest 29/29
    (10 suites, 1 nova, commit `628cf8e`), incluindo explicitamente os itens em aberto
    (Maestro/simulador não executado para a home reescrita, evidência visual claro/escuro pendente,
    ambos acumulados com PR-F3-05/06, e a nota de escopo de que `perfil.tsx` mantém o consumo de
    `/v1/dashboard/resumo` até o PR-F3-13), e seção `PR-F3-08` marcada como `PASS_COM_RESSALVA` com
    evidência de `npx tsc --noEmit` limpo e Jest 31/31 (11 suites, 1 nova, commit `672d97b`),
    incluindo explicitamente os itens em aberto (Maestro/simulador não executado para o fluxo de
    drill-down, evidência visual claro/escuro pendente, ambos acumulados com PR-F3-05/06/07, a
    falha silenciosa para `transacaoId` inválido/inacessível em `transacoes.tsx` sem feedback
    visual ao usuário, e o parser de `params.inicio` sem validação de limites de mês/dia além do
    formato), seção `PR-F3-09` marcada como `PASS_COM_RESSALVA` com evidência de `npx tsc --noEmit`
    limpo e Jest 31/31 (11 suites, mesma contagem do PR-F3-08, commit `0849847`), incluindo
    explicitamente os itens em aberto (Maestro não executado para o onboarding reescrito do zero —
    prioridade CRÍTICA — e para o bloco novo de setup pós-onboarding, evidência visual claro/escuro
    pendente, e o critério de UX "usuário novo chega à home em menos de 60s" não medido), e seção
    `PR-F3-10` marcada como `PASS_COM_RESSALVA` com evidência de `npx tsc --noEmit` limpo e Jest
    33/33 (12 suites, 1 nova, commit `f0b27de`), incluindo explicitamente os itens em aberto (Maestro
    não executado para os três fluxos de criação contextual e para o checklist da home, evidência
    visual claro/escuro pendente, acumulada com PR-F3-05 a PR-F3-09, e a heurística do checklist —
    cartão fica de fora por não ser derivável com confiança das 4 queries da home — registrada como
    decisão de produto, não um bug), e seção `PR-F3-11` marcada como `PASS_COM_RESSALVA` com
    evidência de `./mvnw verify -Pintegration-test` (255 unitários + 27 ITs, commit `6712653`),
    `npx tsc --noEmit` limpo e Jest 36/36 (12 suites), incluindo explicitamente os itens em aberto
    (Maestro/simulador não executado de fato para o flow `financial-critical.yaml`, ainda que o flow
    já tenha sido corrigido textualmente para a escolha de modalidade pelo commit de acompanhamento
    `feee1cb`; evidência visual claro/escuro pendente, acumulada com PR-F3-05 a PR-F3-10; e a nota de
    que este PR, não o PR-F3-10, é quem fecha o Bloco B mobile da Fase 3, PR-F3-05 a PR-F3-11).
- **Risco se ficar pendente:** histórico de versões e checklist de execução ficam temporariamente
  incompletos para quem consulta apenas esses dois arquivos; a rastreabilidade completa dos onze
  PRs já existe em `SYSTEM_OVERVIEW.md` e nos respectivos relatórios de revisão.
- **Status:** FECHADO (2026-08-25) — `CHANGELOG.md` recebeu consolidação das Fases 3 e série
  visual; `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` recebeu estado consolidado e referências
  dos PR-F3-01..13. Relatórios individuais permanecem como evidência detalhada.

---

## BACKLOG-0090 — Decidir destino do stash `fase4-prototipo-descartado-2026-08-19`

- **Titulo:** Definir se o código do redesign visual "Fase 4" (dark-first ciano) descartado fica
  apenas em stash, é exportado para um branch nomeado, ou é dropado de vez
- **Prioridade:** P2
- **Área:** mobile, documentação
- **Motivo:** Em 2026-08-19, na branch `chore/remove-prototipo`, o dono do produto decidiu descartar
  por inteiro o protótipo HTML e o redesign "Fase 4" do mobile ("ficou horrível e só atrapalha o
  sistema" — ver PROB-0082 e `docs/REVIEW_REPORTS/2026-08-19_mobile_decisao_reversao-prototipo-fase4.md`).
  A reversão foi feita via `git stash push --include-untracked -m
  "fase4-prototipo-descartado-2026-08-19"`, então o código completo (tema dark-first ciano em
  `mobile/src/theme/colors.ts`/`theme/index.ts`, telas `mobile/app/(app)/carteira.tsx` e
  `mobile/app/(app)/analises.tsx`, componentes `CardBadge`, `CreditCardArt`, `DiaHeader`,
  `MerchantLogo`, `ProgressRing`, `TransacaoRow`, `mobile/src/domain/marcas.ts`,
  `mobile/src/store/themePref.ts`, `mobile/src/utils/color.ts`, testes novos e
  `mobile/.maestro/fase4-visual.yaml`) existe **somente** em `git stash@{0}` no momento deste
  registro. Um `git stash drop`/`git stash clear` acidental apaga esse trabalho fora do reflog local.
- **Dependências:** Nenhuma técnica; depende apenas de decisão do dono do produto.
- **Critério de aceite:** Uma das três opções executada e documentada: (a) stash mantido
  intencionalmente com prazo de revisão registrado; (b) `git branch fase4-prototipo-descartado
  stash@{0}` (ou equivalente) para persistir o código fora do stash sem afetar `main`; (c)
  `git stash drop` explicitamente autorizado pelo dono após confirmação de que o código não será
  reaproveitado.
- **Risco se ficar pendente:** Perda irreversível (fora do reflog) de ~458 linhas de código já
  implementado e validado (`tsc --noEmit` limpo, Jest 36/36 no momento da reversão) caso o stash
  seja descartado por engano em uma limpeza futura (`git stash clear`, `git gc` agressivo, etc.).
- **Status:** FECHADO em 2026-08-19 — opção (c) executada. O dono autorizou explicitamente apagar de vez ("Apagar de vez"), e o `git stash drop` do stash `fase4-prototipo-descartado-2026-08-19` foi executado após o commit da remoção na `main`. O redesign "Fase 4" não existe mais em nenhum ponto recuperável do repositório.

---

## BACKLOG-0091 — Anotar caminho morto `docs/Gestor Financeiro (standalone).html` em registros históricos

- **Titulo:** Registros históricos que citam o protótipo removido devem sinalizar que o caminho não
  existe mais no working tree
- **Prioridade:** P3
- **Área:** documentação
- **Motivo:** Em 2026-08-19 (branch `chore/remove-prototipo`), o arquivo
  `docs/Gestor Financeiro (standalone).html` foi removido do working tree (`git rm`), permanecendo
  apenas no histórico do git até o commit `ae30d62`. `docs/BACKLOG.md` (BACKLOG-0048), `docs/SYSTEM_OVERVIEW.md`
  (item 11 da lista de decisões técnicas e na seção "Auditoria e estado atual") e `docs/BUGFIX_LOG.md`
  citam esse caminho como referência de design em registros datados de 2026-07-09. Esses registros
  são história verdadeira no momento em que foram escritos e não devem ser apagados nem reescritos,
  mas passam a referenciar um arquivo inexistente no HEAD atual.
  Este registro já cumpre parte da anotação: ver notas adicionadas em `docs/BACKLOG.md` (BACKLOG-0048)
  e `docs/SYSTEM_OVERVIEW.md` (itens 11 e "Correções 2026-07-09") nesta mesma rodada do `docs-reporter`.
- **Dependências:** Nenhuma.
- **Critério de aceite:** `docs/BUGFIX_LOG.md` (~L766, contexto do backup off-host, menção lateral a
  "standalone" no sentido de topologia de nginx, não do protótipo — confirmar se aplica) revisado
  para clareza; nenhuma citação ativa trata o caminho do protótipo como referência válida sem nota.
- **Risco se ficar pendente:** Baixo — confusão eventual de quem consulta o histórico sem saber que o
  redesign visual foi descartado por completo em 2026-08-19.
- **Status:** FECHADO (2026-08-25) — referências ativas apontam `DESIGN.md` e tokens atuais;
  menções ao standalone em BACKLOG/SYSTEM_OVERVIEW estão marcadas como caminho morto e os
  relatórios históricos permanecem deliberadamente datados.

---

## BACKLOG-0092 — Portar o mesmo bug de fuso horário (`LocalDate` formatado via `Date`/UTC) para o frontend web

- **Titulo:** `frontend` tem o mesmo bug corrigido no mobile pelo BUG-0067, sem helper compartilhado de
  formatação de data
- **Prioridade:** P1
- **Área:** frontend
- **Motivo:** Em 2026-08-19, ao corrigir BUG-0067 no mobile (`mobile/src/utils/format.ts`), confirmou-se
  que o `frontend` web tem o mesmo problema — datas `LocalDate` (`YYYY-MM-DD`) formatadas com
  `new Date(x).toLocaleDateString('pt-BR')` caem no dia anterior em `America/Sao_Paulo` (UTC-3) porque
  `new Date('YYYY-MM-DD')` é interpretada como meia-noite UTC. Não existe `frontend/src/utils/format.ts`
  nem equivalente — a formatação é feita inline em cada tela. Confirmado nos seguintes pontos (leitura
  de código, sem execução/reprodução visual no frontend web nesta rodada):
  `frontend/src/pages/Transacoes.tsx:612`, `frontend/src/pages/Faturas.tsx:178` e `:278`,
  `frontend/src/pages/ContasFixas.tsx:445`, `frontend/src/pages/Relatorios.tsx:178`. Já existem
  workarounds ad-hoc (`T12:00:00`/`T00:00:00`) que contornam o mesmo bug em pontos isolados:
  `frontend/src/pages/Dashboard.tsx:112`, `frontend/src/pages/Investimentos.tsx:108`,
  `frontend/src/pages/Metas.tsx:464` — mesmo padrão de contorno disperso que existia no mobile antes do
  BUG-0067.
- **Dependências:** Nenhuma técnica. Referência de implementação: `mobile/src/utils/format.ts`
  (`formatDateOnlyBR` + regex `ISO_DATE_ONLY` em `formatDate`) e `mobile/src/__tests__/format.test.ts`,
  ambos do BUG-0067.
- **Critério de aceite:** Novo `frontend/src/utils/date.ts` com helper equivalente a
  `formatDateOnlyBR`/`formatDate` do mobile (monta a data por componentes, sem depender de
  `new Date(string)` para strings date-only); teste em `frontend/src/utils/date.test.ts` (Vitest,
  já é o runner do frontend) cobrindo os casos de virada de dia/mês/ano documentados no teste do mobile;
  os 5 call sites listados e os 3 workarounds ad-hoc migrados para o helper novo.
  Consultar `docs/BUGFIX_LOG.md` (BUG-0067) para o mesmo padrão de correção já validado no mobile.
- **Risco se ficar pendente:** Dado financeiro exibido com data errada no frontend web (mesma classe de
  impacto do BUG-0067 no mobile — no mobile, a variante em `metas.tsx` chegou a persistir a data errada
  de volta no backend via round-trip do input de edição; risco equivalente não descartado no web sem
  auditoria dos formulários de edição que usam datas como default).
- **Status:** ABERTO

---

## BACKLOG-0093 — Lint do mobile falha com regra `react-hooks/exhaustive-deps` não encontrada

- **Titulo:** `npm run lint` do mobile reporta 2 erros pré-existentes e bloqueantes em
  `NovaTransacaoModal.tsx`, não relacionados a nenhuma mudança recente
- **Prioridade:** P2
- **Área:** mobile, infra
- **Motivo:** Durante a validação do BUG-0067 (2026-08-19), `npm run lint` no `mobile/` retornou 2 erros:
  "Definition for rule 'react-hooks/exhaustive-deps' was not found" em
  `mobile/src/components/NovaTransacaoModal.tsx:108` e `:164`. Confirmado que o problema é pré-existente
  e não foi introduzido pela correção do BUG-0067 — os mesmos 2 erros ocorrem reproduzindo o lint com a
  árvore de trabalho limpa (`git stash -u`), em um arquivo que a correção do BUG-0067 não tocou. Como o
  script de lint roda com `--max-warnings=0` e é bloqueante na CI (`.github/workflows/ci.yml`), qualquer
  PR do mobile — incluindo o do BUG-0067 — fica bloqueado até isso ser resolvido, mesmo sem relação com
  a mudança em si.
- **Dependências:** Nenhuma técnica. Provável causa: dependência `eslint-plugin-react-hooks` ausente,
  desalinhada de versão, ou não referenciada corretamente na config do ESLint do `mobile/` — não
  investigado a fundo nesta rodada (fora do escopo de `docs-reporter`, que não altera config/deps).
- **Critério de aceite:** `npm run lint` no `mobile/` executa sem o erro "Definition for rule ... was not
  found"; a regra `react-hooks/exhaustive-deps` volta a ser aplicada de fato (ativa e funcional, não
  apenas silenciada) em `NovaTransacaoModal.tsx` e no restante do projeto.
- **Risco se ficar pendente:** CI do mobile bloqueada para qualquer PR (lint é `--max-warnings=0`);
  força bypass manual ou espera de correção não relacionada para promover mudanças legítimas, incluindo
  o BUG-0067.
- **Status:** FECHADO — verificado pelo `docs-reporter` em 2026-08-21: `npm run lint` (cwd
  `mobile/`) roda `eslint app src --ext .ts,.tsx --max-warnings=0` e retorna exit code 0, sem
  nenhuma ocorrência de "Definition for rule 'react-hooks/exhaustive-deps' was not found". Não foi
  possível determinar nesta rodada **quando** ou **por qual mudança** o erro deixou de ocorrer —
  nenhum arquivo de configuração de lint (`.eslintrc*`, `package.json` do mobile) foi tocado pela
  sessão de implementação corrente (redesign de `ajustes.tsx`), então a correção aconteceu em
  alguma rodada anterior não documentada por este item. Registrado como fechado por evidência
  direta de execução, não por relato de terceiros.

---

## BACKLOG-0095 — `financial-critical` ainda vermelho no trecho da compra no cartão

- **Titulo:** O flow Maestro `financial-critical` passa 119 comandos e falha ao asseverar
  "Compra cartão smoke" depois de salvar a segunda transação (compra no cartão)
- **Prioridade:** P2
- **Área:** mobile, testes
- **Motivo (histórico, 2026-08-21, execução em simulador):** Na primeira execução real em
  simulador (iPhone 17 Pro, iOS 26.5, Release, backend local 8090/`gf_e2e`), o flow passou a
  cobrir cadastro em 3 passos, onboarding em 6, criação de categoria, cartão, meta e a
  **primeira** transação. A falha restante era no bloco da segunda transação: o flow seleciona
  "Cartão" como forma de pagamento, assevera "Cartão Principal" e salva, mas a transação não
  aparecia na home. Não tinha sido determinado se faltava selecionar o cartão específico (chip
  separado do "pagar com"), se a validação recusava silenciosamente ou se era timing de refetch.
- **Causa raiz identificada (2026-08-21, rodada de correção):** diferente da hipótese acima — não
  é timing nem validação silenciosa. A validação de cartão existe e é visível
  (`mobile/src/components/NovaTransacaoModal.tsx:273` seta `pagamentoError`) e `:151` auto-seleciona
  o primeiro cartão. O real problema: o `invalidateQueries` do modal não incluía `['home']` nem
  `['operacoes']` — as duas únicas chaves que alimentam a Home — e nenhum outro ponto do app
  invalidava essas chaves. A Home só atualizava porque o call site dela (`app/(app)/(inicio)/index.tsx`)
  passava `onSaved` com `refetch()` manual; salvar pelo FAB da tab bar (`app/(app)/_layout.tsx`,
  `onSaved` navega para `/transacoes`) ou pela tela de faturas deixava a Home com dados velhos. O
  flow rola a Home antes de tocar "Nova transação", o `SaldoCard` sai do viewport e o toque acerta
  o FAB da tab bar — o caminho que não atualizava.
- **Correção aplicada (2026-08-21):** fonte única de invalidação em
  `mobile/src/hooks/useInvalidarAposTransacao.ts` (`CHAVES_AFETADAS_POR_TRANSACAO` inclui `['home']`
  e `['operacoes']`), consumida por `mobile/src/components/NovaTransacaoModal.tsx` e
  `mobile/src/components/EditarTransacaoModal.tsx` (que antes duplicavam a lista de chaves cada
  um). O `onSaved` da Home (`app/(app)/(inicio)/index.tsx`) deixou de fazer `refetch()` manual
  (pull-to-refresh continua usando `atualizar()`). No flow `mobile/.maestro/financial-critical.yaml`,
  o `extendedWaitUntil` do bloco "Compra cartão smoke" virou `scrollUntilVisible` (a seção
  "Parcelas Agendadas" aparece e empurra a lista) e foi acrescentado o guard rail
  `assertNotVisible: "Selecione um cartão."`. Teste automatizado novo:
  `mobile/src/__tests__/invalidarAposTransacao.test.ts` (3 casos — invalida as duas queries da
  Home; invalida a lista compartilhada mais as chaves extras da edição; sem chave duplicada na
  lista compartilhada).
- **Dependências:** Nenhuma. Os demais flows (`smoke-auth`, `privacy-consent`,
  `recovery-navigation`) estão verdes.
- **Critério de aceite:** `financial-critical` verde ponta a ponta contra backend local, e os
  passos seguintes (fatura, pagamento parcial, reserva na meta) exercitados pelo menos uma vez.
- **Risco se ficar pendente:** o trecho financeiro mais sensível (fatura e pagamento parcial)
  continua sem cobertura E2E executada, embora coberto por testes de backend.
- **Status:** FECHADO (2026-08-22) — execução posterior registrada em BACKLOG-0098 deixou
  `financial-critical` verde ponta a ponta no simulador, incluindo fatura, pagamento parcial,
  meta e guard-rails. Ver relatório de verificação mobile de 22/08/2026.

---

## BACKLOG-0096 — Telas com texto invisível para a árvore de acessibilidade

- **Titulo:** Vários rótulos só existem dentro de `Touchable` com `accessibilityLabel` próprio, o
  que os esconde de leitores de tela e de automação
- **Prioridade:** P3
- **Área:** mobile, acessibilidade
- **Motivo:** Durante a rodada Maestro de 2026-08-21, `tapOn`/`assertVisible` por texto falharam em
  elementos claramente visíveis: botão "Carteira" do `SaldoCard` (label "Abrir carteira"), ação
  "+ Cartão" da Carteira ("Cadastrar novo cartão"), "Salvar" do formulário de cartão ("Salvar
  cartão") e o nome da meta no `CardMeta` ("Abrir detalhes da meta X"). Os flows foram ajustados
  para usar os labels reais, mas o padrão merece uma revisão: quando o label do container substitui
  o texto, o conteúdo textual some da árvore.
- **Critério de aceite:** convenção registrada no DESIGN.md sobre quando usar `accessibilityLabel`
  no container e quando deixar o texto acessível; telas revisadas conforme a convenção.
- **Status:** FECHADO (2026-08-21; confirmado em `main` em 25/08/2026) — convenção nova
  registrada em `DESIGN.md` (seção "Acessibilidade"): controle com texto visível não leva
  `accessibilityLabel` (o RN deriva o rótulo dos filhos); `accessibilityLabel` só para controles
  icon-only; contexto extra vai em `accessibilityHint`; `accessibilityRole`/`accessibilityState`
  continuam sempre. Pontos corrigidos (label removido, hint quando havia contexto a preservar):
  `mobile/src/components/home/SaldoCard.tsx` (botões "Nova Transação" e "Carteira", era "Abrir
  carteira"); `mobile/app/(app)/more/faturas.tsx` (ação "+ Cartão", era "Cadastrar novo cartão", e
  "Salvar" do formulário de cartão, era "Salvar cartão"); `mobile/src/components/metas/CardMeta.tsx`
  (card da meta, era "Abrir detalhes da meta X", e botão "Depositar", era "Depositar na meta X"; o
  `AnelProgresso` deixou de repetir o nome da meta no label). Mesma regra aplicada em pontos não
  catalogados originalmente no item: `mobile/src/components/NovaTransacaoModal.tsx` (4 botões de
  setup rápido), `mobile/src/components/home/ParcelasCarrossel.tsx` (2),
  `mobile/src/components/carteira/LinhaFatura.tsx` (1), `mobile/app/(app)/more/fatura.tsx` ("Pagar
  Fatura"). Mantidos os labels legítimos de controles icon-only (`ui/Fab`, FAB da tab bar,
  `ui/BackButton`, badge "OK/Divergente" da tela Contas, ícones de editar/excluir). Flows Maestro
  voltaram a buscar por texto: `mobile/.maestro/financial-critical.yaml` usa "Carteira", "Cartão",
  "Salvar", "Meta Smoke", "Depositar" no lugar dos labels de workaround; o comentário de workaround
  foi removido. Evidência: `npx tsc --noEmit` limpo e Jest 200/200 no mobile. Ver BUG-0081.
  **Ressalva:** não houve verificação manual com TalkBack/VoiceOver.

---

## BACKLOG-0094 — Corrigir interceptor Axios do mobile para não descartar mensagem de `BusinessException`

- **Titulo:** `mobile/src/services/api.ts` só usa `error.response.data.details` em 400/422; mensagem
  de `BusinessException` (`{"code":"BUSINESS_ERROR","message":"..."}`) é descartada, virando texto
  genérico na UI
- **Prioridade:** P2
- **Área:** mobile
- **Motivo:** Descoberto em 2026-08-21 (PROB-0083), durante a implementação do fluxo de exclusão de
  conta em `mobile/app/(app)/ajustes.tsx`: enviar senha errada para `DELETE /v1/usuarios/me` retorna
  HTTP 422 com `{"code":"BUSINESS_ERROR","message":"Senha incorreta","details":null}` (confirmado
  via `curl` contra backend local), mas o interceptor de `mobile/src/services/api.ts` só extrai
  `error.response.data.details` para montar `userMessage` em 400/422 — sem `details`, cai no
  fallback fixo "Dados inválidos. Verifique os campos.", escondendo a causa real do erro. O
  contorno aplicado nesta sessão (BUG-0069) foi local, só em `ajustes.tsx` — qualquer outra tela do
  app que dependa de mensagem de `BusinessException` em 400/422 sem `details` tem o mesmo problema,
  não investigado em profundidade nesta rodada.
- **Dependências:** Nenhuma técnica. Consultar `docs/PROBLEM_LEDGER.md` (PROB-0083) para a causa
  raiz completa e a evidência de reprodução.
- **Critério de aceite:** `mobile/src/services/api.ts` passa a usar
  `error.response.data.message` como mensagem amigável quando `error.response.data.code ===
  'BUSINESS_ERROR'` (ou, de forma mais geral, sempre que `details` estiver ausente e `message` for
  uma string não vazia), antes de cair no fallback genérico; varredura das telas do mobile que hoje
  tratam 400/422 via `err.userMessage` para confirmar que nenhuma regra de negócio ficava mascarada
  silenciosamente; o contorno local em `ajustes.tsx` (BUG-0069) pode ser removido depois que o
  interceptor cobrir o caso na origem, sem perda de comportamento.
- **Risco se ficar pendente:** Usuário continua recebendo mensagens genéricas em vez da causa real
  de erros de regra de negócio em qualquer tela nova ou existente que não aplique um contorno
  pontual como o de `ajustes.tsx` — pior experiência de erro e mais chance de o usuário repetir a
  mesma ação sem entender por que ela falha.
- **Status:** FECHADO (2026-08-21) — corrigido na origem em `mobile/src/services/api.ts` junto do redesign de inscrição/onboarding; ver BUG-0070 e PROB-0083.

---

## BACKLOG-0097 — Documentar contrato de investimentos em `backend/API.md` (paginação + `Idempotency-Key`)

- **Titulo:** `backend/API.md` não documenta os endpoints de investimentos; a rodada de 2026-08-21
  (BACKLOG-0081/BACKLOG-0082) tornou a lacuna mais grave ao mudar o contrato HTTP desses endpoints
- **Prioridade:** P2
- **Área:** documentação, backend
- **Motivo:** Na correção de BACKLOG-0081 (idempotência) e BACKLOG-0082 (paginação) em 2026-08-21,
  `POST /api/v1/investimentos/{ativoId}/movimentacoes` passou a aceitar o header `Idempotency-Key`
  e `GET /api/v1/investimentos` / `GET /api/v1/investimentos/{ativoId}/movimentacoes` passaram a
  devolver um envelope `Page` em vez de lista simples (breaking change de contrato). O arquivo de
  referência de contrato do backend, `backend/API.md` (não `docs/API.md` — esse caminho não existe
  no repositório), não documentava investimentos nem antes nem depois dessa mudança
  (`grep -i investimento backend/API.md` não retorna nada).
- **Dependências:** Nenhuma técnica. `backend/API.md` está fora dos arquivos que `docs-reporter`
  mantém (não está em `docs/`) — precisa ser tratado por quem edita `backend/`, ou por decisão
  explícita de estender a responsabilidade deste agente a esse arquivo.
- **Critério de aceite:** `backend/API.md` passa a documentar
  `GET /api/v1/investimentos`, `GET /api/v1/investimentos/{ativoId}/movimentacoes` (envelope
  `Page`, `page`/`size`, teto `size<=100`) e
  `POST /api/v1/investimentos/{ativoId}/movimentacoes` (header `Idempotency-Key` opcional,
  comportamento em reenvio da mesma chave).
- **Risco se ficar pendente:** clientes externos (fora de mobile/web, que já foram atualizados)
  integrando contra o contrato antigo (lista simples, sem idempotência) quebram sem aviso
  documentado; consumidores futuros da API não sabem que o header existe.
- **Status:** FECHADO (2026-08-25) — `backend/API.md` documenta paginação (`Page`, `size<=100`),
  `Idempotency-Key` e semântica de reenvio das movimentações de investimento.

---

## BACKLOG-0098 — Executar os quatro flows Maestro após a série de padronização visual

- **Titulo:** Validar em simulador/dispositivo os ajustes de rótulo feitos em
  `mobile/.maestro/financial-critical.yaml` durante a série de 13 PRs de padronização visual
  (2026-08-21/22), e conferir os outros três flows não tocados
- **Prioridade:** P1
- **Área:** mobile, testes
- **Motivo:** A série de 13 PRs que padronizou o visual do app (kit `ui/`, telas de referência,
  trinco `padraoVisual.test.ts` e migração de 10 telas) removeu vários `accessibilityLabel`
  curados (ver BUG-0084) e mudou o texto de erro exibido nas telas migradas (de "Erro ao ..." para
  "Não deu para ..."). `mobile/.maestro/financial-critical.yaml` foi ajustado 5 vezes ao longo da
  série para acompanhar essas mudanças: rótulos curados removidos passaram a ser casados por regex
  parcial, a espera do extrato de conta passou a olhar o texto visível do banner em vez do rótulo do
  selo, e o guard-rail de erro ganhou `.*Não deu para.*` (o guard antigo só reconhecia `.*Erro
  ao.*`, cego nas telas já migradas). Nenhum dos quatro flows (`financial-critical.yaml`,
  `smoke-auth.yaml`, `privacy-consent.yaml`, `recovery-navigation.yaml`) foi executado nesta máquina
  durante a série — só leitura e edição de YAML.
- **Dependências:** Simulador iOS/Android disponível (não disponível na máquina onde a série foi
  implementada); stack local rodando conforme `docs/SYSTEM_OVERVIEW.md`. Este item estende
  BACKLOG-0095 (já aberto para a execução ponta a ponta de `financial-critical`), mas cobre também
  os três flows não tocados nesta série e o conjunto completo de ajustes de rótulo desta rodada
  especificamente.
- **Critério de aceite:** Os quatro flows executados ao menos uma vez contra o app com o padrão
  visual novo (10 telas migradas), sem falha nos passos que dependem de texto/rótulo alterado por
  esta série; qualquer regressão encontrada registrada como novo item em `PROBLEM_LEDGER.md` ou
  `BUGFIX_LOG.md`.
- **Risco se ficar pendente:** Regressão de automação silenciosa — um ajuste de rótulo mal calibrado
  (regex parcial errada, texto de guard-rail que não bate com a tela real) só seria descoberto na
  próxima tentativa manual de rodar Maestro, potencialmente muito depois da mudança que o causou.
- **Status:** FECHADO
- **Atualização 2026-08-22:** Os quatro flows executados em simulador iPhone 17 Pro (iOS 26.5)
  contra a stack local (Postgres efêmero em container, backend Spring porta 8081, banco
  `gf_verify`, app iOS Debug com `APP_ENV=local-e2e`). `financial-critical.yaml` ficou verde
  ponta a ponta (0 falhas, 6 screenshots, todos os guard-rails de erro passaram), mas só depois de
  5 correções no próprio flow (regex/rótulos que não batiam com a tela real; dependência de data
  na criação do cartão — ver nota abaixo) e 3 correções de bug de app (BUG-0092, BUG-0093,
  BUG-0094). `smoke-auth.yaml` (17s), `privacy-consent.yaml` (34s) e `recovery-navigation.yaml`
  (9s) passaram sem alteração adicional. Critério de aceite cumprido: os quatro flows rodaram
  contra o app com o padrão visual novo e toda regressão encontrada foi registrada (BUG-0092 a
  BUG-0095 em `docs/BUGFIX_LOG.md`). Correções de manutenção do próprio flow (não são bug de app):
  cartão de teste passou a usar `diaFechamento = 31` (clampado ao último dia do mês) em vez de `5`
  — com fechamento 5 o flow só passava em 5 dos ~31 dias do mês, porque a compra do dia caía na
  competência seguinte (`FaturaDatas.competencia`) fora dessa janela; três asserções de regex
  corrigidas para casar o nó de texto completo (Maestro exige igualdade total, não apenas
  substring) — pago/restante, percentual da meta (arredondado para inteiro, "5%" nunca "5,0%") e
  saldo do cofre; navegação para Relatórios trocada de `tapOn: "Voltar"` + `tapOn: "Relatórios"`
  (não funcionava — a pilha do `more` mantinha a fatura embaixo de Contas) para toque por
  coordenada na aba Análises. Detalhe completo em
  `docs/REVIEW_REPORTS/2026-08-22_mobile_verification_maestro-runtime-padrao-visual.md`. Pendência
  não coberta nesta rodada: Android (sem `adb` disponível na máquina), VoiceOver/TalkBack
  realmente ligado (verificação foi por dump de árvore de acessibilidade do Maestro — mesma fonte
  que o leitor de tela consome, mas não o leitor em si) e Reduce Motion (ver atualização em
  BACKLOG-0078).

---

## BACKLOG-0099 — Três `pageSheet` desenhados à mão fora do alcance do trinco visual

- **Titulo:** `ComposicaoMetricaModal`, `EditarTransacaoModal` e `NovaTransacaoModal` continuam com
  barra de folha modal desenhada manualmente, não migrados para `ui/FolhaModal`
- **Prioridade:** P2
- **Área:** mobile
- **Motivo:** A série de 13 PRs de padronização visual (2026-08-21/22) criou `ui/FolhaModal` e
  migrou toda folha `pageSheet` de dentro de `mobile/app/**` para o componente novo. Três modais
  vivem em `mobile/src/components/` (não em `app/**`) e continuam com a barra saída/título/ação
  desenhada à mão: `mobile/src/components/ComposicaoMetricaModal.tsx`,
  `mobile/src/components/EditarTransacaoModal.tsx`, `mobile/src/components/NovaTransacaoModal.tsx`.
  O trinco `mobile/src/__tests__/padraoVisual.test.ts` só varre `app/**`, então esses três arquivos
  não são cobertos e uma regressão neles não quebra o build.
- **Dependências:** Nenhuma técnica. `NovaTransacaoModal.tsx` e `EditarTransacaoModal.tsx` são
  formulários grandes e usados em múltiplos pontos de entrada (FAB da tab bar, home, faturas,
  carteiras) — migração exige atenção extra a todos os call sites.
- **Critério de aceite:** Os três modais passam a usar `ui/FolhaModal`; o trinco
  `padraoVisual.test.ts` estende a varredura para `mobile/src/components/**` (ou uma lista
  equivalente) de forma que esses arquivos deixem de ser um ponto cego.
- **Risco se ficar pendente:** Os três modais são pontos de entrada de alto tráfego (lançamento
  rápido, edição de transação, composição de métrica) e podem divergir do padrão visual sem que
  nenhum teste automatizado detecte.
- **Status:** ABERTO
- **Atualização 2026-08-22:** `NovaTransacaoModal` foi exercitado em runtime durante a rodada de
  verificação com os quatro flows Maestro (simulador iPhone 17 Pro, iOS 26.5) e renderiza
  corretamente, sem defeito visual observado — mas o modal continua fora do alcance do trinco
  `padraoVisual.test.ts`, que só varre `app/**`. Não muda o critério de aceite nem a prioridade.

---

## BACKLOG-0100 — Divergência de nome entre a aba "Análises" e o título "Relatórios"

- **Titulo:** A aba da tab bar chama-se "Análises", mas o título da própria tela
  (`mobile/app/(app)/analises.tsx`) e a entrada do hub em Ajustes usam "Relatórios" para o mesmo
  destino
- **Prioridade:** P3
- **Área:** mobile, produto
- **Motivo:** Descoberto durante a migração visual de `analises.tsx` (commit `d44fc438`, 2026-08-21)
  e não resolvido nesta rodada por não ser uma decisão técnica — depende de escolha do dono do
  produto sobre qual nome é o canônico para a funcionalidade.
- **Dependências:** Decisão do dono do produto sobre o nome definitivo ("Análises" ou "Relatórios").
- **Critério de aceite:** Um único nome usado consistentemente na aba da tab bar
  (`mobile/app/(app)/_layout.tsx`), no título da tela (`ui/CabecalhoDeTela` de `analises.tsx`) e em
  qualquer ponto de entrada do hub de Ajustes que referencie a tela.
- **Risco se ficar pendente:** Inconsistência de nomenclatura visível ao usuário final (aba diz uma
  coisa, tela abre dizendo outra); risco baixo de confusão, mas cresce se a tela ganhar mais pontos
  de entrada com nomes próprios.
- **Status:** ABERTO
- **Atualização 2026-08-22:** Divergência reconfirmada visualmente em runtime (simulador iPhone 17
  Pro) durante a rodada de verificação Maestro — o hub de Ajustes mostra o tile "Relatórios" e a
  aba da tab bar diz "Análises" para o mesmo destino. Continua dependendo de decisão de produto;
  nenhuma alteração de escopo feita.

---

## BACKLOG-0101 — Aritmética monetária em `float` repetida sem util central

- **Titulo:** `analises.tsx`, `more/investimentos.tsx` e `more/orcamentos.tsx` fazem cálculo
  monetário direto em `number` (float) JavaScript, cada tela com sua própria conta, sem um util
  central de arredondamento/precisão
- **Prioridade:** P3
- **Área:** mobile
- **Motivo:** Observado durante a série de 13 PRs de padronização visual (2026-08-21/22) como
  dívida técnica preexistente, não como bug — nenhum erro de arredondamento visível foi encontrado
  ou reportado nas três telas durante a migração, mas a ausência de um util central significa que
  qualquer correção futura de precisão precisa ser replicada em três lugares independentes.
- **Dependências:** Nenhuma técnica. Levantamento de todos os pontos de cálculo monetário no mobile
  (não só os três encontrados nesta rodada) antes de definir a forma do util central.
- **Critério de aceite:** Util central de aritmética monetária (mesmo padrão de arredondamento do
  backend, que usa `BigDecimal` com `HALF_UP` — ver item 12 de "Principais decisões técnicas" em
  `docs/SYSTEM_OVERVIEW.md`) criado em `mobile/src/utils/` e adotado pelas três telas identificadas;
  varredura confirma que nenhuma outra tela do mobile faz o mesmo cálculo direto em `float`.
- **Risco se ficar pendente:** Nenhum erro observado até o momento, mas o padrão de cálculo
  duplicado e sem teste de precisão dedicado é terreno fértil para divergência de centavos entre
  telas diferentes mostrando o mesmo valor agregado.
- **Status:** ABERTO

---

## BACKLOG-0102 — Confirmar layout do card de contas fixas em tela estreita (iPhone SE / ~320dp)

- **Titulo:** Os três botões de ação do card em `mobile/app/(app)/more/contas-fixas.tsx`
  (Editar/Pular/Pagar-Receber) só foram vistos numa tela de 402dp (iPhone 17 Pro); comportamento em
  aparelho estreito de verdade não foi testado
- **Prioridade:** P3
- **Área:** mobile
- **Motivo:** Durante a rodada de verificação Maestro pós-padronização visual (2026-08-22,
  simulador iPhone 17 Pro, iOS 26.5), a linha de ação do card de conta fixa (migrado nesta série
  em `0e65a78c`, com `flexWrap` para os três botões) foi confirmada visualmente apenas na
  geometria de 402dp, onde se comporta bem. Nenhum simulador ou dispositivo estreito (ex.: iPhone
  SE, ~320dp) estava disponível nesta rodada para confirmar se os três botões continuam legíveis e
  tocáveis sem quebra de linha inesperada ou corte de texto.
- **Dependências:** Simulador ou device com largura de tela ~320dp disponível (iPhone SE 2ª/3ª
  geração ou equivalente Android estreito).
- **Critério de aceite:** Card de conta fixa com os três botões de ação verificado visualmente (ou
  por screenshot) numa tela de ~320dp de largura, nos temas claro e escuro, sem corte de texto,
  sobreposição ou alvo de toque abaixo de 44pt.
- **Risco se ficar pendente:** Regressão visual silenciosa em aparelhos mais antigos/estreitos —
  usuários com esses aparelhos podem ver botões cortados ou empilhados de forma inesperada sem que
  isso apareça em nenhum teste automatizado ou verificação manual feita até agora.
- **Status:** ABERTO

---

## BACKLOG-0103 — `csrfToken` gravado no mobile e nunca lido (código morto)

- **Titulo:** `mobile/src/services/authService.ts` e `mobile/src/services/api.ts` continuam
  persistindo `csrfToken` recebido do backend, mas nenhum código do mobile volta a ler esse valor
- **Prioridade:** P3
- **Área:** mobile
- **Motivo:** Achado durante a correção da sessão travada por digital (PROB-0085, 2026-08-22). O
  contrato de refresh do mobile é body-only desde 2026-07-11 (BUG-0051/PROB-0056) — cookie/CSRF
  double-submit é exclusivo do contrato web (ver item 5 de "Fluxo de autenticacao" em
  `docs/SYSTEM_OVERVIEW.md`). O `csrfToken` que o backend continua devolvendo no corpo da resposta
  de login/refresh é salvo no `SecureStore` do mobile mas não tem nenhum consumidor — não é enviado
  em nenhum header nem usado em nenhuma verificação local.
- **Dependências:** Nenhuma técnica. Confirmar por varredura (`rg csrfToken mobile/`) que de fato
  não há nenhum uso antes de remover.
- **Critério de aceite:** `csrfToken` deixa de ser persistido no mobile (ou, alternativamente,
  passa a ser efetivamente usado se alguma decisão de produto exigir double-submit também no
  mobile no futuro); nenhuma mudança de comportamento observável para o usuário.
- **Risco se ficar pendente:** Nenhum risco funcional — é ruído de código morto que pode confundir
  quem ler o fluxo de autenticação do mobile pensando que o CSRF token é usado no cliente nativo.
- **Status:** ABERTO

---

## BACKLOG-0104 — Biometria do mobile não protege o token em repouso no `SecureStore`

- **Titulo:** `SecureStore.setItemAsync` em `mobile/src/store/auth.ts` não usa a opção
  `requireAuthentication` — o `AppLockGate` bloqueia a tela, mas o token gravado em disco não exige
  biometria/PIN do sistema para ser lido
- **Prioridade:** P2
- **Área:** mobile, seguranca
- **Motivo:** Achado durante a correção da sessão travada por digital (PROB-0085, 2026-08-22),
  registrado como risco residual da correção (não como parte dela). Hoje a proteção por biometria é
  inteiramente de UI (`AppLockGate` cobre a tela) — o token em si, uma vez no `SecureStore`, pode
  ser lido por qualquer código do próprio app sem novo desafio biométrico do SO.
- **Dependências:** Decisão do dono do produto — endurecer com `requireAuthentication` tem risco de
  perda de sessão em troca de aparelho (restauração de backup/novo device pode não conseguir
  decifrar o valor protegido pela chave biométrica antiga), o que pode conflitar com a decisão já
  cravada nesta mesma sessão de que "desbloqueio por digital renova o token contra o servidor, com
  tolerância offline" (ver PROB-0085) — precisa de análise conjunta antes de implementar.
  Levantamento de como o Expo SecureStore/Keychain/Keystore se comporta com `requireAuthentication`
  em troca de aparelho, restauração de backup e biometria recadastrada.
- **Critério de aceite:** Decisão de produto registrada (endurecer ou aceitar o risco
  conscientemente) e, se endurecer, implementação com teste cobrindo o caminho de falha
  (biometria recadastrada/aparelho trocado) sem deixar o usuário irrecuperavelmente fora da conta.
- **Risco se ficar pendente:** Um app comprometido no mesmo aparelho (ou acesso físico com o
  aparelho desbloqueado por outro meio) pode ler o token diretamente do armazenamento sem passar
  pelo desafio biométrico da tela de bloqueio do app.
- **Status:** ABERTO

---

## BACKLOG-0105 — Nota operacional: timezone do Postgres do container (UTC) vs. app (`America/Sao_Paulo`) exige margem em verificação manual por SQL

- **Titulo:** Manipular `data_expiracao` de `refresh_tokens` (ou qualquer coluna de data/hora) via
  SQL manual direto no container, para fins de verificação/teste, precisa de margem de ~3h para não
  enganar o resultado
- **Prioridade:** P3
- **Área:** documentacao, infra
- **Motivo:** Observado durante a verificação em runtime de PROB-0085 (2026-08-22). O Postgres do
  container de stack local está em UTC e o app/backend operam em `America/Sao_Paulo`. A coluna
  `data_expiracao` (e equivalentes) é `LocalDateTime`, sempre escrita e lida pelo processo Java — é
  consistente ponta a ponta em uso normal da aplicação. O ponto de atenção é só para quem for
  manipular essas colunas por SQL manual fora do Java (ex.: forçar um token "quase expirado" para
  testar o scheduler de limpeza): um `UPDATE` ingênuo usando `now()` do Postgres (UTC) contra uma
  coluna que o Java trata como hora local de São Paulo introduz um desvio de fuso (~3h) que pode
  fazer um token parecer expirado quando não está, ou vice-versa.
- **Dependências:** Nenhuma técnica — é uma nota de procedimento para verificações futuras, não um
  defeito de código.
- **Critério de aceite:** Nota incorporada a algum guia de verificação/runbook de stack local (se e
  quando um for criado), lembrando de somar/subtrair a margem de fuso ao manipular datas por SQL
  manual, ou de preferir sempre passar pelo fluxo real da aplicação (login/refresh) em vez de UPDATE
  direto.
- **Risco se ficar pendente:** Baixo — risco é de uma futura verificação manual chegar a conclusão
  errada por causa do desvio de fuso, não de comportamento incorreto em produção (que sempre passa
  pelo Java).
- **Status:** ABERTO

---

## BACKLOG-0106 — Completar pipeline canônico de importação após fundação PR-F4-01

- **Título:** Implementar parsers CSV/OFX seguros, normalização, preview, deduplicação, commit,
  conciliação e reversão sobre `import_batches`/`import_records`
- **Prioridade:** P0
- **Área:** backend, banco, integridade financeira
- **Motivo:** PR-F4-01 de 2026-08-26 entregou contrato streaming, lifecycle persistido, ownership,
  idempotência, observabilidade e V46. Importador CSV antigo continua separado e não oferece
  revisão nem reversão por batch.
- **Dependências:** Implementar em PRs pequenos na ordem: parser/detecção/normalização;
  preview/mapeamento; dedupe; commit transacional; conciliação; reversão.
- **Critério de aceite:** CSV e OFX passam pelo mesmo pipeline, arquivos grandes são processados
  com limites, reenvio não duplica ledger, usuário revisa antes do commit e pode reverter batch com
  trilha auditável.
- **Risco se ficar pendente:** Importação produtiva permanece indisponível; endpoint legado não é
  arquitetura válida para produção.
- **Status:** FECHADO_MOBILE — mapeamento, fatura e cliente mobile concluídos. O cliente web foi
  retirado do escopo por decisão mobile-first. Retenção: bruto apagado ao fim do request; lote não
  confirmado expira em 30 dias; confirmado/revertido mantém resumo, hashes, normalizados e vínculos
  até exclusão do titular. O encerramento PR-F4-18 implementou saldo declarado (`MATCH`, `MISMATCH`,
  `UNAVAILABLE`) e seu reconhecimento explícito antes do commit; faltam somente os gates ambientais
  de PostgreSQL/reconciliação e Maestro para o `PASS` documental.

## BACKLOG-0107 — Reabrir a decisão de parse assíncrono quando houver segunda instância ou object storage

- **Título:** Mover o parse de importação para a fila quando o staging do arquivo deixar de ser disco local
- **Prioridade:** P2
- **Área:** backend, importação, infraestrutura
- **Motivo:** o parse continua síncrono por decisão registrada em `docs/adr/ADR-0016`. Tornar o parse
  assíncrono exige o arquivo sobreviver à requisição; hoje isso significaria disco local, e a API roda
  em uma única instância (`container_name` impede `--scale`). Com duas instâncias, o job poderia ser
  reivindicado por quem não tem o arquivo.
- **Dependências:** segunda instância da API **ou** staging em object storage (ADR-0005 já prevê o
  caminho para anexos).
- **Critério de aceite:** upload responde `202` com lote em `RECEIVED`, job `IMPORT_PARSE` enfileirado
  com `job_key` determinística, arquivo em armazenamento acessível a qualquer instância, faxineiro de
  órfãos e cliente acompanhando por consulta.
- **Risco se ficar pendente:** baixo enquanto houver uma instância; alto no dia em que houver duas sem
  revisar esta decisão.
- **Status:** ABERTO — gatilho explícito, não trabalho pendente.

## BACKLOG-0108 — Flow Maestro da importação no mobile

- **Título:** Cobrir a jornada de importação (enviar, revisar, lançar, desfazer) em E2E de aparelho
- **Prioridade:** P2
- **Área:** mobile, testes
- **Motivo:** a tela de importação entrou com teste de unidade (Testing Library), mas nenhum flow
  Maestro cobre a jornada. Escolher arquivo depende do seletor do sistema, que só dá para exercitar
  em simulador/aparelho.
- **Dependências:** simulador com arquivo de extrato preparado no dispositivo.
- **Critério de aceite:** flow em `mobile/.maestro` que envia um CSV, aprova uma linha em revisão,
  lança e desfaz, verde em iOS e Android.
- **Risco se ficar pendente:** regressão de fluxo longo só aparece em uso real.
- **Status:** EM_VALIDACAO — `mobile/.maestro/importacao-mobile.yaml` cobre seleção, revisão,
  lançamento e reversão usando fixture preparado no aparelho; execução verde em iOS e Android
  permanece pendente.

## BACKLOG-0109 — Thresholds de alerta configuráveis e alertas no web

- **Título:** Tornar os limiares de `InsightsService` configuráveis pelo titular e levar os alertas ao web
- **Prioridade:** P2
- **Área:** backend, frontend web
- **Motivo:** os limiares são constantes no código (`variação > 20%`, `gasto > R$ 500`, top 5
  categorias): quem gasta R$ 300 por mês nunca vê alerta de categoria, e quem gasta R$ 20 mil vê
  ruído. O mobile já consome os alertas (PR-F4-10); `frontend/src/services/insightsService.ts`
  continua sendo código morto.
- **Dependências:** decisão de produto sobre o que é configurável (percentual, valor mínimo, número
  de categorias) e onde a preferência mora — hoje a dispensa é local no dispositivo.
- **Critério de aceite:** limiar por titular persistido e respeitado pelo cálculo; tela web
  mostrando os mesmos alertas do mobile; dispensa coerente entre os clientes se ela virar servidor.
- **Risco se ficar pendente:** alerta calibrado para um perfil de gasto só serve a esse perfil.
- **Status:** ABERTO

## BACKLOG-0110 — Ligar o push em produção: `projectId` do EAS e credenciais de entrega

- **Título:** Configurar `extra.eas.projectId` no app mobile e habilitar `app.notificacoes.push.enabled`
- **Prioridade:** P2
- **Área:** mobile, infraestrutura, backend
- **Motivo:** o registro de aparelho e o envio existem (PR-F4-11), mas `app.json` não tem
  `extra.eas.projectId` — sem ele `getExpoPushTokenAsync` não devolve token em build, e o registro
  desiste em silêncio. O envio também nasce desligado por decisão (`push.enabled=false`), para o
  app nunca falar com serviço externo sem configuração explícita.
- **Dependências:** projeto EAS do dono da conta; credenciais de push do iOS (APNs) e Android (FCM).
- **Critério de aceite:** token registrado em aparelho real; aviso entregue com o app fechado; token
  de aparelho desinstalado desativado sozinho ao receber `DeviceNotRegistered`.
- **Risco se ficar pendente:** o aviso continua só na caixa in-app; quem não abre o app não é
  avisado.
- **Status:** ABERTO
