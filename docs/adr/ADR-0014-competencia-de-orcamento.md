# ADR-0014 — Competencia de orcamento

- **Status:** Accepted (2026-07-15, plano Fase 2 rev. 3 aprovado pelo responsavel do produto)
- **Contexto:** orcamento mensal existe (`OrcamentoMensal`/`OrcamentoCategoria`), mas sem politica
  de data definida; rollover de orcamento nao existe (nao confundir com rollover de fatura). Sem
  base cravada agora, a Fase 4 retrabalharia agregacoes da Fase 2.
- **Decisao:**
  - orcamento consome a **visao de competencia** (ADR-0010): gasto conta no mes da compra —
    cartao pela data da compra via `FaturaLancamento`, nunca pela data do pagamento da fatura;
  - transferencias internas, reservas de meta, compra de investimento e pagamento de cartao nao
    consomem orcamento;
  - rollover de orcamento (politicas NONE/SURPLUS_ONLY/DEFICIT_ONLY/BOTH, fechamento idempotente
    e auditavel, com base/carryIn/gasto/ajuste versionados) e **implementacao da Fase 4**; este
    ADR so garante que a agregacao de competencia da Fase 2 e a base sobre a qual ele fecha;
  - mes fechado ou regra historica nunca e reescrito silenciosamente.
- **Consequencias:** a Fase 2 nao cria schema de rollover; apenas garante que os numeros de
  orcamento saem do servico canonico de competencia, reconciliaveis com relatorio e fatura.
