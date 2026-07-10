# Relatorio de Revisao — Fluxo de compra no cartao de credito + parcelas

**Arquivo:** 2026-07-09_backend_review_fatura-cartao-fluxo.md

---

## Objetivo

Revisar o fluxo de compra no cartão de crédito e parcelamento introduzido pelo commit `69e3a3b`
("feat(faturas): add card purchase flow") para verificar consistência entre transação, lançamentos de
fatura (`FaturaLancamento`), parcelas legadas (`Parcela`) e o limite consumido do cartão
(`Conta.valorGasto`).

## Escopo verificado

- Registro de compra no cartão (parcelada e à vista): `FaturaService.registrarCompraCartao`.
- Edição de transação de compra no cartão: `TransacaoService.atualizar`.
- Cancelamento/estorno de compra no cartão: `TransacaoService.deletar`, `FaturaService.cancelarCompraCartao`.
- Cálculo e exibição do valor da fatura: `FaturaService.toResponse`, `FaturaService.pagarFatura`.
- Derivação de status da fatura (ABERTA/FECHADA/VENCIDA/PAGA): bloco final de `FaturaService`.
- Exibição de status de fatura no mobile (`mobile/app/(app)/more/faturas.tsx`) e no frontend web
  (`frontend/src/pages/Faturas.tsx`).
- Ajuste de `valorGasto` de `Conta` e `Categoria` em criação/edição/exclusão de transação
  (`TransacaoService`).

## Arquivos lidos

