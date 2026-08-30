# Relatorio de Revisao

**Arquivo:** 2026-08-29_fullstack_implementation_recorrencia-cartao-v67.md

**Origem:** PROB-0098 — dono do produto tentou cadastrar a assinatura da Netflix (R$60/mes no cartao
de credito) e nao encontrou como.

**Data:** 2026-08-29

---

## Objetivo

Registrar a entrega de recorrência (assinatura) com destino cartão de crédito, migration `V67`, e os
cinco defeitos pré-existentes no motor de recorrência corrigidos na mesma sessão para que o cartão
pudesse usar esse motor com segurança.

## Escopo verificado

Relato de implementação full-stack consolidado após o ciclo completo de implementação (backend +
mobile), com evidência de execução de suíte de testes, verificação de migration contra PostgreSQL
real e verificação em runtime relatadas pelo agente orquestrador. O `docs-reporter` não tem permissão
para ler/editar `backend/`, `mobile/` ou `frontend/` — os arquivos abaixo foram confirmados apenas
por `git status` (existência e natureza modificado/novo), não por leitura de conteúdo.

Escopo técnico coberto pela sessão:
- Migration `V67__recorrencia_cartao.sql`: dois destinos mutuamente exclusivos em `ContaFixa`
  (`carteira_id` XOR `conta_id`), padrão já usado na V55.
- `ContaFixaService.resolverDestino` (renomeado de `resolverCarteira`): validação de exclusividade,
  ownership do cartão (404), cartão inativo (422), ENTRADA+cartão (400).
- Reaproveitamento do pipeline de compra de cartão já existente (`TransacaoService.criar` →
  `FaturaService.registrarCompraCartao`) sem duplicar regra de fatura (ADR-0001).
- Cinco defeitos pré-existentes corrigidos: idempotência descartada em compra de cartão (BUG-0098);
  execução automática sem revalidação de vencimento sob lock (BUG-0099); `carteiraId` do corpo
  desviando cobrança de cartão (BUG-0100); corrida no unique de `execucoes_recorrencia` devolvendo
  500 (BUG-0101); exclusão de cartão não desativando assinaturas (BUG-0102).
- Mobile: switch "Repete todo mês" em `NovaTransacaoModal`, seletor "Cobrar em" em
  `more/contas-fixas.tsx`, novo `mobile/src/domain/recorrencia.ts`.

## Arquivos lidos

Este relatório foi produzido a partir do resumo técnico fornecido pelo agente que orquestrou o ciclo
de implementação, sem leitura direta do código-fonte pelo `docs-reporter` (fora do seu escopo de
permissão em `backend/`/`mobile/`). Confirmados via `git status --short` (existência/natureza, não
conteúdo):

