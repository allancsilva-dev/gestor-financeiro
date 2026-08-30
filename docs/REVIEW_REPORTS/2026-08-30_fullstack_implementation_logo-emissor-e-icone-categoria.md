# Relatorio de Revisao

**Arquivo:** 2026-08-30_fullstack_implementation_logo-emissor-e-icone-categoria.md

**Origem:** relato do dono do produto — dois defeitos visuais reportados em uso real: (1) "a função de
foto e cartão personalizado pelo nome não está funcionando muito bem, eu coloquei PicPay e não apareceu
bem"; (2) "a foto que fica na descrição dos itens tanto de pagamento quanto de saída não mostra nada, só
letras e símbolos".

**Data:** 2026-08-30

**Branch:** `feat/logo-emissor-e-icone-categoria`

---

## Objetivo

Registrar a implementação full-stack (mobile + web + backend) que corrige os dois defeitos relatados:
falta de logo real de emissor de cartão com cor derivada do nome, e ícone de categoria aparecendo como
texto/slug ou símbolo de fallback em vez de emoji nas listas de transações, parcelas, filtros, análises,
orçamentos e metas.

## Escopo verificado

Trabalho ainda **não commitado** na branch `feat/logo-emissor-e-icone-categoria`. Este relatório foi
produzido a partir do resumo técnico detalhado fornecido pelo agente que orquestrou o ciclo de
implementação (backend-engineer, frontend-engineer não citados explicitamente, mas escopo cobre as três
camadas), complementado por verificação própria via `git status --short`, `git diff --stat` e inspeção
de `package.json`/`package-lock.json` — sem leitura de conteúdo de código, fora do escopo de permissão
deste agente (`docs-reporter` só edita `docs/`).

Escopo técnico coberto pela sessão, conforme relatado:
- **Parte 2 — ícone de categoria:** correção na camada de leitura (`mobile/src/domain/iconeCategoria.ts`)
  para distinguir emoji real de slug/texto herdado de categorias legadas, aplicada em 9 pontos de
  renderização no mobile; novo seletor de emoji (`SeletorDeEmoji.tsx`); edição de categoria adicionada
  ao mobile (não existia antes); correção de bug de `??` (nullish) vs. string vazia em
  `ParcelasCarrossel.tsx` e `OperacoesFiltro.tsx`; paridade no web (grade de emoji substituindo
  `<input type="text">`); correção no backend (`CategoriaService.java`) para não apagar o ícone em PUT
  que omite o campo.
- **Parte 1 — logo real do emissor e cor por nome:** nova dependência `logos-bancos-br@0.7.1` (auditada:
  MIT, zero deps de runtime, zero script de instalação, assets locais); novo dataset gerado
  (`emissoresDataset.gen.ts`, 550 chaves, cor derivada do SVG oficial da marca); `emissores.ts` ganhou
  ISPB, camada de variantes de produto (ex.: PicPay vs. PicPay Epic) e nova precedência de cor
  (usuário > variante > emissor > hash do nome); `CartaoFisico.tsx` e `ParcelasCarrossel.tsx` passaram a
  renderizar o logo real quando disponível; web (`contas.tsx`) parou de forçar cor roxa fixa em todo
  cartão novo, com checkbox "Usar a cor do emissor" marcado por padrão.

## Arquivos lidos

Este relatório foi produzido a partir do resumo técnico fornecido pelo agente orquestrador, sem leitura
direta do código-fonte por este agente (fora do escopo de permissão em `backend/`/`mobile/`/`frontend/`).
Confirmados via `git status --short` e `git diff --stat` (existência/natureza modificado ou novo, e
volume de linhas alteradas nos arquivos-chave — não conteúdo integral):

