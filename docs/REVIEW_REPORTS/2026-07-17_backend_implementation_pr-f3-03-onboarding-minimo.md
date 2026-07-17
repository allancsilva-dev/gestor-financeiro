# Relatorio de Revisao

**Arquivo:** 2026-07-17_backend_implementation_pr-f3-03-onboarding-minimo.md

**PR:** PR-F3-03 — Contrato de onboarding minimo (Fase 3)

**Commit:** `ccd0f10` (main)

---

## Objetivo

Registrar a implementacao do PR-F3-03, terceiro PR da Fase 3 ("Experiencia simples"), que torna
`cartao` e `categorias` opcionais no contrato de finalizacao de onboarding, mantendo `carteira` como
unico campo obrigatorio, sem introduzir migration e sem alterar o comportamento do payload completo
ja em uso. A motivacao registrada e preparar a base backend para o onboarding mobile de etapa unica
previsto no PR-F3-09.

## Escopo verificado

Relato de implementacao backend consolidado apos ciclo de implementacao. Nao ha evidencia, nas
informacoes recebidas pelo `docs-reporter`, de acionamento dedicado de `quality-reviewer`,
`security-auditor` ou `lgpd-auditor` especificamente para este PR (ver "O que ficou pendente").

Escopo tecnico coberto pela sessao:
- `OnboardingFinalizarRequest`: remocao de `@NotNull` em `cartao` e de `@NotEmpty` em `categorias`;
  `carteira` continua `@NotNull`.
- `OnboardingService.finalizar()`: `cartao` nulo e `categorias` nula/vazia passam a ser tratados como
  no-op (nenhuma criacao de cartao ou categoria nesses casos).
- Suite de teste existente `OnboardingServiceTest` ganhou 2 casos novos.

## Arquivos lidos

Este relatorio foi produzido a partir do resumo tecnico fornecido pelo agente que orquestrou a
implementacao, sem leitura direta do codigo-fonte pelo `docs-reporter` (fora do seu escopo de
permissao em `backend/`). Arquivos citados como alterados, conforme relatado e conforme
`git show ccd0f10 --stat` (comando de inspecao, somente leitura):

- `backend/src/main/java/com/gestor/financeiro/dto/OnboardingFinalizarRequest.java` (+3/-1)
- `backend/src/main/java/com/gestor/financeiro/service/OnboardingService.java` (+7/-3)
- `backend/src/test/java/com/gestor/financeiro/OnboardingServiceTest.java` (+37, 2 testes novos)

## Comandos executados

Comandos reportados como executados pelo ciclo de implementacao (nao reexecutados pelo
`docs-reporter`, que nao tem permissao para rodar build de `backend/`):

| Comando | Resultado |
|---|---|
| `./mvnw verify -Pintegration-test` | 249 testes unitarios + 27 ITs (failsafe), 0 falhas — BUILD SUCCESS |

Comando de inspecao executado diretamente pelo `docs-reporter` (somente leitura, sem alterar
codigo):

| Comando | Resultado |
|---|---|
| `git show ccd0f10 --stat` | Confirma os 3 arquivos alterados e o resumo de linhas acima |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | INFORMATIVO | `carteira` permanece o unico campo `@NotNull` em `OnboardingFinalizarRequest`; `cartao` e `categorias` passam a aceitar ausencia/nulo/vazio sem rejeicao pelo Validator. | `dto/OnboardingFinalizarRequest.java` (relatado) |
| 2 | INFORMATIVO | `OnboardingService.finalizar()` trata `cartao` nulo como no-op (nao cria cartao) e `categorias` nula ou lista vazia como no-op (nao cria nenhuma categoria); nao ha criacao parcial ou estado intermediario invalido nesses casos. | `service/OnboardingService.java` (relatado) |
| 3 | INFORMATIVO | Mecanismos de integridade existentes nao foram alterados nesta rodada: metodo continua `@Transactional`, lock pessimista via `findByIdComLock`, e idempotencia por `onboardingCompleto` (reenvio apos onboarding ja concluido continua sendo no-op). | Resumo tecnico da sessao; `OnboardingAtomicidadeTest` 3/3 (relatado) |
| 4 | INFORMATIVO | Compatibilidade retroativa: payload completo antigo (carteira + cartao + categorias + renda + meta) permanece valido e produz o mesmo resultado de antes — testes antigos de `OnboardingServiceTest` com payload completo passaram sem alteracao. | Resumo tecnico da sessao (relatado) |
| 5 | BAIXA (rastreabilidade) | Nao ha evidencia recebida pelo `docs-reporter` de execucao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico. O endpoint de finalizacao de onboarding ja e autenticado e restrito ao proprio usuario (mecanismo preexistente, nao alterado nesta rodada); o risco pratico da mudanca (afrouxar duas validacoes de obrigatoriedade em DTO) e considerado baixo, mas fica registrado pela ausencia de auditoria dedicada. | Ausencia de relatorio de auditoria dedicado a PR-F3-03 em `docs/REVIEW_REPORTS/` |
| 6 | BAIXA (rastreabilidade) | O consumo mobile deste contrato mais permissivo (onboarding de etapa unica) esta previsto para PR-F3-09, fora do escopo deste PR — nao ha wizard mobile implementado ou testado nesta rodada que exercite de fato um payload so-com-carteira em producao/app real; a cobertura atual e via `OnboardingServiceTest` (nivel de servico) e validacao direta do `jakarta.validation.Validator` sobre o DTO. | Resumo tecnico da sessao (relatado) |

