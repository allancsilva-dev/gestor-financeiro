# 📝 Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

---

## [Fase 4 — PR-F4-09] - 2026-08-27

### Verificação em runtime e o defeito que ela achou
- Pipeline exercitado ponta a ponta contra PostgreSQL real (stack local, backend na 8081):
  CSV pt-BR com coluna `tipo` e OFX SGML com `TRNTYPE` descritivo, prévia, escolha de conta,
  lançamento pela fila, reenvio, reversão e reconciliação.
- **BUG-0100 corrigido:** reenviar o mesmo arquivo depois do lançamento derrubava a importação com
  violação de `ck_import_batches_counts`. A deduplicação aplicava os contadores um a um e consultava
  entre eles; o auto-flush gravava `duplicate` novo com `valid` antigo. Em H2 o CHECK não existe, e
  por isso três PRs passaram por cima do defeito. `ImportReenvioIT` cobre o caminho contra
  PostgreSQL real.
- Números da verificação: saldo 2.500,00 → 5.333,60 após lançar 3 linhas; reenvio marcou 3
  duplicados e 0 válidos; reversão devolveu 2.500,00 exatos; reconciliação global em zero
  divergências antes e depois; jobs `IMPORT_COMMIT` e `IMPORT_REVERSAL` concluídos em uma tentativa.
- Validação: 358 testes unitários e 45 de integração verdes.

## [Fase 4 — PR-F4-08] - 2026-08-27

### Importação no app (mobile)
- Nova tela `Importar extrato` (`app/(app)/more/importacao.tsx`): escolher CSV ou OFX, ver o que foi
  lido, escolher a conta de destino, decidir linha a linha o que está em revisão ou repetido,
  lançar, acompanhar o lançamento e desfazer.
- O botão de Ajustes deixou de chamar o importador legado — que hoje responde `410` em produção — e
  passa a abrir o fluxo canônico. `importService.ts` (legado) foi removido junto com o tipo morto.
- A tela acompanha o lançamento sozinha enquanto o lote está `COMMITTING` e para de consultar
  quando termina; sair da tela não interrompe nada, porque o trabalho está na fila.
- Motivo técnico do backend (`CURRENCY_MISSING`, `DIRECTION_CONFLICT`, …) vira frase de gente na
  prévia; linha repetida aparece marcada, com ação explícita de trazer mesmo assim.
- `GET /api/v1/importacoes` (histórico paginado por titular) fecha o que faltava para desfazer uma
  importação antiga.
- Validação: 385 testes no mobile (5 novos da tela), trinco visual verde, lint e typecheck limpos;
  358 testes unitários no backend.

## [Fase 4 — PR-F4-07] - 2026-08-27

### Reversão auditável e a invariante que faltava
- `POST /api/v1/importacoes/{id}/reverter` estorna um lote lançado, pela fila. Reversão é
  **compensação** (ADR-0009): cada transação é cancelada pelo caminho de domínio, com movimento
  `ESTORNO`, devolução do gasto da categoria e transação preservada como inativa. O vínculo
  `import_records.transacao_id` é mantido — é o que torna a reversão auditável.
- `TransacaoService.deletar` ganhou sobrecarga com chave de idempotência no estorno: o caminho de
  cancelamento gravava no ledger sem chave, então reexecutar uma reversão estornaria duas vezes.
  Cancelamento manual segue idêntico (chave nula).
- Nova invariante de reconciliação **`CATEGORIA_VALOR_GASTO`**: `categorias.valor_gasto` é verdade
  materializada e nenhuma das quatro invariantes existentes a cobria. Um caminho novo que
  esquecesse de estorná-la deixaria a tela divergente do extrato sem nada acusar.
- Teste fecha o ciclo: lançar e reverter deixa saldo, gasto de categoria e ledger no estado
  anterior, com reconciliação global em zero divergência antes e depois.
- Validação: 357 testes unitários e 44 de integração verdes; cobertura atendida.

## [Fase 4 — PR-F4-06] - 2026-08-26

### Lançamento do lote no ledger
- `POST /api/v1/importacoes/{id}/preparar`, `.../registros/{id}/aprovar` e `.../commit`. O commit
  responde `202` e o trabalho vai para a fila durável; a requisição HTTP deixa de ser onde milhares
  de lançamentos são gravados.
- Cada linha vira transação por `TransacaoService.criar(..., ledgerIdempotencyKey)` — o mesmo caminho
  do lançamento manual, com saldo, categoria e ledger. Nenhuma regra financeira foi reimplementada
  no pacote de importação.
- Idempotência real: a chave `IMPORT:{batchId}:{registroId}` chega ao ledger e o índice único de
  idempotência do movimento é o backstop. Reexecutar o commit (retentativa de job, lease vencido)
  não duplica saldo — provado em teste contra PostgreSQL, com saldo e soma do ledger conferidos a
  centavo.
