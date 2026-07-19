# Relatorio de Revisao

**Arquivo:** 2026-07-19_mobile_implementation_pr-f3-08-drill-down.md

**PR:** PR-F3-08 — Drill-down ate o extrato mobile (Fase 3, quarto PR do Bloco B — consumo mobile)

**Commit:** `672d97b` (main)

---

## Objetivo

Registrar a implementacao do PR-F3-08, quarto PR do Bloco B da Fase 3 ("Experiencia simples") no app
mobile (Expo/React Native). O PR fecha o loop entre o contrato de navegacao aditivo publicado pelo
backend no PR-F3-04 (campo `navegacao` em `MetricasService.Origem`) e o cliente mobile: a composicao de
uma metrica (aberta a partir da tela "Visao financeira" do PR-F3-06 e da home do PR-F3-07) passa a
permitir toque em cada origem para navegar ate o extrato da conta, a transacao, a fatura, a meta, o
investimento ou a lista de transacoes filtrada — sem o cliente inventar nenhuma rota para origens que o
backend nao anota com `navegacao`.

## Escopo verificado

Relato de implementacao mobile consolidado apos ciclo de implementacao, complementado por leitura direta
do commit pelo `docs-reporter` (`git show --stat`, `git show` do diff completo de todos os 5 arquivos
tocados — ferramentas de inspecao somente leitura, nenhuma edicao de codigo feita por este agente).
Diferente das rodadas de PR-F3-05/06/07, nesta rodada o `docs-reporter` leu o diff **completo** (nao
apenas `--stat`) de todos os arquivos alterados, incluindo `ComposicaoMetricaModal.tsx`, `carteiras.tsx` e
`transacoes.tsx` — reduzindo a dependencia exclusiva do relato da sessao de implementacao para os fatos
de codigo (o achado equivalente ao #8 do relatorio do PR-F3-07 nao se repete aqui). Nao ha evidencia, nas
informacoes recebidas, de acionamento dedicado de `quality-reviewer`, `security-auditor` ou
`lgpd-auditor` especificamente para este PR (mesma ressalva ja registrada para PR-F3-01 a PR-F3-07).

Escopo tecnico coberto pela sessao, confirmado por leitura direta do diff:

- **`mobile/src/components/ComposicaoMetricaModal.tsx` (+65/-13 linhas, diff completo lido):** nova
  funcao pura exportada `rotaDaNavegacao(navegacao: NavegacaoOrigem | null | undefined): string | null`
  mapeia `destino` -> rota do app:
  - `EXTRATO_CONTA` com `id` -> `` `/more/carteiras?contaId=${id}` ``
  - `TRANSACAO` com `id` -> `` `/transacoes?transacaoId=${id}` ``
  - `FATURA` -> `/more/faturas`
  - `META` -> `/metas`
  - `INVESTIMENTO` -> `/more/investimentos`
  - `TRANSACOES` -> `/transacoes` com query string montada a partir de `navegacao.filtros`
    (`inicio`/`fim`/`tipo`, apenas as chaves presentes)
  - `EXTRATO_CONTA`/`TRANSACAO` sem `id`, ou qualquer `destino` nao mapeado -> `null`.
  A linha de cada origem na composicao so renderiza o indicador visual `›` (chevron) e so aceita
  `onPress` quando `rotaDaNavegacao(origem.navegacao)` retorna uma rota nao nula; origem sem `navegacao`
  (ou com `navegacao: null`, caso das origens informativas do PR-F3-04 como `VARIACAO_*`) permanece
  apenas informativa, sem toque e sem chevron.
- **`mobile/app/(app)/more/carteiras.tsx` (+16 linhas, diff completo lido):** `useLocalSearchParams<{
  contaId?: string }>()` le o parametro de rota; um `useEffect` localiza a conta correspondente em
  `data?.content` (lista ja carregada pela tela) e chama `setExtratoDe(conta)` para abrir o
  `ExtratoModal` existente; um guard via `useRef` (`contaIdAberto`) garante que a abertura automatica
  ocorra uma unica vez por `contaId` recebido (nao reabre a cada re-render ou refetch). Nenhuma tela nova
  foi criada — a tela de carteiras existente passou a aceitar conta por rota, coerente com a diretriz do
  plano de reaproveitar tela existente sempre que ela aceite o parametro necessario.
- **`mobile/app/(app)/transacoes.tsx` (+23 linhas, diff completo lido):** `useLocalSearchParams<{
  inicio?: string; tipo?: string; transacaoId?: string }>()` le tres parametros:
  - `inicio` (formato `YYYY-MM-DD`, validado por regex `^\d{4}-\d{2}-\d{2}$` antes do uso) posiciona o
    estado inicial `mesRef` no mes/ano informados (substitui o default "mes atual" apenas quando o
    parametro e valido).
  - `tipo` (`'ENTRADA' | 'SAIDA'`) pre-seleciona o chip de filtro `filtro`; qualquer outro valor mantem
    o default `'TODOS'`.
  - `transacaoId`: um `useEffect` com guard via `useRef` (`transacaoIdAberta`, evita reabrir a cada
    render) chama `transacaoService.buscarPorId(Number(id))` e, em caso de sucesso, chama
    `setSelecionada(t)` para abrir o modal de edicao/detalhe da transacao; em caso de erro (`.catch(() =>
    {})`), a falha e silenciosa — a lista permanece visivel sem o detalhe, sem mensagem de erro para o
    usuario. Comportamento coerente com o que a sessao de implementacao descreveu para "erro/ownership
    negado".
- **`mobile/src/types/index.ts` (+19/-1 linhas, diff completo lido):** `OrigemMetrica` antes era um tipo
  de uma linha (`{ tipo: string; id: number; descricao: string; valor: number; }`); passa a ganhar
  comentario explicativo da regra de navegacao e o campo opcional `navegacao?: NavegacaoOrigem | null`.
  Dois tipos novos: `DestinoNavegacao` (uniao literal dos seis destinos suportados) e `NavegacaoOrigem`
  (`{ destino: DestinoNavegacao; id: number | null; filtros: Record<string, string> | null }`) —
  espelham exatamente o shape do record `Navegacao` publicado pelo backend no PR-F3-04 (`destino`, `id`,
  `filtros: Map<String,String>`), confirmado por comparacao com a entrada do PR-F3-04 em
  `docs/SYSTEM_OVERVIEW.md`.
- **`mobile/src/__tests__/rotaDaNavegacao.test.ts` (novo, +28 linhas, diff completo lido):** 2 testes.
  O primeiro cobre os seis destinos mapeados com valores literais (`EXTRATO_CONTA` id 7 ->
  `/more/carteiras?contaId=7`; `TRANSACAO` id 42 -> `/transacoes?transacaoId=42`; `FATURA` id 3 ->
  `/more/faturas`; `META` id 9 -> `/metas`; `INVESTIMENTO` id 4 -> `/more/investimentos`; `TRANSACOES`
  com filtros `{inicio: '2026-07-01', fim: '2026-07-31', tipo: 'ENTRADA'}` ->
  `/transacoes?inicio=2026-07-01&fim=2026-07-31&tipo=ENTRADA`). O segundo cobre explicitamente a regra
  de "nao inventar link": `EXTRATO_CONTA` sem `id`, `TRANSACAO` sem `id`, e um destino literal
  `'DESCONHECIDO'` (forcado via `as any`, simulando um destino futuro do backend ainda nao suportado
  pelo cliente) — os tres casos retornam `null`. O teste faz mock de `expo-router` (`useRouter`) e do
  `metricasService` para isolar apenas a funcao pura `rotaDaNavegacao`, importada diretamente do
  componente.
- Sem mudanca de backend e sem migration neste PR — consome o contrato aditivo ja existente e testado do
  PR-F3-04 (`navegacao` em `Origem`, 4 testes em `NavegacaoOrigemTest.java` conforme registrado no
  relatorio daquele PR).

## Arquivos lidos

Todos os 5 arquivos do commit foram lidos integralmente via `git show` (diff completo, nao apenas
`--stat`), diferente das rodadas anteriores do Bloco B:

- `mobile/src/components/ComposicaoMetricaModal.tsx` (diff completo)
- `mobile/app/(app)/more/carteiras.tsx` (diff completo)
- `mobile/app/(app)/transacoes.tsx` (diff completo)
- `mobile/src/types/index.ts` (diff completo)
- `mobile/src/__tests__/rotaDaNavegacao.test.ts` (novo — conteudo integral)

## Comandos executados

Comandos reportados como executados pelo ciclo de implementacao (nao reexecutados pelo `docs-reporter`,
que nao tem permissao para rodar build/teste de `mobile/`):

| Comando | Resultado |
|---|---|
| `npx tsc --noEmit` | limpo, sem erros |
| Jest (suite mobile) | 31/31 PASS (11 suites — 1 nova em relacao ao PR-F3-07, que estava em 29/29 e 10 suites) |

Comandos de inspecao executados diretamente pelo `docs-reporter` (somente leitura, sem alterar codigo):

| Comando | Resultado |
|---|---|
| `git show 672d97b --stat` | Confirma os 5 arquivos alterados/criados e a contagem de linhas: `mobile/app/(app)/more/carteiras.tsx` (+16), `mobile/app/(app)/transacoes.tsx` (+23), `mobile/src/__tests__/rotaDaNavegacao.test.ts` (novo, +28), `mobile/src/components/ComposicaoMetricaModal.tsx` (+65/-13), `mobile/src/types/index.ts` (+19/-1); total 138 insercoes, 13 delecoes |
| `git show 672d97b -- mobile/src/types/index.ts mobile/src/__tests__/rotaDaNavegacao.test.ts` | Confirma o conteudo integral dos tipos novos (`DestinoNavegacao`, `NavegacaoOrigem`, campo `navegacao` em `OrigemMetrica`) e dos 2 testes de `rotaDaNavegacao.test.ts`, exatamente como reportado pela sessao de implementacao |
| `git show 672d97b -- mobile/app/(app)/more/carteiras.tsx mobile/app/(app)/transacoes.tsx` | Confirma o diff completo das duas telas: guard via `useRef` em ambas para abrir uma unica vez por parametro de rota, `.catch(() => {})` silencioso em `transacoes.tsx` para `transacaoId` invalido/inacessivel, validacao por regex do parametro `inicio` |
| `git log --oneline -3 -- mobile/app/(app)/more/carteiras.tsx` e `git log --oneline -3 -- mobile/app/(app)/transacoes.tsx` | Confirmam `672d97b` como o commit mais recente a tocar ambos os arquivos |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | INFORMATIVO | `rotaDaNavegacao` e uma funcao pura, exportada e testada isoladamente (2 testes cobrindo os 6 destinos e a regra de nao inventar link para destino desconhecido ou `id` ausente). Fecha corretamente o contrato aditivo publicado pelo backend no PR-F3-04 — o mapeamento de destinos no cliente (`EXTRATO_CONTA`, `TRANSACAO`, `FATURA`, `META`, `INVESTIMENTO`, `TRANSACOES`) espelha exatamente os destinos documentados naquele PR. | `git show 672d97b -- mobile/src/components/ComposicaoMetricaModal.tsx mobile/src/__tests__/rotaDaNavegacao.test.ts`, comparado com a entrada do PR-F3-04 em `docs/SYSTEM_OVERVIEW.md` |
| 2 | INFORMATIVO | Nenhuma tela nova foi criada para o extrato da conta — `carteiras.tsx` (tela ja existente) passou a aceitar `?contaId=` por rota e abrir o `ExtratoModal` ja existente automaticamente. Atende a diretriz do plano de Fase 3 de nao duplicar telas quando a existente pode ser adaptada. | `git show 672d97b -- mobile/app/(app)/more/carteiras.tsx` |
| 3 | BAIXA (silencioso por design, risco de UX minimo) | Em `transacoes.tsx`, se `transacaoId` vindo por rota nao existir ou pertencer a outro usuario (ownership negado pelo backend), o `.catch(() => {})` absorve o erro silenciosamente: o usuario navega para a lista de transacoes e nao ve nenhum feedback de que o item que deveria abrir nao foi encontrado (nao ha toast/alert). Comportamento coerente com o que a sessao de implementacao descreveu explicitamente, mas e uma escolha de UX que pode confundir o usuario num caso de borda (ex.: link antigo/expirado, ou transacao ja excluida entre a composicao e o toque). | `git show 672d97b -- mobile/app/(app)/transacoes.tsx` (linha do `.catch(() => {})`) |
| 4 | MEDIA (cobertura de teste) | Maestro/simulador iOS **nao foi executado** nesta rodada, mesma limitacao de ambiente ja registrada nos PR-F3-05/06/07. Este PR altera o comportamento de navegacao de duas telas existentes (`carteiras.tsx`, `transacoes.tsx`) reagindo a parametros de rota — cenario tipicamente dependente de fluxo real de navegacao (deep-link/rota), que teste unitario isolado nao cobre (o teste novo cobre apenas a funcao pura de mapeamento, nao a integracao com `expo-router`/`useLocalSearchParams` nas telas). Acumula-se ao risco ja registrado para a rodada unica de validacao visual do Bloco B (agora 4 PRs consecutivos de UI sem validacao end-to-end automatizada). | Declarado explicitamente pela sessao de implementacao; nenhuma evidencia de execucao em `mobile/.maestro/` para este commit |
| 5 | BAIXA (evidencia de UX) | Evidencia visual do fluxo em tema claro/escuro nao foi capturada nesta rodada — acumula com a mesma pendencia dos PR-F3-05/06/07. | Declarado explicitamente pela sessao de implementacao |
| 6 | BAIXA (rastreabilidade) | Nao ha evidencia recebida pelo `docs-reporter` de execucao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico — mesma ressalva ja registrada para PR-F3-01 a PR-F3-07. O PR nao introduz novo dado pessoal nem novo endpoint (consome apenas o contrato aditivo ja existente do PR-F3-04), o que reduz o risco pratico de auditoria de seguranca/LGPD, mas a ausencia formal permanece registrada. | Ausencia de relatorio de auditoria dedicado a PR-F3-08 em `docs/REVIEW_REPORTS/` |
| 7 | BAIXA (escopo declarado) | O parametro `inicio` em `transacoes.tsx` e validado por regex antes do uso (`^\d{4}-\d{2}-\d{2}$`), mas o parser (`split('-').map(Number)`) nao valida limites de mes/dia (ex.: `2026-13-01` passaria na regex e geraria um `Date` invalido via `new Date(ano, mes - 1, 1)` com `mes=12` (indice 0-based de `13-1=12`), rolando para janeiro do ano seguinte). Como a unica origem confiavel deste parametro e o proprio backend (contrato do PR-F3-04, que sempre gera datas validas a partir de competencia real), o risco pratico e baixo, mas o parser nao e defensivo contra uma origem futura menos confiavel (ex.: link compartilhado manualmente). | `git show 672d97b -- mobile/app/(app)/transacoes.tsx` (bloco de parse de `params.inicio`) |

## O que foi corrigido

Nao ha bug a corrigir neste PR — trata-se de consumo de um contrato aditivo ja existente e testado
(`navegacao` em `Origem`, publicado no PR-F3-04) por telas ja existentes (`carteiras.tsx`,
`transacoes.tsx`, `ComposicaoMetricaModal.tsx`), mais uma funcao pura nova (`rotaDaNavegacao`) e os tipos
correspondentes. Nenhuma entrada foi criada em `docs/BUGFIX_LOG.md` (confirmado: sem bug, sem entrada,
conforme instrucao recebida para esta rodada).

## O que ficou pendente

- Execucao do Maestro/simulador iOS cobrindo o fluxo de drill-down (composicao -> toque -> navegacao ->
  tela de destino) para os seis destinos suportados (achado #4) — recomenda-se tratar como parte da
  mesma rodada unica de validacao visual do Bloco B ja acumulada com PR-F3-05/06/07, dado que este PR e
  o primeiro do bloco cujo comportamento novo depende diretamente de integracao com `expo-router`
  (parametros de rota), nao coberta pelo teste unitario da funcao pura.
- Evidencia visual do fluxo em tema claro/escuro (achado #5), acumulada com PR-F3-05/06/07.
- Confirmacao formal de `quality-reviewer`/`security-auditor`/`lgpd-auditor` dedicada a este PR
  especifico (achado #6).
- Avaliar se o silencio total (`.catch(() => {})`) em `transacoes.tsx` para `transacaoId` invalido ou
  inacessivel merece algum feedback visual minimo ao usuario (achado #3) — decisao de produto, nao um
  bug; registrado como item de backlog opcional.
- Endurecer o parser de `params.inicio` em `transacoes.tsx` para rejeitar mes/dia fora de limite, mesmo
  que a regex de formato passe (achado #7) — risco pratico baixo hoje porque a unica origem e o backend,
  mas fica registrado para quando/se o parametro puder vir de outra origem (ex.: link compartilhado).
- `CHANGELOG.md` e `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` nao foram atualizados com a entrada
  do PR-F3-08: esses dois arquivos nao constam na lista de "Arquivos sob sua responsabilidade" do
  `docs-reporter`, e a edicao direta desses arquivos esta fora da permissao concedida a este agente
  nesta sessao (restricao informada explicitamente pelo solicitante, na mesma linha do PR-F3-01 a
  PR-F3-07). O texto completo que deveria compor as duas entradas fica registrado abaixo, para
  aplicacao por quem tiver permissao. `BACKLOG-0089` foi ampliado nesta rodada para cobrir tambem o
  PR-F3-08 (oito PRs no total).

### Texto pronto para `docs/CHANGELOG.md`

```
## [Fase 3 — PR-F3-08] - 2026-07-19

### Drill-down ate o extrato mobile (quarto PR do Bloco B — consumo mobile)
- `ComposicaoMetricaModal.tsx` consome o campo `navegacao` da `Origem` (contrato aditivo do PR-F3-04).
  Nova funcao pura `rotaDaNavegacao` mapeia destino -> rota do app: `EXTRATO_CONTA` ->
  `/more/carteiras?contaId=`; `TRANSACAO` -> `/transacoes?transacaoId=`; `FATURA` -> `/more/faturas`;
  `META` -> `/metas`; `INVESTIMENTO` -> `/more/investimentos`; `TRANSACOES` -> `/transacoes` com
  filtros `inicio`/`fim`/`tipo` em query string. Destino desconhecido ou sem `id` necessario -> sem
  link (`null`) — o cliente nunca inventa uma rota aproximada.
- Linha de cada origem na composicao so mostra chevron `›` e aceita toque quando ha destino valido;
  origem sem `navegacao` permanece informativa.
- `carteiras.tsx` aceita `?contaId=` por rota e abre o `ExtratoModal` da conta automaticamente ao
  carregar a lista (guard para abrir uma unica vez por id) — nenhuma tela nova criada.
- `transacoes.tsx` aceita por rota: `transacaoId` (abre o detalhe via `transacaoService.buscarPorId`,
  falha silenciosa se inacessivel), `inicio` (posiciona o mes do filtro) e `tipo`
  (`ENTRADA`/`SAIDA` pre-seleciona o chip).
- Tipos novos em `types/index.ts`: `DestinoNavegacao`, `NavegacaoOrigem`; `OrigemMetrica` ganhou o
  campo opcional `navegacao`.
- Sem migration, sem mudanca de backend (consome contrato ja existente do PR-F3-04).
- Commit: `672d97b`. Validacoes: `npx tsc --noEmit` limpo; Jest 31/31 (11 suites, 1 nova). Maestro e
  evidencia visual claro/escuro NAO EXECUTADOS nesta rodada (acumulados com PR-F3-05/06/07).
```

### Texto pronto para `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`

```
# PR-F3-08 — Drill-down ate o extrato mobile

**Status local:** `PASS_COM_RESSALVA`
**Data:** 2026-07-19
**Commit:** `672d97b`

- [x] `ComposicaoMetricaModal.tsx` consome o campo `navegacao` da `Origem` (contrato do PR-F3-04)
- [x] funcao pura `rotaDaNavegacao` mapeia os 6 destinos suportados e retorna `null` para destino
      desconhecido ou id ausente
- [x] linha de origem so aceita toque/chevron quando ha destino valido
- [x] `carteiras.tsx` aceita `?contaId=` por rota e abre o extrato automaticamente (sem tela nova)
- [x] `transacoes.tsx` aceita `transacaoId`/`inicio`/`tipo` por rota
- [x] `transacaoId` invalido/inacessivel falha silenciosamente, sem quebrar a lista
- [x] tipos novos `DestinoNavegacao`/`NavegacaoOrigem` em `types/index.ts`
- [x] `rotaDaNavegacao.test.ts` novo, 2 testes (6 destinos + regra de nao inventar link)
- [x] nenhuma migration, sem mudanca de backend
- [x] `npx tsc --noEmit` limpo
- [x] Jest 31/31 (11 suites, 1 nova)
- [ ] Maestro/simulador executado para o fluxo de drill-down (acumulado com PR-F3-05/06/07)
- [ ] evidencia visual do fluxo em tema claro/escuro (acumulado com PR-F3-05/06/07)
- [ ] revisao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico
- [ ] avaliar feedback visual para `transacaoId` invalido/inacessivel (hoje silencioso por design)
- [ ] endurecer parser de `inicio` em `transacoes.tsx` contra mes/dia fora de limite
- [ ] `CHANGELOG.md`/checklist atualizados diretamente (aplicado manualmente a partir deste bloco)
```

## Recomendacao final

Implementacao coerente com o contrato aditivo publicado pelo backend no PR-F3-04 e com a regra cravada no
plano de Fase 3 de nunca inventar links aproximados no cliente. A funcao de mapeamento e pura, testada e
isolavel; as duas telas de destino (`carteiras.tsx`, `transacoes.tsx`) foram reaproveitadas em vez de
criar telas novas, reduzindo superficie de codigo. Diferente das tres rodadas anteriores do Bloco B, o
`docs-reporter` conseguiu ler o diff completo de todos os arquivos nesta rodada, o que reduz a
dependencia do relato da sessao de implementacao para os fatos de codigo. O principal risco em aberto
continua sendo a ausencia de validacao end-to-end (Maestro/simulador) — agora acumulada em 4 PRs
consecutivos do Bloco B — especialmente relevante aqui porque o comportamento novo depende de integracao
real com `expo-router` (parametros de rota), que nenhum teste unitario desta rodada exercita.

## Status final

PASS_COM_RESSALVA — ressalvas: (1) Maestro/simulador iOS nao executado para o fluxo de drill-down,
acumulado com PR-F3-05/06/07 (risco relevante por depender de integracao real com `expo-router`, nao
coberta por teste unitario); (2) evidencia visual claro/escuro pendente, mesma acumulacao; (3)
`CHANGELOG.md` e o checklist de execucao de PRs nao puderam ser atualizados por este agente por
restricao de permissao de arquivo (texto pronto acima); (4) ausencia de evidencia direta de
revisao/auditoria dedicada a este PR especifico; (5) falha silenciosa (`.catch(() => {})`) para
`transacaoId` invalido/inacessivel em `transacoes.tsx`, sem feedback visual ao usuario — decisao de UX
registrada, nao um bug; (6) parser de `params.inicio` nao valida limites de mes/dia alem do formato via
regex, risco pratico baixo dado que a unica origem hoje e o backend.

---

> Relatorio mantido pelo `docs-reporter`.
