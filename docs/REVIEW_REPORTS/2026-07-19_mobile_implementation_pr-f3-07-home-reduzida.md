# Relatorio de Revisao

**Arquivo:** 2026-07-19_mobile_implementation_pr-f3-07-home-reduzida.md

**PR:** PR-F3-07 — Home reduzida mobile (Fase 3, terceiro PR do Bloco B — consumo mobile)

**Commit:** `628cf8e` (main)

---

## Objetivo

Registrar a implementacao do PR-F3-07, terceiro PR do Bloco B da Fase 3 ("Experiencia simples") no app
mobile (Expo/React Native). O PR reescreve a tela inicial (`app/(app)/index.tsx`) para reduzir a home a
uma ordem fixa de seis blocos, substitui o card de falhas de recorrencia por informacao embutida no novo
bloco de Compromissos proximos, e remove numeros/alertas de categoria do card de insights, deixando a
home com exatamente 4 requests HTTP.

## Escopo verificado

Relato de implementacao mobile consolidado apos ciclo de implementacao, complementado por leitura direta
do commit pelo `docs-reporter` (`git show --stat`, `git show` dos arquivos de suporte — ferramentas de
inspecao somente leitura, nenhuma edicao de codigo feita por este agente). Nao ha evidencia, nas
informacoes recebidas, de acionamento dedicado de `quality-reviewer`, `security-auditor` ou
`lgpd-auditor` especificamente para este PR (mesma ressalva ja registrada para PR-F3-01 a PR-F3-06).

Escopo tecnico coberto pela sessao:

- **`mobile/app/(app)/index.tsx` (reescrita, -251/+217 linhas liquidas no commit):** ordem fixa da home
  passa a ser (1) Disponivel para gastar (hero, sem os pills de Resultado mensal/Dividas que existiam
  antes); (2) Compromissos proximos (novo bloco, ver abaixo); (3) botao Lancar (abre
  `NovaTransacaoModal`); (4) cinco movimentacoes recentes com acao repetir (herdada do PR-F3-05); (5) uma
  recomendacao **textual** de insights (`recomendacoes[0]` ou resumo — numeros de insights nunca
  aparecem); (6) link "Ver visao financeira completa" (para a tela do PR-F3-06).
- **`mobile/src/services/compromissosService.ts` (novo, +14 linhas, confirmado via `git show`):**
  `listar(ate?)` chama `GET /v1/compromissos` (contrato do PR-F3-01) e retorna o payload tipado
  `Compromissos`.
- **Bloco "Compromissos proximos":** itens `COMPROMETIDO` e `PREVISTO` (contrato do PR-F3-01) exibidos em
  grupos visualmente distintos; cabeçalho do grupo `PREVISTO` traz o texto "Previstos · fora do total";
  cabeçalho da secao exibe o `totalComprometido` do payload. Item com `alerta: 'FALHA_SALDO'` (contrato
  ja preparado no PR-F3-01) exibe icone de alerta e subtitulo "Aguardando saldo" — **substitui** o request
  separado `GET /v1/contas-fixas/falhas-pendentes` que a home anterior fazia. Toque no item navega para
  faturas/contas-fixas/transacoes conforme o `tipo` (`FATURA`/`PARCELA`/`CONTA_FIXA`).
- **Requests da home reduzidos a exatamente 4:** `GET /v1/metricas`, `GET /v1/compromissos`,
  `GET /v1/transacoes/minhas` (5 itens), `GET /v1/insights`. Removidos: `GET /v1/dashboard/resumo`
  (resumo legado), `GET /v1/dashboard/projecao`, e o request de falhas pendentes de contas fixas
  (absorvido pelo alerta do item de compromisso).
- **UI removida da home:** grid de metricas, faixa KPI Receitas/Despesas/Saldo, card de insights com
  numeros e alertas de categoria, banner de falhas de recorrencia, grid de atalhos rapidos.
- **`mobile/src/components/NovaTransacaoModal.tsx` (+2 linhas, confirmado via `git show --stat`):** passa
  a invalidar os caches de `metricas` e `compromissos` (alem dos ja invalidados anteriormente) ao salvar
  um lancamento — necessario porque a home nova depende desses dois caches para nao ficar desatualizada
  apos um lancamento rapido.