## O que foi corrigido

Nao ha bug a corrigir neste PR — trata-se de relaxamento de contrato (DTO + servico) para viabilizar
um fluxo futuro, sem migration e sem alteracao de comportamento para o payload completo existente.
Nenhuma entrada foi criada em `docs/BUGFIX_LOG.md`.

## O que ficou pendente

- Confirmacao formal de `quality-reviewer`/`security-auditor`/`lgpd-auditor` dedicada a este PR
  especifico (achado #5).
- Implementacao e teste do wizard mobile de onboarding de etapa unica que de fato envia somente
  `carteira` (PR-F3-09), fora do escopo deste PR (achado #6).
- `CHANGELOG.md` e `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` nao foram atualizados com a entrada
  do PR-F3-03: esses dois arquivos nao constam na lista de "Arquivos sob sua responsabilidade" do
  `docs-reporter`, e a edicao direta desses arquivos esta fora da permissao concedida a este agente
  nesta sessao (restricao informada explicitamente pelo solicitante, na mesma linha do PR-F3-01 e
  do PR-F3-02). O texto completo que deveria compor as duas entradas fica registrado abaixo, para
  aplicacao por quem tiver permissao. `BACKLOG-0089` foi ampliado nesta rodada para cobrir os tres
  PRs (PR-F3-01, PR-F3-02 e PR-F3-03).

### Texto pronto para `docs/CHANGELOG.md`

```
## [Fase 3 — PR-F3-03] - 2026-07-17

### Contrato de onboarding minimo
- `OnboardingFinalizarRequest`: `cartao` perdeu `@NotNull` e `categorias` perdeu `@NotEmpty` — ambos
  agora opcionais. `carteira` continua `@NotNull` (unico campo obrigatorio).
- `OnboardingService.finalizar()`: `cartao` nulo e `categorias` nula ou vazia viram no-op (nao criam
  cartao/categoria); metodo continua `@Transactional`, com lock pessimista (`findByIdComLock`) e
  idempotencia por `onboardingCompleto` — nada disso foi alterado.
- Payload completo antigo (carteira + cartao + categorias + renda + meta) permanece valido, mesmo
  resultado de antes.
- Motivacao: base para o onboarding mobile de etapa unica (PR-F3-09), que enviara somente carteira.
- Sem migration.
- Commit: `ccd0f10`. Validacoes: `./mvnw verify -Pintegration-test` → 249 unitarios + 27 ITs, 0
  falhas, BUILD SUCCESS.
```

### Texto pronto para `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`

```
# PR-F3-03 — Contrato de onboarding minimo

**Status local:** `PASS_COM_RESSALVA`
**Data:** 2026-07-17
**Commit:** `ccd0f10`

- [x] `OnboardingFinalizarRequest.cartao` sem `@NotNull` (opcional)
- [x] `OnboardingFinalizarRequest.categorias` sem `@NotEmpty` (opcional)
- [x] `OnboardingFinalizarRequest.carteira` continua `@NotNull` (unico campo obrigatorio)
- [x] `OnboardingService.finalizar()`: cartao nulo vira no-op (nao cria cartao)
- [x] `OnboardingService.finalizar()`: categorias nula ou vazia vira no-op (nao cria categoria)
- [x] transacao, lock pessimista (`findByIdComLock`) e idempotencia (`onboardingCompleto`) preservados
- [x] payload completo antigo continua valido e com o mesmo resultado
- [x] nenhuma migration
- [x] `./mvnw verify -Pintegration-test`: 249 unitarios + 27 ITs, 0 falhas
- [x] `OnboardingServiceTest` 6/6 e `OnboardingAtomicidadeTest` 3/3
- [ ] wizard mobile de etapa unica consumindo o contrato minimo (previsto para PR-F3-09)
- [ ] revisao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico
- [ ] `CHANGELOG.md`/checklist atualizados diretamente (aplicado manualmente a partir deste bloco)
```

## Recomendacao final

Mudanca de contrato pequena, coerente e sem migration, que preserva integralmente os mecanismos de
integridade existentes (transacao, lock pessimista, idempotencia) e a compatibilidade com o payload
completo hoje em uso. E uma mudanca habilitadora (nao consumida ainda por nenhum cliente real) para
o PR-F3-09. Recomenda-se seguir para o PR-F3-09 (wizard mobile de etapa unica) e aplicar as entradas
de `CHANGELOG.md`/checklist transcritas acima assim que houver permissao de escrita nesses arquivos.

## Status final

PASS_COM_RESSALVA — ressalvas: (1) `CHANGELOG.md` e o checklist de execucao de PRs nao puderam ser
atualizados por este agente por restricao de permissao de arquivo (texto pronto acima); (2) ausencia
de evidencia direta de revisao/auditoria dedicada a este PR especifico; (3) o contrato minimo ainda
nao e exercido por nenhum cliente real (mobile/web) nesta rodada — o consumo fica para o PR-F3-09.

---

> Relatorio mantido pelo `docs-reporter`.
