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
- **Dependencias:** PROB-0013 resolvido
- **Criterio de aceite:** Token armazenado no SecureStore; sessao restaurada no cold start; sem flash de login
- **Risco se ficar pendente:** Experiencia de usuario inaceitavel
- **Status:** ABERTO

---

## BACKLOG-0006 — Configurar URL da API mobile por ambiente

- **Titulo:** Substituir IP hardcoded por configuracao de ambiente no mobile
- **Prioridade:** P0
- **Area:** mobile
- **Motivo:** IP fixo quebra app em qualquer rede que nao a do dev
- **Dependencias:** PROB-0014 resolvido
- **Criterio de aceite:** URL da API configurada via expo-constants ou env var
- **Risco se ficar pendente:** App inutilizavel fora da rede do dev
- **Status:** ABERTO

---

## BACKLOG-0007 — Fortalecer politica de senha

- **Titulo:** Implementar validacao de complexidade de senha
- **Prioridade:** P0
- **Area:** backend, seguranca
- **Motivo:** Minimo de 6 caracteres sem requisitos de complexidade para app financeiro
- **Dependencias:** PROB-0007 resolvido
- **Criterio de aceite:** Min 8 chars, 1 maiuscula, 1 minuscula, 1 digito, 1 especial
- **Risco se ficar pendente:** Contas vulneraveis a ataques de forca bruta
- **Status:** ABERTO

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
- **Status:** ABERTO

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
- **Dependencias:** PROB-0009 resolvido
- **Criterio de aceite:** App falha na inicializacao sem DB_PASSWORD e JWT_SECRET setados
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
- **Status:** ABERTO

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
- **Dependencias:** PROB-0015 resolvido
- **Criterio de aceite:** "Esqueceu a senha" navega para forgot-password; "Ver todas" navega para lista
- **Risco se ficar pendente:** UX quebrada, frustracao do usuario
- **Status:** ABERTO

---

## BACKLOG-0015 — Remover entry points zumbis do mobile

- **Titulo:** Deletar App.tsx (template Expo) e corrigir index.ts
- **Prioridade:** P1
- **Area:** mobile
- **Motivo:** Codigo morto causando confusao
- **Dependencias:** PROB-0025 resolvido
- **Criterio de aceite:** App.tsx removido; index.ts limpo ou re-exportando expo-router
- **Risco se ficar pendente:** Confusao para devs — "qual entry point esta sendo usado?"
- **Status:** ABERTO

---

## BACKLOG-0016 — Corrigir API path inconsistente no mobile

- **Titulo:** Corrigir /dashboard/resumo para /v1/dashboard/resumo no perfil.tsx
- **Prioridade:** P0
- **Area:** mobile
- **Motivo:** Endpoint 404 — dados nao carregam na tela de perfil
- **Dependencias:** PROB-0016 resolvido
- **Criterio de aceite:** Perfil carrega dados do dashboard corretamente
- **Risco se ficar pendente:** Tela de perfil quebrada
- **Status:** ABERTO

---

## BACKLOG-0017 — Tratar erros em mutations mobile

- **Titulo:** Adicionar onError em mutations de carteira e contas-fixas
- **Prioridade:** P1
- **Area:** mobile
- **Motivo:** Falhas silenciosas — usuario nao sabe que operacao falhou
- **Dependencias:** PROB-0017 resolvido
- **Criterio de aceite:** Toda mutation com onError que mostra Alert ou toast
- **Risco se ficar pendente:** Usuario acredita que operacao foi concluida mas nao foi
- **Status:** ABERTO

---

## BACKLOG-0018 — Centralizar parseCurrencyBR no mobile

- **Titulo:** Extrair logica de parse de moeda BR para utils/format.ts
- **Prioridade:** P2
- **Area:** mobile
- **Motivo:** Codigo duplicado em 5 arquivos
- **Dependencias:** PROB-0028 resolvido
- **Criterio de aceite:** Funcao parseCurrencyBR exportada de format.ts; 5 arquivos importam dela
- **Risco se ficar pendente:** Manutencao fragil — bug de parse precisa ser corrigido em 5 lugares
- **Status:** ABERTO

---

## BACKLOG-0019 — Migrar JwtUtil para API nao-deprecated do JJWT

