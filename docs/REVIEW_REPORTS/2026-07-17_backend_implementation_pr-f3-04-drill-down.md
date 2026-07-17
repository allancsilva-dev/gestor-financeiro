# Relatorio de Revisao

**Arquivo:** 2026-07-17_backend_implementation_pr-f3-04-drill-down.md

**PR:** PR-F3-04 — Fundacao de drill-down (Fase 3)

**Commit:** `7cc4aeb` (main)

---

## Objetivo

Registrar a implementacao do PR-F3-04, quarto PR da Fase 3 ("Experiencia simples") e ultimo do
Bloco A backend (PR-F3-01 a PR-F3-04). O PR adiciona filtros opcionais de categoria/carteira/cartao
a `GET /v1/transacoes/periodo` e um campo aditivo `navegacao` a `MetricasService.Origem`, para que o
backend informe ao cliente para onde um drill-down deve navegar, sem introduzir migration.

## Escopo verificado

Relato de implementacao backend consolidado apos ciclo de implementacao. Nao ha evidencia, nas
informacoes recebidas pelo `docs-reporter`, de acionamento dedicado de `quality-reviewer`,
`security-auditor` ou `lgpd-auditor` especificamente para este PR (mesma ressalva ja registrada para
PR-F3-01/02/03).

Escopo tecnico coberto pela sessao:
- `TransacaoRepository`: dois metodos novos, `buscarPorPeriodoComFiltros` e
  `buscarPorPeriodoTipoComFiltros`, com predicados null-tolerantes para `categoriaId`, `carteiraId`
  e `cartaoId`, `@EntityGraph(attributePaths = {"categoria", "conta"})`.
- `TransacaoController`/`TransacaoService`: `GET /v1/transacoes/periodo` ganha os tres `@RequestParam`
  opcionais novos, combinaveis com `inicio`/`fim`/`tipo`/`q` ja existentes; caminho legado sem os
  filtros novos permanece intacto (mesmas queries de antes, nao alteradas por este commit conforme
  o diff nao as remove).
- Ownership: filtro com categoria/carteira/cartao alheio ou inexistente lanca
  `ResourceNotFoundException` (404), validado via `findByIdAndUsuarioId` de Categoria/Carteira/Conta
  no service (contrato ja existente no sistema, reaplicado aqui).
- `MetricasService.Origem`: novo campo aditivo `navegacao` (record `Navegacao(destino, id, filtros
  Map<String,String>)`); construtor antigo de `Origem` preservado (compatibilidade). Destinos
  mapeados: `CONTA_FINANCEIRA`/`COFRE` -> `EXTRATO_CONTA` (id da conta financeira); `ALOCACAO_VIRTUAL`
  -> `META`; `FATURA` -> `FATURA`; `PARCELA` -> `TRANSACAO` (id da transacao-mae, novo campo
  `transacaoId` em `ObrigacaoComprometida`); `POSICAO` -> `INVESTIMENTO`;
  `ENTRADAS_COMPETENCIA` -> `TRANSACOES` com filtros `{inicio, fim, tipo=ENTRADA}` que casam com
  `/v1/transacoes/periodo`. Origens sem destino exato (`SAIDAS_NAO_CARTAO_COMPETENCIA`,
  `CONSUMO_CARTAO_COMPETENCIA`, `VARIACAO_*`) ficam com `navegacao` nula — informativas, cliente nao
  inventa link.

## Arquivos lidos

Este relatorio foi produzido a partir do resumo tecnico fornecido pelo agente que orquestrou a
implementacao, complementado por leitura direta do diff do commit pelo `docs-reporter` (uso de
`git show`, ferramenta de inspecao somente leitura, permitida mesmo fora de `docs/`; nenhuma edicao
foi feita nesses arquivos):

- `backend/src/main/java/com/gestor/financeiro/repository/TransacaoRepository.java` (+37, dois
  metodos novos — conteudo integral lido via `git show 7cc4aeb`)
- `backend/src/main/java/com/gestor/financeiro/controller/TransacaoController.java` (+4/-1 —
  conteudo integral lido via `git show 7cc4aeb`)
- `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java` (+38, relatado)
- `backend/src/main/java/com/gestor/financeiro/service/MetricasService.java` (+62/-15, relatado)
- `backend/src/test/java/com/gestor/financeiro/TransacaoPeriodoFiltrosTest.java` (novo, +140,
  2 testes, relatado)
- `backend/src/test/java/com/gestor/financeiro/NavegacaoOrigemTest.java` (novo, +187, 4 testes,
  relatado)

## Comandos executados

Comando reportado como executado pelo ciclo de implementacao (nao reexecutado pelo `docs-reporter`,
que nao tem permissao para rodar build de `backend/`):

| Comando | Resultado |
|---|---|
| `./mvnw verify -Pintegration-test` | 255 testes unitarios + 27 ITs (failsafe), 0 falhas — BUILD SUCCESS |