**Modificados:**
- `backend/src/main/java/com/gestor/financeiro/controller/ContaFixaController.java`
- `backend/src/main/java/com/gestor/financeiro/dto/ContaFixaRequest.java`
- `backend/src/main/java/com/gestor/financeiro/dto/ContaFixaResponseDto.java`
- `backend/src/main/java/com/gestor/financeiro/model/ContaFixa.java`
- `backend/src/main/java/com/gestor/financeiro/repository/ContaFixaRepository.java`
- `backend/src/main/java/com/gestor/financeiro/service/CartaoService.java`
- `backend/src/main/java/com/gestor/financeiro/service/ContaFixaService.java`
- `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`
- `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- `backend/src/test/java/com/gestor/financeiro/UsuarioExclusaoTest.java`
- `mobile/app/(app)/more/contas-fixas.tsx`
- `mobile/src/components/NovaTransacaoModal.tsx`
- `mobile/src/hooks/useInvalidarAposTransacao.ts`
- `mobile/src/types/index.ts`

**Novos (untracked):**
- `backend/src/main/resources/db/migration/V67__recorrencia_cartao.sql`
- `backend/src/test/java/com/gestor/financeiro/ContaFixaCartaoTest.java`
- `mobile/src/__tests__/recorrenciaCartao.test.tsx`
- `mobile/src/domain/recorrencia.ts`

**Nao relacionados a esta entrega** (modificados no working tree, mas fora do escopo relatado desta
feature — origem nao verificada por este agente): `mobile/app.json`, `mobile/package.json`.

## Comandos executados

Comandos reportados como executados pelo ciclo de implementação (não reexecutados pelo
`docs-reporter`, que não tem permissão para rodar build de `backend/`/`mobile/`):

| Comando | Resultado |
|---|---|
| `./mvnw test` (backend) | 501 testes, 0 falhas (491 após a feature + 10 do novo `ContaFixaCartaoTest`; +1 caso novo em `UsuarioExclusaoTest`) |
| `npm test` (mobile) | 447 testes, 40 suítes, 0 falhas |
| `npm run lint -- --max-warnings=0` (mobile) | limpo |
| `npx tsc --noEmit` (mobile) | limpo |
| `scripts/verify-postgres-migrations.sh` | exit 0, "now at version v67", PostgreSQL 16 real |
| Runtime manual (backend porta 8081, banco limpo) | assinatura Spotify criada com `cartao` no response; cobrança lançada na fatura de setembro (compra dia 29 > fechamento dia 10), R$60, sem parcelas; conta corrente permaneceu 2500,00; passivo do cartão 60 → 120; reexecução → 422 sem duplicar; `carteiraId` no corpo ignorado para assinatura de cartão; DELETE do cartão (204) desativou as duas assinaturas; restart do backend manteve 3 lançamentos/3 transações/3 execuções; validações → 400/400/422/404 (dois destinos, ENTRADA+cartão, cartão inativo, cartão inexistente), nenhum 500 |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | INFORMATIVO | Destino único (caixa XOR cartão) implementado com CHECK no banco + validação equivalente no service — nenhum CHECK disparou em runtime, confirmando que o service intercepta antes do banco. | `V67__recorrencia_cartao.sql`, `ContaFixaService.resolverDestino` (relatado); runtime: 400/400/422/404 sem 500 |
| 2 | ALTA (pré-existente, corrigida) | `Idempotency-Key` era descartada silenciosamente em qualquer compra de cartão, não só recorrência — achado colateral que teria comprometido a proteção contra duplicidade de qualquer cliente que enviasse a chave numa compra de cartão avulsa. | BUG-0098, `FaturaService.java`, `TransacaoService.java` |
| 3 | ALTA (pré-existente, corrigida) | Execução automática de recorrência não revalidava vencimento sob lock — risco real de cobrança duplicada em ambiente multi-instância, vale para caixa e cartão. | BUG-0099, `ContaFixaService.java`; runtime: restart manteve 3/3/3 sem duplicar |
| 4 | MEDIA (pré-existente, corrigida) | `carteiraId` do corpo de `realizar` tinha prioridade sobre o destino cadastrado, permitindo desviar uma assinatura de cartão para o caixa. | BUG-0100; runtime: `carteiraId` no corpo não tocou o caixa |
| 5 | MEDIA (pré-existente, corrigida) | Corrida no unique de `execucoes_recorrencia` devolvia HTTP 500 em vez de 422. | BUG-0101; runtime: reexecução → 422, não 500 |
| 6 | MEDIA (pré-existente, corrigida) | Exclusão de cartão não desativava assinaturas vinculadas, deixando cobrança invisível. | BUG-0102; runtime: DELETE (204) desativou as duas assinaturas |
| 7 | BAIXA (rastreabilidade) | Não há evidência, nas informações recebidas por este agente, de acionamento formal de `security-auditor`/`lgpd-auditor` dedicado a esta entrega. O DTO de exibição do cartão (`CartaoResumo`) expõe apenas `id, nome, bandeira, ultimosDigitos` — nunca PAN — e o padrão de ownership por `usuarioId` é o mesmo já auditado em endpoints similares, mas fica registrado para rastreabilidade. | Ausência de relatório de auditoria dedicado nesta pasta para esta entrega específica |
| 8 | BAIXA | `CarteiraService.deletar` tem o mesmo tipo de furo que `CartaoService.deletarCartao` tinha antes de BUG-0102 — não verifica `contas_fixas`, risco de 500 por FK `RESTRICT` numa carteira referenciada por recorrência sem movimentos. Pré-existente, não introduzido por esta entrega. | BACKLOG-0122 |
| 9 | BAIXA | Frequência de recorrência é hard-coded mensal (`plusMonths(1)`); assinaturas anuais/semanais não têm caminho de cadastro. | BACKLOG-0120 |
| 10 | BAIXA | Notificação de estouro de limite de cartão não implementada — decisão de produto foi "lançar e avisar", só o "lançar" existe. | BACKLOG-0125 |

## O que foi corrigido

Cinco bugs pré-existentes no motor de recorrência, todos registrados individualmente em
`docs/BUGFIX_LOG.md`: BUG-0098 (idempotência descartada em compra de cartão), BUG-0099 (execução
automática sem revalidação de vencimento sob lock), BUG-0100 (`carteiraId` do corpo desviando
cobrança de cartão), BUG-0101 (corrida no unique de `execucoes_recorrencia` devolvendo 500) e
BUG-0102 (exclusão de cartão não desativando assinaturas vinculadas). Todos relacionados a
PROB-0098, fechado nesta mesma sessão.

## O que ficou pendente

- Sete itens novos de backlog abertos nesta sessão: BACKLOG-0120 (frequência mensal hard-coded),
  BACKLOG-0121 (refactor de `@Data` nas entidades JPA), BACKLOG-0122 (`CarteiraService.deletar` com
  o mesmo tipo de furo de FK para `contas_fixas`), BACKLOG-0123 (remover flag morta
  `Transacao.recorrente`), BACKLOG-0124 (sugestão de recorrência detectada não herda cartão),
  BACKLOG-0125 (notificação de estouro de limite de cartão) e BACKLOG-0126 (paridade web).
- Confirmação formal de `security-auditor`/`lgpd-auditor` dedicada a esta entrega (achado #7) — o
  novo campo `contas_fixas.conta_id` e o DTO `CartaoResumo` não expõem PAN nem dado sensível novo,
  mas não há evidência direta de auditoria específica recebida por este agente.
- `docs/CHANGELOG.md`, `docs/DEPLOY.md`, `docs/PROXIMOS_PASSOS.md`, `backend/API.md` e
  `docs/GLOSSARIO.md`/`docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` não foram atualizados por
  este agente — os quatro primeiros estão em `deny` no `.claude/settings.json` (ver
  BACKLOG-0116, mesmo padrão de bloqueio já registrado para `DEPLOY.md`), e os dois últimos não
  constam na lista de arquivos sob responsabilidade do `docs-reporter`. Fica registrado aqui como
  pendência administrativa para quem tiver a permissão adequada.
- Verificação de concorrência real (duas threads/duas instâncias de fato) para BUG-0099 e BUG-0101 —
  a validação em runtime desta sessão foi por restart/reexecução sequencial, não por processos
  paralelos genuínos.

## Recomendacao final

Entrega consistente com o padrão arquitetural já estabelecido (destino único mutuamente exclusivo,
já usado na V55; reaproveitamento do pipeline de fatura sem duplicação, ADR-0001; migration expand
puro, ADR-0015). Os cinco bugs pré-existentes corrigidos eram condição necessária para o cartão
poder usar o motor de recorrência com segurança, e a correção de cada um also fecha o mesmo furo no
caminho de caixa já existente. Recomenda-se priorizar BACKLOG-0125 (notificação de limite) e
BACKLOG-0122 (mesmo padrão de furo de FK em `CarteiraService.deletar`) por serem os itens de maior
risco percebido entre os pendentes.

## Status final

PASS_COM_RESSALVA — ressalvas: (1) ausência de evidência direta de auditoria de segurança/LGPD
dedicada a esta entrega específica; (2) verificação de concorrência real não executada para
BUG-0099/BUG-0101 (apenas restart/reexecução sequencial); (3) `CHANGELOG.md`, `DEPLOY.md`,
`PROXIMOS_PASSOS.md`, `backend/API.md`, `GLOSSARIO.md` e o checklist de execução de PRs não puderam
ser atualizados por este agente por restrição de permissão/escopo de arquivo.

---

> Relatorio mantido pelo `docs-reporter`.