- Uma transação por linha (`REQUIRES_NEW`), nunca uma cobrindo o lote: o pool tem 10 conexões e o
  lote vai a dezenas de milhares de linhas. Linha que falha vira `INVALID` com motivo e o lote
  continua; commit parcial é resultado válido e visível.
- V49 (expand): destino do lote (`carteira_id`), categoria do registro, motivos `COMMIT_FAILED` e
  `CURRENCY_UNSUPPORTED`, e guard de banco recusando lote em commit sem conta de destino.
- Conta de cartão é recusada na preparação: compra de cartão nasce na fatura (ADR-0009).
- Validação: 354 testes unitários e 44 de integração verdes; cobertura atendida.

## [Fase 4 — PR-F4-05] - 2026-08-26

### Deduplicação de importação
- Reenviar o mesmo arquivo deixa de ser risco de ledger duplicado: ao fim do parse, o lote marca
  `DUPLICATE` comparando com registros **já lançados** (`COMMITTED`) do próprio titular.
- Duas identidades com pesos diferentes: **forte** (mesma instituição + mesmo `external_id`/FITID —
  vale mesmo com valor diferente) e **heurística** (impressão digital de data, valor, moeda, direção
  e descrição). A heurística **nunca** vira constraint: dois lançamentos idênticos no mesmo dia podem
  ser dois fatos reais, então o registro é apenas marcado e quem decide é o usuário na prévia.
- Registro em revisão não é marcado pela heurística, id externo de outra instituição não é o mesmo
  fato, lote de outro titular não contamina, e registro ainda não lançado não bloqueia novo envio —
  cada uma dessas fronteiras tem teste.
- Migration V48 cria índices parciais sobre os registros já lançados, que é o único recorte que a
  deduplicação consulta.
- Ordem de execução ajustada em relação ao plano: deduplicação, commit e reversão vêm antes do
  mapeamento configurável de colunas — a brecha aberta é o ledger, não o cabeçalho exótico.
- Validação: 346 testes unitários e 40 de integração verdes.

## [Fase 4 — PR-F4-04] - 2026-08-26

### Prévia da importação
- `GET /api/v1/importacoes/{id}/registros` devolve as linhas já normalizadas, com filtro por status
  e paginação **por cursor de `sourceLine`** — `OFFSET` faria o banco varrer o lote inteiro a cada
  página, e um lote vai a dezenas de milhares de linhas.
- `ImportRecordRepository`, que era uma interface vazia, ganha a consulta paginada e a contagem por
  status; o lote é resolvido pelo titular antes da leitura, então lote alheio responde `404` e nunca
  lista vazia.
- Status inválido no filtro responde `422` em vez de lista silenciosamente vazia.
- Validação: 340 testes unitários e gate de cobertura verdes.

## [Fase 4 — PR-F4-03] - 2026-08-26

### Worker da fila durável
- `BackgroundJobWorker` passa a consumir `background_jobs`, que existia desde o PR-F4-01 sem nenhum
  produtor ou consumidor em produção. Executor próprio (o `TaskScheduler` do Spring tem pool 1 e já
  hospeda recorrências, reconciliação e limpeza de rate limit), concorrência default 2 para não
  esgotar o pool de 10 conexões disputado por 50 threads HTTP.
- `claim` acontece fora da transação de trabalho, lease é renovado em segundo plano enquanto o
  handler executa, falha vira retentativa com backoff e depois dead letter, e tipo sem handler vai
  direto para dead letter em vez de girar na fila.
- Contrato do `JobHandler`: idempotente, sem `SecurityContext` (é `ThreadLocal` e não cruza thread),
  com código de erro estável e sem PII. Métrica `app.jobs.processed{type,result}`.
- `BackgroundJobService.cancel` fecha a lacuna do estado `CANCELLED`, que existia no CHECK da V45 sem
  método correspondente.
- `docs/adr/ADR-0016` registra a decisão de manter o parse de importação síncrono enquanto o staging
  for disco local e a API tiver instância única; BACKLOG-0107 guarda o gatilho para reabrir.
- Validação: 338 testes unitários e 40 de integração verdes.

## [Fase 4 — PR-F4-02] - 2026-08-26

### Endpoint de importação com admissão controlada
- `POST /api/v1/importacoes` (multipart, `Idempotency-Key`) e `GET /api/v1/importacoes/{id}`. O
  arquivo vira lote auditável; nenhuma escrita no ledger acontece neste passo.
- `TempFileImportSource`: o upload é copiado para arquivo temporário próprio, com permissão de dono,
  stream reabrível (o orquestrador lê o conteúdo mais de uma vez) e remoção garantida em qualquer
  saída, inclusive erro — coberto por teste.
- Admissão em três travas: rate limit por titular, lotes em voo por titular (com janela, para lote
  órfão não bloquear o usuário para sempre) e teto de parses simultâneos por instância — esta última
  é o que impede uploads concorrentes de estourarem o heap e matarem o processo.
