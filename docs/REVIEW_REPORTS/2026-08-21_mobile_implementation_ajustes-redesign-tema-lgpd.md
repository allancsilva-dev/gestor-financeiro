# Relatorio de Revisao

**Arquivo:** 2026-08-21_mobile_implementation_ajustes-redesign-tema-lgpd.md

---

## Objetivo

Registrar a implementação, nesta sessão, do redesenho da tela `mobile/app/(app)/ajustes.tsx` para
o padrão visual atualmente em uso nas telas Home/Carteira/Metas, com extração das primitivas
visuais compartilhadas desse padrão, mais três entregas de produto que dependiam dessa tela:
escolha de tema claro/escuro/sistema pelo usuário (antes o app só seguia o SO), consumo pela
primeira vez do endpoint de exclusão de conta LGPD no mobile, e acesso à política de privacidade
por quem já tem conta (antes só linkável no cadastro). Todas as decisões descritas abaixo foram
aprovadas pelo dono do produto nesta sessão.

## Escopo verificado

- `mobile/app/(app)/ajustes.tsx` (reescrita completa)
- `mobile/src/components/ui/CabecalhoDeTela.tsx` (novo)
- `mobile/src/components/ui/SuperficieComBrilho.tsx` (novo, extraído de `CardMeta`)
- `mobile/src/components/ui/CabecalhoSecao.tsx` (movido de `src/components/metas/`, prop `escalar`
  nova)
- `mobile/src/components/metas/CardMeta.tsx` (passou a consumir `SuperficieComBrilho`)
- `mobile/src/theme/tokens.ts` (`typography.screenTitle` novo)
- `mobile/src/store/temaPreferido.ts` (novo)
- `mobile/src/context/TemaContext.tsx` (novo)
- `mobile/src/theme/index.ts` (`useEsquema()`/`useTheme()` passam a ler o contexto)
- `mobile/src/components/ui/Card.tsx` (migrado para `useEsquema()`)
- `mobile/app/_layout.tsx` (envolvido por `TemaProvider`)
- `mobile/src/services/usuarioService.ts` (novo)
- `mobile/app/(auth)/privacidade.tsx` (já existente, ganhou segundo ponto de entrada)
- `mobile/.maestro/smoke-auth.yaml` (asserts de tab bar corrigidos)
- `DESIGN.md` (reescrito)
- `mobile/src/__tests__/AjustesScreen.test.tsx` (novo, 10 casos)
- `mobile/src/__tests__/temaPreferido.test.ts` (novo, 3 casos)

## Arquivos lidos

Todos os arquivos listados acima (via `git diff` contra `HEAD`, baseline `main` em `12cc447`),
mais `mobile/src/services/api.ts` (bloco do interceptor de erro 400/422), `mobile/app/(app)/metas.tsx`
(diff mínimo — import de `CabecalhoSecao` atualizado e prop `escalar` adicionada), `mobile/src/theme/colors.ts`,
`docs/BACKLOG.md`, `docs/PROBLEM_LEDGER.md`, `docs/SYSTEM_OVERVIEW.md` (estado anterior).

## Comandos executados

| Comando | Resultado |
|---|---|
| `git status --short` (raiz do repo) | 10 arquivos modificados, 1 rename, 7 novos untracked — todos no working tree, nada commitado |
| `git diff --stat HEAD` | 11 arquivos, +701/-333 linhas |
| `npm run lint` (cwd `mobile/`) | exit code 0, sem erros — inclui a confirmação de que BACKLOG-0093 (erro pré-existente `react-hooks/exhaustive-deps`) não reproduz mais |
| `grep -c "it(\|test("` nos dois arquivos de teste novos | `AjustesScreen.test.tsx`: 10; `temaPreferido.test.ts`: 3 |
| `curl -X DELETE http://localhost:8093/api/v1/usuarios/me` com senha errada e com senha certa (relatado pela sessão de implementação, não reexecutado por este agente) | 422 `BUSINESS_ERROR`/"Senha incorreta"; 204 e login subsequente falhando |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | MEDIUM | Interceptor Axios do mobile descarta a mensagem de `BusinessException` em 400/422 quando não há `details` — usuário vê "Dados inválidos. Verifique os campos." em vez da causa real (ex.: "Senha incorreta") | `mobile/src/services/api.ts` (bloco `if (status === 400 \|\| status === 422)`, ~linhas 110-113); reproduzido via `curl` contra `DELETE /v1/usuarios/me` com senha errada. Ver PROB-0083. |
| 2 | LOW/informativo | `mobile/.maestro/smoke-auth.yaml` assertava rótulos de tab bar ("Transações", "Planejamento", "Mais") que não existem mais na tab bar atual (`Início · Análises · + · Metas · Ajustes`) — corrigido nesta sessão | `git diff -- mobile/.maestro/smoke-auth.yaml`. Ver BUG-0068. |
| 3 | informativo/documental | `DESIGN.md` estava defasado, descrevendo marca violeta `#7c5cfc`, tema claro por padrão e tab bar antiga — corrigido nesta sessão | `git diff -- DESIGN.md`. |
| 4 | informativo/documental | `docs/SYSTEM_OVERVIEW.md` tinha dois itens de "Limitações conhecidas" (política de privacidade e exportação de dados) desatualizados em relação ao estado real do mobile, contradizendo BACKLOG-0077 já registrado | `docs/SYSTEM_OVERVIEW.md`, itens 5 e 6 antes desta rodada; corrigidos por este `docs-reporter`. |

