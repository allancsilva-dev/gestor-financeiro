# Glossario Financeiro — Nexos Finanças

Vocabulario oficial do produto e do codigo, no estado atual do sistema. Termos novos de Fase 2+
(FinancialAccount, LedgerEntry etc.) so entram aqui apos ADRs da Fase 0B. Mantido junto com os ADRs
em `docs/adr/`.

## Entidades e conceitos atuais

- **Carteira** — recipiente de dinheiro do usuario com saldo materializado e ledger proprio
  (`MovimentoCarteira`). No mobile e exibida como "Conta". E a fonte de verdade de caixa hoje.
- **Conta** — registro de conta bancaria/cartao com campos de saldo (`saldoAtual`) que quase nao
  participam das regras. Sobrepoe conceito de Carteira; unificacao e escopo da Fase 2 (ADR futuro,
  Fase 0B).
- **ContaFixa** — compromisso recorrente planejado (assinatura, salario, aluguel). Tem campo `tipo`
  (`ENTRADA` ou `SAIDA`). Default historico e `SAIDA`; renda e uma ContaFixa de `ENTRADA`.
- **Renda** — ContaFixa de tipo `ENTRADA` criada no onboarding (ou manualmente) representando
  receita recorrente do usuario. Deve usar categoria "Renda".
- **ExecucaoRecorrencia** — registro de cada disparo do scheduler para uma ContaFixa: realizada,
  pulada ou falha por saldo. Referencia usuario, conta fixa e (opcionalmente) a transacao gerada.
- **Transacao** — lancamento financeiro (entrada ou saida) do usuario. Pode ou nao movimentar
  Carteira (lacuna P1-2, escopo Fase 2).
- **Parcela** — fracao de uma compra parcelada fora do cartao, com status de pagamento proprio.
- **FaturaCartao / FaturaLancamento** — fatura mensal de cartao de credito e seus lancamentos.
  Possui rollover de fatura (saldo devedor/credito levado a proxima fatura) — nao confundir com
  rollover de orcamento, que nao existe ainda.
- **Meta** — objetivo de poupanca com valor reservado debitado de Carteira via `MovimentoMeta`.
  Ciclo de vida: `ATIVA` → `CONCLUIDA` (reserva atinge objetivo) ou `ARQUIVADA` (exclusao sem
  reserva). Conclusao nunca oculta o valor reservado (ADR-0004).
- **valorReservado** — dinheiro ja debitado da carteira e alocado a uma Meta. Continua sendo
  patrimonio do usuario; nunca pode sumir da visao por conclusao/arquivamento.
- **MovimentoCarteira** — ledger da Carteira (origem, tipo, valor). Fonte de auditoria de saldo.
- **MovimentoMeta** — ledger da Meta (aportes e resgates de reserva).
- **OrcamentoMensal / OrcamentoCategoria** — limites de gasto por mes e por categoria.
- **Ativo / MovimentacaoAtivo** — posicao de investimento e suas movimentacoes. Nao compoe saldo
  disponivel (regra alvo; consolidacao na Fase 2).
- **Categoria** — dimensao analitica de transacoes. Nao e fonte de saldo.
- **Onboarding completo** — estado do usuario apos `POST /api/v1/onboarding/finalizar` (unico
  caminho canonico, ADR-0002): carteira, conta, categorias, renda e meta iniciais criadas em uma
  transacao; flag `onboardingCompleto = true`.

## Metricas (definicao alvo, implementacao progressiva)

- **Saldo disponivel** — dinheiro liquido imediatamente utilizavel nas contas de caixa.
- **Reservado** — parte do caixa alocada a metas/envelopes.
- **Comprometido** — contas e faturas futuras dentro de horizonte definido.
- **Disponivel para gastar** — disponivel menos reservado e comprometido.
- **Resultado do mes** — receitas menos despesas por politica definida; exclui transferencias
  internas e compra de investimento.
- **Investido** — valor de mercado das posicoes, com data de cotacao e liquidez explicitas.
- **Dividas** — cartoes, emprestimos e financiamentos.
- **Patrimonio liquido** — ativos menos passivos.

## Regras de ouro (resumo executivo)

1. Backend e a unica fonte de regra financeira; clientes apresentam (ADR-0001).
2. Nenhum dado financeiro desaparece por arquivamento, conclusao ou deploy (ADR-0004).
3. Datas de negocio usam o timezone de negocio via `Clock` injetado (ADR-0003).
4. Exclusao LGPD remove todos os dados do titular, e somente do titular (ADR-0007).
5. Backup e criptografado, off-host e com restore comprovado (ADR-0006).
