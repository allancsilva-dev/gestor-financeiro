# ADR-0013 — Metricas oficiais do produto (9)

- **Status:** Accepted (2026-07-15, plano Fase 2 rev. 3 aprovado pelo responsavel do produto)
- **Contexto:** nao existe patrimonio liquido nem "disponivel para gastar" em nenhum service; o
  unico saldo real e `SUM(carteiras.saldo)`. O doc mestre define 9 metricas (o glossario listava
  8; prevalecem as **9 do doc mestre**, incluindo Variacao patrimonial).
- **Decisao:** as 9 metricas oficiais, calculadas por servico unico sobre ledger + faturas +
  alocacoes + posicoes, cada uma com data de referencia, politica e drill-down ate a origem:
  - **Disponivel agora** — contas ATIVO com liquidez IMEDIATA; COFRE entra apenas quando sua
    liquidez for IMEDIATA;
  - **Reservado** — COFRE real + alocacoes virtuais;
  - **Comprometido** — obrigacoes vencidas nao pagas + obrigacoes com vencimento entre
    dataReferencia e horizonte (default: fim do mes atual); fatura/parcela distante nao entra so
    por estar aberta;
  - **Disponivel para gastar** — disponivel - reservado - comprometido, sem truncar negativos;
  - **Investido** — posicoes pela ultima cotacao valida na data de referencia;
  - **Dividas** — soma de `max(passivo, 0)`; credito de cartao (saldo negativo) nao vira divida;
  - **Resultado mensal** — competencia; exclui transferencias, reservas, investimento e pagamento
    de cartao;
  - **Patrimonio liquido** — contas ativas + investimentos - passivos assinados;
  - **Variacao patrimonial** — diferenca inicio/fim do periodo (default mes atual), com
    decomposicao (aportes, retiradas, rendimentos, preco de mercado); pode ser o ultimo
    incremento do servico, mas dentro da Fase 2.
- **Consequencias:** campos aditivos em `DashboardResumo`; endpoint `GET /v1/metricas` +
  drill-down por metrica ate operacao, lancamento, fatura, posicao ou alocacao. Criterio de
  aceite: **a mesma metrica produz o mesmo valor em toda superficie que a exibe**. Baseline
  pre-migracao (PR-F2-01) marca como NAO_CALCULAVEL as metricas impossiveis no modelo antigo —
  nenhuma formula aproximada vira verdade oficial.