Comandos de inspecao executados diretamente pelo `docs-reporter` (somente leitura, sem alterar
codigo):

| Comando | Resultado |
|---|---|
| `git show 7cc4aeb --stat` | Confirma os 6 arquivos alterados/criados e o resumo de linhas acima |
| `git show 7cc4aeb -- .../TransacaoRepository.java .../TransacaoController.java` | Confirma o texto exato das duas queries JPQL novas e da assinatura do endpoint (ver "Achados") |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | INFORMATIVO | `buscarPorPeriodoComFiltros` e `buscarPorPeriodoTipoComFiltros` usam predicados `(:param IS NULL OR ...)`, padrao ja usado em outras queries do repositorio — filtro ausente nao restringe a busca, filtro presente restringe por igualdade exata (`t.categoria.id = :categoriaId`, etc.). | `repository/TransacaoRepository.java` (confirmado via `git show`) |
| 2 | INFORMATIVO | `cartaoId` no filtro mapeia para `t.conta.id` (nao para uma entidade `Cartao` separada) — coerente com o modelo existente do sistema, onde cartao de credito e uma `Conta` com natureza especifica. | `repository/TransacaoRepository.java` (confirmado via `git show`) |
| 3 | INFORMATIVO | `@EntityGraph(attributePaths = {"categoria", "conta"})` nas duas queries novas evita N+1 ao serializar `TransacaoResponseDto` (carteira nao esta no graph destas duas queries, diferente do `findByIdAndUsuarioId` que inclui `carteira`). | `repository/TransacaoRepository.java` (confirmado via `git show`) |
| 4 | INFORMATIVO | O endpoint `GET /v1/transacoes/periodo` manteve a assinatura anterior (`inicio`, `fim`, `tipo`, `q`, `pageable`) e apenas adicionou tres `@RequestParam(required = false)` novos ao final — chamadas de clientes existentes sem os novos parametros continuam validas (compatibilidade retroativa por design de parametro opcional). | `controller/TransacaoController.java` (confirmado via `git show`) |
| 5 | INFORMATIVO | `MetricasService.Origem` ganhou campo aditivo `navegacao` via novo record `Navegacao`; o construtor antigo de `Origem` foi preservado (reportado, nao lido diretamente pelo `docs-reporter` no arquivo `MetricasService.java`, que nao foi aberto nesta rodada). | Resumo tecnico da sessao |
| 6 | BAIXA (rastreabilidade) | Origens `SAIDAS_NAO_CARTAO_COMPETENCIA`, `CONSUMO_CARTAO_COMPETENCIA` e `VARIACAO_*` ficam deliberadamente sem `navegacao` (nula) por nao terem destino exato de drill-down — decisao de design registrada para evitar que o cliente "invente" um link incorreto; nenhum cliente (mobile/web) consome este campo ainda, entao o comportamento so sera exercitado de fato nos PRs de consumo (PR-F3-08 mobile, PR-F3-12 web). | Resumo tecnico da sessao |
| 7 | BAIXA (rastreabilidade) | Nao ha evidencia recebida pelo `docs-reporter` de execucao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico — mesma ressalva ja registrada para PR-F3-01, PR-F3-02 e PR-F3-03. O contrato de ownership (404 para recurso alheio) reaplica um padrao ja auditado no sistema (`findByIdAndUsuarioId`), o que reduz o risco pratico, mas a ausencia de auditoria dedicada permanece registrada. | Ausencia de relatorio de auditoria dedicado a PR-F3-04 em `docs/REVIEW_REPORTS/` |

## O que foi corrigido

Nao ha bug a corrigir neste PR — trata-se de extensao aditiva de contrato (novos parametros opcionais
de filtro e novo campo `navegacao`), sem migration e sem alteracao de comportamento do caminho legado
(sem os filtros novos). Nenhuma entrada foi criada em `docs/BUGFIX_LOG.md`.

## O que ficou pendente

