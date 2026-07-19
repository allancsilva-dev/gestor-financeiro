# Relatorio de Revisao

**Arquivo:** 2026-07-19_mobile_implementation_pr-f3-09-onboarding-minimo.md

**PR:** PR-F3-09 — Onboarding mobile minimo (Fase 3, quinto PR do Bloco B — consumo mobile)

**Commit:** `0849847` (main)

---

## Objetivo

Registrar a implementacao do PR-F3-09, quinto PR do Bloco B da Fase 3 ("Experiencia simples") no app
mobile (Expo/React Native). O PR fecha o loop com o contrato de onboarding minimo publicado pelo backend
no PR-F3-03 (`carteira` como unico campo obrigatorio de `OnboardingFinalizarRequest`, `cartao` e
`categorias` opcionais): o wizard mobile de seis passos e substituido por uma etapa unica — apenas a conta
principal (nome, tipo, saldo inicial) — e o cartao, as categorias, a renda e a meta passam a ser setup
progressivo, criados pelas telas normais do app apos o onboarding, nao mais dentro dele.

## Escopo verificado

Relato de implementacao mobile consolidado apos ciclo de implementacao, complementado por leitura direta
do commit pelo `docs-reporter` (`git show --stat` e `git show` do diff completo de todos os 6 arquivos
tocados — ferramentas de inspecao somente leitura, nenhuma edicao de codigo feita por este agente). Nao ha
evidencia, nas informacoes recebidas, de acionamento dedicado de `quality-reviewer`, `security-auditor` ou
`lgpd-auditor` especificamente para este PR (mesma ressalva ja registrada para PR-F3-01 a PR-F3-08).

Escopo tecnico coberto pela sessao, confirmado por leitura direta do diff:

- **`mobile/app/onboarding.tsx` (reescrito, -327/+121 linhas liquidas no commit, diff completo lido):** o
  wizard de seis passos (`PASSOS = ['Conta', 'Cartão', 'Categorias', 'Renda', 'Meta', 'Confirmar']`, estado
  `passo`, `validarPasso()` por passo, `montarRequest()` agregando carteira+cartao+categorias+renda+meta)
  foi inteiramente removido. A tela nova tem estado unico (`nome` default `'Conta Principal'`, `tipo`
  default `'CONTA_BANCARIA'`, `saldo` string vazia) e uma unica funcao `handleComecar`:
  - Validacao: `nome.trim().length < 2` bloqueia com "Informe o nome da conta principal."; `saldo` vazio e
    tratado como `'0'` antes de `parseCurrencyBR`; `saldoNum < 0` ou nao finito bloqueia com "Saldo inicial
    deve ser zero ou positivo."
  - Envio: `onboardingService.finalizar({ carteira: { nome: nome.trim(), subtipo: tipo === 'CONTA_BANCARIA'
    ? 'CORRENTE' : tipo, saldo: saldoNum } })` — **somente** o campo `carteira` e enviado, sem `cartao`,
    `categorias`, `renda` ou `meta` (os quatro ultimos nem existem mais como estado do componente).
  - UI: titulo fixo "Sua conta principal", subtitulo "Só isso para começar. Cartão, categorias e metas
    você adiciona depois, quando precisar.", `Field` de nome (`testID="onboarding-account-name"`), 3
    `Chip` de tipo (Bancária/Dinheiro/Poupança, mesmos 3 valores de `TipoContaInicial` do wizard anterior)
    e `Field` de saldo (`testID="onboarding-account-balance"`, mesmo testID do wizard anterior — preservado
    para nao quebrar automacao existente que dependia dele). Barra de progresso (`PASSOS.map`), rotulo
    "Passo N de 6" e os botoes "Voltar"/"Continuar" foram removidos; um unico botao
    (`accessibilityLabel="Começar"`) chama `handleComecar` diretamente.
  - As constantes/estruturas removidas junto com o wizard: `CATEGORIAS_SUGERIDAS` (9 categorias com cor da
    paleta canonica e icone), toda a logica de selecao de categorias por `TouchableOpacity` em grid, os
    campos de renda (nome/valor/dia de recebimento, com checkbox "Pular — configuro depois") e os campos de
    meta (nome/valor total, mesmo checkbox de pular).
