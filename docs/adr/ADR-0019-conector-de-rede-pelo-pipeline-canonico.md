# ADR-0019 — Conector de rede entra pelo pipeline canônico como `ImportSource`

- **Status:** ACEITO
- **Data:** 2026-09-01
- **Relacionado:** Fase 6 (PR-F6-00), ADR-0001 (backend fonte única), ADR-0009 (ledger),
  ADR-0016 (fila durável), BACKLOG-0107 (parse assíncrono)

## Contexto

A Fase 4 entregou um pipeline canônico de importação com uma fronteira desenhada de propósito:

- `FinancialDataConnector` é a SPI de leitura, com `detect`, `parse` e `declaredBalances`;
- `ImportSource` entrega **bytes reabríveis** e nada mais — o javadoc é explícito: "conectores não
  recebem paths nem URLs";
- `CanonicalImportRecord` é o registro normalizado, documentado como "independente de CSV, OFX ou
  futuro conector";
- `CanonicalImportArchitectureTest` transforma essa fronteira em teste: reflete sobre a SPI e
  proíbe `List`, `Path` e `URL` nas assinaturas, e varre `*Connector.java` proibindo
  `readAllBytes`, `readAllLines`, `org.w3c.dom`, `TransacaoService` e `displayName()`.

A Fase 6 precisa ingerir dados vindos de uma API remota (agregador Open Finance), que é uma fonte
paginada, autenticada, com cursor e com falha de rede — nada disso cabe na forma de um arquivo. A
pergunta é onde a rede entra sem desmontar as garantias que a Fase 4 pagou para ter.

Duas formas foram consideradas. A primeira é adaptar a fonte remota para `ImportSource`: o job de
sincronização busca as páginas, escreve um snapshot NDJSON determinístico em arquivo temporário e
entrega esse arquivo ao pipeline existente. A segunda é criar uma SPI de pull paralela, que emitisse
`CanonicalImportRecord` direto no orquestrador, sem passar por bytes.

## Decisão

**A fonte remota é adaptada para `ImportSource`. Não existe caminho paralelo na SPI.**

- HTTP, OAuth, paginação, cursor e retry vivem num pacote novo `service/openfinance/`, **fora** do
  pacote `service/importacao`. Nenhuma classe `*Connector.java` abre conexão de rede.
- O job de sincronização materializa a resposta como snapshot NDJSON em `TempFileImportSource`, com
  a primeira linha sendo um envelope (`schema`, instituição, conta externa, janela, saldo de
  abertura e de fechamento) e as demais sendo fatos.
- O snapshot é **determinístico**: ordem de campos fixa, sem timestamp de captura, datas em ISO,
  `\n` puro, sem indentação. Buscar a mesma janela duas vezes produz exatamente os mesmos bytes.
- `OpenFinanceNdjsonConnector` implementa `FinancialDataConnector` como qualquer outro conector, lê
  em streaming e respeita os tetos de `ImportLimits`.
- A SPI ganha apenas `ImportFormat format()`, aditivo e com retorno enum. `ImportConnectorRegistry`
  ganha `forFormat(ImportFormat)`; `detect(...)` fica inalterado — o caminho de conector nunca passa
  por detecção heurística, porque o envelope já declara o que é, e submeter NDJSON à competição de
  confiança contra CSV produziria `DETECTION_FAILED` por ambiguidade.
- `CanonicalImportArchitectureTest` **não é afrouxado**. Ao contrário: ganha proibição explícita de
  rede em `*Connector.java` (`java.net.http`, `HttpClient`, `RestTemplate`, `WebClient`, `okhttp`,
  `URI.create`, `OAuth`) e proibição de logar `CanonicalImportRecord` inteiro.

## Consequências

- `import_batches.file_sha256` continua sendo prova auditável exata do que o parceiro devolveu, e o
  determinismo do snapshot faz do hash um detector barato de replay.
- `declaredBalances(...)` funciona sem código novo: o envelope carrega abertura e fechamento, e a
  conciliação de saldo do lote sai de graça.
- Deduplicação, categorização automática, prévia paginada, commit idempotente pela fila e reversão
  auditável são reusados integralmente. Fonte externa nova **não** ganha um segundo caminho até o
  ledger — que é a regra do documento de meta e o critério de sucesso nº 18 do produto.
- Custo assumido: o snapshot é escrito em disco antes de ser lido. Isso consome I/O e deixa, por
  alguns segundos, dado bancário em claro no volume. Mitigações obrigatórias: arquivo criado com
  permissão `rw-------`, apagado em `close()` dentro de `finally`, e varredura horária de órfãos
  (um `SIGKILL`, um OOM ou um deploy no meio do parse deixam o arquivo para trás).
- Custo assumido: o teto de `app.import.limits.file-bytes` passa a valer para a janela de
  sincronização. Uma conta movimentada estouraria o limite num backfill mensal, então o fetcher mede
  bytes enquanto escreve e fecha a janela antes do teto, reenfileirando o restante. A janela encolhe;
  a sincronização não falha.
- `ImportFormat` ganha `OPEN_FINANCE` e `import_batches` ganha `origin` (`UPLOAD`/`CONNECTOR`), para
  que proveniência não seja inferida do formato.
- `BACKLOG-0107` (parse assíncrono) **não** é resolvido por esta fase, e é importante não confundir:
  o caminho de conector é assíncrono por construção, executado pela fila durável do ADR-0016, mas o
  parse do **upload HTTP** continua síncrono, com o gatilho original intacto (segunda instância da
  API ou staging em object storage). O que esta fase entrega do critério de aceite daquele item é a
  varredura de temporários órfãos, que passa a existir por necessidade própria.

## Alternativa descartada

**SPI de pull paralela emitindo `CanonicalImportRecord` direto no orquestrador.** Parece mais
direta — elimina a materialização em disco — mas custa caro em três pontos. Primeiro, não há bytes:
`file_sha256` teria de ser sintetizado sobre os registros emitidos, ou seja, reinventar o snapshot
sem ficar com o artefato que prova o que o parceiro respondeu. Segundo, `declaredBalances(...)` fica
sem fonte e exigiria assinatura paralela, duplicando a conciliação de saldo. Terceiro, e decisivo, o
teste de arquitetura teria de ser afrouxado: a cláusula que hoje protege `ImportSource` viraria vácua
para o caminho de rede, e a experiência com esse tipo de interface é que quem escreve um "feed
connector" acumula a página inteira em memória — exatamente o que a guarda existe para impedir, num
processo cujo heap de produção é da ordem de 500 MB. Descartada porque troca uma garantia testada
por uma conveniência de I/O.
