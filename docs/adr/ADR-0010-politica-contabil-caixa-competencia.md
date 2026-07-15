# ADR-0010 — Politica contabil: caixa canonica, competencia derivada

- **Status:** Accepted (2026-07-15, plano Fase 2 rev. 3 aprovado pelo responsavel do produto)
- **Contexto:** parcelamento tem multiplas fontes de verdade (P1-3): dashboard/relatorio usam
  `valorEfetivo` (valorParcela se parcelado) na data da transacao; projecao usa vencimentos de
  `parcelas` + faturas; cronograma bifurca entre `Parcela` e `FaturaLancamento`. Numeros
  individualmente corretos discordam na tela.
- **Decisao:**
  - **caixa e a politica canonica do ledger**: movimento na data em que o dinheiro se move;
  - **competencia e visao derivada** para relatorios e orcamento: no cartao, competencia = data da
    compra (via `FaturaLancamento`); caixa = pagamento da fatura;
  - servico canonico de cronograma oferece tres visoes nomeadas — **compra, competencia e caixa**
    — consumidas por dashboard, relatorio, projecao e cronograma;
  - nenhum numero exibido mistura politicas sem rotulo explicito (cada valor informa politica e
    data de referencia);
  - `FaturaLancamento` e o cronograma unico do cartao: contract V27 (equivalencia
    `Parcela` <-> `FaturaLancamento` validada por soma e por parcela) e promovido apos dry-run;
  - parcelamento nunca e inferido por campos opcionais divergentes.
- **Consequencias:** elimina a bifurcacao de leitura; mes fechado ou regra historica nunca e
  reescrito silenciosamente. Rollover de fatura (divida de cartao) permanece distinto de rollover
  de orcamento (ADR-0014, implementacao Fase 4).
