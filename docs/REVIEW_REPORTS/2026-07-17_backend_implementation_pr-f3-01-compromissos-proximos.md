# Relatorio de Revisao

**Arquivo:** 2026-07-17_backend_implementation_pr-f3-01-compromissos-proximos.md

**PR:** PR-F3-01 — Compromissos proximos (Fase 3)

**Branch:** `pr-f3-01-compromissos-proximos`

**Data:** 2026-07-17

---

## Objetivo

Registrar a implementacao do PR-F3-01, primeiro PR da Fase 3 ("Experiencia simples"), que expoe um
endpoint de compromissos financeiros proximos (faturas, parcelas e contas fixas previstas) derivado
da mesma logica de calculo do Comprometido ja usada na metrica oficial (ADR-0013), sem introduzir
migration nem duplicar regra de negocio.

## Escopo verificado

Relato de implementacao backend consolidado apos o ciclo `backend-engineer` (implementacao) →
`quality-reviewer` (revisao). Nao ha indicio, nas evidencias recebidas, de acionamento de
`security-auditor` ou `lgpd-auditor` para este PR — o endpoint e autenticado, restrito por
`usuarioId` e nao introduz novo dado pessoal, mas o `docs-reporter` nao tem evidencia direta de uma
auditoria de seguranca/LGPD dedicada a este PR especificamente (ver "O que ficou pendente").

Escopo tecnico coberto pela sessao:
- Novo endpoint `GET /api/v1/compromissos?ate=`.
- Extracao aditiva de calculo em `MetricasService` (sem alterar comportamento de `calcular()` nem do
  drill-down `COMPROMETIDO`).
- Nova query em `ContaFixaRepository` para contas fixas previstas.
- Reuso de `ContaFixaService.listarFalhasPendentes` para alerta `FALHA_SALDO` no item `CONTA_FIXA`.
- Suite de testes dedicada `CompromissosServiceTest` (4 testes).

## Arquivos lidos

Este relatorio foi produzido a partir do resumo tecnico fornecido pelo agente que orquestrou o
ciclo de implementacao (backend-engineer/quality-reviewer), sem leitura direta do codigo-fonte
pelo `docs-reporter` (fora do seu escopo de permissao em `backend/`). Arquivos citados como
alterados/criados, conforme relatado:

- `backend/src/main/java/com/gestor/financeiro/service/CompromissosService.java` (novo)
- `backend/src/main/java/com/gestor/financeiro/controller/CompromissosController.java` (novo)
- `backend/src/main/java/com/gestor/financeiro/service/MetricasService.java` (refactor aditivo)
- `backend/src/main/java/com/gestor/financeiro/repository/ContaFixaRepository.java` (query nova)
- `backend/src/test/java/com/gestor/financeiro/service/CompromissosServiceTest.java` (novo, 4 testes)

## Comandos executados

Comandos reportados como executados pelo ciclo de implementacao (nao reexecutados pelo
`docs-reporter`, que nao tem permissao para rodar build de `backend/`):

| Comando | Resultado |
|---|---|
| `./mvnw test` | 243 testes, 0 falhas, 0 erros — BUILD SUCCESS |
| `./mvnw verify -Pintegration-test` | 243 unitarios + 27 ITs (failsafe), 0 falhas, cobertura JaCoCo OK — BUILD SUCCESS |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | INFORMATIVO | Fonte unica de calculo do Comprometido: `MetricasService.calcular()` e o drill-down `COMPROMETIDO` passaram a delegar para os mesmos metodos publicos usados pelo novo endpoint (`comprometido`/`obrigacoesComprometidas`), eliminando risco de divergencia entre o total do endpoint e a metrica oficial (ADR-0013). | `service/MetricasService.java` (refactor aditivo, relatado) |
| 2 | INFORMATIVO | Regra de "previsto" para contas fixas segue criterio documentado (ativa, SAIDA, nao paga, nao cancelada, `dataProximoVencimento <= horizonte`, sem piso de data para vencidas), coberto por teste dedicado. | `repository/ContaFixaRepository.java` (query nova), `test/CompromissosServiceTest.java` |
| 3 | BAIXA (rastreabilidade) | Nao ha evidencia de execucao de `security-auditor`/`lgpd-auditor` para este PR nas informacoes recebidas pelo `docs-reporter`, apesar de o endpoint expor dados financeiros por usuario. Como o dado exposto (faturas/parcelas/contas fixas) e da mesma natureza ja auditada em endpoints existentes e o isolamento e por `usuarioId`, o risco pratico e considerado baixo, mas fica registrado para rastreabilidade. | Ausencia de relatorio de auditoria dedicado a PR-F3-01 em `docs/REVIEW_REPORTS/` |
| 4 | BAIXA | Clientes mobile/web nao foram alterados nem testados neste PR; o endpoint fica disponivel no backend sem consumo ainda por nenhuma tela (preparacao para PR-F3-07, conforme mencionado no resumo tecnico). | Resumo tecnico da sessao |

## O que foi corrigido

Nao ha bug a corrigir neste PR — trata-se de implementacao de funcionalidade nova. Nenhuma entrada
foi criada em `docs/BUGFIX_LOG.md`.

## O que ficou pendente

- Confirmacao formal de `security-auditor`/`lgpd-auditor` sobre o novo endpoint (achado #3), ainda
  que o padrao de ownership `usuarioId` siga o mesmo modelo ja auditado em endpoints similares.
- Consumo do endpoint pelos clientes mobile/web (previsto para PR-F3-07 e PRs subsequentes da Fase 3).
- `CHANGELOG.md` e `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` nao foram atualizados com a entrada
  do PR-F3-01: esses dois arquivos nao constam na lista de "Arquivos sob sua responsabilidade" do
  `docs-reporter` e a tentativa de edicao foi bloqueada pelo sistema de permissoes da ferramenta.
  Fica registrado aqui, em `docs/BACKLOG.md` (nao aplicavel como item novo, ja que e tarefa
  administrativa pontual) e em `docs/SYSTEM_OVERVIEW.md` como pendencia explicita para quem tiver
  permissao sobre esses dois arquivos.

## Recomendacao final

Implementacao consistente com a decisao arquitetural de fonte unica de calculo (ADR-0013) e sem
migration. Recomenda-se seguir para os proximos PRs da Fase 3 (ex.: PR-F3-07) que consumirao este
endpoint, e resolver a atualizacao de `CHANGELOG.md`/checklist de execucao por quem tiver permissao
de escrita nesses arquivos.

## Status final

PASS_COM_RESSALVA — ressalvas: (1) `CHANGELOG.md` e o checklist de execucao de PRs nao puderam ser
atualizados por este agente por restricao de permissao de arquivo; (2) ausencia de evidencia direta
de auditoria de seguranca/LGPD dedicada a este PR especifico.

---

> Relatorio mantido pelo `docs-reporter`.