- Replay de `Idempotency-Key` com o mesmo arquivo devolve o mesmo lote (antes o formato detectado
  fazia o reenvio legítimo virar `409`); replay de chave cujo lote falhou repete o mesmo erro.
- `backend/API.md` passa a documentar o pipeline e a tabela de erros do envio.
- Validação: 338 testes unitários e gate de cobertura verdes.

## [Fase 4 — PR-F4-01d] - 2026-08-26

### Envelope de runtime alinhado para receber upload
- `docker-compose.production.yml` recebe o mesmo envelope do compose da VPS (`mem_limit`, `cpus`,
  `JAVA_TOOL_OPTIONS` com `MaxRAMPercentage`/`ExitOnOutOfMemoryError`, `DB_POOL_*`,
  `SERVER_TOMCAT_THREADS_MAX`), que antes divergiam.
- Temporário de multipart passa a viver em tmpfs dedicado com teto de 64 MB nos dois composes:
  upload não enche mais a camada de escrita do container.
- nginx aceita 12 MB de corpo (acima do teto de 11 MB do Spring), com timeouts próprios do path de
  importação — o 413 passa a vir da aplicação, com corpo de erro, em vez do proxy.
- Tomcat ganha `max-swallow-size` (413 no meio do upload drena o corpo em vez de abortar a conexão),
  `keep-alive-timeout` e teto de form post; Hibernate ganha `jdbc.batch_size` e `order_inserts`.
- Erros de upload deixam de virar 500: `MaxUploadSizeExceededException` → 413 `UPLOAD_TOO_LARGE`,
  `MultipartException` → 400 `INVALID_MULTIPART`, parte ausente → 400 `MISSING_REQUEST_PART`,
  `ImportParsingException` → 422 `IMPORT_PARSING_FAILED` com `failureCode` em `details`.
- `requestId` passa a aparecer no log dos perfis em uso (estava só no MDC); `Retry-After` entra em
  `exposedHeaders` do CORS; exposição do actuator vira parametrizável por `ACTUATOR_EXPOSURE`,
  mantendo o default mínimo enquanto BACKLOG-0074 estiver aberto.
- Validação: 330 testes unitários e gate de cobertura verdes.

## [Fase 4 — PR-F4-01c] - 2026-08-26

### Testes de integração passam a rodar no CI
- O perfil `integration-test` nunca era ativado e o Surefire não casa `*IT.java`: os testes de
  concorrência, lease de fila, idempotência e migration existiam sem nunca executar em CI. Novo job
  `backend-it` roda `mvn verify -Pintegration-test` com PostgreSQL real via Testcontainers.
- Perfil de integração passou a executar só `*IT.java` (Surefire desligado, cobertura desligada),
  para não duplicar o build padrão.
- `BackgroundJobIT` ganhou disputa real de `claim` com 4 threads (o `FOR UPDATE SKIP LOCKED` só é
  exercitado com concorrência de verdade) e retomada de job com lease vencido. Os testes deixaram
  de comparar o relógio do host com o do banco, que era fonte de intermitência.
- Validação: 35 testes de integração verdes contra PostgreSQL 16/17.

## [Fase 4 — PR-F4-01b] - 2026-08-26

### Correção da fundação de importação e envelope de runtime
- Transição para `PARSED` passa a usar `ImportBatchService.transition`, recuperando validação do
  grafo de estados e a métrica `app.import.batch.transitions`; o SHA-256 do arquivo deixa de ser
  calculado duas vezes e só é reconferido quando o chamador declara o hash.
- CSV reconhece a coluna `tipo` (cabeçalho pt-BR mais comum) e `trntype`; `TRNTYPE` de OFX volta a
  ser descritivo — a direção é decidida pelo sinal de `TRNAMT`, evitando extrato inteiro em
  `PENDING_REVIEW`. `DIRECTION_MISSING` passa a ser emitido; campo `metadata` morto do registro
  canônico foi removido do contrato.
- `CanonicalImportOrchestrator` ganha cobertura (contagem por status, flush em lote, hash
  divergente, teto de arquivo, teto de registros e falha de detecção deixando o lote em `FAILED`);
  somados testes de OFX SGML e de charset windows-1252.
- Exportação CSV neutraliza fórmula de planilha (CSV injection) em todos os campos de texto.
- Runtime explícito: pool Hikari, threads Tomcat, limites de multipart, `Idempotency-Key` validada
  com 400 dedicado, CORS com whitelist de headers e `mem_limit`/`JAVA_TOOL_OPTIONS` no compose da
  VPS. Restore passa a exigir marcador do alvo e banco vazio.
- Validação: 326 testes unitários verdes e gate de cobertura JaCoCo atendido.

## [Fase 4 — PR-F4-01] - 2026-08-26