- **`mobile/src/utils/format.ts` (+7 linhas, confirmado via `git show`):** nova funcao `formatDateOnlyBR`
  — converte `YYYY-MM-DD` para `DD/MM/AAAA` via split de string, sem passar por `new Date()`, evitando o
  deslocamento de fuso horario (UTC) que `new Date('YYYY-MM-DD')` introduziria em datas de vencimento.
- **`mobile/src/types/index.ts` (+22 linhas, confirmado via `git show`):** tipos novos `Compromissos`,
  `CompromissoItem`, `GrupoCompromisso` (`'COMPROMETIDO' | 'PREVISTO'`), `TipoCompromisso`
  (`'FATURA' | 'PARCELA' | 'CONTA_FIXA'`), com `alerta: 'FALHA_SALDO' | null` no item.
- **`mobile/src/__tests__/compromissosService.test.ts` (novo, +41 linhas, confirmado via `git show
  --stat`):** 3 testes, incluindo cobertura de `formatDateOnlyBR`.
- **Nota de escopo declarada pela sessao de implementacao:** `mobile/app/(app)/more/perfil.tsx` (nome
  aproximado, tela de perfil) ainda consome `GET /v1/dashboard/resumo` — a remocao global desse endpoint
  legado do cliente mobile fica fora deste PR, e e escopo do PR-F3-13.
- Sem mudanca de backend e sem migration neste PR — consome contratos ja existentes (`/v1/compromissos`
  do PR-F3-01, `/v1/metricas`, `/v1/transacoes/minhas`, `/v1/insights`).

## Arquivos lidos

- `mobile/app/(app)/index.tsx` (reescrita — lido apenas via `git show --stat`, nao via diff completo)
- `mobile/src/services/compromissosService.ts` (novo — lido integralmente via `git show`)
- `mobile/src/types/index.ts` (diff lido integralmente via `git show`)
- `mobile/src/utils/format.ts` (diff lido integralmente via `git show`)
- `mobile/src/components/NovaTransacaoModal.tsx` (lido apenas via `git show --stat`, nao via diff
  completo)
- `mobile/src/__tests__/compromissosService.test.ts` (novo — lido apenas via `git show --stat`, conteudo
  nao lido integralmente pelo `docs-reporter` nesta rodada)

## Comandos executados

Comandos reportados como executados pelo ciclo de implementacao (nao reexecutados pelo `docs-reporter`,
que nao tem permissao para rodar build/teste de `mobile/`):

| Comando | Resultado |
|---|---|
| `npx tsc --noEmit` | limpo, sem erros |
| Jest (suite mobile) | 29/29 PASS (10 suites — 1 nova em relacao ao PR-F3-06, que estava em 26/26 e 9 suites) |

Comandos de inspecao executados diretamente pelo `docs-reporter` (somente leitura, sem alterar codigo):