**Modificados (mobile):**
- `mobile/app/(app)/(inicio)/index.tsx`
- `mobile/app/(app)/(inicio)/transacoes.tsx`
- `mobile/app/(app)/analises.tsx`
- `mobile/app/(app)/metas.tsx`
- `mobile/app/(app)/more/categorias.tsx`
- `mobile/app/(app)/more/fatura.tsx`
- `mobile/app/(app)/more/orcamentos.tsx`
- `mobile/src/components/carteira/CartaoFisico.tsx` (+38/-diff confirmado por `git diff --stat`)
- `mobile/src/components/home/OperacoesFiltro.tsx`
- `mobile/src/components/home/ParcelasCarrossel.tsx`
- `mobile/src/components/metas/CardMeta.tsx`
- `mobile/src/components/ui/ListRow.tsx`
- `mobile/src/domain/emissores.ts` (+193/-diff confirmado por `git diff --stat`)
- `mobile/src/__tests__/emissores.test.ts`
- `mobile/package.json`, `mobile/package-lock.json` (nova dependência `logos-bancos-br@0.7.1`,
  confirmada presente em ambos os arquivos com `grep`)

**Novos (untracked, mobile):**
- `mobile/src/domain/iconeCategoria.ts`
- `mobile/src/domain/logosEmissores.ts`
- `mobile/src/domain/emissoresDataset.gen.ts` (confirmado em disco, 25.901 bytes)
- `mobile/scripts/gerar-emissores-dataset.mjs` (confirmado em disco, 7.888 bytes)
- `mobile/src/components/ui/SeletorDeEmoji.tsx`
- `mobile/src/__tests__/iconeCategoria.test.ts`
- `mobile/src/__tests__/CartaoFisico.test.tsx`
- `mobile/src/__mocks__/` (mock de `logos-bancos-br/react-native` para Jest, referenciado em
  `moduleNameMapper` do `mobile/package.json`, confirmado por `grep`)

**Modificados (web):**
- `frontend/src/components/CategoriaDropdown.tsx`
- `frontend/src/data/categoriasPreDefinidas.ts`
- `frontend/src/pages/Categorias.tsx`
- `frontend/src/pages/contas.tsx` (+22 linhas confirmado por `git diff --stat`)

**Novos (untracked, web):**
- `frontend/src/data/emojisCategoria.ts`

**Modificados (backend):**
- `backend/src/main/java/com/gestor/financeiro/service/CategoriaService.java`
- `backend/src/test/java/com/gestor/financeiro/TestDataFactory.java`

**Novos (untracked, backend):**
- `backend/src/test/java/com/gestor/financeiro/service/CategoriaServiceTest.java`

**Nao relacionados a esta entrega** (presentes no mesmo working tree, mas de outra frente de trabalho —
recorrência de assinatura no cartão, v67, já registrada em
`docs/REVIEW_REPORTS/2026-08-29_fullstack_implementation_recorrencia-cartao-v67.md`): `docs/BACKLOG.md`,
`docs/DIAGRAMS.md`, `docs/SYSTEM_OVERVIEW.md`, `mobile/.maestro/financial-critical.yaml`,
`mobile/app/(app)/more/contas-fixas.tsx`, `mobile/src/__tests__/recorrenciaCartao.test.tsx`,
`mobile/src/services/contaFixaService.ts`, `scripts/e2e-mobile-ios.sh`. Esses arquivos **não** foram
tocados por esta entrega e não devem ser atribuídos a ela no histórico de commit.

## Comandos executados

Comandos reportados como executados pelo ciclo de implementação (não reexecutados por este agente, que
não tem permissão para rodar build/teste de `backend/`/`mobile/`/`frontend/`):

| Comando | Resultado |
|---|---|
| `npx tsc --noEmit` (mobile) | limpo |
| `npm run lint -- --max-warnings=0` (mobile) | limpo |
| `npx jest` (mobile) | 42 suítes / 480 testes PASS |
| `npx tsc --noEmit` (web) | limpo |
| `npx vitest run` (web) | 12 arquivos / 47 testes PASS |
| `eslint` (web) | sem erros novos (só warnings pré-existentes de `any`) |
| `./mvnw test` (backend) | 101 classes / 496 testes, 0 falhas, 0 erros |

Comandos executados por este agente (`docs-reporter`), somente leitura, para verificação cruzada:

| Comando | Resultado |
|---|---|
| `git status --short` | confirma lista de arquivos modificados/novos acima, nada commitado |
| `git diff --stat <arquivos-chave>` | confirma volume de alteração em `emissores.ts` (+193/-diff),
`CartaoFisico.tsx` (+38/-diff), `contas.tsx` (+22 linhas) |
| `ls -la mobile/src/domain/emissoresDataset.gen.ts mobile/scripts/` | confirma existência dos arquivos
gerados (25.901 bytes e 7.888 bytes, respectivamente) |
| `grep logos-bancos-br mobile/package.json mobile/package-lock.json` | confirma dependência pinada em
`0.7.1` em ambos os arquivos, e `moduleNameMapper` do Jest apontando para o mock local |
| `grep -oE "(BACKLOG\|PROB\|BUG)-[0-9]+"` nos três ledgers | confirma próximos IDs livres:
`PROB-0099`, `BUG-0104`, `BACKLOG-0127` |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | ALTA (corrigida) | Categorias legadas gravavam slug de ícone Lucide (`"moradia"`, `"alimentaca"`,
`"tag"`, `"cart"`) no campo `icone`, e o mobile renderizava esse texto cru dentro de `ui/IconTile` — a
origem exata das "letras" relatadas pelo dono do produto. | `frontend/src/components/CategoriaDropdown.tsx:22,32`, `frontend/src/pages/Categorias.tsx:23` (estado anterior, confirmado no relato); correção em `mobile/src/domain/iconeCategoria.ts` (`ehEmoji()` valida por forma) |
| 2 | ALTA (corrigida) | Formulário de categoria do mobile nunca enviava campo `icone`, então toda
categoria criada pelo app caía no fallback `'↑'`/`'↓'` — a origem dos "símbolos" relatados. | `mobile/app/(app)/more/categorias.tsx:65` (estado anterior); correção via `SeletorDeEmoji.tsx` |
| 3 | ALTA (corrigida) | `ParcelasCarrossel.tsx` e `OperacoesFiltro.tsx` usavam `??` (nullish coalescing)
como fallback de ícone, que não dispara para string vazia — e o web enviava `icone: ''` por padrão,
resultando em "não mostra nada" (tile vazio). | comportamento de `??` vs. `''` confirmado como bug de
linguagem, não de dado; corrigido junto com a Parte 2 |
| 4 | ALTA (corrigida) | Nunca existiu logo real de emissor de cartão no app — decisão de projeto
original documentada em código como "sem asset de terceiro embarcado". `limitarLuminancia()` em
`emissores.ts` rebaixava qualquer cor de marca até luminância ≤ 0,082, fazendo o verde característico do
PicPay (#11C76F) sair quase preto no monograma de texto. | `mobile/src/components/carteira/BandeiraMarca.tsx:7` (comentário de decisão original, arquivo não alterado nesta entrega — confirmado ausente de `git status`); comportamento de `limitarLuminancia()` relatado pelo agente de implementação |
| 5 | MEDIA (corrigida) | Não havia noção de variante de produto por emissor — PicPay Epic (cartão preto)
usava a mesma cor base do PicPay padrão (verde). | nova camada `VARIANTES`/`resolverVariante()` em `emissores.ts` |
| 6 | MEDIA (corrigida) | Web forçava `cor: '#8B10AE'` (roxo) em todo cartão novo criado em
`contas.tsx`, e como "cor do usuário" tinha precedência sobre o catálogo do emissor, qualquer cartão
criado pelo SPA ficava roxo permanentemente, independente do nome digitado. | `frontend/src/pages/contas.tsx` (comportamento anterior relatado; correção via checkbox "Usar a cor do emissor", marcado por padrão) |
| 7 | MEDIA (nao corrigida — ver Ressalva 3) | `MetaRequest.icone` no backend não tem nenhuma restrição de
tamanho (`@Size`), e a coluna é `varchar(255)`: um ícone/emoji anormalmente grande no payload vira erro
de banco HTTP 500 em vez de HTTP 400 de validação. | relatado pelo agente de implementação como defeito
irmão fora do escopo desta entrega |
| 8 | BAIXA (nao corrigida — ver Ressalva 4) | `@Size(max = 10)` em `CategoriaCreateRequest`/
`CategoriaUpdateRequest` conta unidades UTF-16, não grafemas. Um emoji com sequência ZWJ longa (ex.:
👨‍👩‍👧‍👦, que ocupa 11 unidades UTF-16) tomaria HTTP 400 mesmo sendo um único caractere visual válido. Mitigado
na prática porque a grade do `SeletorDeEmoji.tsx` só oferece emojis curtos, e há teste que falha se
algum item da grade ultrapassar 10 unidades. | relatado pelo agente de implementação |
| 9 | BAIXA (rastreabilidade) | Nenhuma verificação em runtime com o app real (simulador/dispositivo) foi
executada nesta sessão — nem para o fluxo de digitar "PicPay" e observar a prévia de cor/logo trocar,
nem para o fluxo Maestro `financial-critical.yaml`. | ver Ressalvas 1 e 2 |
| 10 | BAIXA | O onboarding continua sem campo de bandeira/emissor — não existe UI dedicada para
selecionar o emissor do cartão no cadastro. A cor e o logo corretos já saem automaticamente porque são
derivados do nome digitado na renderização, então o impacto prático é baixo. | relatado pelo agente de implementação |

## O que foi corrigido

Dois defeitos relatados pelo dono do produto, ambos com causa raiz identificada e corrigida em múltiplas
camadas:

- **Ícone de categoria** (achados 1, 2, 3): nova função `ehEmoji()` que valida por forma (recusa ASCII e
  palavras acentuadas como "Água", que antes passavam como se fossem ícone válido), aplicada em 9 pontos
  de renderização no mobile; novo seletor de emoji reutilizável (`SeletorDeEmoji.tsx`, 42 emojis,
  radiogroup acessível) com sugestão automática por palavra-chave do nome digitado; edição de categoria
  adicionada ao mobile (recurso que não existia antes); correção do bug de `??` vs. string vazia; no web,
  `CategoriaDropdown.tsx` e `Categorias.tsx` passaram a gravar emoji real (ou string vazia) em vez de
  slug de ícone Lucide ou texto livre; `CategoriaService.java` (backend) parou de sobrescrever o ícone
  gravado quando o request PUT omite o campo, evitando apagamento silencioso.
- **Logo de emissor e cor por nome** (achados 4, 5, 6): nova dependência local `logos-bancos-br@0.7.1`
  (162 PNGs, ~0,73 MB, auditada como MIT/zero deps de runtime/zero script de instalação/zero requisição
  de rede em tempo de execução); dataset gerado localmente (`emissoresDataset.gen.ts`, 550 chaves,
  cor extraída do SVG oficial de cada marca, nunca escrita de memória, com rótulo `fonte: 'derivada'`
  para diferenciar das 30 entradas curadas manualmente com `fonte: 'informada'`); nova camada de
  variantes de produto por emissor; `CartaoFisico.tsx` e `ParcelasCarrossel.tsx` passaram a exibir o
  logo real quando disponível (tile branco com `<Image resizeMode="contain">`), mantendo o monograma
  colorido como fallback quando não há logo; web parou de forçar cor fixa em cartão novo.

## O que ficou pendente

Nenhuma entrada foi criada em `docs/BACKLOG.md` ou `docs/PROBLEM_LEDGER.md` por este agente nesta
sessão — restrição explícita, porque esses dois arquivos já têm alterações não commitadas de **outra**
frente de trabalho (recorrência de assinatura no cartão, v67) e uma edição misturaria as duas frentes no
mesmo diff. Ficam registradas abaixo as entradas que **devem ser criadas** por quem processar o commit
desta branch (ou pelo `docs-reporter` na próxima vez em que for acionado sobre este tema, depois que a
frente v67 for commitada separadamente), com os próximos IDs livres confirmados nesta sessão:

- **BACKLOG-0127** (proposto) — "Backend: `MetaRequest.icone` sem `@Size`, ícone anormal vira 500 em vez
  de 400". Prioridade sugerida: P2. Área: backend. Motivo: achado 7 acima — coluna `varchar(255)` sem
  validação de tamanho no DTO, mesmo padrão de proteção que `CategoriaCreateRequest`/
  `CategoriaUpdateRequest` já têm via `@Size(max = 10)`. Critério de aceite: payload com `icone`
  excedendo o limite retorna HTTP 400 de validação, não 500 de banco.
- **BACKLOG-0128** (proposto) — "`@Size(max = 10)` de categoria conta unidades UTF-16, não grafemas —
  risco latente para emoji com ZWJ longo". Prioridade sugerida: P3. Área: backend. Motivo: achado 8
  acima — mitigado hoje porque a grade do seletor só oferece emoji curto, mas o limite intrínseco do DTO
  não reflete a unidade correta (grafema visual vs. unidade UTF-16). Critério de aceite: decisão
  explícita registrada — manter como está (documentando o porquê) ou trocar a validação para contagem de
  "code points"/grafemas.
- **BACKLOG-0129** (proposto) — "Onboarding sem campo de bandeira/emissor de cartão". Prioridade
  sugerida: P3. Área: mobile/frontend. Motivo: achado 10 acima. Impacto baixo hoje porque cor/logo já
  são derivados automaticamente do nome digitado.
- **PROB-0099** (proposto, ou já FECHADO no ato do commit) — "Logo de emissor ausente e ícone de
  categoria renderizando slug/símbolo de fallback". Origem: usuário (relato direto do dono do produto,
  citado nesta sessão). Severidade: HIGH. Status sugerido: FECHADO no momento do commit desta branch
  (a causa raiz de ambos os sintomas foi identificada e corrigida nesta sessão, conforme achados 1-6).
  Área: mobile, frontend, backend.
