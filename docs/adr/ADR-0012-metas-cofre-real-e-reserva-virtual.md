# ADR-0012 — Metas: cofre real por meta e reserva virtual

- **Status:** Accepted (2026-07-15, plano Fase 2 rev. 3 aprovado pelo responsavel do produto)
- **Contexto:** hoje a reserva de meta debita a carteira via ledger (RESERVA_META/RESGATE_META) e o
  valor passa a existir apenas em `meta.valorReservado` — um cofrinho sem conta de destino. O doc
  mestre exige distinguir reserva virtual (alocacao) de cofrinho real (transferencia entre contas).
- **Decisao:**
  - **cofre real = uma conta financeira subtipo COFRE por meta** (FK da meta para seu cofre);
    aporte/resgate = operacao de transferencia entre conta de caixa e o cofre (ADR-0009);
  - invariante: `meta.valorReservado == saldo do COFRE da meta`, por meta e agregado;
    `valorReservado` vira derivado;
  - migracao do legado: para cada reserva/resgate verificavel, manter o movimento antigo,
    adicionar contraparte MIGRACAO na mesma data efetiva e vincular ambos a operacao; divergencia
    aborta o lote ou vira pendencia inventariada — nunca correcao silenciosa;
  - **reserva virtual** = alocacao explicita sobre conta de caixa, sem lancamento no ledger;
    reduz apenas "Disponivel para gastar" (ADR-0013);
  - modalidade `COFRE_REAL | RESERVA_VIRTUAL`: exatamente uma por meta;
  - ciclo de vida de metas permanece o do ADR-0004; reservado passa a ser visivel no patrimonio.
- **Consequencias:** COFRE entra em "Disponivel agora" somente quando sua liquidez for IMEDIATA;
  Reservado = COFRE real + alocacoes virtuais. Conclusao/arquivamento de meta continua sem
  movimentar dinheiro; exclusao com saldo exige resgate (transferencia de volta).