| Comando | Resultado |
|---|---|
| `git show 628cf8e --stat` | Confirma os 6 arquivos alterados/criados: `mobile/app/(app)/index.tsx` (382 linhas tocadas, -251/+217 liquido do resumo do commit), `mobile/src/__tests__/compromissosService.test.ts` (novo, +41), `mobile/src/components/NovaTransacaoModal.tsx` (+2), `mobile/src/services/compromissosService.ts` (novo, +14), `mobile/src/types/index.ts` (+22), `mobile/src/utils/format.ts` (+7); total 217 insercoes, 251 delecoes |
| `git show 628cf8e -- mobile/src/utils/format.ts mobile/src/services/compromissosService.ts mobile/src/types/index.ts` | Confirma o conteudo de `formatDateOnlyBR`, de `compromissosService.listar` e dos tipos `Compromissos`/`CompromissoItem`/`GrupoCompromisso`/`TipoCompromisso` exatamente como reportado pela sessao de implementacao |
| `git log --oneline -5 -- mobile/app/(app)/index.tsx` | Confirma `628cf8e` como o commit mais recente a tocar o arquivo, precedido por `0c892bc` (PR-F3-06) e `413d191` (PR-F3-05) |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | INFORMATIVO | A home passa a fazer exatamente 4 requests HTTP (`/v1/metricas`, `/v1/compromissos`, `/v1/transacoes/minhas`, `/v1/insights`), reduzindo de um numero maior de chamadas na versao anterior (resumo legado, projecao, falhas pendentes de contas fixas eram requests separados). Coerente com o objetivo de "Experiencia simples" do plano de Fase 3. | Reportado pela sessao de implementacao; nao contado de forma independente pelo `docs-reporter` (nao ha leitura de diff completo do arquivo reescrito) |
| 2 | INFORMATIVO | O alerta `FALHA_SALDO` do item de compromisso (contrato ja preparado no PR-F3-01, achado registrado naquele relatorio) e agora efetivamente consumido pelo cliente, eliminando o request separado `/v1/contas-fixas/falhas-pendentes` — fecha o loop entre o PR-F3-01 (backend) e o PR-F3-07 (mobile) exatamente como planejado no relatorio do PR-F3-01. | Reportado pela sessao de implementacao; confirmado indiretamente pela redacao do commit `628cf8e` |
| 3 | BAIXA (correcao preventiva, nao um bug reportado) | `formatDateOnlyBR` foi introduzida especificamente para evitar deslocamento de fuso horario ao formatar `vencimento` (string `YYYY-MM-DD`) — `new Date('YYYY-MM-DD')` e interpretado como UTC pelo motor JS e pode exibir o dia anterior em fusos negativos (ex.: `America/Sao_Paulo`, UTC-3). A funcao evita o problema via split de string, sem instanciar `Date`. Nao ha registro de que esse problema de fuso jamais tenha se manifestado como bug visivel em producao antes deste PR; a introducao parece preventiva, nao uma correcao de um sintoma ja relatado. | `git show 628cf8e -- mobile/src/utils/format.ts` confirma a implementacao e o comentario no codigo |
| 4 | MEDIA (cobertura de teste) | Maestro/simulador iOS **nao foi executado** nesta rodada, mesma limitacao de ambiente ja registrada nos PR-F3-05 e PR-F3-06. A home e a tela mais usada do app e mudou de estrutura visual (blocos removidos e adicionados) — a ausencia de validacao end-to-end automatizada e o achado de maior risco pratico acumulado do Bloco B ate aqui (3 PRs consecutivos sem essa validacao). | Declarado explicitamente pela sessao de implementacao; nenhuma evidencia de execucao em `mobile/.maestro/` para este commit |
| 5 | BAIXA (evidencia de UX) | Evidencia visual do fluxo em tema claro/escuro nao foi capturada nesta rodada — acumula com a mesma pendencia dos PR-F3-05 e PR-F3-06. Recomenda-se uma rodada unica de validacao visual cobrindo os tres PRs do Bloco B ate aqui antes de qualquer um deles ser considerado encerrado sem ressalva. | Declarado explicitamente pela sessao de implementacao |
| 6 | BAIXA (rastreabilidade) | Nao ha evidencia recebida pelo `docs-reporter` de execucao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico — mesma ressalva ja registrada para PR-F3-01 a PR-F3-06. A home consome apenas contratos ja existentes e auditados (metricas, compromissos, transacoes, insights), sem novo dado pessoal, o que reduz o risco pratico, mas a ausencia de auditoria dedicada permanece registrada. | Ausencia de relatorio de auditoria dedicado a PR-F3-07 em `docs/REVIEW_REPORTS/` |
| 7 | BAIXA (escopo declarado, nao um problema em si) | `perfil.tsx` (ou tela equivalente de perfil) ainda consome `GET /v1/dashboard/resumo`, endpoint legado que a home deixou de usar neste PR. A remocao global do endpoint do cliente mobile fica explicitamente fora de escopo, planejada para o PR-F3-13. Ate la, o endpoint legado precisa continuar disponivel no backend mesmo apos a home parar de o consumir. | Declarado explicitamente pela sessao de implementacao; nao verificado por leitura direta de `perfil.tsx` pelo `docs-reporter` nesta rodada |
| 8 | BAIXA (confirmacao independente pendente) | Assim como no achado #3 do relatorio do PR-F3-06, o `docs-reporter` nao leu o diff completo de `mobile/app/(app)/index.tsx` (382 linhas tocadas) nem de `mobile/src/components/NovaTransacaoModal.tsx` — a confirmacao de que a reescrita da home preserva a navegacao correta por tipo de compromisso, a invalidacao de cache no modal, e a exibicao correta dos grupos `COMPROMETIDO`/`PREVISTO` apoia-se no relato da sessao de implementacao e na contagem do Jest (29/29, 10 suites, 1 nova), sem leitura de diff completo por este agente. | `git show --stat` confirma apenas contagem de linhas, nao o conteudo funcional da reescrita |

