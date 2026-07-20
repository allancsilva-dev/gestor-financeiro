# Relatorio de Revisao

**Arquivo:** 2026-07-19_fullstack_implementation_pr-f3-13-legados-documentacao.md

**PR:** PR-F3-13 — Legados e documentacao (Fase 3, segundo PR do Bloco C — **fecha o Bloco C e a
Fase 3 "Experiencia simples", PR-F3-01 a PR-F3-13**). Dependencias satisfeitas: PR-F3-07 e
PR-F3-12.

**Commit:** `90bc02d` (main) — código. Documentação no commit de evidência subsequente.

---

## Objetivo

Remover os consumos do endpoint legado `/dashboard/resumo` de mobile e frontend, marcar o
endpoint como deprecated no codigo/OpenAPI sem remove-lo, executar varredura de linguagem
conforme glossario e consolidar glossario/changelog/checklist/review report.

## Escopo implementado

### Remocao do legado `/dashboard/resumo`

- **`mobile/app/(app)/perfil.tsx`:** removida a query `['dashboard-resumo']`
  (`GET /v1/dashboard/resumo`) e o grid de 4 contadores que ela alimentava (Metas, Categorias,
  "Cartoes" — que na verdade exibia `totalContas` —, Contas Fixas). O perfil mantem avatar,
  dados pessoais, seguranca e logout. Import de `DashboardResumo` e `useQuery` removidos.
- **Invalidacoes orfas removidas (10 ocorrencias em 6 arquivos):** `metas.tsx` (5),
  `more/carteiras.tsx` (1), `more/contas-fixas.tsx` (1), `more/faturas.tsx` (1),
  `src/components/EditarTransacaoModal.tsx` (1), `src/components/NovaTransacaoModal.tsx` (1) —
  todas eram `invalidateQueries({ queryKey: ['dashboard-resumo'] })` sobre uma query que deixou
  de existir.
- **`mobile/src/types/index.ts`:** interface `DashboardResumo` removida.
- **`frontend/src/services/dashboardService.ts`:** metodo `resumo()` e interface
  `DashboardResumo` removidos — nenhum chamador existia (o Dashboard web ja consumia somente
  `/v1/metricas` desde a Fase 2).
- **Backend `DashboardController.obterResumo`:** anotado `@Deprecated` e
  `@Operation(deprecated = true)` com descricao apontando `/v1/metricas` e `/v1/compromissos`.
  **Endpoint NAO removido** (contrato preservado para clientes antigos), sem mudanca de
  comportamento.

### Varredura de linguagem (glossario)

- Web: "Deletar"/"deletar"/"deletada(o)!" em textos visiveis → "Excluir"/"excluir"/
  "excluida(o)!" em `Transacoes.tsx`, `contas.tsx`, `Categorias.tsx`, `ContasFixas.tsx`
  (botoes, confirms e toasts; identificadores de codigo como `handleDeletar` preservados).
- Web `Onboarding.tsx`: passo "Carteira" → "Conta"; default "Carteira Principal" → "Conta
  Principal"; rotulo do resumo "Carteira" → "Conta" (glossario: Carteira e exibida como
  "Conta").
- Web `Faturas.tsx`: "Erro ao carregar carteiras" → "contas"; "Selecione uma carteira" →
  "Selecione uma conta".
- Mobile `more/contas-fixas.tsx`: "Selecione a carteira da execucao automatica." → "a conta".
- Mobile `more/index.tsx`: item Investimentos, sub "Carteira" → "Posicoes" (evita colisao com o
  termo reservado Carteira/Conta do glossario).
- Nenhum flow Maestro referencia os textos alterados (`grep Carteira mobile/.maestro/*.yaml`
  vazio).

### Documentacao

- **`docs/GLOSSARIO.md`:** nova secao "Experiencia simples (Fase 3)" — Compromissos proximos
  (COMPROMETIDO vs PREVISTO, `FALHA_SALDO`), Previsto, Sugestao de categoria, Navegacao de
  origem (drill-down) e Modalidade da meta (imutavel, endurecimento da ADR-0012).
- **`docs/CHANGELOG.md` e `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`: BLOQUEADOS por
  permissao tambem para a sessao principal** (erro "directory denied by permission settings" na
  tentativa de edicao desta rodada) — nao apenas para o `docs-reporter`, como registrado nas
  rodadas anteriores. O texto pronto esta na secao abaixo; BACKLOG-0089 atualizado para refletir
  a Fase 3 completa e apontar para ca.

