# PR-F2-20 — Relatório de implementação da reconciliação global

## Resultado

Implementação local concluída sobre o contract V41, sem migration e sem escrita financeira. O
`stash@{0}` foi somente consultado como referência e permaneceu intacto.

## Entregas

- relatório público por titular com `OK`/`DIVERGENTE`, totais, resumo fixo das quatro invariantes
  e somente detalhes divergentes;
- queries projetadas para ledger, passivo/faturas, cofre/meta e transação incompleta;
- snapshot `REPEATABLE_READ` read-only por usuário;
- varredura paginada por keyset, transações independentes e continuidade após falha;
- scheduler `0 30 0 * * *` no fuso de negócio, com exclusão local de sobreposição;
- gauges Micrometer sem IDs como tags e contributor de health degradável sem 503;
- maintenance `global-reconciliation`, artefato JSON 0600 e `.sha256`, sem `--apply` e fora do
  repositório;
- testes de serviço, API, sistema, scheduler, métricas, health e maintenance.

## Invariantes

1. `SALDO_LEDGER`: saldo materializado de toda conta financeira igual à soma assinada do ledger.
2. `PASSIVO_FATURAS`: passivo do cartão igual à soma assinada das faturas não pagas que não foram
   origem de rollover, menos `valorPago`; cartões inativos continuam incluídos.
3. `COFRE_META`: toda meta `COFRE_REAL` com reserva positiva aponta para cofre do mesmo usuário,
   subtipo `COFRE`, com saldo idêntico; metas arquivadas com reserva continuam incluídas.
4. `TRANSACAO_INCOMPLETA`: transação comum conciliada exige conta financeira e compra de cartão
   conciliada exige lançamento `COMPRA`; incompletude explícita pendente é aceita.

## Rollout e bloqueios

`PROB-0081` permanece aberto. PR-F2-20 não deve ser promovido antes de backup off-host e restore
drill aprovados. No clone restaurado: aplicar V41, exigir postflight PR-F2-19 verde, executar o
maintenance e exigir zero divergências/erros. Depois do deploy, validar endpoint autenticado,
health, gauges e checksum. Rollback é reimplantação do artefato anterior.

## Evidência local

- backend `./mvnw -q verify`: 239 testes unitários, cobertura JaCoCo e build PASS;
- PostgreSQL 16 via `scripts/verify-postgres-migrations.sh`: V1..V41 e 11 testes selecionados PASS,
  incluindo o cenário global pós-V41;
- web: lint e build PASS; Vitest 35/35; Playwright 1/1;
- mobile: typecheck e lint PASS; Jest 17/17; Expo Doctor 18/18;
- `npm audit --audit-level=high`: web sem vulnerabilidades; mobile sem high/critical, mantendo 17
  achados low/moderate da cadeia Expo registrados pelo audit (upgrade forçado seria breaking);
- OWASP Dependency-Check com limiar CVSS 7: PASS;
- OpenAPI do endpoint e health `DEGRADED` HTTP 200 cobertos por teste;
- `git diff --check`: PASS.

Esses gates não substituem o drill off-host nem a execução do artefato em clone restaurado.