### Fundação canônica de importação
- Adicionados contrato streaming independente de formato, batches e registros canônicos com
  lifecycle fechado, ownership, optimistic lock, idempotência por titular e métricas de baixa
  cardinalidade. Nenhum parser novo ou write no ledger foi habilitado.
- Migration V46 cria staging auditável com constraints e índices; arquivo bruto não é persistido.
  Exclusão LGPD passou a remover batches e registros importados.
- Validação: 330 testes unitários e 33 testes de integração PostgreSQL, sem falhas.
- Próximo PR: parser CSV/OFX seguro e normalização streaming; endpoint CSV legado continua fora do
  pipeline novo.

## [Estado consolidado] - 2026-08-25

### Experiência mobile e confiabilidade
- Padrão visual mobile unificado em Home, Carteira, Metas e demais telas, com tema
  sistema/claro/escuro, componentes canônicos e trinco de arquitetura visual.
- Cadastro/onboarding, Ajustes, privacidade, exclusão LGPD e recuperação de sessão foram
  redesenhados e verificados contra o backend real.
- Falhas de refresh expirado/revogado retornam `SESSION_EXPIRED` (401); cliente remove sessão
  morta e volta ao login sem ficar preso em loading/erro.
- Quatro flows Maestro passaram em simulador iOS em 22/08: `financial-critical`, `smoke-auth`,
  `privacy-consent` e `recovery-navigation`.
- Correções runtime cobrem invalidação da Home após transação, pagamento de fatura no primeiro
  toque, ações acessíveis em metas, MIME CSV Android, insets e ícone nulo de categoria.

### Fase 3 — experiência simples
- Compromissos próximos, sugestão determinística de categoria, contrato de onboarding mínimo e
  drill-down foram entregues no backend.
- Mobile ganhou lançamento rápido, visão das métricas oficiais, Home reduzida, setup progressivo,
  modalidade/histórico de metas e navegação até a origem dos valores.
- Web passou a consumir métricas e drill-down mínimos; `/dashboard/resumo` ficou deprecado.
- Linguagem financeira foi alinhada ao glossário. Fase 3 encerrada no PR-F3-13.

### Contratos e segurança posteriores
- Investimentos agora usam paginação e `Idempotency-Key`; migration V44 protege duplicação por
  titular.
- Defaults do perfil base foram endurecidos; entidades JPA bidirecionais e `RefreshToken.toString()`
  deixaram de expor dados/recursão.
- Spring Boot atualizado para 3.5.16 e dependências runtime sem vulnerabilidade high/critical
  conhecida na última auditoria registrada.

### Gates ainda abertos
- Deploy público continua bloqueado por backup/restore off-host real (`PROB-0081`), promoção do
  PR-F2-20 em clone restaurado e gates externos de release.
- VoiceOver/TalkBack em hardware físico, pendências web/mobile do backlog e operação externa
  continuam fora deste fechamento.

## [Fase 2 — PR-F2-20] - 2026-07-16

### Reconciliação global automatizada
- Novo `GET /api/v1/reconciliacao/global`, autenticado e restrito ao titular, verifica saldo do
  ledger, passivo de faturas terminais, cofres de metas e transações incompletas.
- Cada relatório usa transação read-only `REPEATABLE_READ`; a varredura diária pagina usuários
  por keyset, isola falhas e não corrige divergências automaticamente.
- Health inicia `UNKNOWN`, fica `UP` sem divergências e `DEGRADED` com divergência/erro. O estado
  degradado continua HTTP 200; `DOWN` e `OUT_OF_SERVICE` permanecem HTTP 503.
- Gauges Micrometer registram última execução e quatro invariantes com tags fixas, sem IDs.
- O maintenance job `global-reconciliation` rejeita `--apply` e caminhos dentro do repositório,
  gera JSON restrito fora da árvore de código e checksum SHA-256.
- Não há migration nem histórico persistido. Rollback é apenas reimplantar o artefato anterior.
- `PROB-0081` permanece aberto. Promoção exige V41, backup off-host, restore drill, postflight do
  PR-F2-19 e artefato global com zero divergências.

## [Fase 2 — PR-F2-19] - 2026-07-16

### Contract financeiro V41
- A V41 e os contratos canônicos do PR-F2-19 estão implementados e validados localmente; não
  houve deploy. O gate operacional `PROB-0081` continua aberto.
- `contas` representa apenas configuração de cartão e o passivo vive exclusivamente no ledger
  da conta financeira `PASSIVO/CARTAO`; campos legados foram removidos.

## [Fase 2 — PR-F2-18A/18B] - 2026-07-16

### Contratos finais e clientes canônicos
- `/api/v1/contas-financeiras` usa contrato canônico sem `tipo`, aceita somente contas ATIVO
  manuais de caixa e mantém CARTAO/COFRE/CUSTODIA somente leitura.
- `/api/v1/cartoes` cria e consulta cartões pareados à conta financeira PASSIVO; dívida e limite
  disponível derivam do saldo do ledger, inclusive crédito representado por saldo negativo.