- **Titulo:** Upgrade do jjwt para 0.12.x e uso da nova API de parser
- **Prioridade:** P2
- **Area:** backend
- **Motivo:** API atual deprecated; upgrade necessario para correcoes de seguranca
- **Dependencias:** PROB-0022 resolvido
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
- **Criterio de aceite:** Conta bloqueada por 15min apos 10 falhas; mensagem clara ao usuario
- **Risco se ficar pendente:** Senhas vulneraveis a brute force multi-IP
- **Status:** ABERTO

---

## BACKLOG-0021 — Limpeza periodica do rate limit map

- **Titulo:** Adicionar @Scheduled para limpar entradas expiradas do ConcurrentHashMap
- **Prioridade:** P2
- **Area:** backend
- **Motivo:** Memory leak lento de entradas de IPs que nunca mais fazem request
- **Dependencias:** PROB-0024 resolvido
- **Criterio de aceite:** Scheduled task limpa entradas expiradas a cada 60s
- **Risco se ficar pendente:** Memory leak em uptime prolongado
- **Status:** ABERTO

---

## BACKLOG-0022 — Remover dead code e imports nao usados

- **Titulo:** Limpeza de arquivos e imports nao utilizados no frontend e mobile
- **Prioridade:** P3
- **Area:** frontend, mobile
- **Motivo:** Codigo morto polui repositorio e confunde devs
- **Dependencias:** Nenhuma
- **Criterio de aceite:** GraficoComparacaoMensal removido ou integrado; mobile App.tsx removido; imports unused removidos; dependencias nao usadas removidas do package.json
- **Risco se ficar pendente:** Build levemente maior; confusao para novos devs
- **Status:** ABERTO

---

## BACKLOG-0023 — Tipar services do frontend

- **Titulo:** Substituir `any` por tipos explicitos nos metodos de service do frontend
- **Prioridade:** P2
- **Area:** frontend
- **Motivo:** 49 ocorrencias de `any` removem type safety
- **Dependencias:** PROB-0027 resolvido
- **Criterio de aceite:** Zero any nos arquivos de service; parametros tipados com interfaces do types/index.ts
- **Risco se ficar pendente:** Erros de tipo so descobertos em runtime
- **Status:** ABERTO

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
- **Status:** ABERTO

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
- **Status:** ABERTO

---

## BACKLOG-0029 — Health check de banco no Actuator

- **Titulo:** Adicionar health indicator para conectividade PostgreSQL
- **Prioridade:** P3
- **Area:** backend, infra
- **Motivo:** /actuator/health nao verifica banco — falsos positivos
- **Dependencias:** spring-boot-starter-actuator ja incluso
- **Criterio de aceite:** Health endpoint retorna status do banco; readiness probe funcional
- **Risco se ficar pendente:** App considerado healthy mesmo com banco fora do ar
- **Status:** ABERTO

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
- **Dependencias:** PROB-0029 resolvido
- **Criterio de aceite:** Rota `*` renderiza NotFound com link para Dashboard ou Login
- **Risco se ficar pendente:** UX ruim para URLs erradas
- **Status:** ABERTO

---

## BACKLOG-0032 — Remover console.log do frontend

- **Titulo:** Limpar console.log e console.error residuais
- **Prioridade:** P3
- **Area:** frontend
- **Motivo:** Logs de debug em producao
- **Dependencias:** PROB-0030 resolvido
- **Criterio de aceite:** Zero console.log; console.error apenas em ErrorBoundary
- **Risco se ficar pendente:** Console poluido; dados vazados em logs
- **Status:** ABERTO

---

## BACKLOG-0033 — Adicionar confirmPassword no registro

- **Titulo:** Validar confirmacao de senha no backend e frontend
- **Prioridade:** P2
- **Area:** backend, frontend
- **Motivo:** Usuario pode digitar senha errada sem perceber — conta inacessivel
- **Dependencias:** Nenhuma
- **Criterio de aceite:** Campo confirmPassword no DTO RegisterRequest; validacao de igualdade no backend e frontend
- **Risco se ficar pendente:** Contas perdidas por typo na senha
- **Status:** ABERTO

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

> Mantido pelo `docs-reporter`. Ultima atualizacao: 2026-07-06 (auditoria completa do sistema).