## Validacoes executadas

- **Backend:** `./mvnw verify -Pintegration-test` → BUILD SUCCESS (exit 0), 277 testes unitarios
  (surefire) + 27 ITs (failsafe), 0 falhas.
- **Mobile:** `npx tsc --noEmit` limpo; Jest 36/36 PASS (12 suites).
- **Frontend:** Vitest 44/44 PASS (12 suites); `vite build` OK.
- **Runtime (backend local em banco limpo `gf_verify_f313`, descartado ao final):**
  `/v3/api-docs` expõe `GET /api/v1/dashboard/resumo` com `deprecated: true` e summary "Resumo
  financeiro (legado)"; o endpoint continua respondendo HTTP 200 com o contrato integral para
  usuario autenticado (nao foi removido).
- Varredura confirmada por grep: zero ocorrencias de `dashboard/resumo`, `dashboard-resumo` e
  `DashboardResumo` em `mobile/` e `frontend/src` apos as remocoes.

## Texto pronto para CHANGELOG/CHECKLIST (BACKLOG-0089 — basta colar)

### CHANGELOG.md (inserir no topo, antes de "[Fase 2 — PR-F2-20]")

```markdown
## [Fase 3 (UX) — Experiência simples, PR-F3-01 a PR-F3-13] - 2026-07-17 a 2026-07-19

Consolidação do BACKLOG-0089. Detalhes por PR em `docs/REVIEW_REPORTS/`. Nenhuma migration.

### Bloco A — Backend aditivo (2026-07-17)
- **PR-F3-01** (`3db4979`): `GET /v1/compromissos?ate=` — itens FATURA/PARCELA (Comprometido,
  cálculo compartilhado com `MetricasService`) e CONTA_FIXA (PREVISTO, fora do total); alerta
  `FALHA_SALDO`; `Clock`, ownership e horizonte validados.
- **PR-F3-02** (`483ef36`): `GET /v1/transacoes/sugestao-categoria` determinística (descrição
  normalizada → mais usada em 90 dias → menor ID; `criterio: NENHUMA` sem resultado).
- **PR-F3-03** (`ccd0f10`): onboarding mínimo — `cartao` e `categorias` opcionais/no-op;
  `finalizar()` transacional, com lock e idempotência; payload completo antigo válido.
- **PR-F3-04** (`7cc4aeb`): filtros `categoriaId`/`carteiraId`/`cartaoId` em
  `/v1/transacoes/periodo`; `Origem` ganha `navegacao { destino, id, filtros }` (EXTRATO_CONTA,
  TRANSACAO, FATURA, META, INVESTIMENTO, TRANSACOES); origem sem destino permanece informativa.

### Bloco B — Mobile (2026-07-19)
- **PR-F3-05** (`413d191`): lançamento rápido — data hoje, valor com autoFocus, última
  conta/cartão do dispositivo, sugestão de categoria com um toque, "Repetir lançamento" com
  confirmação.
- **PR-F3-06** (`0c892bc`): tela Visão financeira só com `/v1/metricas`;
  `ComposicaoMetricaModal` extraído; linguagem do glossário.
- **PR-F3-07** (`628cf8e`): home reduzida em ordem fixa, máximo quatro requests; comprometidos e
  previstos separados; resumo legado/projeção/grid removidos da home.
- **PR-F3-08** (`672d97b`): drill-down mobile pelo contrato F3-04; `carteiras` aceita
  `?contaId=`, `transacoes` aceita `?transacaoId=`/período; linha sem destino não é clicável.
- **PR-F3-09** (`0849847`): onboarding em etapa única (só `carteira`; "Conta Principal", saldo
  vazio = 0); Maestro atualizado no mesmo PR.
- **PR-F3-10** (`f0b27de`): setup progressivo — CTAs contextuais (cartão inline, pacote de 9
  categorias, metas/recorrências) e checklist discreto da home sem requests extras.
- **PR-F3-11** (`6712653` + `feee1cb`): modalidade COFRE_REAL/RESERVA_VIRTUAL obrigatória e
  imutável (endurece ADR-0012); cards com modalidade; extrato do cofre; concluídas com data,
  valor final e duração.

### Bloco C — Web e consolidação (2026-07-19)
- **PR-F3-12** (`9d1e8a6`): origens do Dashboard web navegáveis pelo contrato F3-04; linha sem
  destino não aparenta clicável; rótulos do glossário; E2E real com evidência em
  `docs/REVIEW_REPORTS/evidence/2026-07-19_web-drilldown-f3-12/`.
- **PR-F3-13**: consumo de `/dashboard/resumo` removido de mobile e frontend; endpoint marcado
  deprecated no OpenAPI sem remoção; varredura de linguagem (deletar→excluir, carteira→conta);
  glossário e rastreabilidade consolidados.
```

### CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md (apendar ao final)

```markdown
---

# Fase 3 (UX) — Experiência simples (PR-F3-01 a PR-F3-13)

Plano rev. 2 (2026-07-17). Execução direta na main. Nenhuma migration.

| # | PR | Título | Status | Commit | Data |
|---|----|--------|--------|--------|------|
| 1 | PR-F3-01 | Compromissos próximos | PASS | `3db4979` | 2026-07-17 |
| 2 | PR-F3-02 | Sugestão determinística de categoria | PASS | `483ef36` | 2026-07-17 |
| 3 | PR-F3-03 | Contrato de onboarding mínimo | PASS | `ccd0f10` | 2026-07-17 |
| 4 | PR-F3-04 | Fundação de drill-down | PASS | `7cc4aeb` | 2026-07-17 |
| 5 | PR-F3-05 | Lançamento rápido | PASS | `413d191` | 2026-07-19 |
| 6 | PR-F3-06 | Visão financeira | PASS | `0c892bc` | 2026-07-19 |
| 7 | PR-F3-07 | Home reduzida | PASS | `628cf8e` | 2026-07-19 |
| 8 | PR-F3-08 | Drill-down até extrato | PASS | `672d97b` | 2026-07-19 |
| 9 | PR-F3-09 | Onboarding mobile mínimo | PASS | `0849847` | 2026-07-19 |
| 10 | PR-F3-10 | Setup progressivo | PASS | `f0b27de` | 2026-07-19 |
| 11 | PR-F3-11 | Modalidade e histórico de metas | PASS | `6712653`+`feee1cb` | 2026-07-19 |
| 12 | PR-F3-12 | Web mínimo (drill-down Dashboard) | PASS | `9d1e8a6` | 2026-07-19 |
| 13 | PR-F3-13 | Legados e documentação | PASS_COM_RESSALVA | `90bc02d` | 2026-07-19 |

Ressalvas da fase: (1) rodada Maestro/simulador iOS + evidência visual claro/escuro pendente
desde o PR-F3-05 (crítica); (2) CHANGELOG/CHECKLIST exigiram aplicação manual por bloqueio de
permissão (BACKLOG-0089); (3) publicação segue travada pelo gate PROB-0081.

**Fase 3 (UX) concluída.**
```

## Testes NAO EXECUTADOS

- Maestro/simulador iOS e evidencia visual claro/escuro seguem pendentes (acumulado critico
  desde o PR-F3-05; inclui agora o perfil sem o grid de contadores).
- Nenhum E2E real do backend deprecated (mudanca e de anotacao/OpenAPI, sem comportamento).

## Achados e ressalvas

1. O contador "Cartoes" do perfil mobile exibia `totalContas` (dado errado do resumo legado) —
   removido junto com o grid, sem substituicao (nenhuma metrica oficial cobre contagens de
   cadastro; se a contagem fizer falta, tratar como feature nova, nao como legado).
2. Bloqueio de permissao de `CHANGELOG.md`/`CHECKLIST_...md` confirmado para a sessao principal
   (nao so para `docs-reporter`) — ver "Texto pronto" acima e BACKLOG-0089.
3. `insightsQuery.data?.resumo` na home mobile NAO e o resumo legado (e o campo `resumo` de
   `/v1/insights`) — mantido.

## Validacoes (resultado)

Preenchidas na secao "Validacoes executadas" acima. Resumo: backend BUILD SUCCESS (277 unit +
27 IT, 0 falhas); mobile tsc limpo + Jest 36/36; frontend Vitest 44/44 + build OK; OpenAPI em
runtime com `deprecated: true` e endpoint legado ainda funcional (HTTP 200).