- Faturas aceitam rotas por `cartaoId`; transações aceitam `cartaoId`; onboarding aceita o objeto
  `cartao`, preservando aliases antigos somente até o contract.
- Web e mobile deixaram de chamar `/contas` e `/carteiras`. Dashboard mobile prioriza
  “Disponível para gastar” e expõe as nove métricas com drill-down e estados completos.
- Contas, cartões, faturas, transações, metas, recorrências e investimentos usam serviços e
  seletores canônicos; operações de investimento exigem caixa real ou snapshot `EXTERNO`.
- O stash `WIP PR-F2-20 reconciliacao global antes PR-F2-16A` permanece intacto.
- `PROB-0081` continua reaberto: o ambiente não possui rclone, remote off-host, chave pública nem
  variáveis operacionais. Esta entrada precede a implementação local posterior de PR-F2-19/20.

## [Fase 2 — PR-F2-16A] - 2026-07-16

### Contratos prontos para clientes
- As nove métricas oficiais expõem drill-down cuja soma reconcilia com o total; metas arquivadas,
  faturas roladas e parcelas de cartão já representadas na fatura ficam fora da origem.
- Resultado mensal é detalhado por componentes de competência e variação patrimonial por
  componentes de caixa, passivo e aportes líquidos.
- `/api/v1/contas-financeiras` passa a oferecer criação, edição, exclusão, ajuste, movimentos e
  reconciliação; `/api/v1/carteiras` permanece compatível até PR-F2-19.
- Movimentações de investimento retornam `conciliacao` e `operacaoId`.
- Expo atualizado somente de `54.0.35` para o patch `54.0.36` do SDK 54.
- Nenhum deploy foi executado. `PROB-0081` permanece reaberto e rclone off-host continua sendo
  gate obrigatório antes de produção, PR-F2-19 e encerramento da Fase 2.

## [Mobile 1.1.1] - 2026-07-15

### Android
- Novo APK interno gerado a partir da release 1.1.0 com `versionCode 5`.
- Metadados do pacote npm foram alinhados à versão do aplicativo.
- Fluxos Maestro de recuperação e privacidade foram alinhados aos textos e à rolagem da interface atual.
- Instalações assinadas por certificado diferente ainda exigem desinstalar o aplicativo anterior antes de instalar o APK interno.
- Commit-fonte: `6be3f16aba529e9502aae541fd895d4de53cd61e`.
- APK: `nexos-financas-1.1.1.apk` (`80.323.889` bytes).
- SHA-256: `8e8929ecbfb4a8fabe56f8bcdd827a4124db75cd59c99d824d7797e5cc8ba485`.

## [Mobile 1.1.0] - 2026-07-14

### Recorrências e projeção
- Contas fixas foram generalizadas para recorrências de entrada ou saída, manuais ou automáticas.
- Execuções automáticas ocorrem às 00:05 em `America/Sao_Paulo` e recuperam vencimentos perdidos após reinício.
- Cada ocorrência possui chave idempotente, bloqueio no banco e histórico de realização, salto ou falha por saldo.
- Saídas sem saldo permanecem pendentes e não geram transação nem saldo negativo.
- Projeção mensal passa a calcular `saldo inicial + entradas - saídas` e expõe `totalEntradas`.

### Segurança e experiência mobile
- Sessão continua salva no SecureStore, mas dados ficam protegidos por biometria, credencial do aparelho ou senha da conta.
- App bloqueia na abertura e após um minuto em segundo plano; valores são ocultados imediatamente no seletor de apps.
- Navegação usa crossfade nativo com suporte a Reduce Motion e splash claro/escuro.
- Atalhos do Dashboard foram alinhados e Perfil foi removido da tela Mais.

### Release Android
- Versão mobile `1.1.0`, `versionCode 4`.
- APK Release interno: `nexos-financas-1.1.0.apk`.
- SHA-256: `931f6754c9056239f3db9508dc2c47731317ac3eef29abf78d26ba2c65e47fc9`.
- O APK local usa a chave debug do template Expo; assinatura de distribuição continua responsabilidade do CI/store.

## [1.4.0] - 2025-11-30

### 🔐 Segurança
- **[CRÍTICO]** Movido JWT secret para variável de ambiente
- **[CRÍTICO]** Reduzido tempo de expiração do access token de 24h para 15min
- **[CRÍTICO]** Protegidas credenciais do banco com variáveis de ambiente
- Adicionado `.env` no `.gitignore`
- Criado `application-prod.properties` para produção
- Criado `.env.example` como template
- Removidos logs com informações sensíveis

### ✅ Validações
- Sistema validado e pronto para deploy em produção
- Todos os secrets protegidos
- CORS configurado para produção

---

## [1.3.0] - 2025-11-30

### ⭐ Features
- **Refresh Token implementado** (auto-renovação de sessão)
- Access token expira em 15 minutos
- Refresh token expira em 7 dias
- Renovação automática transparente para o usuário
- Logout revoga tokens no backend

