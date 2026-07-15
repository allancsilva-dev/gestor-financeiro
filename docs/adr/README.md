# ADRs — Nexos Finanças

Registros de decisao arquitetural (formato MADR curto: Contexto / Decisao / Consequencias / Status).

Regras:

- ADR aceito e vinculante para implementacao; mudanca exige novo ADR que o substitua.
- Nenhum item do `PROBLEM_LEDGER.md` e marcado corrigido por ADR — correcao exige evidencia.
- Fase 0B (antes da Fase 2) adicionara ADRs de conta financeira, ledger, investimentos,
  orcamento, competencia, liquidez, metricas oficiais e reconciliacao. A Fase 2 nao inicia
  antes da aprovacao desses ADRs.

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
