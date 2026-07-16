# PR-F2-16A — Contratos prontos para clientes

## Objetivo

Fechar lacunas dos contratos entregues em PR-F2-13/15/16 antes da migração do cliente web,
sem incorporar o WIP de PR-F2-20 e sem qualquer ação em produção.

## Escopo verificado

- Drill-down das nove métricas oficiais e reconciliação de cada soma com o valor oficial.
- Exclusão de metas arquivadas, faturas roladas e parcelas de cartão duplicadas.
- Resultado mensal por competência e variação patrimonial por componente.
- CRUD, ajuste, movimentos e reconciliação em `/api/v1/contas-financeiras`, preservando
  `/api/v1/carteiras`.
- Contrato de conciliação das movimentações de investimento.
- Patch Expo SDK 54 de `54.0.35` para `54.0.36`.

## Isolamento do WIP

O estado não commitado de PR-F2-20 (`TransacaoRepository`, `ReconciliacaoGlobalController` e
`ReconciliacaoGlobalService`) foi guardado antes da implementação em:

`stash@{0}: WIP PR-F2-20 reconciliacao global antes PR-F2-16A`

A implementação partiu de `896965e`; o stash não foi aplicado nem incluído neste trabalho.

## Evidência de contrato

- As nove chaves aceitas por `GET /api/v1/metricas/{metrica}/origens` são testadas comparando
  `sum(origens.valor)` ao respectivo campo oficial.
- Cenário dedicado comprova que meta arquivada, origem de rollover e parcela de cartão já
  coberta por fatura não entram no drill-down.
- Compra real retorna `CONCILIADA` e `operacaoId`; snapshot declarado externo retorna `EXTERNO`
  sem operação.
- Rotas canônicas são testadas para autenticação, ownership, paginação, criação, edição,
  exclusão, ajuste, movimentos e reconciliação.

## Comandos executados

| Comando | Resultado |
|---|---|
| `./mvnw -q -DskipTests compile` | PASS |
| `./mvnw -q -Dtest=MetricasServiceTest,InvestimentoCaixaTest,ContaFinanceiraControllerTest test` | PASS |
| `./mvnw -q test` | PASS — 236 testes, zero falha/erro |
| `./mvnw -q verify -Pintegration-test` | PASS — 17 testes PostgreSQL/Testcontainers, migrations até V40 |
| `npm run lint && npm run build && npm run test` (frontend) | PASS — zero erro de lint, build e 21 testes |
| `npm run typecheck && npm run lint && npm run test` (mobile) | PASS — 17 testes |
| `npx expo-doctor` | PASS — 18/18 |
| `npm audit --omit=dev --audit-level=high` | PASS — zero alta/crítica em frontend e mobile |
| `./mvnw -q -DskipTests verify -Psecurity-scan` | PASS — OWASP sem CVSS bloqueante |
| `./scripts/e2e-web.sh` | PASS — Playwright Chromium com PostgreSQL, 1/1 |
| `git diff --check` | PASS |

## Riscos residuais e gates

- `PROB-0081` permanece **REABERTO**. A evidência com remote local não substitui um drill
  off-host real com `rclone check --download`, checksum remoto e restauração completa.
- Sem esse drill são proibidos deploy, migrations em produção, PR-F2-19 contract e encerramento
  da Fase 2. Desenvolvimento e validação locais permanecem permitidos.
- `precoMercado` da decomposição de variação patrimonial continua `null`, conforme ADR-0013,
  porque esta fase ainda não possui histórico de cotação suficiente.
- O audit de produção mobile mantém 15 achados moderados e um baixo, sem alta/crítica; ficam
  registrados para atualização compatível futura, sem usar `audit fix --force` nesta correção.
- O próximo trabalho funcional após aceite é PR-F2-17. Sistema violeta deve ser aplicado apenas
  às superfícies tocadas; redesign global continua reservado à Fase 3.

## Status final

PASS COM RESSALVA OPERACIONAL — contratos e CI locais verdes; nenhum deploy executado. A
ressalva é exclusivamente o gate off-host de `PROB-0081`, que permanece reaberto.