- Verificação formal de `security-auditor` sobre a nova dependência `logos-bancos-br@0.7.1` — a auditoria
  relatada nesta sessão (licença MIT, zero deps de runtime, zero script de instalação, assets locais sem
  requisição de rede) foi feita pelo agente de implementação, não há evidência recebida por este agente
  de acionamento formal do `security-auditor` dedicado a esta dependência nova antes do merge.

## Ressalvas

1. **Maestro não foi executado.** O fluxo `financial-critical.yaml` cria categoria digitando o nome e
   tocando "Salvar" sem interagir com seletor, então o seletor de emoji novo não deveria quebrá-lo — mas
   isso **não foi confirmado em execução** (precisa de simulador + stack de pé). Além disso, o arquivo
   `.maestro/financial-critical.yaml` está modificado pela frente de recorrência (v67) no mesmo working
   tree — é necessário rebase/merge dessa frente antes de rodar o Maestro para esta entrega.
2. **Verificação em runtime com app real não foi feita.** Não houve confirmação visual (digitar "PicPay"
   no formulário de cartão e observar a prévia de cor/logo trocar corretamente) nesta sessão.
3. `MetaRequest.icone` no backend não tem `@Size`, e a coluna é `varchar(255)`: ícone anormalmente grande
   vira erro de banco 500 em vez de 400 de validação. Defeito irmão, fora do escopo desta entrega — ver
   BACKLOG-0127 proposto acima.