- `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`
- `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- `backend/src/test/java/com/gestor/financeiro/FaturaCartaoWorkflowTest.java`
- `mobile/app/(app)/more/faturas.tsx`
- `frontend/src/pages/Faturas.tsx`

## Comandos executados

| Comando | Resultado |
|---|---|
| `git diff HEAD -- backend/.../FaturaService.java` | Confirmado diff com as 4 correções (arredondamento, edição, retroativa, status FECHADA, fonte da verdade valorTotal) |
| `git diff HEAD -- backend/.../TransacaoService.java` | Confirmado diff com correções de arredondamento, ressincronização em `atualizar()` e guarda `SAIDA` em `valorGasto` |
| `git diff HEAD -- backend/.../FaturaCartaoWorkflowTest.java` | Confirmados 3 novos testes: arredondamento, edição ressincroniza, compra retroativa |
| `git diff HEAD -- mobile/app/(app)/more/faturas.tsx frontend/src/pages/Faturas.tsx` | Confirmado label `FECHADA`/`Fechada` adicionado nos badges de status |
| `cd backend && ./mvnw -o test` (modo offline) | `Tests run: 76, Failures: 0, Errors: 0` — `BUILD SUCCESS` |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | HIGH | Arredondamento HALF_UP de parcelas deixava resíduo de centavo permanente em `Conta.valorGasto` após quitação total das faturas (ex.: 100/3 = 33,33×3 = 99,99) | `FaturaService.java:161-181` (`registrarCompraCartao`), `TransacaoService.java:138-166` (`criarParcelas`) — ver PROB-0038 |
| 2 | HIGH | Editar valor/data de compra de cartão não recriava lançamentos de fatura nem ajustava `valorGasto` — fatura e limite ficavam permanentemente dessincronizados da transação real | `TransacaoService.java:172-226` (`atualizar`) — ver PROB-0039 |
| 3 | HIGH | Compra retroativa podia ser lançada em fatura já com status `PAGA`, sem checagem de status antes do lançamento | `FaturaService.java:161-181` (`registrarCompraCartao`, antes da correção não chamava nenhum helper de disponibilidade) — ver PROB-0040 |
| 4 | MEDIUM | Status `FECHADA` da fatura nunca era derivado/exibido, mesmo com `dataFechamento` já passada | `FaturaService.java:376-389` (bloco de derivação de status), `mobile/app/(app)/more/faturas.tsx:196`, `frontend/src/pages/Faturas.tsx:168` — ver PROB-0041 |
| 5 | MEDIUM | `pagarFatura`/`toResponse` priorizavam `fatura.getValorTotal()` persistido (sujeito a dessincronia) em vez da soma real dos lançamentos, gerando falso erro "Pagamento parcial de fatura ainda não é suportado" | `FaturaService.java:85-140` (`pagarFatura`), `FaturaService.java:247-282` (`toResponse`) — ver PROB-0042 |
| 6 | MEDIUM | Transação `ENTRADA` com `Conta` associada incrementava `valorGasto` (limite do cartão) da mesma forma que `SAIDA` | `TransacaoService.java:101-104`, `206-215`, `253` — ver PROB-0043 |

## O que foi corrigido

Todos os 6 achados foram corrigidos no mesmo ciclo de trabalho (working tree, ainda não commitado):

1. Última parcela/lançamento absorve a diferença de arredondamento (helper `valorParcelaOuResto` em
   `TransacaoService`; lógica equivalente inline em `FaturaService.registrarCompraCartao`).
2. `TransacaoService.atualizar()` cancela e recria lançamentos de fatura via
   `faturaService.cancelarCompraCartao`/`registrarCompraCartao` quando valor ou data de compra de cartão
   muda; ajusta `valorGasto` de conta/categoria pela diferença; recalcula parcelas legadas
   (`atualizarValorParcelas`).
3. Novo helper `faturaDisponivelParaLancamento` rola a competência até 24 meses à frente procurando
   fatura não paga.
4. Branch de derivação de `FaturaStatus.FECHADA` adicionado quando `dataFechamento` já passou; labels de
   UI adicionados no mobile e no frontend.
5. `pagarFatura`/`toResponse` passam a usar a soma dos `FaturaLancamento` como fonte da verdade, com
   fallback ao `valorTotal` persistido apenas para faturas pré-migration V17 sem lançamentos.
6. Guarda `transacao.getTipo() == TipoTransacao.SAIDA` adicionada antes de toda chamada a
   `contaService.adicionarGasto`/`removerGasto`.

3 novos testes adicionados em `FaturaCartaoWorkflowTest`:
`ultimaParcelaAbsorveArredondamentoELimiteZeraAposPagarTodasAsFaturas`,
`editarValorDeCompraNoCartaoRessincronizaFaturaELimite`,
`compraRetroativaNaoEntraEmFaturaPagaVaiParaProximaAberta`.

Suite completa validada nesta revisão: `./mvnw -o test` (modo offline) → `Tests run: 76, Failures: 0,
Errors: 0` — `BUILD SUCCESS`.

## O que ficou pendente

- Achado #4 (status FECHADA) e #6 (ENTRADA + valorGasto) não têm teste automatizado dedicado — cobertura
  apenas por revisão manual de código (ver BUG-0020, BUG-0022 em `BUGFIX_LOG.md`, marcados
  `PASS_COM_RESSALVA`).
- Achado #5 (fonte da verdade do valorTotal) não tem teste dedicado ao caso específico de fatura
  pré-migration V17 sem `FaturaLancamento` (fallback só verificado por leitura de código).
- Sem backfill para compras parceladas já persistidas antes desta correção que possam ter resíduo de
  arredondamento antigo (ver BACKLOG-0051).
- Redundância entre tabela `Parcela` (legada) e `FaturaLancamento` não foi resolvida — ambas precisaram
  ser corrigidas separadamente para o mesmo bug de arredondamento (ver BACKLOG-0050).
- Decisão de manter pagamento parcial de fatura bloqueado por design não está formalmente registrada em
  `SYSTEM_OVERVIEW.md` além desta revisão (ver BACKLOG-0049).
- Nenhuma alteração foi commitada nesta sessão — todas as mudanças permanecem no working tree.

## Recomendacao final

As 4 correções principais (arredondamento, ressincronização de edição, compra retroativa, status
FECHADA) e as 2 correções extras (fonte da verdade do valorTotal, guarda ENTRADA/valorGasto) resolvem
inconsistências financeiras reais e verificáveis no fluxo de cartão de crédito. A suíte de testes backend
passa integralmente (76/76). Recomenda-se: (1) revisar o diff completo antes de commit, dado que o
working tree também contém mudanças não relacionadas a fatura (`ContaController`, `ContaRequest`,
`Conta.java`, telas mobile de carteiras/categorias/contas-fixas/orçamentos/relatórios — aparentemente de
outra frente de trabalho, campo `banco` em `Conta`); (2) adicionar os testes automatizados pendentes
listados acima antes de considerar o fluxo definitivamente fechado; (3) avaliar os itens de backlog
registrados (BACKLOG-0049 a BACKLOG-0051).

## Status final (rodada 1)

> PASS_COM_RESSALVA — correções verificadas via diff e suíte de testes (76/76 PASS); ressalvas de
> cobertura de teste e itens de backlog documentados acima.

---

## Atualização (revisão 2, mesma sessão, 2026-07-09) — modelo de ajuste/estorno substitui bloqueios

### O que motivou a segunda rodada

Após o fechamento da rodada 1, os bloqueios registrados como "solução aplicada" para edição/cancelamento
de compra com fatura paga (`BusinessException` em `PROB-0039`, comportamento pré-existente em
`cancelarCompraCartao(Transacao)` para cancelamento) se mostraram uma limitação funcional real: usuário
não conseguia corrigir valor nem cancelar compra parcelada após a primeira fatura ser paga. O time de
implementação substituiu esse modelo por um definitivo, ainda na mesma sessão e antes de qualquer commit.

### Escopo adicional verificado

- `FaturaService.ressincronizarCompraCartao` (novo método) e `FaturaService.cancelarCompraCartao`
  (assinatura alterada para `(Transacao, Long usuarioId)`).
- Novos helpers privados `criarLancamento`, `removerLancamentoDeFaturaAberta`, `ajustarLimiteUtilizado`.
- Novo enum `TipoFaturaLancamento` (`COMPRA`, `AJUSTE`, `ESTORNO`) e coluna `tipo` em
  `fatura_lancamentos` (`V18__fatura_lancamento_tipo.sql`).
- `TransacaoService.atualizar`/`deletar`: chamadas atualizadas para os novos métodos de `FaturaService`;
  `contaService.adicionarGasto`/`removerGasto` deixou de ser chamado para compras de cartão.
- UI: `mobile/app/(app)/more/faturas.tsx`, `frontend/src/pages/Faturas.tsx` (cor condicional por sinal do
  valor), `mobile/src/types/index.ts`, `frontend/src/services/faturaService.ts`,
  `backend/.../dto/FaturaLancamentoDto.java` (campo `tipo`).

### Comandos executados (rodada 2)

| Comando | Resultado |
|---|---|
| `git diff HEAD -- backend/.../FaturaService.java` (estado atualizado) | Confirmado `ressincronizarCompraCartao`, `cancelarCompraCartao` nova assinatura, helpers `criarLancamento`/`removerLancamentoDeFaturaAberta`/`ajustarLimiteUtilizado` |
| `git diff HEAD -- backend/.../TransacaoService.java` (estado atualizado) | Confirmado fim das chamadas diretas a `adicionarGasto`/`removerGasto` para compra de cartão |
| `git diff HEAD -- backend/.../FaturaCartaoWorkflowTest.java` (estado atualizado) | Confirmados 2 novos testes: `editarCompraJaPagaGeraLancamentoDeAjusteNaProximaFatura`, `cancelarCompraParceladaComFaturaPagaGeraEstornoNaProximaFatura` |
| `cat backend/.../V18__fatura_lancamento_tipo.sql` | Confirmada migration: `ALTER TABLE fatura_lancamentos ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'COMPRA'` |
| `cd backend && ./mvnw -o test` (modo offline) | `Tests run: 78, Failures: 0, Errors: 0` — `BUILD SUCCESS` |

### Achados adicionais (rodada 2)

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 7 | MEDIUM (resolvido) | Bloqueio de edição/cancelamento de compra com fatura paga era funcionalmente limitante — usuário não conseguia corrigir/cancelar compra parcelada após primeira fatura paga | `FaturaService.java` (`cancelarCompraCartao` original lançava `BusinessException`) — ver PROB-0044 |
| 8 | LOW | `Conta.valorGasto` pode ficar temporariamente negativo (crédito de estorno maior que compras em aberto) sem tratamento de UX dedicado | Ver BACKLOG-0053 |
| 9 | LOW | Fatura contendo apenas estorno (total ≤ 0) não é "pagável"; sem rollover explícito de crédito entre faturas | Ver BACKLOG-0054 |
| 10 | LOW | Redistribuição de parcelas na edição usa "restante ÷ parcelas não pagas", não recalcula parcela cheia | Ver BACKLOG-0055 |

### O que foi corrigido (rodada 2)

Bloqueio de edição/cancelamento de compra com fatura paga substituído por modelo de compensação
automática (lançamento `AJUSTE`/`ESTORNO` na próxima fatura em aberto), com invariante de limite
centralizado em `FaturaService`. Ver `BUGFIX_LOG.md` BUG-0023 a BUG-0026 e `PROBLEM_LEDGER.md` PROB-0044
para detalhes completos.

### O que ficou pendente (rodada 2)

- UX para `valorGasto` negativo (BACKLOG-0053), rollover de crédito entre faturas só-estorno
  (BACKLOG-0054), recálculo de parcela cheia na redistribuição (BACKLOG-0055) — todos registrados como
  decisões de produto pendentes, não bugs.
- BUG-0026 (UI de cor condicional) marcado `NAO_EXECUTADO` em testes automatizados — sem suíte e2e/component
  para telas de fatura no mobile ou frontend.
- Claim de "typecheck mobile limpo" e "erros TS do frontend pré-existentes fora dos arquivos de fatura"
  relatado pelo agente de implementação, não reexecutado/verificado independentemente pelo `docs-reporter`
  nesta rodada.

### Recomendacao final (atualizada)

O modelo de compensação (fatura paga imutável, ajuste/estorno na próxima fatura aberta) é uma evolução de
design consistente com o comportamento de estorno de cartão de crédito real, resolve uma limitação
funcional genuína identificada na rodada 1, e está coberto por 2 novos testes automatizados. Suíte
completa passa integralmente (78/78). Mantêm-se as recomendações da rodada 1 quanto a revisar o diff
completo antes do commit (mudanças de outra frente de trabalho ainda presentes no working tree) e quanto
aos itens de backlog, agora também incluindo BACKLOG-0053/0054/0055.

## Status final (rodada 2)

> PASS_COM_RESSALVA — modelo de ajuste/estorno verificado via diff e suíte de testes (78/78 PASS);
> ressalvas de UX (valorGasto negativo), rollover de crédito e cobertura de teste de UI documentadas acima.

---

## Atualização (revisão 3, mesma sessão, 2026-07-09) — complemento mobile do módulo de fatura/cartão

### O que motivou a terceira rodada

Verificação de integração do módulo de faturas/cartão especificamente no app mobile (Expo) — distinta das
duas rodadas anteriores, que focaram no backend e na exibição de fatura no mobile/web, mas não cobriram o
fluxo de edição/exclusão de transação nem o detalhamento visual de lançamentos de ajuste/estorno no
mobile. A verificação apontou 3 lacunas, todas implementadas nesta mesma sessão, antes de qualquer commit.

### Escopo adicional verificado

- `mobile/app/(app)/transacoes.tsx` e novo componente `mobile/src/components/EditarTransacaoModal.tsx`.
- `mobile/app/(app)/more/faturas.tsx` (badge de status da fatura e badge de tipo de lançamento).
- Contrato de `PUT`/`DELETE /api/v1/transacoes/{id}` contra o backend local (porta 8081), com payloads
  exatos do app.
- Paridade com `frontend/src/pages/Faturas.tsx` para o badge de tipo de lançamento.

### Arquivos lidos (rodada 3)

- `mobile/src/components/EditarTransacaoModal.tsx` (novo)
- `mobile/app/(app)/transacoes.tsx`
- `mobile/app/(app)/more/faturas.tsx`
- `mobile/src/types/index.ts`
- `frontend/src/pages/Faturas.tsx`

### Comandos executados (rodada 3)

| Comando | Resultado |
|---|---|
| `git diff -- mobile/app/\(app\)/transacoes.tsx` | Confirmado `onPress` na linha da lista abrindo `EditarTransacaoModal`, subtítulo com `· Nx` para parcelas |
| `wc -l mobile/src/components/EditarTransacaoModal.tsx` | 171 linhas — componente novo, não commitado (`git status` mostra `??`) |
| `git diff -- mobile/app/\(app\)/more/faturas.tsx` | Confirmada constante `statusBadge` (4 cores) e cálculo de `tipoBadge`/remoção de prefixo por lançamento |
| `git diff -- mobile/src/types/index.ts` | Confirmado campo `tipo: 'COMPRA' \| 'AJUSTE' \| 'ESTORNO'` em `FaturaLancamento` (herdado de BUG-0026, sem mudança nesta rodada) |
| `git diff -- frontend/src/pages/Faturas.tsx` | Confirmado que o web só tem a cor condicional do valor (BUG-0026) — sem badge de tipo nem remoção de prefixo, diferente do mobile |
| Validação manual de contrato contra backend local (porta 8081), payloads exatos do app | `POST` compra 3x → `201`; `PUT` com corpo do modal → `200`; `DELETE` → `204`; usuário de teste `teste-fatura-ui@teste.com` criado e dados de transação removidos após o teste |
| Leitura de `backend/target/surefire-reports/` (suíte completa executada às 22:03, antes desta rodada) | 78 testes, 0 falhas no agregado dos reports; `FaturaCartaoWorkflowTest` reexecutado às 22:23 nesta rodada → 7/7 PASS (suite backend não alterada nesta rodada) |

### Achados adicionais (rodada 3)

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 11 | HIGH (resolvido) | Mobile não tinha nenhuma forma de editar/excluir transação | `mobile/app/(app)/transacoes.tsx` (sem `onPress` antes desta rodada), novo `EditarTransacaoModal.tsx` — ver PROB-0045 |
| 12 | LOW (resolvido) | Badge de status da fatura no mobile era binário (PAGA verde / resto vermelho), tratando `ABERTA` como alerta | `mobile/app/(app)/more/faturas.tsx` — ver PROB-0046 |
| 13 | LOW (resolvido) | Lançamentos `AJUSTE`/`ESTORNO` sem indicador visual dedicado (só prefixo textual + cor) | `mobile/app/(app)/more/faturas.tsx` — ver PROB-0047 |
| 14 | MEDIUM (documentado; reinício a cargo do usuário) | Backend local (porta 8081) servindo build defasado durante a validação manual de contrato — processo não foi reiniciado na sessão | Observação direta durante a sessão (JVM iniciada 08:17, classes recompiladas 22:00) — ver PROB-0048 |
| 15 | LOW (não resolvido, registrado como backlog) | Badge de tipo de lançamento não replicado no frontend web — divergência de paridade mobile/web | `git diff -- frontend/src/pages/Faturas.tsx` — ver BACKLOG-0057 |

### O que foi corrigido (rodada 3)

1. Novo `mobile/src/components/EditarTransacaoModal.tsx`: edita valor/descrição/data/observações (únicos
   campos aplicados pelo backend), com aviso de ressincronização de fatura para compra de cartão e
   confirmação de exclusão com texto específico para estorno. Integrado a `transacoes.tsx` (toque na linha
   abre o modal; subtítulo mostra `· Nx` quando parcelado).
2. Badge de status da fatura em `faturas.tsx` (mobile) passou a usar 4 cores semânticas em vez de
   binário.
3. Lançamentos `AJUSTE`/`ESTORNO` ganharam chip de tipo e tiveram o prefixo textual removido da exibição
   (mantendo a descrição original intacta na API).

Ver `docs/BUGFIX_LOG.md` BUG-0027 a BUG-0029 e `docs/PROBLEM_LEDGER.md` PROB-0045 a PROB-0048 para
detalhes completos.

### O que ficou pendente (rodada 3)

- Nenhum teste automatizado cobre `EditarTransacaoModal` nem os badges (mobile sem suíte de UI
  configurada) — validação foi `tsc --noEmit` + leitura de diff + um teste manual de contrato único.
- Badge de tipo de lançamento não replicado no frontend web (BACKLOG-0057).
- Risco de ambiente (backend local defasado, PROB-0048) é recorrente e não tem mitigação automática
  (`spring-boot-devtools` não avaliado/instalado nesta sessão — fora do escopo de `docs-reporter`).
- Mensagem de erro confusa de "pagamento parcial não suportado" por corrida entre fetch e toque em "Pagar
  Fatura" identificada como item de UX separado, não corrigida nesta rodada (BACKLOG-0056).
- Nenhuma alteração foi commitada nesta sessão — todas as mudanças permanecem no working tree, junto com
  mudanças de outra frente de trabalho já sinalizada nas rodadas anteriores (campo `banco` em `Conta`,
  migration `V16__conta_banco.sql`, ainda não documentada por este agente por estar fora do escopo desta
  tarefa).

### Recomendacao final (atualizada, rodada 3)

As 3 correções mobile fecham uma lacuna funcional real (mobile sem edição/exclusão de transação é HIGH,
dado que compras de cartão parceladas frequentemente precisam de correção/cancelamento) e duas melhorias
de clareza visual de baixo risco. Validação de contrato foi manual (não automatizada), mas cobriu os três
verbos HTTP relevantes (`POST`/`PUT`/`DELETE`) com payloads reais do app contra o backend local — porém
contra um processo com build defasado (identificado durante a própria validação; reinício ficou a cargo
do usuário), de modo que ela comprova o contrato HTTP, e o comportamento do código atual foi comprovado
pela suíte de testes (`FaturaCartaoWorkflowTest` 7/7). Recomenda-se: (1) revisar o
diff completo antes de commit, incluindo mudanças de outra frente de trabalho ainda presentes no working
tree; (2) considerar `spring-boot-devtools` para reduzir recorrência do risco de build defasado em
desenvolvimento local; (3) tratar BACKLOG-0056 (retry de pagamento de fatura) e BACKLOG-0057 (paridade
web do badge de tipo) como itens de UX de prioridade P2/P3.

## Status final (rodada 3)

> PASS_COM_RESSALVA — 3 lacunas mobile corrigidas e verificadas via diff + validação manual de contrato
> (POST/PUT/DELETE); ressalvas de cobertura de teste automatizado mobile, paridade web pendente
> (BACKLOG-0057) e risco operacional de ambiente local documentado (PROB-0048).

---

> Relatorio gerado pelo `docs-reporter` em 2026-07-09 (rodada 1), atualizado no mesmo dia (rodada 2) e
> novamente atualizado no mesmo dia (rodada 3, complemento mobile). Nenhum arquivo de codigo de aplicacao
> foi alterado durante esta documentacao.
