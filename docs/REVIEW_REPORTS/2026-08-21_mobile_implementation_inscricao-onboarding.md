# Relatorio de Implementacao — Inscrição e Onboarding

- **Data:** 2026-08-21
- **Area:** mobile (`app/(auth)`, `app/onboarding.tsx`, `src/components/ui`), backend (`AuthController`)
- **Baseline:** `12cc447` (working tree com o redesign de Ajustes já aplicado, não commitado)

## Objetivo

Levar o fluxo de criação de conta e o onboarding para o padrão visual das telas novas (Home,
Cartões, Metas, Ajustes) e, antes disso, corrigir os defeitos de comportamento do fluxo — a ordem
foi invertida de propósito: primeiro os defeitos, com teste, depois a tela.

## Auditoria antes da implementação

Oito hipóteses de defeito levantadas; **seis confirmadas, uma já resolvida, uma inexistente**:

| # | Achado | Situação |
|---|---|---|
| D1 | Interceptor descarta `message` de `BusinessException` em 400/422 | Confirmado → BUG-0070 |
| D2 | 429 (rate limit e `ACCOUNT_LOCKED`) sem tratamento | Confirmado → BUG-0070 |
| D3 | `details` (campo→mensagem) nunca chegava à tela | Confirmado → BUG-0070 |
| D4 | `register.tsx` sem `finally { setLoading(false) }` | Confirmado → corrigido na reescrita |
| D5 | E-mail sem normalização em nenhuma ponta | Confirmado → BUG-0071 |
| D6 | Onboarding sem proteção de teclado, sem erro recuperável, sem teste | Confirmado → reescrita + 6 testes |
| D7 | Checklist "Complete seu setup" sumiu da home | Confirmado (regressão `9a3b205`) → PROB-0084 / BUG-0072 |
| D8 | Falta de guarda de rota para onboarding incompleto | **Não existe** — `app/(app)/_layout.tsx:72` já redireciona |

## O que foi implementado

**Correções (antes da UI):** envelope de erro preservado em `src/services/api.ts` (`codigo`,
`campos`, `status`, `retryAfterSegundos`) + `src/utils/erros.ts`; normalização de e-mail no
cadastro e busca tolerante a caixa no login/recuperação; remoção do contorno local do BUG-0069.

**Onboarding (prioridade):** wizard de 6 passos — conta principal (obrigatória), renda, categorias,
cartão, meta e revisão. Opcionais puláveis em um toque. Um único POST idempotente para
`/v1/onboarding/finalizar` (contrato do PR-F3-03, já aceitava tudo isso; nenhuma mudança de
backend). Rascunho em SecureStore, erro do backend levado ao passo dono do campo, "Entrar de novo"
quando a sessão expira no envio.

**Inscrição:** três passos (identidade, senha com olho + medidor, consentimento LGPD com resumo do
que é coletado), erro por campo, progresso visível.

**Login/recuperação/política:** mesmo esqueleto, "lembrar e-mail" via `src/store/ultimoEmail.ts`.

**Design system:** `ui/Botao` (primeiro botão compartilhado do app — antes eram
`TouchableOpacity` inline em ~15 arquivos com duas assinaturas), `ui/CampoSenha`,
`ui/PassosProgresso` e `ui/TelaFluxo`; `DESIGN.md` ganhou a receita de fluxo de entrada.

## Validações executadas

- `npx tsc --noEmit` limpo; `npm run lint` (ESLint a11y, `--max-warnings=0`) limpo.
- Jest mobile: **194/194 PASS** (25 suítes), com 4 suítes novas — `apiErros` (7),
  `OnboardingWizard` (6), `RegisterWizard` (6), `forcaSenha` (3).
- `AuthControllerTest`: **28/28 PASS**, com 3 casos novos de e-mail/caixa.
- Runtime contra backend local (porta 8094, banco descartável `gf_auth_v2`, host Postgres):
  cadastro com `Ana.Souza@Teste.com` gravou `ana.souza@teste.com`; duplicado com outra caixa → 422
  com `"Email já cadastrado!"`; login com `ANA.souza@teste.com` autenticou; senha errada → 422
  `"Email ou senha incorretos"`; sequência de erros → 429 `RATE_LIMIT` com `Retry-After: 50`.
- Onboarding completo em um único POST → 200, e conferência no banco: carteira `Nubank` (250,00),
  cartão `Roxinho`, categorias `Alimentação`/`Transporte` + `Renda`, conta fixa `Salário` (4.500,00,
  dia 5, ENTRADA) e meta `Reserva` (10.000,00, `COFRE_REAL`).

## O que ficou pendente

- **Rodada de simulador e Maestro** — não executada. Os flows `.maestro/*.yaml` foram atualizados
  para a nova UI (passos do cadastro, "Pular por agora"/"Concluir" no onboarding, rótulo `Entrar`),
  mas não rodaram contra simulador. É a mesma pendência crítica acumulada desde o Bloco B da Fase 3.
- **Validação visual em tema claro/escuro** — tentada via Expo Web (servidor já rodando na 8081) e
  **impossível nesse alvo**: `expo-secure-store` não existe na web
  (`ExpoSecureStore.default.getValueWithKeyAsync is not a function`, disparado por
  `store/temaPreferido.ts`), limitação anterior a esta sessão. Só simulador/dispositivo resolve.
- **Decisão de produto pendente:** o PR-F3-09 reduziu o onboarding a uma etapa de propósito. Esta
  entrega mantém **uma etapa obrigatória** e torna as demais opcionais/puláveis; se a preferência
  for voltar à etapa única pura, os passos opcionais viram só checklist da home.
- **Contas legadas** com e-mail em maiúsculas seguem existindo; a correção evita novas duplicatas e
  tolera caixa no login, mas não faz migração de dados.

## Resultado

PASS_COM_RESSALVA — código, testes e contrato de API verificados; validação visual e E2E pendentes.