### 🗄️ Banco de Dados
- Criada tabela `refresh_tokens`
- Índices para otimização de queries
- Foreign key com cascade delete

### 🔧 Backend
- Criado `RefreshToken` entity
- Criado `RefreshTokenRepository`
- Criado `RefreshTokenService`
- Atualizado `AuthController` com novos endpoints:
  - `POST /api/auth/refresh-token` - Renovar access token
  - `POST /api/auth/logout` - Revogar refresh token
  - `POST /api/auth/logout-all` - Revogar todos os tokens

### 💻 Frontend
- Atualizado `authService` para salvar refresh token
- Implementado interceptor Axios para renovação automática
- Atualizado `AuthContext` para renovar token ao inicializar
- Adicionado `refreshToken` em `LoginResponse` type

### 📚 Documentação
- Criado `LICOES_APRENDIDAS.md` com debugging experiences
- Atualizado README com refresh token

---

## [1.2.0] - 2025-11-29

### 📊 Dashboard
- Implementados gráficos com Recharts
- Gráfico de pizza (Gastos por Categoria)
- Gráfico de linhas (Evolução Mensal)
- Cards de resumo financeiro
- Cards secundários (Cartões, Metas, Contas Fixas)

### 🐛 Correções
- Corrigido Lazy Loading do JPA/Hibernate
  - Adicionado `JOIN FETCH` em queries customizadas
  - Categorias agora carregam corretamente nas transações
- Corrigido cache do Vite com prop `chartData`
- Corrigido layout dos gráficos (grid responsivo)
- Corrigido usuário hardcoded em todas as telas

### 🔧 Backend
- Criado `DashboardController`
- Criado `DashboardService` com cálculos de:
  - Saldo total de carteiras
  - Total de entradas/saídas do mês
  - Gastos por categoria
  - Evolução mensal (6 meses)
  - Comparação mensal
- Query customizada no `TransacaoRepository`

### 💻 Frontend
- Criado componente `Dashboard`
- Criado `GraficoGastosPorCategoria`
- Criado `GraficoEvolucaoMensal`
- Criado `dashboardService`
- Implementado `useAuth()` em TODAS as páginas

---

## [1.1.0] - 2025-11-28

### ⭐ Features
- Sistema de Autenticação JWT completo
- Recuperação de senha por email
- Gestão de transações (criar, editar, deletar)
- Parcelamento de compras
- Categorias personalizadas (cores e ícones)
- Controle de cartões de crédito
- Gestão de metas financeiras
- Contas fixas mensais

### 🔧 Backend
- Spring Security configurado
- JWT authentication filter
- BCrypt para senhas
- Soft delete para categorias
- Validação de proprietário (usuário só vê seus dados)

### 💻 Frontend
- AuthContext com Context API
- Rotas protegidas
- Interceptor Axios para token
- Notificações toast
- UI responsiva com Tailwind

### 🗄️ Banco de Dados
- Tabelas: usuarios, categorias, transacoes, contas, metas, contas_fixas
- Relacionamentos JPA configurados
- Índices para performance

---

## [1.0.0] - 2025-11-25

### 🎉 Lançamento Inicial
- Estrutura básica do projeto
- Configuração Spring Boot
- Configuração React + Vite
- PostgreSQL configurado
- Primeiras telas de Login e Registro

---

## 📊 Estatísticas de Desenvolvimento

### Versão 1.4.0 (Atual)
- **Tempo de desenvolvimento:** ~15 horas
- **Commits:** 50+
- **Arquivos modificados:** 30+
- **Linhas de código:** ~8.000+
- **Problemas resolvidos:** 10+

### Principais Desafios
1. **Lazy Loading JPA** (~2h de debugging)
2. **Cache do Vite** (~1.5h)
3. **Usuário hardcoded** (~1h)
4. **Layout dos gráficos** (~30min)
5. **Refresh token implementation** (~3h)

---

## 🎯 Próximas Versões

Ver [PROXIMOS_PASSOS.md](./PROXIMOS_PASSOS.md)

### v1.5.0 (Planejado)
- Skeleton Loaders
- Filtros no Dashboard
- Rate Limiting
- Validações de entrada

### v2.0.0 (Futuro)
- Dark/Light mode
- Exportação CSV/PDF
- Notificações push
- App mobile (React Native)

---

## 📝 Notas de Versão

### [1.4.0] - Segurança
Esta versão foca em **segurança e preparação para produção**. Todas as vulnerabilidades críticas foram corrigidas e o sistema está pronto para deploy.

### [1.3.0] - Refresh Token
Implementação do sistema de **refresh token** para melhorar a experiência do usuário, permitindo sessões de até 7 dias sem necessidade de novo login.

### [1.2.0] - Dashboard
Implementação completa do **dashboard com gráficos** e correção de bugs críticos de Lazy Loading e cache.