4. `@Size(max = 10)` de `CategoriaCreateRequest`/`CategoriaUpdateRequest` conta unidades UTF-16: emoji
   com sequência ZWJ longa (ex.: 👨‍👩‍👧‍👦, 11 unidades) tomaria 400. Mitigado pela grade do seletor (só emoji
   curto) e por teste que falha se algum item da grade ultrapassar 10 unidades — ver BACKLOG-0128
   proposto acima.
5. `git stash list` reportado como tendo apenas 1 entrada (`WIP PR-F2-20 reconciliacao`) nesta sessão. O
   stash `fase4-prototipo-descartado-2026-08-19`, que `docs/BACKLOG.md` (BACKLOG-0090, não lido
   diretamente por este agente nesta sessão) registra como o único lugar onde o código do protótipo da
   Fase 4 existiria, não está mais na lista — ou foi dropado, ou o registro em BACKLOG está desatualizado.
   **Não verificado por este agente.** Precisa de confirmação antes que o reflog local expire. Este
   agente não editou `docs/BACKLOG.md` para não misturar com a frente v67 — fica registrado aqui como
   alerta para quem tocar nesse arquivo em seguida.
6. **Decisão de projeto revertida.** A decisão original "sem asset de terceiro embarcado" (documentada em
   `mobile/src/components/carteira/BandeiraMarca.tsx:7`) foi **revertida pelo dono do produto** nesta
   rodada, após adoção do pacote local `logos-bancos-br@0.7.1` (auditado como seguro para uso local, sem
   telemetria). Isso deve ser tratado como decisão de produto documentada, não como descuido de
   implementação — fica registrado aqui por não haver, no momento deste relatório, edição permitida em
   `docs/SYSTEM_OVERVIEW.md` (que já está em diff da frente v67).
