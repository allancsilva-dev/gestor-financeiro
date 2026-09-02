# Glossario Financeiro — Nexos Finanças

Vocabulario oficial do produto e do codigo. Fase 0B aprovada (2026-07-15): termos da Fase 2 abaixo
seguem ADR-0008..0015. Mantido junto com os ADRs em `docs/adr/`.

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

## Termos da Fase 2 (ADR-0008..0015)

- **Conta financeira** — entidade unica de conta (fisicamente `carteiras` promovida, ADR-0008):
  natureza ATIVO/PASSIVO, subtipo (DINHEIRO, CORRENTE, POUPANCA, PAGAMENTO, COFRE, CUSTODIA,
  CARTAO), liquidez, origem dos dados, estado de conciliacao, moeda. Saldo derivado do ledger.
- **Operacao financeira** — agrupador imutavel de 1..N lancamentos com idempotencia, hash do
  request e estorno referenciando a original (ADR-0009). Correcao nunca altera operacao
  confirmada.
- **Lancamento** — nome de dominio do `MovimentoCarteira` vinculado a uma operacao.
- **Transferencia interna** — operacao com dois lancamentos vinculados entre contas do mesmo
  usuario; nunca e receita, despesa ou resultado mensal.
- **COFRE** — conta financeira de reserva real, uma por meta (ADR-0012); invariante
  `meta.valorReservado == saldo do COFRE`.
- **Reserva virtual** — alocacao explicita sobre conta de caixa, sem lancamento; reduz apenas
  "Disponivel para gastar". Exatamente uma modalidade (COFRE_REAL/RESERVA_VIRTUAL) por meta.
- **CUSTODIA** — conta container de posicoes de investimento; saldo monetario zero tecnico, valor
  vem de quantidade x ultima cotacao valida (ADR-0011).
- **Snapshot EXTERNO** — posicao/movimentacao de investimento sem historico de caixa; permanece
  explicitamente nao conciliada; importacao nunca inventa movimento de caixa.
- **PENDENTE_CONCILIACAO** — estado de lancamento legado/importado sem movimento financeiro;
  sempre excluido de saldos e metricas conciliadas.

## Metricas oficiais (9, ADR-0013)

- **Disponivel agora** — contas ATIVO com liquidez IMEDIATA; COFRE entra apenas quando sua
  liquidez for IMEDIATA.
- **Reservado** — COFRE real + alocacoes virtuais.
- **Comprometido** — obrigacoes vencidas nao pagas + obrigacoes com vencimento entre a data de
  referencia e o horizonte (default: fim do mes atual).
- **Disponivel para gastar** — disponivel menos reservado e comprometido, sem truncar negativos.
- **Resultado mensal** — competencia; exclui transferencias, reservas, investimento e pagamento
  de cartao.
- **Investido** — posicoes pela ultima cotacao valida, com data de cotacao e liquidez explicitas.
- **Dividas** — soma de `max(passivo, 0)`; credito de cartao nao vira divida.
- **Patrimonio liquido** — contas ativas + investimentos - passivos assinados.
- **Variacao patrimonial** — diferenca de patrimonio inicio/fim do periodo, com decomposicao
  (aportes, retiradas, rendimentos, preco de mercado).

## Experiencia simples (Fase 3)

- **Compromissos proximos** — `GET /v1/compromissos?ate=`: itens FATURA/PARCELA (status
  COMPROMETIDO, compoem exatamente a metrica oficial Comprometido, calculo compartilhado com
  `MetricasService`) e CONTA_FIXA (status PREVISTO, exibida separada e fora do total). Item pode
  carregar alerta `FALHA_SALDO` da recorrencia automatica.
- **Previsto** — conta fixa ainda nao lancada dentro do horizonte; nunca soma no Comprometido e
  nunca deve aparentar estar incluida no total.
- **Sugestao de categoria** — `GET /v1/transacoes/sugestao-categoria`: deterministica; prioridade
  para ultima transacao de descricao normalizada igual, depois categoria mais usada em 90 dias
  para o mesmo tipo, empate por menor ID; `criterio: NENHUMA` sem resultado. Nao cria categoria
  nem altera lancamento; nao substitui regras persistentes da Fase 4.
- **Navegacao de origem (drill-down)** — cada `Origem` de metrica pode trazer
  `navegacao { destino, id, filtros }` fornecida pelo backend (destinos: EXTRATO_CONTA, TRANSACAO,
  FATURA, META, INVESTIMENTO, TRANSACOES). Origem sem destino exato e informativa: o cliente
  nunca inventa link aproximado nem exibe affordance de clique.
