# Smoke financeiro crítico no iOS Simulator

## Escopo

Foi adicionado um smoke local e integrado para a jornada financeira crítica do mobile. A execução usa PostgreSQL e backend descartáveis, app iOS Debug apontado exclusivamente para `http://127.0.0.1:8081/api` e um iPhone 17 Pro já bootado.

O flow registra um usuário único, conclui o onboarding, lança despesa e compra no cartão, paga parte da fatura, reserva dinheiro em meta, confere o extrato reconciliado e valida Relatórios. Ao final, o runner autentica pela API e exige reconciliação global sem divergências, as quatro invariantes aprovadas e saldos/métricas coerentes.

## Artefatos

- Runner: `scripts/e2e-mobile-ios.sh`
- Flow: `mobile/.maestro/financial-critical.yaml`
- Evidências por execução: `/tmp/gf-mobile-smoke-<timestamp>-<pid>`
- Evidências incluem JUnit, screenshots, logs do Maestro/backend/Xcode e `reconciliation-report.txt`.

## Isolamento

O PostgreSQL é publicado em porta aleatória e removido no `trap`, juntamente com backend, app instalado e DerivedData temporário. O diretório de evidências é preservado em sucesso ou falha. Nenhum endpoint de staging ou produção participa da execução.

O workflow `mobile-maestro.yml` agora seleciona explicitamente apenas a tag `smoke`; o flow `financial-critical` não recebe essa tag e, portanto, não cria dados financeiros em staging.

## Execução

```bash
./scripts/e2e-mobile-ios.sh
```

Pré-condições: Docker funcional, Xcode, Node da `.nvmrc`, Maestro, dependências mobile instaladas e iPhone 17 Pro bootado.

## Resultado esperado

- Conta Principal: R$ 825,00
- Cofre da meta: R$ 50,00
- Passivo do cartão: R$ 50,00
- Fatura: R$ 75,00 total, R$ 25,00 pago, R$ 50,00 restante
- Resultado mensal: -R$ 175,00
- Patrimônio líquido: R$ 825,00
- Reconciliação: `status=OK`, zero divergências nas quatro invariantes

Falhas funcionais preservam evidências e encerram com código diferente de zero; este smoke não altera regras de produto para mascarar falhas.
