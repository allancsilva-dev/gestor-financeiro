# ADR-0011 — Investimentos: custodia, posicoes e vinculo obrigatorio com caixa

- **Status:** Accepted (2026-07-15, plano Fase 2 rev. 3 aprovado pelo responsavel do produto)
- **Contexto:** `Ativo`/`MovimentacaoAtivo` nao tem FK para caixa; `integrarCaixa` so registra
  movimento se `carteiraId` for informado (volatil). Ativos nao entram em nenhum saldo ou
  patrimonio. Posicao pode mudar sem o caixa correspondente.
- **Decisao:**
  - conta financeira de subtipo **CUSTODIA** agrupa posicoes; sem saldo monetario (saldo=0
    tecnico, ADR-0008); valor investido = quantidade x ultima cotacao valida na data de
    referencia, com fonte e instante da cotacao explicitos;
  - **operacao real de investimento exige conta de caixa** origem/destino e liga movimento de
    caixa e `MovimentacaoAtivo` na mesma operacao (ADR-0009): compra = conversao patrimonial
    (nunca despesa de consumo); venda credita caixa; dividendo credita caixa como receita de
    investimento sem alterar quantidade; bonificacao nao movimenta caixa;
  - legado: movimentacao com movimento origem=INVESTIMENTO existente e conciliada; sem movimento
    vira **snapshot EXTERNO, explicitamente nao conciliado** — importacao nunca inventa movimento
    de caixa ausente;
  - cotacao manual datada nesta fase; cotacao automatica fora de escopo;
  - investimento nunca compoe saldo disponivel; entra apenas em Investido/Patrimonio (ADR-0013).
- **Consequencias:** comprar/vender ativo nao distorce despesa, caixa ou patrimonio; taxa,
  imposto, lucro e prejuizo ganham tratamento explicito na implementacao dos lancamentos.
