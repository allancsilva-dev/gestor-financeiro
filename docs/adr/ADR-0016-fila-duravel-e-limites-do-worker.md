# ADR-0016 — Fila duravel, worker e o que fica sincrono

- **Status:** Accepted (2026-08-26, decidido com o responsavel do produto durante a Fase 4)
- **Contexto:**
  - `background_jobs` (V45) existia desde o PR-F4-01 com claim por `FOR UPDATE SKIP LOCKED`, lease,
    tentativas e dead letter, mas **sem nenhum produtor ou consumidor em producao**.
  - Envelope real medido: **uma unica instancia** (`container_name` impede `--scale`), heap de
    ~537 MB (`mem_limit: 768m` + `MaxRAMPercentage=70`) com `ExitOnOutOfMemoryError`, pool Hikari de
    10 conexoes disputado por 50 threads Tomcat, e `TaskScheduler` do Spring com **pool 1**, ja
    ocupado por recorrencias, reconciliacao, limpeza de rate limit e refresh tokens.
  - A Fase 4 precisa de trabalho longo e retomavel: commit de lote no ledger, reversao, sincronizacao
    de notificacoes, fechamento de orcamento, deteccao de recorrencias e expurgo de dados importados.
- **Decisao:**
  - **Worker proprio, nunca no `TaskScheduler`.** `BackgroundJobWorker` usa `ExecutorService`
    dedicado com concorrencia default 2, para nao travar os `@Scheduled` existentes nem esgotar o
    pool de conexoes (o worker nunca segura mais conexoes que sua concorrencia).
  - **`claim` fora da transacao de trabalho.** Processar dentro da transacao do claim esconderia o
    lease das demais instancias ate o commit.
  - **Handler idempotente e obrigatorio.** Job pode ser reexecutado por lease vencido ou processo
    morto; renovacao de lease roda em segundo plano enquanto o handler executa.
  - **Handler roda sem `SecurityContext`** (`ThreadLocal` nao cruza thread): todo servico chamado
    pelo worker recebe `usuarioId` explicito.
  - **`@Scheduled` vira apenas enfileirador**, com `job_key` deterministica; o `UNIQUE (job_key)` da
    V45 deduplica entre instancias quando houver mais de uma.
  - **Tipo sem handler vai direto para dead letter**, em vez de girar na fila.
  - **Parse de importacao continua sincrono**, protegido por teto de parses simultaneos, limites de
    arquivo/registros e timeout de proxy. Tornar o parse assincrono exigiria o arquivo sobreviver a
    requisicao em disco local, o que amarraria o pipeline a uma instancia — decisao adiada com
    gatilho explicito (ver Consequencias).
- **Consequencias:**
  - Escrita financeira longa (commit e reversao de lote) entra pela fila, com retomada e trilha; a
    requisicao HTTP deixa de ser o lugar onde milhares de lancamentos sao gravados.
  - Enquanto houver uma unica instancia, o worker roda no mesmo processo da API; o desenho ja suporta
    replicas sem mudanca de codigo, porque a exclusao e feita no banco.
  - **Gatilho para reabrir a decisao do parse assincrono:** segunda instancia da API ou staging de
    arquivo em object storage — o que vier primeiro. Registrado no backlog.
  - Cardinalidade de metrica fechada: `app.jobs.processed{type,result}` usa tipo de handler
    (whitelist) e resultado constante.
