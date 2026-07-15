# Anexo Fase 0B — Mapeamento dados atuais -> modelo futuro e plano de migracao reversivel

Exigido pelo BACKLOG-0086. Complementa ADR-0008..0015. Plano executavel completo: plano da Fase 2
rev. 3 (aprovado 2026-07-15).

## Mapeamento de entidades

| Hoje (fisico) | Futuro (dominio/API) | Transformacao |
|---|---|---|
| `carteiras` (DINHEIRO/CONTA_BANCARIA/POUPANCA) | Conta financeira ATIVO subtipo DINHEIRO/CORRENTE/POUPANCA, liquidez IMEDIATA | Expand: colunas natureza/subtipo/liquidez/origem_dados/estado_conciliacao/moeda; backfill deterministico por tipo (PR-F2-02) |
| `contas` tipo CREDITO | Configuracao interna de cartao 1:1 com conta financeira PASSIVO subtipo CARTAO | Conta CARTAO criada por FK unica; passivo de abertura = `valorGasto` (origem BACKFILL, saldo de abertura no corte — nao afeta resultado mensal nem variacao patrimonial) (PR-F2-06) |
| `contas` tipo DEBITO/DINHEIRO/POUPANCA | Conta financeira ATIVO | Migradas com guard se ambiguo; criacao nova bloqueada (dentro do bloco A) |
| `contas.saldoAtual` | — (morto) | Deprecado; removido do contrato no PR-F2-19 |
| `contas.valorGasto` | Derivado do ledger de passivo | Dupla escrita verificada ate PR-F2-19; reconciliacao transitoria tripla: passivo ledger == valorGasto == faturas nao pagas |
| `movimentos_carteira` | Lancamento (1..N por operacao) | Expand: FK `operacao_id` nullable p/ legado; preencher em legado = metadado permitido, conteudo financeiro intocavel (PR-F2-03) |
| — | `operacoes_financeiras` | Nova: tipo, datas, status, origem, idempotency (usuario+chave), hash do request, referencia de estorno (PR-F2-03) |
| — | Transferencia interna | Nova: operacao com 2 lancamentos vinculados (PR-F2-04) |
| `transacoes` sem carteira | Estado `PENDENTE_CONCILIACAO` (legado/importacao) | Inventario E0-2 (SQL auditado, sem endpoint admin); backfill so nas reconciliaveis; novas operacoes manuais de caixa exigem `contaFinanceiraId` (422) (PR-F2-05) |
| `parcelas` de compra de cartao | — (eliminadas) | Contract V27 promovido apos dry-run; guard de equivalencia Parcela<->FaturaLancamento (PR-F2-09) |
| `parcelas` fora de cartao | Cronograma canonico, visao por vencimento | Servico canonico com visoes compra/competencia/caixa (PR-F2-10) |
| `fatura_lancamentos` | Cronograma/detalhe do cartao, com `operacao_id` | Compra/edicao/exclusao geram operacoes/estornos (PR-F2-07) |
| Pagamento de fatura (acumulado em `valorPago`) | Registro proprio + operacao -caixa/-passivo | Historico explicito de pagamentos (PR-F2-08) |
| `metas.valorReservado` | Derivado: saldo do COFRE da meta | COFRE por meta; contrapartes MIGRACAO na mesma data efetiva; divergencia aborta lote ou vira pendencia (PR-F2-11) |
| — | Modalidade COFRE_REAL / RESERVA_VIRTUAL | Nova; alocacao virtual sem lancamento (PR-F2-12) |
| `ativos`/`movimentacoes_ativo` | Posicoes sob conta CUSTODIA (saldo=0 tecnico) | Operacao real exige caixa; legado sem movimento INVESTIMENTO vira snapshot EXTERNO nao conciliado, delta de caixa zero (PR-F2-13/14) |
| `DashboardResumo` | + 9 metricas oficiais (ADR-0013) | Campos aditivos; `GET /v1/metricas` + drill-down (PR-F2-15/16) |

## Plano de migracao reversivel (resumo)

1. Baseline (PR-F2-01): snapshot por usuario das 9 metricas contra o modelo atual
   (NAO_CALCULAVEL onde impossivel) — referencia de toda reconciliacao pre/pos.
2. Toda data-migration segue ADR-0015: restore drill antes; guard `RAISE EXCEPTION`;
   invariantes SQL; recuperacao = restore + migration compensatoria (nunca undo manual).
3. Ordem: fundacao (01-05) -> cartao (06-10) / metas (11-12) / investimentos (13-14) ->
   metricas (15-16) -> clientes (17-18) -> contract (19) -> reconciliacao global (20).
   Migrations que tocam `carteiras` serializadas.
4. Compatibilidade: tudo aditivo ate ambos os clientes migrarem; contract (19) fecha dupla
   escrita de `valorGasto` e remove campos/APIs legados.