- **`mobile/src/services/onboardingService.ts` (+6/-2 linhas, diff completo lido):** `cartao` e
  `categorias` em `OnboardingFinalizarRequest` passam de obrigatorios para opcionais (`cartao?:`,
  `categorias?:`), com comentario novo referenciando PR-F3-03/09 e explicando que ausencia de
  cartao/categorias e no-op no backend. Alinha o tipo TypeScript do cliente ao contrato ja relaxado no
  backend pelo PR-F3-03 (`OnboardingFinalizarRequest` Java, `@NotNull`/`@NotEmpty` removidos naquele PR).
- **`mobile/app/(app)/more/categorias.tsx` (+1/-1 linha):** o `Field` de nome do formulario de nova
  categoria ganhou `testID="category-name"` (antes sem testID). Suporte de automacao para o setup
  progressivo de categoria fora do onboarding.
- **`mobile/app/(app)/more/contas.tsx` (+2 linhas):** o FAB ("+") que abre o modal de novo cartao ganhou
  `accessibilityRole="button"` e `accessibilityLabel="Novo cartão"` (antes sem nenhum dos dois). Suporte de
  automacao para o setup progressivo de cartao fora do onboarding.
- **`mobile/app/(app)/metas.tsx` (+2/-2 linhas):** os `Field` de nome e valor total do formulario de nova
  meta ganharam `testID="goal-name"` e `testID="goal-total"` respectivamente (antes sem testID). Mesmos
  nomes de testID que o onboarding antigo usava para os campos equivalentes dentro do wizard (o wizard
  usava `onboarding-goal-name`/`onboarding-goal-total`; a tela de metas agora usa `goal-name`/`goal-total`
  sem prefixo `onboarding-`, jah que o campo deixou de existir dentro do onboarding).
