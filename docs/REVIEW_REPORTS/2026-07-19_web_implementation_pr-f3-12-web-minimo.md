# Relatorio de Revisao

**Arquivo:** 2026-07-19_web_implementation_pr-f3-12-web-minimo.md

**PR:** PR-F3-12 — Web minimo com drill-down do Dashboard (Fase 3, primeiro PR do Bloco C — web e
consolidacao). Dependencia satisfeita: PR-F3-04 (contrato de navegacao em Origem) publicado no
Bloco A.

**Commit:** `9d1e8a6` (main).

---

## Objetivo

Tornar as origens do Dashboard web navegaveis usando o contrato de navegacao do PR-F3-04
(`navegacao { destino, id, filtros }` em cada `Origem` de `/v1/metricas/{metrica}/origens`), com os
mapeamentos do plano: conta → extrato; parcela → Transacoes (transacao focada); extrato filtravel →
Transacoes com filtros; fatura → Faturas; meta → Metas; posicao → Investimentos. Corrigir rotulos
conforme glossario. Nenhuma linha aparenta ser clicavel sem destino valido.

## Escopo implementado

- **`frontend/src/services/metricasService.ts` (+12/-1):** tipos `DestinoNavegacao`
  (`EXTRATO_CONTA|TRANSACAO|FATURA|META|INVESTIMENTO|TRANSACOES`) e `NavegacaoOrigem`
  (`destino`, `id: number | null`, `filtros: Record<string,string> | null`); `OrigemMetrica` ganha
  `navegacao?: NavegacaoOrigem | null` e `id` passa a `number | null` (origens agregadas como
  `ENTRADAS_COMPETENCIA` vem com `id: null` do backend).
- **`frontend/src/utils/rotaDaNavegacao.ts` (novo):** mapeamento puro destino → rota do app web,
  espelhando o `rotaDaNavegacao` do mobile (PR-F3-08): `EXTRATO_CONTA` →
  `/contas-financeiras?contaId={id}`; `TRANSACAO` → `/transacoes?transacaoId={id}`; `FATURA` →
  `/faturas`; `META` → `/metas`; `INVESTIMENTO` → `/investimentos`; `TRANSACOES` → `/transacoes?`
  + filtros do backend serializados via `URLSearchParams`. Destino desconhecido ou sem ID
  obrigatorio → `null` (cliente nunca inventa link aproximado).
- **`frontend/src/pages/Dashboard.tsx` (+16/-2):** linha de origem com rota valida vira `<button>`
  com chevron (`ChevronRight`), `aria-label` "{descricao}, {valor}. Abrir detalhe" e `useNavigate`;
  linha sem destino permanece `<div>` estatico, sem affordance de clique.
- **`frontend/src/pages/Carteira.tsx` (+11):** `?contaId=` (via `useSearchParams`) abre
  automaticamente o extrato da conta indicada apos o carregamento e remove o parametro da URL
  (`replace: true`) para nao re-disparar.
- **`frontend/src/pages/Transacoes.tsx` (+71/-21):** le `transacaoId` (busca `GET /transacoes/{id}`
  e exibe a transacao focada) ou `inicio`+`fim` (+`tipo`, `q`, `categoriaId`, `carteiraId`,
  `cartaoId` opcionais, repassados a `GET /transacoes/periodo` — filtros do PR-F3-04). Banner
  "Exibindo transacoes filtradas a partir da visao financeira" com acao "Limpar filtro"; estado
  vazio filtrado tem texto proprio. **Correcao de bug pre-existente encontrada durante o PR:**
  `handleImportar`, `carregarAnexos`, `handleUploadAnexo` e `handleDeletarAnexo` estavam
  sintaticamente aninhados dentro de `handleDeletar` (corpo real de `handleDeletar` orfao no fim do
  bloco) — em runtime, "Importar CSV" e os anexos de edicao lancariam `ReferenceError` ao renderizar
  essas secoes; `tsc -b` acusava `TS2304 Cannot find name 'handleImportar'` e afins. Funcoes movidas
  para o escopo do componente; os erros de `tsc` correspondentes sumiram.
- **`frontend/src/services/transacaoService.ts` (+23):** `buscarPorId` e `listarPorPeriodo`
  (tipo `FiltroPeriodo`) consumindo os endpoints existentes do backend.
- **Rotulos conforme glossario:** menu lateral "Dashboard" → "Visao financeira" (alinha com o `h1`
  da pagina e o vocabulario da Fase 2/3); no formulario de transacao, o select que lista cartoes
  estava rotulado "Conta" → corrigido para "Cartao" (glossario distingue Conta financeira de
  Cartao).
- **Testes (Vitest):** `frontend/src/utils/rotaDaNavegacao.test.ts` (novo, 7 testes — um por
  destino suportado + `TRANSACOES` com/sem filtros + destino sem ID obrigatorio/desconhecido →
  `null`); `Dashboard.test.tsx` (+1 teste: origem com navegacao navega via `useNavigate` mockado,
  origem sem navegacao nao e botao); `Carteira.test.tsx` (+1 teste: `?contaId=1` chama
  `listarMovimentos(1)` e abre o extrato; suite envolvida em `MemoryRouter` por causa do
  `useSearchParams`).

