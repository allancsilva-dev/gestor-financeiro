# ADRs — Nexos Finanças

Registros de decisao arquitetural (formato MADR curto: Contexto / Decisao / Consequencias / Status).

Regras:

- ADR aceito e vinculante para implementacao; mudanca exige novo ADR que o substitua.
- Nenhum item do `PROBLEM_LEDGER.md` e marcado corrigido por ADR — correcao exige evidencia.
- Fase 0B concluida em 2026-07-15: ADR-0008..0015 aprovados via plano da Fase 2 rev. 3
  (responsavel do produto). Ver `ANEXO-fase-0b-mapeamento-dados.md` para o mapeamento
  dados atuais -> modelo futuro exigido pelo BACKLOG-0086.

## Indice

| ADR | Titulo | Status |
|---|---|---|
| [ADR-0001](ADR-0001-backend-fonte-unica-de-regra-financeira.md) | Backend como fonte unica de regra financeira | Accepted |
| [ADR-0002](ADR-0002-onboarding-canonico-via-finalizar.md) | Onboarding canonico via `/finalizar` | Accepted |
| [ADR-0003](ADR-0003-timezone-de-negocio-e-clock-injetavel.md) | Timezone de negocio e `Clock` injetavel | Accepted |
| [ADR-0004](ADR-0004-ciclo-de-vida-de-metas.md) | Ciclo de vida de metas e valor reservado | Accepted |
| [ADR-0005](ADR-0005-persistencia-de-anexos.md) | Persistencia de anexos: volume agora, object storage depois | Accepted |
| [ADR-0006](ADR-0006-backup-criptografado-off-host.md) | Backup criptografado off-host com restore drill | Accepted |
| [ADR-0007](ADR-0007-exclusao-lgpd-ordenada.md) | Exclusao LGPD por manifesto ordenado app-level | Accepted |
| [ADR-0008](ADR-0008-conta-financeira-unificada.md) | Conta financeira unificada | Accepted |
| [ADR-0009](ADR-0009-operacao-financeira-e-ledger.md) | Operacao financeira e ledger operacional | Accepted |
| [ADR-0010](ADR-0010-politica-contabil-caixa-competencia.md) | Politica contabil: caixa canonica, competencia derivada | Accepted |
| [ADR-0011](ADR-0011-investimentos-custodia-e-caixa.md) | Investimentos: custodia e vinculo com caixa | Accepted |
| [ADR-0012](ADR-0012-metas-cofre-real-e-reserva-virtual.md) | Metas: cofre real por meta e reserva virtual | Accepted |
| [ADR-0013](ADR-0013-metricas-oficiais.md) | Metricas oficiais do produto (9) | Accepted |
| [ADR-0014](ADR-0014-competencia-de-orcamento.md) | Competencia de orcamento | Accepted |
| [ADR-0015](ADR-0015-reconciliacao-e-migracao.md) | Padrao obrigatorio de reconciliacao e migracao | Accepted |
| [ADR-0016](ADR-0016-fila-duravel-e-limites-do-worker.md) | Fila duravel, worker e o que fica sincrono | Accepted |
| [ADR-0017](ADR-0017-assistente-financeiro-mobile-first.md) | Assistente financeiro mobile-first | Accepted |
| [ADR-0018](ADR-0018-gate-de-feature-em-runtime-via-capacidades.md) | Gate de feature do app sai do build e vira runtime via `/api/v1/capacidades` | Accepted |
| [ADR-0019](ADR-0019-conector-de-rede-pelo-pipeline-canonico.md) | Conector de rede entra pelo pipeline canonico como `ImportSource` | Accepted |
| [ADR-0020](ADR-0020-consentimento-e-credenciais-de-terceiro.md) | Consentimento, credenciais de terceiro e revogacao | Accepted |
| [ADR-0021](ADR-0021-ingestao-automatica-e-divergencia-banco-ledger.md) | Ingestao automatica: o que entra, o que duplica e divergencia banco/ledger | Accepted |
