# ADR-0009 — Operacao financeira e ledger operacional

- **Status:** Accepted (2026-07-15, plano Fase 2 rev. 3 aprovado pelo responsavel do produto)
- **Contexto:** o ledger atual (`MovimentoCarteira` via `LedgerService`) e single-entry: cada
  movimento e isolado, sem agrupador que explique origem e destino. Transferencia, pagamento de
  fatura, reserva de meta e operacao de investimento precisam de lados vinculados. O indice
  `ux_movimentos_carteira_usuario_idempotency` permite apenas uma idempotency_key por usuario,
  impossivel para operacoes com 2+ lancamentos.
- **Decisao:** **ledger operacional unificado, nao contabilidade empresarial** (sem plano de
  contas, debito/credito generico, DRE ou double-entry pleno):
  - tabela `operacoes_financeiras`: tipo, datas, status, origem, chave idempotente (unica por
    usuario+chave **na operacao**), hash do request e referencia de estorno;
  - lancamentos 1..N por operacao: FK `operacao_id` em `movimentos_carteira`,
    `fatura_lancamentos`, `movimentacoes_ativo` e `movimentos_meta`; historico explicito de
    pagamentos de fatura;
  - idempotencia: chave repetida com payload igual retorna a operacao original; payload diferente
    retorna HTTP 409;
  - **operacao confirmada e imutavel**: correcao gera nova operacao de estorno referenciando a
    original — nunca update do conteudo financeiro;
  - transferencia = operacao com 2 lancamentos vinculados, locks em ordem determinística por id,
    contas do mesmo usuario, origem != destino, excluida de receita, despesa e resultado mensal;
  - regra de saldo insuficiente aplica so a contas ATIVO sem credito permitido;
  - legado: preencher `operacao_id` em registro antigo e enriquecimento de metadado permitido;
    valor, data efetiva e conteudo financeiro original nunca sao alterados.
- **Consequencias:** `LedgerService` evolui para registrar via operacao; a reconciliacao existente
  (`saldo == SUM(valorAssinado)`) permanece valida por conta. Toda superficie nova (transferencia,
  cartao, meta, investimento, importacao futura) entra pelo mesmo caminho — prepara Open Finance,
  automacao e IA sem segunda verdade.
