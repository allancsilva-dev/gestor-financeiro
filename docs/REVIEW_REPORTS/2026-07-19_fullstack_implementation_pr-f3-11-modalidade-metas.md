# Relatorio de Revisao

**Arquivo:** 2026-07-19_fullstack_implementation_pr-f3-11-modalidade-metas.md

**PR:** PR-F3-11 — Modalidade imutavel e historico de metas (Fase 3, setimo PR do Bloco B — consumo
mobile + endurecimento de backend). **Este PR fecha o Bloco B mobile da Fase 3 (PR-F3-05 a PR-F3-11).**

**Commit:** `6712653` (main) — implementacao. Complementado por `feee1cb` (main, mesma sessao), que
ajusta o flow Maestro `financial-critical.yaml` para a escolha de modalidade tornada obrigatoria por
este PR (ver "Achados", item 3).

---

## Objetivo

Registrar a implementacao do PR-F3-11, que torna a modalidade da meta (`COFRE_REAL`/`RESERVA_VIRTUAL`)
uma escolha obrigatoria e definitiva no momento da criacao — tanto no contrato de backend
(`POST /v1/metas`) quanto na tela de metas do app mobile — e adiciona historico de conclusao (data,
valor final reservado e duracao em dias) as metas concluidas. O PR tambem endurece a regra de
imutabilidade de modalidade que a `ADR-0012` havia definido de forma parcial (permitia troca com
reserva zerada).

## Correcao de rastreabilidade em relacao ao registro anterior (PR-F3-10)

O relatorio `2026-07-19_mobile_implementation_pr-f3-10-setup-progressivo.md` e a entrada correspondente
em `SYSTEM_OVERVIEW.md`/`BACKLOG.md` (BACKLOG-0089) registraram, na rodada anterior, que o PR-F3-10
"fecha o Bloco B" da Fase 3 (PR-F3-05 a PR-F3-10, seis PRs). Essa afirmacao **nao foi corrigida
retroativamente** (o historico permanece intacto, conforme a diretriz de nao apagar entradas antigas),
mas fica registrado aqui, de forma explicita, que o plano de Fase 3 foi estendido em um setimo PR
(PR-F3-11) dentro do mesmo Bloco B, e que **e este PR — PR-F3-11 — quem efetivamente fecha o Bloco B
mobile da Fase 3 (PR-F3-05 a PR-F3-11)**, conforme instrucao explicita recebida para esta rodada.

## Escopo verificado

Relato de implementacao full-stack (backend + mobile) consolidado apos ciclo de implementacao,
complementado por leitura direta do commit `6712653` pelo `docs-reporter` (`git show --stat` e
`git show` do diff completo dos 7 arquivos tocados, mais `git show feee1cb` para o ajuste de flow
Maestro subsequente — ferramentas de inspecao somente leitura, nenhuma edicao de codigo feita por este
agente). Nao ha evidencia, nas informacoes recebidas, de acionamento dedicado de `quality-reviewer`,
`security-auditor` ou `lgpd-auditor` especificamente para este PR (mesma ressalva ja registrada para
PR-F3-01 a PR-F3-10).

Escopo tecnico coberto pela sessao, confirmado por leitura direta do diff:

- **`backend/src/main/java/com/gestor/financeiro/controller/MetaController.java` (+5):**
  `criar()` passa a lancar `BusinessException` ("Escolha a modalidade da meta: COFRE_REAL ou
  RESERVA_VIRTUAL") quando `request.getModalidade() == null`, antes de montar a entidade e chamar
  `metaService.criar`. O caminho legado de onboarding (`OnboardingService.finalizar`, que monta a Meta
  diretamente, sem passar pelo `MetaController`) nao passa por essa validacao e continua recebendo o
  default do modelo (`COFRE_REAL`), preservando o contrato relaxado do PR-F3-03.
- **`backend/src/main/java/com/gestor/financeiro/service/MetaService.java` (+10/-12 liquido, ver
  diff):** o bloco de `atualizar()` que antes permitia trocar `modalidade` quando
  `valorReservado <= 0` (comentado "Troca de modalidade so com reserva zerada (ADR-0012)") foi
  substituido: **qualquer** troca de modalidade (`metaAtualizada.getModalidade() != null &&
  metaAtualizada.getModalidade() != meta.getModalidade()`) agora lanca `BusinessException`
  ("Modalidade da meta não pode ser alterada após a criação"), inclusive com reserva zerada. Enviar a
  **mesma** modalidade no payload (nao e uma troca, condicao `!=` nao dispara) continua aceito e
  silencioso.
- **`backend/src/test/java/com/gestor/financeiro/MetaReservaVirtualTest.java` (+13/-2):** o teste
  `trocaDeModalidadeComReservaEBloqueada` foi renomeado para
  `trocaDeModalidadeEBloqueadaMesmoComReservaZerada` e ganhou dois blocos novos na mesma funcao: (1)
  apos o bloco original (troca bloqueada com reserva > 0), `metaService.removerValor` zera a reserva e
  a mesma tentativa de troca continua lancando `BusinessException` (cobre o caso que a ADR-0012 antiga
  permitia); (2) enviar a **mesma** modalidade (`RESERVA_VIRTUAL`) no payload de atualizacao e aceito
  sem excecao (nao e tratado como troca).
- **`mobile/src/types/index.ts` (+6):** novo `export type ModalidadeMeta = 'COFRE_REAL' |
  'RESERVA_VIRTUAL'`; `Meta` ganha `modalidade: ModalidadeMeta` (obrigatorio, sem `?`),
  `cofreId?: number | null` e `carteiraAlocadaId?: number | null`; `MetaRequest` ganha
  `modalidade?: ModalidadeMeta` (comentario no proprio tipo: "obrigatória na criação; na edição, mesma
  modalidade ou ausente").
- **`mobile/src/domain/metaPolicy.ts` (+11):** `acoesDaMeta` ganha o campo `verExtratoCofre: meta.modalidade
  === 'COFRE_REAL' && meta.cofreId != null`. Nova funcao pura exportada
  `duracaoDaMetaConcluidaEmDias(meta)`: retorna `null` se `meta.status !== 'CONCLUIDA'` ou faltar
  `dataInicio`/`dataConclusao`; caso contrario calcula a diferenca em dias entre as duas datas
  (normalizadas para meio-dia, `T12:00:00`, para evitar deslocamento de fuso) via
  `Math.round(diferencaMs / 86400000)`; retorna `null` tambem se o resultado for negativo (guarda
  defensiva contra dados inconsistentes).
- **`mobile/app/(app)/metas.tsx` (+84/-19 aprox., ver diff completo lido):**
  - Novo array `MODALIDADES` (2 itens, `COFRE_REAL`/`RESERVA_VIRTUAL`) com titulo e descricao no
    vocabulario do glossario (`ADR-0012`): "O dinheiro sai da sua conta e fica guardado num cofre com
    extrato próprio." / "O dinheiro continua na sua conta, marcado como reservado; reduz só o
    disponível para gastar."
  - Estado novo `modalidadeCriar: ModalidadeMeta | null` e `modalidadeError: string | null`,
    resetados em `resetFormularioMeta`. `abrirEditarMeta` inicializa `modalidadeCriar` com
    `meta.modalidade ?? 'COFRE_REAL'` (fallback defensivo, nao deveria ocorrer pois `modalidade` e
    obrigatorio no tipo `Meta`).
  - `montarPayloadMeta`: quando `!editandoMeta && modalidadeCriar == null`, seta
    `modalidadeError = 'Escolha como a meta guarda o dinheiro.'` e marca `hasErr = true`, bloqueando o
    submit; o payload inclui `modalidade: modalidadeCriar ?? undefined`.
  - UI do formulario: quando **criando** (`!editandoMeta`), renderiza os dois cartoes de modalidade
    como `TouchableOpacity` com `accessibilityRole="radio"` e `accessibilityState={{selected:
    modalidadeCriar === m.id}}`, destaque visual (`colors.brand`/`colors.brandBg`) no selecionado, e o
    aviso fixo "A escolha é definitiva para esta meta." abaixo dos dois cartoes. Quando **editando**,
    mostra bloco somente leitura: label "Modalidade", valor ("Reserva virtual"/"Cofre real") e o aviso
    "Definida na criação — não pode ser alterada." — nenhum campo interativo, a modalidade nao pode
    ser trocada pela UI (reforca a regra de backend, embora o backend ja rejeitasse a troca
    independentemente do que a UI enviasse).
  - Card de cada meta na listagem ganhou uma linha com o rotulo da modalidade ("Reserva virtual"/
    "Cofre real") logo abaixo do valor reservado/total.
  - Bloco condicional `concluida && meta.dataConclusao`: texto "Concluída em {formatDateOnlyBR
    (meta.dataConclusao)} com {formatCurrency(meta.valorReservado)}" seguido de
    " · {N} dias" quando `duracaoDaMetaConcluidaEmDias(meta) != null`.
  - Bloco condicional `acoesDaMeta(meta).verExtratoCofre`: `TouchableOpacity` "Ver extrato do cofre ›"
    (`accessibilityLabel="Ver extrato do cofre da meta {nome}"`) que chama
    `router.push('/more/carteiras?contaId=' + meta.cofreId)` — reaproveita a rota de drill-down para
    extrato de conta ja aceita por `carteiras.tsx` desde o PR-F3-08, nenhuma tela nova criada.
- **`mobile/src/__tests__/metaPolicy.test.ts` (+44/-2, total 6 testes conforme declarado pela
  sessao de implementacao):** ampliado para cobrir `verExtratoCofre` (true somente com
  `COFRE_REAL` + `cofreId` presente) e `duracaoDaMetaConcluidaEmDias` (null fora de `CONCLUIDA`,
  null sem `dataInicio`/`dataConclusao`, calculo correto incluindo o caso de 199 dias verificado
  explicitamente pela sessao de implementacao).
- Sem migration neste PR — mudanca de contrato (`modalidade` obrigatoria em `MetaRequest` do lado
  mobile) e de regra de negocio (backend), nenhuma alteracao de schema.

## Arquivos lidos

Todos os 7 arquivos do commit `6712653` foram lidos integralmente via `git show` (diff completo), mais
o diff de 1 linha do commit `feee1cb`:

- `backend/src/main/java/com/gestor/financeiro/controller/MetaController.java` (diff completo)
- `backend/src/main/java/com/gestor/financeiro/service/MetaService.java` (diff completo)
- `backend/src/test/java/com/gestor/financeiro/MetaReservaVirtualTest.java` (diff completo)
- `mobile/app/(app)/metas.tsx` (diff completo)
- `mobile/src/__tests__/metaPolicy.test.ts` (diff completo, resumo por contagem de testes)
- `mobile/src/domain/metaPolicy.ts` (diff completo)
- `mobile/src/types/index.ts` (diff completo)
- `mobile/.maestro/financial-critical.yaml` (diff do commit `feee1cb`, 1 linha)

## Comandos executados

Comandos reportados como executados pelo ciclo de implementacao (datados 2026-07-19; nao
reexecutados pelo `docs-reporter`, que nao tem permissao para rodar build/teste de `backend/` nem de
`mobile/`):

| Comando | Resultado |
|---|---|
| `./mvnw verify -Pintegration-test` | 255 unitarios + 27 ITs, 0 falhas, BUILD SUCCESS |
| `npx tsc --noEmit` | limpo, sem erros |
| Jest (suite mobile) | 36/36 PASS (12 suites) |

Comandos de inspecao executados diretamente pelo `docs-reporter` (somente leitura, sem alterar
codigo):

| Comando | Resultado |
|---|---|
| `git show 6712653 --stat` | Confirma os 7 arquivos alterados e a contagem 154 insercoes / 19 delecoes |
| `git show 6712653 -- backend/.../MetaController.java backend/.../MetaService.java` | Confirma a validacao 400 na criacao e o bloqueio total de troca de modalidade (inclusive reserva zerada) na atualizacao |
| `git show 6712653 -- backend/.../MetaReservaVirtualTest.java` | Confirma os 3 cenarios cobertos pelo teste renomeado: troca com reserva (bloqueada), troca com reserva zerada (bloqueada), mesma modalidade (aceita) |
| `git show 6712653 -- mobile/src/domain/metaPolicy.ts mobile/src/types/index.ts` | Confirma `verExtratoCofre`, `duracaoDaMetaConcluidaEmDias` e os tipos novos/alterados |
| `git show 6712653 -- 'mobile/app/(app)/metas.tsx'` | Confirma a escolha obrigatoria na criacao, o bloco somente-leitura na edicao, o rotulo de modalidade no card, o texto de conclusao e o link "Ver extrato do cofre" |
| `git log --oneline -- mobile/.maestro/financial-critical.yaml` / `git show feee1cb` | Confirma commit de ajuste do flow Maestro (`tapOn: "Cofre real.*"` antes de `goal-name`), na mesma sessao, ja aplicado em `main` (HEAD no momento deste relatorio) |
| `git rev-parse HEAD` | `feee1cbbe1ff4ebf54ba0e0adf1451ef691500b9` — confirma que o ajuste de flow ja esta em `main`, acima de `6712653` |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | INFORMATIVO | `abrirEditarMeta` inicializa `modalidadeCriar` com `meta.modalidade ?? 'COFRE_REAL'` — fallback defensivo para um campo que o tipo `Meta` declara obrigatorio (`modalidade: ModalidadeMeta`, sem `?`). Nao e um bug: cobre o caso teorico de uma meta antiga no backend sem o campo populado (nao verificado se existe tal registro em producao) sem quebrar a tela de edicao. | `git show 6712653 -- 'mobile/app/(app)/metas.tsx'` (funcao `abrirEditarMeta`) |
| 2 | INFORMATIVO | A UI de edicao bloqueia visualmente a troca de modalidade (bloco somente leitura, nenhum campo interativo), mas isso e reforco de UX — o backend ja rejeita a troca de forma incondicional em `MetaService.atualizar`, independentemente do que qualquer cliente (mobile, web ou chamada direta a API) enviar no payload. Nao ha caminho de contorno client-side. | `git show 6712653` (ambos os arquivos de backend e o bloco condicional de UI em `metas.tsx`) |
| 3 | MEDIA (ja remediada nesta mesma sessao, mas Maestro real ainda nao executado) | O flow `mobile/.maestro/financial-critical.yaml` cria a "Meta Smoke" pelo formulario de criacao de meta, que agora exige a escolha de modalidade antes de preencher nome/valor. Como registrado explicitamente pela instrucao desta rodada, o flow **como estava no momento da implementacao de `6712653`** nao tocava nessa escolha e teria falhado numa execucao real. **Este `docs-reporter` verificou, por leitura direta de `git log`/`git show`, que esse ajuste ja foi aplicado**: o commit `feee1cb` ("test(mobile): choose modalidade in maestro meta creation"), tambem em `main` e HEAD no momento deste relatorio, adiciona `- tapOn: "Cofre real.*"` imediatamente antes do `tapOn: {id: "goal-name"}` na secao de criacao de meta do flow. O flow, portanto, ja esta textualmente correto para o novo formulario. O que **continua** nao executado (nem por este PR, nem pelo commit de ajuste) e a rodada real de Maestro/simulador iOS — acumula com a mesma pendencia critica acumulada desde o PR-F3-05, agora tambem cobrindo esta correcao de flow especifica. | `git show feee1cb`; `git log --oneline -- mobile/.maestro/financial-critical.yaml`; `git rev-parse HEAD` = `feee1cb...` |
| 4 | BAIXA (evidencia de UX) | Evidencia visual do fluxo em tema claro/escuro nao foi capturada nesta rodada — acumula com a mesma pendencia dos PR-F3-05 a PR-F3-10 (agora 7 PRs consecutivos de UI/regra de negocio sem validacao visual formal). | Declarado explicitamente pela sessao de implementacao |
| 5 | BAIXA (rastreabilidade) | Nao ha evidencia recebida pelo `docs-reporter` de execucao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico — mesma ressalva ja registrada para PR-F3-01 a PR-F3-10. O endurecimento de uma regra de negocio ja existente (ADR-0012) e uma mudanca de baixo risco de seguranca/LGPD (nao envolve dados sensiveis novos), o que reduz o risco pratico, mas a ausencia formal permanece registrada. | Ausencia de relatorio de auditoria dedicado a PR-F3-11 em `docs/REVIEW_REPORTS/` |
| 6 | BAIXA (rastreabilidade de plano) | O plano de Fase 3 registrado anteriormente em `docs/SYSTEM_OVERVIEW.md`/`docs/BACKLOG.md` (entrada do PR-F3-10) descrevia o Bloco B como encerrado em seis PRs (PR-F3-05 a PR-F3-10). Este PR (PR-F3-11) estende o Bloco B para sete PRs — ver secao "Correcao de rastreabilidade" acima. Registrado para nao deixar a divergencia apenas implicita. | Comparacao direta entre a entrada de `SYSTEM_OVERVIEW.md` do PR-F3-10 (2026-07-19) e a instrucao explicita recebida nesta rodada |

## O que foi corrigido

Nao ha bug a corrigir neste PR do ponto de vista deste agente — trata-se de uma regra de negocio nova
(modalidade obrigatoria e imutavel) e endurecimento de uma regra ja existente (`ADR-0012`), com o
respectivo consumo mobile. O flow Maestro desatualizado (achado #3) **foi corrigido pela propria sessao
de implementacao**, no commit de acompanhamento `feee1cb`, antes deste registro — nenhuma acao de
correcao coube a este `docs-reporter`. Nenhuma entrada foi criada em `docs/BUGFIX_LOG.md` (confirmado:
sem bug, sem entrada, conforme instrucao recebida para esta rodada).

## O que ficou pendente

- Execucao real de Maestro/simulador iOS para o Bloco B completo (PR-F3-05 a PR-F3-11), incluindo
  especificamente a criacao de meta com escolha de modalidade (achado #3) — o flow ja esta
  textualmente correto (commit `feee1cb`), mas nunca foi executado contra simulador real.
- Evidencia visual do fluxo em tema claro/escuro (achado #4), acumulada com PR-F3-05 a PR-F3-10.
- Confirmacao formal de `quality-reviewer`/`security-auditor`/`lgpd-auditor` dedicada a este PR
  especifico (achado #5).
- `CHANGELOG.md` e `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` nao foram atualizados com a entrada
  do PR-F3-11: esses dois arquivos nao constam na lista de "Arquivos sob sua responsabilidade" do
  `docs-reporter`, e a edicao direta desses arquivos esta fora da permissao concedida a este agente
  nesta sessao (restricao informada explicitamente pelo solicitante, na mesma linha do PR-F3-01 a
  PR-F3-10). O texto completo que deveria compor as duas entradas fica registrado abaixo, para
  aplicacao por quem tiver permissao. `BACKLOG-0089` foi ampliado nesta rodada para cobrir tambem o
  PR-F3-11 (onze PRs no total, fechando o Bloco B da Fase 3, PR-F3-05 a PR-F3-11).

### Texto pronto para `docs/CHANGELOG.md`

```
## [Fase 3 — PR-F3-11] - 2026-07-19

### Modalidade imutável e histórico de metas (sétimo PR do Bloco B — fecha o Bloco B mobile
  da Fase 3, PR-F3-05 a PR-F3-11)
- Backend: `POST /v1/metas` passa a exigir `modalidade` (`COFRE_REAL`|`RESERVA_VIRTUAL`) —
  sem ela, `BusinessException` 400 "Escolha a modalidade da meta". Caminho legado do
  onboarding (payload completo antigo) segue válido com o default do modelo (`COFRE_REAL`),
  preservando o contrato do PR-F3-03.
- `MetaService.atualizar` passa a rejeitar qualquer troca de modalidade após a criação,
  inclusive com reserva zerada — endurece a `ADR-0012`, que antes permitia a troca nesse
  caso. Enviar a mesma modalidade no payload continua aceito (não é troca).
- Teste `MetaReservaVirtualTest.trocaDeModalidadeEBloqueadaMesmoComReservaZerada` cobre os
  três cenários: troca com reserva (bloqueada), troca com reserva zerada (bloqueada) e mesma
  modalidade (aceita).
- Mobile: criação de meta exige escolha explícita entre "Cofre real" e "Reserva virtual"
  (textos do glossário, `ADR-0012`) com aviso "A escolha é definitiva para esta meta"; sem
  escolha, erro "Escolha como a meta guarda o dinheiro." Edição mostra a modalidade somente
  leitura ("Definida na criação — não pode ser alterada"). Cards da listagem mostram a
  modalidade. Meta `COFRE_REAL` com `cofreId` ganha "Ver extrato do cofre", navegando para
  `/more/carteiras?contaId=` (rota do PR-F3-08). Metas concluídas mostram "Concluída em
  {dataConclusao} com {valorReservado} · {N} dias" (duração entre `dataInicio` e
  `dataConclusao`).
- `metaPolicy.ts` ganha `verExtratoCofre` (só `COFRE_REAL` com `cofreId`) e
  `duracaoDaMetaConcluidaEmDias` (null sem datas completas ou fora de `CONCLUIDA`). Tipos
  novos/alterados em `src/types/index.ts`: `ModalidadeMeta`, `Meta.modalidade`/`cofreId`/
  `carteiraAlocadaId`, `MetaRequest.modalidade`.
- Maestro `financial-critical.yaml` ajustado em commit de acompanhamento (`feee1cb`) para
  tocar "Cofre real" antes de preencher a meta smoke — necessário pela escolha agora
  obrigatória.
- Sem migration.
- Commit: `6712653` (ajuste de flow Maestro em `feee1cb`). Validações: `./mvnw verify
  -Pintegration-test` → 255 unit + 27 IT, 0 falhas, BUILD SUCCESS; `npx tsc --noEmit` limpo;
  Jest 36/36 (12 suites). Maestro/simulador NÃO EXECUTADO nesta rodada (flow já corrigido,
  mas nunca rodado) — acumula com a pendência crítica de validação visual/E2E do Bloco B
  inteiro (PR-F3-05 a PR-F3-11). Evidência visual claro/escuro também pendente.
```

### Texto pronto para `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`

```
# PR-F3-11 — Modalidade imutável e histórico de metas

**Status local:** `PASS_COM_RESSALVA`
**Data:** 2026-07-19
**Commit:** `6712653` (+ ajuste de flow Maestro em `feee1cb`)

- [x] `POST /v1/metas` exige `modalidade` (400 "Escolha a modalidade da meta" sem ela)
- [x] caminho legado do onboarding preserva default `COFRE_REAL` (contrato do PR-F3-03 intacto)
- [x] `MetaService.atualizar` bloqueia qualquer troca de modalidade após a criação, inclusive
      com reserva zerada (endurece `ADR-0012`)
- [x] mesma modalidade no payload de atualização continua aceita (não é troca)
- [x] `MetaReservaVirtualTest.trocaDeModalidadeEBloqueadaMesmoComReservaZerada` cobre os 3
      cenários (troca c/ reserva, troca c/ reserva zerada, mesma modalidade)
- [x] mobile: escolha obrigatória Cofre real/Reserva virtual na criação (textos do glossário,
      aviso de decisão definitiva)
- [x] mobile: edição mostra modalidade somente leitura
- [x] mobile: card da listagem mostra a modalidade
- [x] mobile: "Ver extrato do cofre" para `COFRE_REAL` com `cofreId` (rota do PR-F3-08)
- [x] mobile: meta concluída mostra data de conclusão, valor reservado final e duração em dias
- [x] `metaPolicy.ts`: `verExtratoCofre`, `duracaoDaMetaConcluidaEmDias`
- [x] tipos mobile: `ModalidadeMeta`, `Meta.modalidade`/`cofreId`/`carteiraAlocadaId`,
      `MetaRequest.modalidade`
- [x] flow Maestro `financial-critical.yaml` ajustado para tocar "Cofre real" antes da meta
      smoke (commit `feee1cb`, mesma sessão)
- [x] nenhuma migration
- [x] `./mvnw verify -Pintegration-test` → 255 unit + 27 IT, 0 falhas, BUILD SUCCESS
- [x] `npx tsc --noEmit` limpo
- [x] Jest 36/36 (12 suites)
- [ ] Maestro/simulador executado de fato para o flow financial-critical (flow já corrigido,
      mas rodada real ainda pendente — ALTA PRIORIDADE, fecha o Bloco B inteiro F3-05 a F3-11)
- [ ] evidência visual do fluxo em tema claro/escuro (acumulado com PR-F3-05 a PR-F3-10)
- [ ] revisão dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR
      específico
- [ ] `CHANGELOG.md`/checklist atualizados diretamente (aplicado manualmente a partir deste bloco)
- [x] nota de rastreabilidade: este PR (F3-11), não o PR-F3-10, é quem fecha o Bloco B mobile
      da Fase 3 (F3-05 a F3-11) — ver observação no relatório de revisão
```

## Recomendacao final

Implementacao coerente com a diretriz de produto da `ADR-0012`: a modalidade de uma meta (cofre real
vs. reserva virtual) passa de "trocavel sob condicao" para "definitiva desde a criacao", eliminando um
estado intermediario que exigia logica condicional tanto no backend quanto na UI. A decisao de tornar a
escolha obrigatoria ja na criacao (em vez de aceitar um default silencioso no cliente mobile) forca o
usuario a entender a diferenca entre as duas modalidades no momento em que ela realmente importa,
apoiada pelos textos do glossario oficial. O historico de conclusao (data, valor final, duracao) e uma
melhoria de UX de baixo risco, puramente aditiva. O ponto mais relevante encontrado nesta rodada nao e
um defeito do PR em si, mas uma correcao de rastreabilidade: o Bloco B da Fase 3, registrado
anteriormente como encerrado no PR-F3-10, foi estendido para incluir este PR, e e este PR quem
efetivamente o fecha — registrado explicitamente para que consultas futuras a `SYSTEM_OVERVIEW.md` nao
fiquem com uma afirmacao desatualizada sem contexto. O risco tecnico central continua sendo a ausencia
de qualquer execucao real de Maestro/simulador para os sete PRs do Bloco B, agora acumulado tambem para
a criacao de meta com modalidade obrigatoria — embora, notavelmente, o flow ja tenha sido corrigido
textualmente pela propria sessao de implementacao (commit `feee1cb`) antes mesmo deste registro.

## Status final

PASS_COM_RESSALVA — ressalvas: (1) flow Maestro para o Bloco B inteiro (incluindo a escolha de
modalidade introduzida por este PR) ja corrigido textualmente (commit `feee1cb`), mas nunca executado
contra simulador real — achado de severidade **MEDIA**, acumulado com a pendencia critica ja registrada
desde o PR-F3-05; (2) evidencia visual claro/escuro pendente, acumulada com PR-F3-05 a PR-F3-10; (3)
`CHANGELOG.md` e o checklist de execucao de PRs nao puderam ser atualizados por este agente por
restricao de permissao de arquivo (texto pronto acima); (4) ausencia de evidencia direta de
revisao/auditoria dedicada a este PR especifico; (5) correcao de rastreabilidade registrada: este PR,
nao o PR-F3-10, e quem fecha o Bloco B mobile da Fase 3 (PR-F3-05 a PR-F3-11).

---

> Relatorio mantido pelo `docs-reporter`.