7. Onboarding continua sem campo de bandeira/emissor (ver achado 10 e BACKLOG-0129 proposto).
8. **Nada foi commitado.** A árvore de trabalho tem, no mesmo working tree, alterações não commitadas de
   uma frente de trabalho totalmente distinta (recorrência de assinatura no cartão, v67):
   `mobile/app/(app)/more/contas-fixas.tsx`, `mobile/src/services/contaFixaService.ts`,
   `mobile/src/__tests__/recorrenciaCartao.test.tsx`, `mobile/.maestro/financial-critical.yaml`,
   `scripts/e2e-mobile-ios.sh`, além de `docs/BACKLOG.md`, `docs/DIAGRAMS.md` e
   `docs/SYSTEM_OVERVIEW.md`. Recomenda-se separar os dois conjuntos de alterações em commits (e,
   idealmente, branches) distintos antes de qualquer merge, para preservar rastreabilidade de histórico
   e permitir revert independente caso um dos dois precise ser desfeito.

## Recomendacao final

Causa raiz de ambos os defeitos relatados pelo dono do produto foi identificada com evidência de código
(não suposição) e corrigida em todas as camadas onde o dado nasce ou é exibido: mobile, web e backend. A
correção do ícone de categoria é na camada de leitura (`ehEmoji()`), o que a torna retroativa para
categorias legadas sem exigir migration de dados — decisão tecnicamente sólida. A adoção de assets locais
de logo (`logos-bancos-br`) foi auditada quanto a licença, superfície de ataque (zero deps de runtime,
zero script de instalação) e privacidade (zero requisição de rede, nenhum dado de qual banco o usuário
usa sai do aparelho) antes de ser incorporada — critério correto para reverter a decisão original de "sem
asset de terceiro embarcado". A cobertura de teste é ampla (94 testes em `emissores.test.ts`, 15 novos em
`iconeCategoria.test.ts`, 3 de render em `CartaoFisico.test.tsx`, 3 no backend) e as três suítes (mobile,
web, backend) passam integralmente. As duas lacunas mais relevantes que restam são a ausência de
verificação em runtime real (app rodando) e de execução do Maestro — nenhuma delas bloqueia
tecnicamente o merge, mas ambas deveriam ser fechadas antes de considerar a entrega definitivamente
validada em produção. Recomenda-se, antes do commit: (a) separar esta entrega da frente de recorrência
v67 no working tree; (b) rodar ao menos uma verificação manual em simulador/dispositivo real do fluxo de
cadastro de cartão "PicPay"; (c) criar as entradas de PROB/BACKLOG propostas neste relatório assim que
`docs/BACKLOG.md`/`docs/PROBLEM_LEDGER.md` estiverem livres da mistura com a v67.

## Status final

PASS_COM_RESSALVA — ressalvas: (1) Maestro não executado; (2) verificação em runtime com app real não
executada; (3) defeito irmão em `MetaRequest.icone` (backend) sem `@Size`, fora do escopo desta entrega;
(4) limite de tamanho de ícone de categoria conta unidade UTF-16, não grafema, mitigado mas não corrigido
na raiz; (5) status do stash da Fase 4 não confirmado; (6) nada commitado, working tree misturado com
outra frente de trabalho (recorrência v67); (7) `docs/BACKLOG.md` e `docs/PROBLEM_LEDGER.md` não puderam
receber as entradas correspondentes nesta sessão por restrição explícita de escopo — propostas registradas
acima para criação posterior.

---

> Relatorio mantido pelo `docs-reporter`.