Sem mudanca de backend e sem migration neste PR (consome contrato do PR-F3-04 ja publicado).

## Validacoes executadas

- **Vitest:** 12 suites, 44/44 PASS (2 suites novas/ampliadas em relacao ao PR-F3-11).
- **`npx tsc -b`:** nenhum erro nos arquivos tocados pelo PR. Erros pre-existentes em arquivos fora
  do escopo permanecem (`App.tsx`, `ContasFixas.tsx`, `Faturas.tsx`, `Orcamentos.tsx`,
  `Relatorios.tsx`, `useApi.test.tsx`, e `Carteira.test.tsx` linhas 17-18 `tipo` — anteriores ao
  PR; o build de producao usa `vite build`, que nao roda tsc, por isso nunca bloquearam).
- **`vite build`:** sucesso.
- **`eslint` nos arquivos tocados:** 0 erros (17 warnings pre-existentes de `any`/deps no
  `Transacoes.tsx` legado).
- **Verificacao E2E real (Playwright + chromium, backend Spring Boot local em banco Postgres limpo
  `gf_verify_f312`, usuario novo registrado e onboarding minimo via payloads identicos aos do
  mobile):**
  1. Login web → Dashboard → expandir "Disponivel agora" → origem "Conta Principal" exibida com
     chevron → clique → `/contas-financeiras` com extrato da conta aberto automaticamente
     (movimento "Saldo inicial da carteira" visivel).
  2. "Resultado mensal" → origem "Entradas por competencia" clicavel; origens "Saidas nao cartao" e
     "Consumo de cartao" (sem `navegacao` no backend) renderizadas **sem** botao (contagem de
     botoes = 0, conforme criterio "nenhuma linha aparenta ser clicavel sem destino valido").
  3. Clique em "Entradas por competencia" → `/transacoes?inicio=2026-07-01&tipo=ENTRADA&fim=2026-07-31`
     com banner de filtro; "Limpar filtro" → `/transacoes` sem parametros.
  - Evidencia visual: `docs/REVIEW_REPORTS/evidence/2026-07-19_web-drilldown-f3-12/` (6 capturas:
    dashboard, origens abertas com chevron, extrato via drill-down, origens do resultado mensal,
    transacoes filtradas com banner, transacoes apos limpar).

## Testes NAO EXECUTADOS

- Destinos `FATURA`, `META`, `INVESTIMENTO` e `TRANSACAO` (parcela) nao foram exercitados no E2E
  real por exigirem massa de dados (cartao com fatura, meta, posicao de investimento, parcela) —
  cobertos pelo teste unitario de `rotaDaNavegacao` (mapeamento) e pelas telas de destino ja
  existentes, que nao recebem parametros nesses casos (rotas estaticas).
- Suite Playwright formal (`npm run test:e2e`) nao foi rodada — o drive E2E desta rodada foi um
  script dedicado de verificacao, nao um teste versionado.

## Achados e ressalvas

1. **Bug pre-existente corrigido** (aninhamento de handlers em `Transacoes.tsx`, descrito acima) —
   corrigido dentro deste PR por estar no arquivo do escopo e ser bloqueador de runtime das
   funcoes de importacao CSV e anexos.
2. **Ambiente dev:** reload completo da pagina derruba a sessao web local (refresh token cookie
   nao chega em requisicao cross-origin `5173 → 8081`; erros `422 Refresh token nao fornecido` e
   `403 CSRF_REQUIRED` no console). Pre-existente, nao afeta navegacao client-side (o drill-down
   funciona normalmente); relevante para quem for depurar a stack local.
3. **Banco dev local `gestor_financeiro`:** migration `V36__remove_redundant_card_parcels.sql`
   aborta com "Divergencia entre parcelas e fatura_lancamentos; contrato abortado" sobre os dados
   antigos do banco local — o backend nao sobe nesse banco. A verificacao usou banco limpo
   (`gf_verify_f312`, descartado ao final). Registrado para investigacao futura (dados locais, nao
   reproduzido em banco novo).
4. `CHANGELOG.md` e `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` seguem pendentes de atualizacao
   agregada (BACKLOG-0089, ampliado para incluir este PR); o PR-F3-13 ("Legados e documentacao")
   cobre essa consolidacao.

## O que ficou pendente (texto pronto para consolidacao no PR-F3-13)

- **CHANGELOG.md:** "PR-F3-12 (2026-07-19, commit `9d1e8a6`): origens do Dashboard web navegaveis
  pelo contrato de drill-down do PR-F3-04 (conta → extrato; parcela → transacao focada; extrato
  filtravel → Transacoes com filtros; fatura → Faturas; meta → Metas; posicao → Investimentos);
  linhas sem destino valido nao sao clicaveis; rotulos corrigidos conforme glossario (menu 'Visao
  financeira'; select de cartao); correcao de escopo dos handlers de importacao CSV/anexos em
  Transacoes."
- **Checklist:** marcar PR-F3-12 como concluido com evidencia neste relatorio.