- **Modalidade da meta** — escolha obrigatoria COFRE_REAL/RESERVA_VIRTUAL na criacao e imutavel
  depois, inclusive com reserva zerada (endurecimento da ADR-0012 no PR-F3-11).

## Conectores regulados (Fase 6 — ADR-0019..0021)

- **Conector de dados financeiros** — implementacao de `FinancialDataConnector`. Le bytes de um
  `ImportSource` e emite `CanonicalImportRecord`. Nunca abre conexao de rede (ADR-0019).
- **Snapshot NDJSON** — materializacao deterministica da resposta paginada do parceiro em arquivo
  temporario, com envelope na primeira linha (instituicao, conta, janela, saldos). Mesma janela
  buscada duas vezes produz os mesmos bytes e o mesmo `file_sha256`.
- **Conexao** — vinculo do titular com uma instituicao atraves de um provedor. Tem status proprio
  (`PENDENTE`, `ATIVA`, `EXPIRADA`, `REVOGADA`, `ERRO`, `DESVINCULADA`).
- **Consentimento** — autorizacao com escopo e prazo dada pelo titular a instituicao. E append-only:
  revogar muda status, nunca apaga linha (ADR-0020).
- **Revogacao** — encerra consentimento e apaga credencial, no sistema e no parceiro. **Nao** e
  exclusao: as transacoes ja lancadas continuam no ledger.
- **Conta conectada** — conta ou cartao do parceiro vinculado a uma Carteira (ou Conta de cartao).
  Uma Carteira nunca recebe duas conexoes ativas.
- **Sincronizacao incremental** — busca periodica de uma janela de transacoes, com sobreposicao
  deliberada de alguns dias porque banco publica com atraso.
- **Cursor de sincronizacao** — marcador opaco do parceiro que define onde a proxima janela comeca.
  So avanca depois que o lote correspondente e criado com sucesso.
- **Backfill** — carga inicial do historico, fatiada em janelas mensais para tras.
- **Fato efetivado** — lancamento ja liquidado. So ele e ingerido; pendente e autorizacao nao entram
  (ADR-0021, coerente com a caixa canonica do ADR-0010).
- **Saldo contabil / saldo disponivel** — o parceiro publica os dois. A conciliacao usa o
  **contabil**; a diferenca entre eles e o diagnostico de pendentes ainda nao efetivados.
- **Origem do lote** — `UPLOAD` (titular enviou arquivo) ou `CONNECTOR` (sincronizacao
  automatica). Separada do formato de proposito: um conector futuro pode entregar CSV de verdade, e
  ai so a origem distingue.
- **Instituicao canonica** — linha unica do catalogo para a qual todos os nomes do mesmo banco
  convergem (codigo de OFX, codigo de agregador, alias). Sem ela a deduplicacao compara texto livre
  e o mesmo fato entra duas vezes por rotas diferentes.
- **Duplicado em revisao** (`DUPLICATE_PENDING_BATCH`) — ja existe em outro lote seu que ainda
  espera revisao. Marcar nao e bloquear: o titular pode aprovar na previa se forem dois fatos reais.
- **Duplicado revertido** (`DUPLICATE_REVERSED`) — voce ja desfez este lancamento antes; a
  ressincronizacao nao o traz de volta sozinha.
- **Provedor** — parceiro de dados financeiros (agregador, acesso direto ou o fake deterministico
  de teste). Endpoint e segredo vivem em property; o banco guarda so o `config_ref`.
- **Ajuste de conciliacao** — lancamento explicito do titular, com descricao obrigatoria, para
  fechar divergencia que a ressincronizacao nao resolveu. Nunca e feito pelo sistema.

## Regras de ouro (resumo executivo)

1. Backend e a unica fonte de regra financeira; clientes apresentam (ADR-0001).
2. Nenhum dado financeiro desaparece por arquivamento, conclusao ou deploy (ADR-0004).
3. Datas de negocio usam o timezone de negocio via `Clock` injetado (ADR-0003).
4. Exclusao LGPD remove todos os dados do titular, e somente do titular (ADR-0007).
5. Backup e criptografado, off-host e com restore comprovado (ADR-0006).
6. Fonte externa entra sempre pelo pipeline canonico de importacao; nunca ha um segundo caminho ate
   o ledger (ADR-0019).
7. Automacao nunca grava operacao definitiva sobre duvida: lote com invalido, pendente de revisao,
   duplicado heuristico ou saldo divergente fica retido para revisao humana (ADR-0021).
