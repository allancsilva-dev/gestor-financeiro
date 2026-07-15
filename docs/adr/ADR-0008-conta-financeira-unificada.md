# ADR-0008 — Conta financeira unificada

- **Status:** Accepted (2026-07-15, plano Fase 2 rev. 3 aprovado pelo responsavel do produto)
- **Contexto:** `Conta` e `Carteira` representam conceitos sobrepostos (P1-1). `Carteira` e a unica
  fonte de caixa real (saldo materializado + ledger `MovimentoCarteira` + reconciliacao); `Conta`
  so importa como cartao de credito (`limiteTotal`/`valorGasto`); `Conta.saldoAtual` e campo morto.
  O mobile ja exibe Carteira como "Conta".
- **Decisao:** existe uma unica entidade de conta financeira no dominio, construida **promovendo
  `Carteira`** (nunca entidade nova):
  - as tabelas fisicas `carteiras` e `movimentos_carteira` permanecem; dominio e API passam a
    chama-las **conta financeira** e **lancamento** (`/api/v1/contas-financeiras`, aditivo);
  - colunas novas: `natureza` (ATIVO/PASSIVO), `subtipo` (DINHEIRO, CORRENTE, POUPANCA, PAGAMENTO,
    COFRE, CUSTODIA, CARTAO), `liquidez` (IMEDIATA, D1, D2, CARENCIA, BLOQUEADA), `origem_dados`
    (MANUAL, CSV, OFX, INTEGRACAO, AJUSTE), `estado_conciliacao`, `moeda` (BRL unica nesta fase);
  - **CARTAO** e conta de natureza PASSIVO com ledger proprio (ADR-0009); a entidade `Conta` vira
    configuracao interna de cartao, **1:1** com a conta financeira via FK unica; tabela permanece
    nesta fase e a API legada sai apenas no contract (PR-F2-19);
  - **CUSTODIA** nao possui saldo monetario: usa `saldo = 0` tecnico (coluna NOT NULL) com
    constraint garantindo zero; valor vem de posicoes/cotacoes (ADR-0011); a invariante de saldo
    do ledger aplica somente a CAIXA, COFRE e CARTAO;
  - `Conta.saldoAtual` e tipos DEBITO/DINHEIRO/POUPANCA de `Conta` sao declarados mortos: criacao
    nova bloqueada, dados remanescentes migrados para contas financeiras, campo removido do
    contrato no PR-F2-19;
  - rename fisico de tabela e adiado (cosmetico e arriscado); se vier, sera por view em ADR novo.
- **Consequencias:** clientes migram por contratos aditivos (mobile impacto medio, web alto);
  `DashboardResumo` so ganha campos. Emprestimo/financiamento fica declarado como subtipo futuro,
  sem fluxo nesta fase. Multi-moeda fora de escopo.