- **`mobile/.maestro/financial-critical.yaml` (diff lido integralmente):** o bloco de onboarding foi
  reescrito. Antes: `assertVisible: "Conta"` -> preenche saldo -> "Continuar" -> assert "Cartão" -> preenche
  limite/fechamento/vencimento -> "Continuar" -> assert "Categorias"+"Alimentação" -> "Continuar" -> assert
  "Renda" -> tapOn "Pular — configuro depois" -> "Continuar" -> assert "Meta" -> preenche
  `onboarding-goal-name`/`onboarding-goal-total` -> "Continuar" -> assert "Confirmar" -> tapOn "Começar" ->
  espera "Início" -> screenshot. Depois: `extendedWaitUntil visible: "Sua conta principal"` -> `assertVisible
  "Sua conta principal"` -> preenche apenas `onboarding-account-balance` com "100000" -> tapOn "Começar" ->
  `extendedWaitUntil visible: "Início"` -> screenshot `01-onboarding-completo`. Em seguida, um bloco **novo**
  de "Setup pos-onboarding pelas telas" navega por UI normal (nao mais dentro do onboarding) para criar:
  categoria "Alimentação" (Mais > Categorias > Nova categoria > `category-name` > Salvar > assert
  "Alimentação" visivel), cartao "Cartão Principal" com limite 5.000, fechamento dia 5, vencimento dia 12
  (Mais > Cartões > Novo cartão > campos por texto visivel "Nome da conta"/"Limite total"/"Dia de
  fechamento"/"Dia de vencimento" > Salvar > assert "Cartão Principal" visivel), e meta "Meta Smoke" 1.000
  (Planejamento > Criar meta > `goal-name`/`goal-total` > Salvar > assert "Meta Smoke" visivel > tapOn
  "Início" para voltar). O restante do flow (despesa de R$ 100, compra no cartao, fatura, meta, extrato) nao
  foi alterado neste diff.
- Sem mudanca de backend e sem migration neste PR — consome o contrato ja relaxado e testado do PR-F3-03
  (`carteira` unico campo obrigatorio de `OnboardingFinalizarRequest`).

## Arquivos lidos

Todos os 6 arquivos do commit foram lidos integralmente via `git show` (diff completo):

- `mobile/app/onboarding.tsx` (reescrito — diff completo)
- `mobile/src/services/onboardingService.ts` (diff completo)
- `mobile/app/(app)/more/categorias.tsx` (diff completo)
- `mobile/app/(app)/more/contas.tsx` (diff completo)
- `mobile/app/(app)/metas.tsx` (diff completo)
- `mobile/.maestro/financial-critical.yaml` (diff completo)

## Comandos executados

Comandos reportados como executados pelo ciclo de implementacao (nao reexecutados pelo `docs-reporter`,
que nao tem permissao para rodar build/teste de `mobile/`):

| Comando | Resultado |
|---|---|
| `npx tsc --noEmit` | limpo, sem erros |
| Jest (suite mobile) | 31/31 PASS (11 suites — mesma contagem do PR-F3-08, nenhum teste novo reportado para este PR) |

Comandos de inspecao executados diretamente pelo `docs-reporter` (somente leitura, sem alterar codigo):

| Comando | Resultado |
|---|---|
| `git show 0849847 --stat` | Confirma os 6 arquivos alterados e a contagem de linhas: `mobile/.maestro/financial-critical.yaml` (+64/-22 aprox.), `mobile/app/(app)/metas.tsx` (+2/-2), `mobile/app/(app)/more/categorias.tsx` (+1/-1), `mobile/app/(app)/more/contas.tsx` (+2), `mobile/app/onboarding.tsx` (+121/-370 conforme resumo do commit), `mobile/src/services/onboardingService.ts` (+6/-2); total 121 insercoes, 327 delecoes (numeros da mensagem de commit) |
| `git show 0849847 -- mobile/app/onboarding.tsx` | Confirma a reescrita completa: estado unico `nome`/`tipo`/`saldo`, `handleComecar` unico, envio de somente `{carteira}`, remocao de `CATEGORIAS_SUGERIDAS`, passos de cartao/categorias/renda/meta e da barra de progresso |
| `git show 0849847 -- mobile/src/services/onboardingService.ts mobile/app/(app)/more/categorias.tsx mobile/app/(app)/more/contas.tsx mobile/app/(app)/metas.tsx` | Confirma `cartao?`/`categorias?` opcionais no tipo TS, e os testIDs/accessibilityLabel novos em cada tela |
| `git show 0849847 -- mobile/.maestro/financial-critical.yaml` | Confirma o bloco de onboarding minimo reescrito e o bloco novo de setup pos-onboarding (categoria, cartao, meta pelas telas) |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | INFORMATIVO | O onboarding mobile passa a enviar exatamente o payload minimo que o backend aceita desde o PR-F3-03 (`{ carteira }`, sem `cartao`/`categorias`/`renda`/`meta`). Fecha corretamente o contrato aditivo daquele PR — nenhum campo obrigatorio no cliente que o backend ja tornou opcional. | `git show 0849847 -- mobile/app/onboarding.tsx` (bloco de `handleComecar`), comparado com a entrada do PR-F3-03 em `docs/SYSTEM_OVERVIEW.md` |
| 2 | INFORMATIVO | `testID="onboarding-account-balance"` foi preservado identico ao do wizard anterior; os testIDs novos (`category-name`, `goal-name`, `goal-total`) e o `accessibilityLabel="Novo cartão"` foram adicionados as telas de destino do setup progressivo, e o Maestro foi atualizado no mesmo commit para exercitar o fluxo completo (onboarding minimo + criacao de categoria/cartao/meta pelas telas normais). Reduz o risco de dessincronia entre codigo de produto e script de automacao que poderia surgir se o Maestro fosse atualizado em PR separado. | `git show 0849847 -- mobile/.maestro/financial-critical.yaml mobile/app/(app)/more/categorias.tsx mobile/app/(app)/more/contas.tsx mobile/app/(app)/metas.tsx` |
| 3 | MEDIA/ALTA (cobertura de teste, risco elevado em relacao aos PRs anteriores) | Maestro/simulador iOS **nao foi executado** nesta rodada — mesma limitacao de ambiente ja registrada nos PR-F3-05/06/07/08, mas aqui o risco pratico e maior: este e o **primeiro fluxo que todo novo usuario percorre** (registro -> onboarding -> home), e o flow do Maestro mudou substancialmente (onboarding reduzido de 6 passos para 1, e um bloco inteiro novo de setup pos-onboarding via navegacao real por Mais>Categorias, Mais>Cartões e Planejamento foi adicionado). Diferente dos PR-F3-05/06/07/08 (que alteravam telas ja em uso, cobertas por sessoes de teste anteriores), aqui a tela de onboarding foi **reescrita do zero** e nenhuma execucao real (simulador ou Maestro) validou que o botao "Começar" de fato cria a carteira e navega para a home, nem que o bloco novo de setup pos-onboarding (3 telas, 3 fluxos de criacao) funciona fim-a-fim. Nao ha teste unitario Jest cobrindo `onboarding.tsx` (a contagem de 31/31 suites permanece identica ao PR-F3-08, confirmando que nenhum teste novo foi adicionado para esta tela). | Declarado explicitamente pela sessao de implementacao; nenhuma evidencia de execucao em `mobile/.maestro/` para este commit; ausencia de arquivo de teste Jest para `onboarding.tsx` em `mobile/src/__tests__/` |
| 4 | BAIXA (evidencia de UX) | Evidencia visual do fluxo em tema claro/escuro nao foi capturada nesta rodada — acumula com a mesma pendencia dos PR-F3-05/06/07/08. Adicionalmente, o criterio de UX cravado no plano de Fase 3 para este PR especifico ("novo usuario chega a home em menos de 60s") nao foi medido nem verificado nesta rodada. | Declarado explicitamente pela sessao de implementacao |
| 5 | BAIXA (rastreabilidade) | Nao ha evidencia recebida pelo `docs-reporter` de execucao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico — mesma ressalva ja registrada para PR-F3-01 a PR-F3-08. O PR nao introduz novo dado pessoal nem novo endpoint (consome apenas o contrato ja existente do PR-F3-03), o que reduz o risco pratico de auditoria de seguranca/LGPD, mas a ausencia formal permanece registrada. | Ausencia de relatorio de auditoria dedicado a PR-F3-09 em `docs/REVIEW_REPORTS/` |
| 6 | BAIXA (escopo declarado) | O onboarding minimo remove a oferta guiada de categorias sugeridas (as 9 categorias com cor/icone da paleta canonica que o wizard antigo pre-selecionava) e a oferta de renda/meta dentro do fluxo inicial. Isso e a mudanca de produto intencional deste PR (setup progressivo em vez de wizard longo), nao um bug — mas registra-se como decisao de produto rastreavel: um usuario que nao souber navegar ate Mais>Categorias pode operar por um tempo sem nenhuma categoria alem das que o sistema ja provisiona por padrao (nao verificado neste PR se existe fallback de categoria padrao fora do onboarding). | `git show 0849847 -- mobile/app/onboarding.tsx` (remocao de `CATEGORIAS_SUGERIDAS` e do passo de renda/meta) |

## O que foi corrigido

Nao ha bug a corrigir neste PR — trata-se de uma reescrita de produto (reducao de wizard de 6 passos para
etapa unica) consumindo um contrato ja relaxado e testado no backend (PR-F3-03). Nenhuma entrada foi criada
em `docs/BUGFIX_LOG.md` (confirmado: sem bug, sem entrada, conforme instrucao recebida para esta rodada).

## O que ficou pendente

- Execucao do Maestro `financial-critical.yaml` atualizado (simulador iOS + stack local) cobrindo o
  onboarding minimo reescrito e o bloco novo de setup pos-onboarding (categoria, cartao, meta pelas telas) —
  achado #3, classificado como **MEDIA/ALTA** por ser o primeiro fluxo que todo novo usuario percorre e por
  a tela ter sido reescrita do zero sem cobertura de teste unitario ou execucao real. Recomenda-se agendar
  esta execucao com prioridade acima da rodada unica ja acumulada para PR-F3-05/06/07/08, dado que um
  onboarding quebrado bloqueia a entrada de qualquer usuario novo no app.
- Evidencia visual do fluxo em tema claro/escuro (achado #4), acumulada com PR-F3-05/06/07/08.
- Medicao do criterio de UX "novo usuario chega a home em menos de 60s" (achado #4), especifico deste PR e
  ainda nao verificado.
- Confirmacao formal de `quality-reviewer`/`security-auditor`/`lgpd-auditor` dedicada a este PR especifico
  (achado #5).
- Avaliar se a remocao da oferta guiada de categorias sugeridas dentro do onboarding deixa o usuario sem
  categoria alguma ate navegar manualmente ate Mais>Categorias (achado #6) — decisao de produto, nao um
  bug; registrado para verificacao futura se houver fallback de categoria padrao fora do onboarding.
- `CHANGELOG.md` e `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` nao foram atualizados com a entrada do
  PR-F3-09: esses dois arquivos nao constam na lista de "Arquivos sob sua responsabilidade" do
  `docs-reporter`, e a edicao direta desses arquivos esta fora da permissao concedida a este agente nesta
  sessao (restricao informada explicitamente pelo solicitante, na mesma linha do PR-F3-01 a PR-F3-08). O
  texto completo que deveria compor as duas entradas fica registrado abaixo, para aplicacao por quem tiver
  permissao. `BACKLOG-0089` foi ampliado nesta rodada para cobrir tambem o PR-F3-09 (nove PRs no total).

### Texto pronto para `docs/CHANGELOG.md`

```
## [Fase 3 — PR-F3-09] - 2026-07-19

### Onboarding mobile minimo (quinto PR do Bloco B — consumo mobile)
- `app/onboarding.tsx` reescrito: wizard de 6 passos (Conta/Cartão/Categorias/Renda/Meta/Confirmar)
  substituido por etapa unica — nome da conta principal (default "Conta Principal"), tipo
  (Bancária/Dinheiro/Poupança) e saldo inicial (vazio tratado como zero). Um unico botão "Começar".
- Envia somente `{ carteira }` no `POST /v1/onboarding/finalizar`, usando o contrato minimo do
  PR-F3-03 (`cartao`/`categorias`/`renda`/`meta` deixam de existir dentro do onboarding).
- `onboardingService`: `OnboardingFinalizarRequest.cartao` e `.categorias` agora opcionais no
  TypeScript, alinhado ao contrato já relaxado no backend.
- Cartão, categorias e metas viram setup progressivo — criados pelas telas normais do app
  (Mais>Categorias, Mais>Cartões, Planejamento) após o onboarding, não mais dentro dele.
- Suporte de automação: novos `testID` `category-name` (categoria), `goal-name`/`goal-total` (meta)
  e `accessibilityLabel="Novo cartão"` no FAB de `contas.tsx`.
- `mobile/.maestro/financial-critical.yaml` atualizado no mesmo PR: onboarding mínimo preenche só o
  saldo e toca "Começar"; bloco novo de setup pós-onboarding cria categoria "Alimentação", cartão
  "Cartão Principal" (limite 5.000, fechamento dia 5, vencimento dia 12) e meta "Meta Smoke" (1.000)
  pelas telas normais; restante do fluxo (despesa, compra no cartão, fatura, meta, extrato) inalterado.
- Sem migration, sem mudança de backend (consome contrato já existente do PR-F3-03).
- Commit: `0849847`. Validações: `npx tsc --noEmit` limpo; Jest 31/31 (11 suites, nenhum teste novo
  para esta tela). Maestro atualizado mas NÃO EXECUTADO nesta rodada — pendência classificada como
  MÉDIA/ALTA por ser o primeiro fluxo de todo usuário novo e a tela ter sido reescrita do zero.
  Evidência visual claro/escuro e critério "usuário novo chega à home em <60s" também pendentes.
```

### Texto pronto para `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`

```
# PR-F3-09 — Onboarding mobile mínimo

**Status local:** `PASS_COM_RESSALVA`
**Data:** 2026-07-19
**Commit:** `0849847`

- [x] `onboarding.tsx` reescrito para etapa única (nome/tipo/saldo da conta principal)
- [x] envia somente `{ carteira }` no `POST /v1/onboarding/finalizar` (contrato do PR-F3-03)
- [x] `onboardingService.OnboardingFinalizarRequest.cartao`/`.categorias` agora opcionais
- [x] cartão/categorias/renda/meta removidos do onboarding, viram setup progressivo pelas telas
- [x] `testID="category-name"` em `categorias.tsx`
- [x] `testID="goal-name"`/`"goal-total"` em `metas.tsx`
- [x] `accessibilityLabel="Novo cartão"` no FAB de `contas.tsx`
- [x] `testID="onboarding-account-balance"` preservado (mesmo nome do wizard anterior)
- [x] `financial-critical.yaml` atualizado: onboarding mínimo + setup pós-onboarding (categoria,
      cartão, meta pelas telas normais)
- [x] nenhuma migration, sem mudança de backend
- [x] `npx tsc --noEmit` limpo
- [x] Jest 31/31 (11 suites, sem teste novo para `onboarding.tsx`)
- [ ] Maestro executado para o fluxo de onboarding mínimo + setup pós-onboarding (CRÍTICO — primeiro
      fluxo de todo usuário novo, tela reescrita do zero, ainda não validada por execução real)
- [ ] evidência visual do fluxo em tema claro/escuro (acumulado com PR-F3-05/06/07/08)
- [ ] medir critério de UX "usuário novo chega à home em menos de 60s"
- [ ] revisão dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR específico
- [ ] avaliar se falta categoria padrão fora do onboarding até o usuário navegar até Mais>Categorias
- [ ] `CHANGELOG.md`/checklist atualizados diretamente (aplicado manualmente a partir deste bloco)
```

## Recomendacao final

Implementacao coerente com o contrato ja relaxado e testado pelo backend no PR-F3-03 e com a diretriz de
produto da Fase 3 de reduzir friccao no primeiro uso. A remocao do wizard de 6 passos e a substituicao por
uma unica tela, junto com o suporte de automacao adicionado nas telas de destino do setup progressivo
(categoria, cartao, meta), sao mudancas coerentes e bem direcionadas. O risco central desta rodada e maior
do que nos PRs anteriores do Bloco B: `onboarding.tsx` foi reescrito do zero, e o Maestro atualizado no
mesmo commit para exercitar tanto o onboarding minimo quanto o bloco novo de setup pos-onboarding **nao foi
executado**. Diferente de PR-F3-05/06/07/08 (que alteravam telas ja em producao/uso continuo), aqui a
ausencia de qualquer execucao real (simulador ou Maestro) cobre o primeiro fluxo que todo usuario novo
percorre — recomenda-se tratar a execucao deste Maestro especifico com prioridade mais alta do que a rodada
unica ja acumulada para os PRs anteriores do Bloco B, dado o risco de bloquear a entrada de usuarios novos
se algo estiver quebrado.

## Status final

PASS_COM_RESSALVA — ressalvas: (1) Maestro/simulador iOS nao executado para o onboarding minimo reescrito
e para o bloco novo de setup pos-onboarding, achado de severidade **MEDIA/ALTA** dado que cobre o primeiro
fluxo de todo usuario novo e a tela foi reescrita do zero sem nenhum teste unitario Jest dedicado; (2)
evidencia visual claro/escuro pendente, acumulada com PR-F3-05/06/07/08; (3) criterio de UX "usuario novo
chega a home em menos de 60s" nao medido; (4) `CHANGELOG.md` e o checklist de execucao de PRs nao puderam
ser atualizados por este agente por restricao de permissao de arquivo (texto pronto acima); (5) ausencia de
evidencia direta de revisao/auditoria dedicada a este PR especifico; (6) remocao da oferta guiada de
categorias sugeridas dentro do onboarding — decisao de produto registrada, nao um bug, mas pendente de
verificacao se ha fallback de categoria padrao fora do fluxo.

---

> Relatorio mantido pelo `docs-reporter`.