### [1.1.0] - MVP
Primeira versão funcional completa com todas as funcionalidades principais implementadas.

### [1.0.0] - Fundação
Estrutura básica do projeto e configurações iniciais.

---

## [1.5.0] - 2026-07-08

### Fase 2 — Web e mobile de qualidade (inicio)

#### Mobile — PR-FASE2-01 (P0)
- **[P0]** Token de acesso persistido via `expo-secure-store` (sessao mantida entre cold starts)
- **[P0]** URL da API configurada via `expo-constants` (app.json extra.apiBaseUrl) — sem IP hardcoded
- **[P0]** Restore de sessao ao abrir app: valida token via `GET /api/v1/usuarios/me`
- **[P0]** Corrigido path `/dashboard/resumo` → `/v1/dashboard/resumo` em perfil.tsx
- Cache de usuario no SecureStore para restore instantaneo antes da validacao

#### Mobile — PR-FASE2-02 (P1)
- **[P1]** "Esqueceu a senha?" navega para tela de forgot-password
- **[P1]** "Ver todas" no dashboard navega para lista de transacoes
- **[P1]** App.tsx removido (template Expo morto); index.ts usa `expo-router/entry`
- **[P1]** onError adicionado em mutations: criarMutation (carteiras), pagarMutation (contas-fixas)

#### Arquivos alterados (mobile)
- `src/store/auth.ts` — SecureStore em vez de memoria
- `src/config/api.config.ts` — expo-constants em vez de IP fixo
- `src/context/AuthContext.tsx` — restoreSession + isLoading dinamico
- `src/services/authService.ts` — login/logout async com SecureStore
- `app/(app)/perfil.tsx` — path corrigido
- `app/index.tsx` — loading state durante restore
- `app.json` — extra.apiBaseUrl
- `App.tsx` — removido (template Expo morto)
- `index.ts` — `import 'expo-router/entry'`
- `app/(auth)/forgot-password.tsx` — nova tela de recuperacao de senha
- `app/(auth)/login.tsx` — "Esqueceu a senha?" navega para forgot-password
- `app/(app)/index.tsx` — "Ver todas" navega para transacoes
- `app/(app)/more/carteiras.tsx` — onError em criarMutation
- `app/(app)/more/contas-fixas.tsx` — onError em pagarMutation

#### Frontend — PR-FASE2-04 (P3)
- **[P3]** Rota 404 adicionada (pagina NotFound com link para Dashboard)
- **[P3]** 27 console.log/console.error removidos de page components
- **[P3]** Componente morto GraficoComparacaoMensal removido

#### Mobile — PR-FASE2-05 (P2)
- `parseCurrencyBR` centralizado em `utils/format.ts`

#### Frontend — PR-FASE2-06 (P2)
- Zero `any` nos services (substituidos por `Omit<T>`, `Partial<T>`, `unknown`)

#### Backend/Frontend — PR-FASE2-07 (P2)
- `confirmPassword` no RegisterRequest backend e frontend
- `@AssertTrue` validando igualdade de senhas

#### Frontend — PR-FASE2-08 (P2)
- `aria-label` em Login e Layout (menu lateral)

#### Backend — PR-LEDGER-01
- Testcontainers PostgreSQL adicionado para validar Flyway em banco limpo
- Profile Maven `integration-test` com Failsafe para testes `*IT.java`
- `PostgresMigrationIT` valida startup Spring, migrations Flyway e Hibernate `ddl-auto=validate`
- CI passou a rodar `mvn verify -Pintegration-test --batch-mode`
- Mockito configurado como `javaagent` no Surefire/Failsafe para JDK 21
- Validação equivalente em PostgreSQL VPS real concluída em 2026-07-08 com usuário `dbnexos_gestor`

#### Backend — PR-LEDGER-02
- Migration `V11__movimento_carteira.sql` criada para schema inicial do Ledger
- Entidade `MovimentoCarteira` criada com `usuario`, `carteira`, tipo, origem, valor absoluto, valor assinado, saldo resultante, moeda e idempotency key
- Enums `TipoMovimentoCarteira` e `OrigemMovimentoCarteira` adicionados
- `MovimentoCarteiraRepository` adicionado com consultas por ownership, carteira e idempotência
- `PostgresMigrationIT` ampliado para validar `V11`, constraints e FK em PostgreSQL real quando Docker estiver ativo
- Testes backend: `./mvnw -q test` -> 38/38 PASS
- BUG-0010 corrigiu `MovimentoCarteira.moeda`: migration usa `CHAR(3)` e JPA agora usa `@JdbcTypeCode(SqlTypes.CHAR)`
- Smoke VPS PostgreSQL: Flyway validou 14 migrations, schema `ddl-auto=validate` PASS