- Confirmacao formal de `quality-reviewer`/`security-auditor`/`lgpd-auditor` dedicada a este PR
  especifico (achado #7).
- Consumo real do contrato de filtros e de `navegacao` pelos clientes: mobile (PR-F3-08) e web
  (PR-F3-12), ambos fora do escopo deste PR — nao ha UI de drill-down implementada ou testada nesta
  rodada que exercite o contrato em app/frontend real.
- `CHANGELOG.md` e `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` nao foram atualizados com a entrada
  do PR-F3-04: esses dois arquivos nao constam na lista de "Arquivos sob sua responsabilidade" do
  `docs-reporter`, e a edicao direta desses arquivos esta fora da permissao concedida a este agente
  nesta sessao (restricao informada explicitamente pelo solicitante, na mesma linha do PR-F3-01, do
  PR-F3-02 e do PR-F3-03). O texto completo que deveria compor as duas entradas fica registrado
  abaixo, para aplicacao por quem tiver permissao. `BACKLOG-0089` foi ampliado nesta rodada para
  cobrir tambem o PR-F3-04 (quatro PRs no total).

### Texto pronto para `docs/CHANGELOG.md`

```
## [Fase 3 — PR-F3-04] - 2026-07-17

### Fundacao de drill-down
- `GET /v1/transacoes/periodo` ganha filtros opcionais `categoriaId`, `carteiraId` e `cartaoId`,
  combinaveis com periodo, tipo e busca (`q`). Novos `TransacaoRepository.buscarPorPeriodoComFiltros`
  e `buscarPorPeriodoTipoComFiltros` (predicados null-tolerantes, `@EntityGraph` categoria+conta).
  Caminho legado sem os filtros novos permanece intacto.
- Ownership: filtro com recurso alheio ou inexistente lanca `ResourceNotFoundException` (404),
  contrato seguro ja existente, validado via `findByIdAndUsuarioId` de Categoria/Carteira/Conta.
- `MetricasService.Origem` ganha campo aditivo `navegacao` (record `Navegacao`: destino, id, filtros)
  — construtor antigo de `Origem` preservado. Destinos: conta financeira/cofre -> `EXTRATO_CONTA`;
  alocacao virtual -> `META`; fatura -> `FATURA`; parcela -> `TRANSACAO` (id da transacao-mae, novo
  campo `transacaoId` em `ObrigacaoComprometida`); posicao -> `INVESTIMENTO`; entradas por
  competencia -> `TRANSACOES` com filtros `{inicio, fim, tipo=ENTRADA}`. Origens sem destino exato
  (saidas nao-cartao por competencia, consumo de cartao por competencia, variacoes) ficam sem
  navegacao.
- Fecha o Bloco A backend da Fase 3 (PR-F3-01 a PR-F3-04).
- Sem migration.
- Commit: `7cc4aeb`. Validacoes: `./mvnw verify -Pintegration-test` → 255 unitarios + 27 ITs, 0
  falhas, BUILD SUCCESS.
```

### Texto pronto para `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`

```
# PR-F3-04 — Fundacao de drill-down

**Status local:** `PASS_COM_RESSALVA`
**Data:** 2026-07-17
**Commit:** `7cc4aeb`

- [x] `GET /v1/transacoes/periodo`: filtros opcionais `categoriaId`, `carteiraId`, `cartaoId`
- [x] filtros combinaveis com periodo, tipo e busca (`q`)
- [x] `TransacaoRepository.buscarPorPeriodoComFiltros`/`buscarPorPeriodoTipoComFiltros` (predicados
      null-tolerantes, `@EntityGraph` categoria+conta)
- [x] caminho legado sem os filtros novos permanece intacto
- [x] ownership: recurso alheio/inexistente -> 404 (`ResourceNotFoundException`)
- [x] `MetricasService.Origem.navegacao` aditivo (construtor antigo preservado)
- [x] mapeamento de destinos: conta/cofre -> EXTRATO_CONTA, alocacao virtual -> META, fatura ->
      FATURA, parcela -> TRANSACAO, posicao -> INVESTIMENTO, entradas por competencia -> TRANSACOES
- [x] origens informativas (saidas nao-cartao, consumo cartao, variacoes) sem navegacao
- [x] nenhuma migration
- [x] `./mvnw verify -Pintegration-test`: 255 unitarios + 27 ITs, 0 falhas
- [x] `TransacaoPeriodoFiltrosTest` (2 testes) e `NavegacaoOrigemTest` (4 testes)
- [x] fecha o Bloco A backend da Fase 3 (PR-F3-01..04)
- [ ] consumo mobile do contrato (PR-F3-08)
- [ ] consumo web do contrato (PR-F3-12)
- [ ] revisao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico
- [ ] `CHANGELOG.md`/checklist atualizados diretamente (aplicado manualmente a partir deste bloco)
```

## Recomendacao final

Extensao aditiva de contrato, coerente com o padrao de ownership ja existente no sistema
(`findByIdAndUsuarioId` -> 404), sem migration e sem alteracao do caminho legado. Fecha o Bloco A
backend da Fase 3 (PR-F3-01 a PR-F3-04). Recomenda-se seguir para os PRs de consumo (PR-F3-08 mobile,
PR-F3-12 web) e aplicar as entradas de `CHANGELOG.md`/checklist transcritas acima assim que houver
permissao de escrita nesses arquivos.

## Status final

PASS_COM_RESSALVA — ressalvas: (1) `CHANGELOG.md` e o checklist de execucao de PRs nao puderam ser
atualizados por este agente por restricao de permissao de arquivo (texto pronto acima); (2) ausencia
de evidencia direta de revisao/auditoria dedicada a este PR especifico; (3) o contrato de filtros e
de `navegacao` ainda nao e exercido por nenhum cliente real (mobile/web) nesta rodada — o consumo
fica para PR-F3-08/PR-F3-12.

---

> Relatorio mantido pelo `docs-reporter`.