## O que foi corrigido

Nao ha bug a corrigir neste PR — trata-se de reescrita/reducao de tela existente consumindo contratos ja
existentes e testados (`/v1/compromissos` do PR-F3-01, `/v1/metricas`, `/v1/transacoes/minhas`,
`/v1/insights`) mais uma funcao utilitaria nova (`formatDateOnlyBR`) de carater preventivo. Nenhuma
entrada foi criada em `docs/BUGFIX_LOG.md` (confirmado: sem bug, sem entrada, conforme instrucao recebida
para esta rodada).

## O que ficou pendente

- Execucao do Maestro/simulador iOS para a home reescrita (achado #4) — recomenda-se tratar como
  prioridade dentro da rodada unica de validacao visual do Bloco B, dado que a home e a tela mais
  frequentada do app e teve estrutura visual alterada em 3 PRs consecutivos (PR-F3-05, PR-F3-06,
  PR-F3-07) sem nenhuma validacao end-to-end automatizada.
- Evidencia visual do fluxo em tema claro/escuro (achado #5), acumulada com PR-F3-05 e PR-F3-06.
- Confirmacao formal de `quality-reviewer`/`security-auditor`/`lgpd-auditor` dedicada a este PR
  especifico (achado #6).
- Leitura de diff completo (nao apenas `--stat`) de `mobile/app/(app)/index.tsx` e de
  `mobile/src/components/NovaTransacaoModal.tsx` por um agente de revisao dedicado, para confirmacao
  independente do comportamento funcional descrito (achado #8).
- Verificacao direta de que `perfil.tsx` continua consumindo `GET /v1/dashboard/resumo` e de que o
  backend mantem esse endpoint disponivel ate o PR-F3-13 remover o consumo globalmente (achado #7).
- `CHANGELOG.md` e `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` nao foram atualizados com a entrada
  do PR-F3-07: esses dois arquivos nao constam na lista de "Arquivos sob sua responsabilidade" do
  `docs-reporter`, e a edicao direta desses arquivos esta fora da permissao concedida a este agente
  nesta sessao (restricao informada explicitamente pelo solicitante, na mesma linha do PR-F3-01 a
  PR-F3-06). O texto completo que deveria compor as duas entradas fica registrado abaixo, para
  aplicacao por quem tiver permissao. `BACKLOG-0089` foi ampliado nesta rodada para cobrir tambem o
  PR-F3-07 (sete PRs no total).

### Texto pronto para `docs/CHANGELOG.md`

```
## [Fase 3 — PR-F3-07] - 2026-07-19

### Home reduzida mobile (terceiro PR do Bloco B — consumo mobile)
- `app/(app)/index.tsx` reescrita: ordem fixa (1) Disponivel para gastar (hero, sem pills de
  Resultado mensal/Dividas); (2) Compromissos proximos; (3) botao Lancar; (4) cinco movimentacoes
  recentes com repetir; (5) uma recomendacao textual de insights (nunca numeros); (6) link "Ver visao
  financeira completa".
- Novo `src/services/compromissosService.ts` consome `GET /v1/compromissos` (contrato do PR-F3-01):
  itens `COMPROMETIDO`/`PREVISTO` em grupos distintos, cabecalho "Previstos · fora do total", total
  comprometido no cabecalho da secao; item com alerta `FALHA_SALDO` mostra aviso "Aguardando saldo",
  substituindo o request separado `/v1/contas-fixas/falhas-pendentes`. Toque navega conforme o tipo
  (faturas/contas-fixas/transacoes).
- Exatamente 4 requests na home: `/v1/metricas`, `/v1/compromissos`, `/v1/transacoes/minhas` (5),
  `/v1/insights`. Removidos: `/v1/dashboard/resumo`, `/v1/dashboard/projecao`, grid de metricas, faixa
  KPI, card de insights com numeros/alertas, banner de falhas de recorrencia, grid de atalhos.
- `NovaTransacaoModal` passa a invalidar tambem os caches de metricas e compromissos ao salvar.
- Nova `formatDateOnlyBR` em `utils/format.ts`: `YYYY-MM-DD` -> `DD/MM/AAAA` sem `new Date()`, evita
  deslocamento de fuso em vencimentos.
- Nota de escopo: `perfil.tsx` ainda consome `/v1/dashboard/resumo` — remocao global do endpoint legado
  e escopo do PR-F3-13.
- Sem migration, sem mudanca de backend.
- Commit: `628cf8e`. Validacoes: `npx tsc --noEmit` limpo; Jest 29/29 (10 suites, 1 nova). Maestro e
  evidencia visual claro/escuro NAO EXECUTADOS nesta rodada (acumulados com PR-F3-05/06).
```

### Texto pronto para `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`

```
# PR-F3-07 — Home reduzida mobile

**Status local:** `PASS_COM_RESSALVA`
**Data:** 2026-07-19
**Commit:** `628cf8e`

- [x] `app/(app)/index.tsx` reescrita com ordem fixa de 6 blocos (disponivel, compromissos, lancar,
      movimentacoes, recomendacao textual, link visao financeira)
- [x] novo `compromissosService.ts` consumindo `GET /v1/compromissos`
- [x] grupos `COMPROMETIDO`/`PREVISTO` visualmente distintos, total comprometido no cabecalho
- [x] alerta `FALHA_SALDO` no item de compromisso, substituindo request separado de falhas pendentes
- [x] navegacao por toque conforme tipo do compromisso
- [x] exatamente 4 requests na home (metricas, compromissos, transacoes/minhas, insights)
- [x] remocao de resumo legado, projecao, grid de metricas, faixa KPI, numeros/alertas de insights,
      banner de falhas de recorrencia, grid de atalhos
- [x] `NovaTransacaoModal` invalida caches de metricas e compromissos ao salvar
- [x] `formatDateOnlyBR` nova em `utils/format.ts`
- [x] nenhuma migration, sem mudanca de backend
- [x] `npx tsc --noEmit` limpo
- [x] Jest 29/29 (10 suites, 1 nova)
- [ ] Maestro/simulador executado para a home reescrita (acumulado com PR-F3-05/06)
- [ ] evidencia visual do fluxo em tema claro/escuro (acumulado com PR-F3-05/06)
- [ ] revisao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico
- [ ] leitura de diff completo de `index.tsx`/`NovaTransacaoModal.tsx` por revisor dedicado
- [ ] confirmacao de que `perfil.tsx` mantem `/v1/dashboard/resumo` ate o PR-F3-13
- [ ] `CHANGELOG.md`/checklist atualizados diretamente (aplicado manualmente a partir deste bloco)
```

## Recomendacao final

Reescrita coerente com o objetivo de "Experiencia simples" do plano de Fase 3: reduz a home a uma ordem
fixa de blocos essenciais, elimina numeros de insights da tela inicial, absorve o alerta de falha de
saldo no bloco de compromissos (fechando o loop com o PR-F3-01) e reduz o numero de requests HTTP da
tela mais usada do app. O principal risco em aberto e a ausencia de validacao end-to-end (Maestro/
simulador) para uma tela que mudou de estrutura em 3 PRs consecutivos do Bloco B sem nenhuma execucao
automatizada de UI — recomenda-se priorizar essa rodada de validacao visual antes de qualquer novo PR de
UI do Bloco B ser iniciado.

## Status final

PASS_COM_RESSALVA — ressalvas: (1) Maestro/simulador iOS nao executado para a home reescrita, acumulado
com PR-F3-05 e PR-F3-06 (risco elevado por ser a tela mais frequentada do app); (2) evidencia visual
claro/escuro pendente, mesma acumulacao; (3) `CHANGELOG.md` e o checklist de execucao de PRs nao puderam
ser atualizados por este agente por restricao de permissao de arquivo (texto pronto acima); (4) ausencia
de evidencia direta de revisao/auditoria dedicada a este PR especifico; (5) `docs-reporter` nao leu o
diff completo de `index.tsx`/`NovaTransacaoModal.tsx`, apoiando-se no relato da sessao de implementacao e
na contagem do Jest; (6) verificacao independente de que `perfil.tsx` mantem o consumo de
`/v1/dashboard/resumo` ate o PR-F3-13 nao foi feita nesta rodada.

---

> Relatorio mantido pelo `docs-reporter`.