## O que foi corrigido

- Tela `ajustes.tsx` redesenhada com componentes extraídos e tokens de tema, substituindo estilo
  hardcoded por primitivas reutilizáveis (`CabecalhoDeTela`, `SuperficieComBrilho`,
  `CabecalhoSecao`, `typography.screenTitle`).
- Escolha de tema claro/escuro/sistema implementada (`temaPreferido.ts`, `TemaContext.tsx`,
  `theme/index.ts`, `Card.tsx`, `app/_layout.tsx`).
- Exclusão de conta LGPD consumida pela primeira vez no mobile, com confirmação dupla e leitura
  correta da mensagem de erro de negócio (contorno local, ver BUG-0069).
- Política de privacidade linkável a partir de Ajustes para quem já tem conta.
- `DESIGN.md` reescrito para refletir a marca ciano e a tab bar atuais.
- `mobile/.maestro/smoke-auth.yaml` corrigido para não depender de rótulos de aba inexistentes/não
  observáveis pelo Maestro.
- 13 testes novos (`AjustesScreen.test.tsx` + `temaPreferido.test.ts`); suite completa do mobile
  relatada pela sessão de implementação como 172 testes PASS; `npm run lint`/`npm run typecheck`
  confirmados limpos por este agente (lint) e relatados limpos (typecheck) pela sessão de
  implementação.

## O que ficou pendente

- **Rodada formal de Maestro/simulador e evidência visual em tema claro/escuro** — a verificação
  desta sessão foi manual, em um único simulador (iPhone 17 Pro, Release), sem execução automatizada
  de flows Maestro contra a tela nova. Esta é a mesma pendência crítica acumulada desde o Bloco B da
  Fase 3 (PR-F3-05 em diante).
- **Correção do interceptor Axios na origem** (`api.ts`) para não descartar mensagens de
  `BusinessException` — o contorno aplicado (BUG-0069) é local a `ajustes.tsx`; nenhuma varredura
  das demais telas do app foi feita para confirmar se o mesmo problema afeta outros fluxos (ver
  PROB-0083, BACKLOG-0094).
- **Validação em ambiente implantado (staging/produção)** — a verificação de exclusão de conta e
  do fluxo de tema foi feita só contra backend local (porta 8093, banco `gf_ajustes`).
- **Revisão jurídica da política de privacidade e publicação equivalente no frontend web** — fora
  do escopo desta sessão (mobile-only), ver BACKLOG-0077.
- **Origem histórica não determinada** de dois itens de "Limitações conhecidas" já desatualizados
  antes desta sessão (exportação de dados, política de privacidade) — corrigidos quanto ao estado
  atual, mas sem certeza de qual rodada anterior os corrigiu de fato no código.

## Recomendação final

Trabalho consistente com o padrão visual já estabelecido nas demais telas do app e com as decisões
de produto aprovadas nesta sessão. A funcionalidade de exclusão de conta LGPD é a peça de maior
risco por tratar de dado irreversível — foi testada ponta a ponta contra backend local com os dois
caminhos (senha errada / senha certa), o que dá confiança razoável, mas a ausência de validação em
ambiente implantado e de cobertura Maestro automatizada mantém uma lacuna de verificação que se
acumula com sessões anteriores do mesmo bloco de trabalho. Recomenda-se priorizar a rodada de
Maestro/visual regression antes de qualquer publicação, dado que já é uma pendência repetida em
múltiplas entregas consecutivas do mobile.

## Status final

PASS_COM_RESSALVA

---

> Relatório escrito pelo `docs-reporter` com base no contexto fornecido pela sessão de
> implementação e em inspeção direta do working tree (`git diff`, leitura de arquivos, execução de
> `npm run lint`). Nenhum código de aplicação foi alterado na produção deste relatório.