#### Backend — PR-LEDGER-03
- `LedgerService` criado para registrar movimento e atualizar `Carteira.saldo` na mesma transação
- `RegistrarMovimentoCommand` criado como command interno
- `CarteiraRepository.findByIdAndUsuarioIdForUpdate` adicionado com `PESSIMISTIC_WRITE`
- `CarteiraService` passou a usar Ledger para criar saldo inicial, ajustar saldo, adicionar e remover dinheiro
- Conflitos financeiros/locks retornam 409 (`FINANCIAL_CONFLICT`)
- `LedgerServiceTest` cobre entrada, saída, saldo insuficiente, ownership e concorrência
- Testes backend: `./mvnw -q test` -> 43/43 PASS
- Smoke VPS PostgreSQL validou Flyway/schema real

#### Backend — PR-LEDGER-04
- Reconciliação de saldo adicionada para comparar `Carteira.saldo` com soma de `MovimentoCarteira.valorAssinado`
- `LedgerReconciliationService` criado com status `OK` ou `DIVERGENTE`
- Endpoints adicionados: `GET /api/v1/carteiras/{id}/reconciliacao` e `GET /api/v1/carteiras/minhas/reconciliacao`
- Logs de divergência usam apenas `usuarioId`, `carteiraId` e `diferenca`
- `PostgresMigrationIT` ampliado com query real de reconciliação para PostgreSQL quando Docker estiver ativo
- Testes backend: 47/47 unitários PASS
- Smoke VPS PostgreSQL validou Flyway/schema real

#### Backend — PR-LEDGER-05
- Migration `V12__ledger_backfill_carteiras.sql` criada para backfill inicial idempotente de carteiras
- Backfill calcula `saldo_materializado - saldo_ledger`, evitando duplicar carteiras que já possuem movimentos
- Unique parcial adicionada para impedir mais de um `BACKFILL` por carteira
- `LedgerBackfillService` e `LedgerBackfillResult` criados para rotina interna por usuário ou todas as carteiras
- Diferença negativa bloqueia backfill e exige auditoria manual
- `LedgerBackfillServiceTest` cobre abertura, idempotência, reconciliação, isolamento por usuário, diferença parcial e bloqueio
- Testes backend: 53/53 unitários PASS
- Smoke VPS PostgreSQL validou Flyway/schema real

#### Backend — PR-LEDGER-06
- Endpoint `POST /api/v1/carteiras/{id}/ajustes` — ajuste manual explícito via Ledger com payload `{tipo, valor, descricao}`
- Endpoint `GET /api/v1/carteiras/{id}/movimentos` — extrato paginado de movimentos do Ledger por carteira
- Endpoints `POST /{id}/adicionar` e `POST /{id}/remover` marcados `@Deprecated(since = "PR-LEDGER-06")` — continuam funcionais
- DTO `AjusteCarteiraRequest` e `MovimentoCarteiraResponse` criados
- `CarteiraService.ajustarSaldo` e `CarteiraService.listarMovimentos` delegam para `LedgerService`
- `CarteiraControllerTest` com 10 testes: ajuste entrada/saída, tipo inválido, ownership cruzado, listagem, reconciliação, deprecated
- Testes backend: 63/63 unitários PASS
- Smoke VPS PostgreSQL validou Flyway/schema real

#### Backend — PR-LEDGER-07
- Migration `V13__transacao_carteira.sql`: coluna `carteira_id` FK opcional e `ativa` (default true) em transacoes
- Transacao ganha campos `carteira` e `ativa`; TransacaoRequest ganha `carteiraId`
- `TransacaoService.criar()`: cria movimento Ledger (ENTRADA/SAIDA) quando carteiraId presente
- `TransacaoService.atualizar()`: computa delta de valor e registra ajuste com direcao correta (SAIDA/ENTRADA)
- `TransacaoService.deletar()`: soft-delete (`ativa = false`) + estorno via Ledger; `cancelar()` alias
- `TransacaoServiceLedgerTest` com 6 testes: criar com/sem carteira, atualizar, cancelar entrada/saida
- Testes backend: 69/69 unitários PASS

#### Documentacao
- 15 Backlog items fechados (0005, 0006, 0009, 0014, 0015, 0016, 0017, 0018, 0022, 0023, 0024, 0025, 0031, 0032, 0033)
- PROB-0019 fechado
- PR-LEDGER-00 a PR-LEDGER-20 registrados no checklist
- Fase Ledger registrada com 20/20 PRs em status aceito (`PASS` ou `PASS_COM_RESSALVA`)
- PostgreSQL VPS real validado: PostgreSQL 17.10, Flyway 14 migrations, schema JPA OK
- BUG-0010 registrado e corrigido (`moeda CHAR(3)` vs mapeamento JPA)
- Fundação contábil estabelecida: Ledger, reconciliação, backfill, idempotência
- Fase 2 concluida

---

**Ultima atualizacao:** 2026-07-08 (BUG-0010, validacao PostgreSQL VPS)
**Mantido por:** Zero (Allan Carvalho)
