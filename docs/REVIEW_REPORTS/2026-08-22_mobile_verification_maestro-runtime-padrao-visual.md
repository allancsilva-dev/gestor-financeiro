# Verificação em runtime — série de padronização visual do mobile (Maestro + verificação manual)

- **Data:** 2026-08-22
- **Área:** mobile, backend
- **Tipo:** verification (runtime, pós-implementação)
- **Branch:** `design/pr13-perfil`
- **Commits cobertos pela série que motivou esta verificação:** `9c1335be`..`ba199be` (13 PRs de
  padronização visual, ver `docs/SYSTEM_OVERVIEW.md` "Principais decisões técnicas" item 27)
- **Estado do repositório nesta rodada:** correções encontradas foram aplicadas sobre a working
  tree após `ba199be`, ainda **não commitadas** no momento deste registro (`git status --short`
  mostra `backend/.../RelatorioService.java`, `mobile/.maestro/financial-critical.yaml`,
  `mobile/app/(app)/more/fatura.tsx`, `mobile/src/components/metas/CardMeta.tsx` modificados)

## Objetivo

BACKLOG-0098 exigia executar os quatro flows Maestro (`financial-critical.yaml`,
`smoke-auth.yaml`, `privacy-consent.yaml`, `recovery-navigation.yaml`) contra o app já com o
padrão visual novo (10 telas migradas, kit `ui/` normalizado, trinco `padraoVisual.test.ts`), algo
que a série de implementação (2026-08-21/22) não pôde fazer por falta de simulador/dispositivo na
máquina onde o código foi escrito. Esta rodada fecha essa lacuna.

## Escopo verificado

- Os quatro flows Maestro completos, em simulador iOS.
- Verificação manual em runtime de itens de risco específicos herdados da série de implementação:
  `orcamentos.tsx` (BUG-0083), FAB de investimentos (BUG-0085), folha dentro de folha em
  `metas.tsx`, CTA "Depositar" do card de meta, `CabecalhoDeTela`/padding em Metas/Carteira/
  Análises, `ui/ProgressBar` nos dois temas, Aparência (Ajustes) nos três modos, `perfil.tsx`
  (troca de senha), reconciliação financeira do extrato.

## Ambiente

- Simulador iPhone 17 Pro, iOS 26.5.
- Stack local: Postgres efêmero em container + backend Spring Boot na porta 8081, banco
  descartável `gf_verify`.
- App iOS Debug com bundle embutido apontando para `http://127.0.0.1:8081/api`,
  `APP_ENV=local-e2e`.

## Comandos executados

- `maestro test mobile/.maestro/financial-critical.yaml` (múltiplas iterações até ficar verde)
- `maestro test mobile/.maestro/smoke-auth.yaml`
- `maestro test mobile/.maestro/privacy-consent.yaml`
- `maestro test mobile/.maestro/recovery-navigation.yaml`
- `maestro hierarchy` (dumps de árvore de acessibilidade, usados para diagnosticar BUG-0094)
- `npx tsc --noEmit`, `npm run lint`, `npm test` (mobile) — limpos ao final
- `./mvnw test` (backend) — 292 testes PASS, `BUILD SUCCESS`
- `curl` direto contra a API local para confirmar o payload de `gastosPorCategoria`
  (`"icone": null` após a correção de BUG-0095) e o comportamento de `orcamentos.tsx` (404 vs.
  backend indisponível)

## Resultado geral dos quatro flows

| Flow | Resultado | Observação |
|---|---|---|
| `financial-critical.yaml` | PASS (verde ponta a ponta) | 0 falhas, 6 screenshots; precisou de 5 correções no flow e 3 correções de app |
| `smoke-auth.yaml` | PASS (17s) | Sem alteração necessária |
| `privacy-consent.yaml` | PASS (34s) | Sem alteração necessária |
| `recovery-navigation.yaml` | PASS (9s) | Sem alteração necessária |

Suites de regressão ao final: mobile `npx tsc --noEmit` limpo, `npm run lint` limpo, 244 testes em
29 suítes PASS; backend 292 testes PASS, `BUILD SUCCESS`.

## Achados

### BLOCKER/ALTO — bugs de app corrigidos durante a rodada

Registrados individualmente em `docs/BUGFIX_LOG.md` (ver arquivo para causa raiz, correção e
evidência completas):

1. **BUG-0092** (ALTO, pré-existente) — `mobile/app/(app)/more/fatura.tsx`: primeiro toque em
   "Pagar Fatura" engolido pelo teclado (`ScrollView` sem `keyboardShouldPersistTaps="handled"`).
   Comprovado por divergência entre o passo Maestro reportado como `COMPLETED` e
   `faturas_cartao.valor_pago` continuando `0.00` no banco.
2. **BUG-0093** (MÉDIO) — mesma tela, teclado permanecia aberto cobrindo a lista de lançamentos
   após pagamento concluído; consequência direta de BUG-0092.
3. **BUG-0094** (ALTO, pré-existente) — `mobile/src/components/metas/CardMeta.tsx`: touchable
   aninhado (raiz `TouchableOpacity` envolvendo a linha de ações) apagava
   "Depositar"/"Editar"/"Excluir" da árvore de acessibilidade — confirmado por dump `maestro
   hierarchy` antes/depois.
4. **BUG-0095** (MÉDIO, pré-existente) — `backend/.../RelatorioService.java`:
   `gastosPorCategoria` transformava ícone `NULL` na string literal `"null"` via
   `String.valueOf`, exibida na tela em vez do fallback 🏷️.

### MÉDIO — manutenção do próprio flow (não é bug de app)

Cinco correções em `mobile/.maestro/financial-critical.yaml`, detalhadas no diff e no changelog
inline do próprio arquivo:

1. Cartão de teste com `diaFechamento = 31` em vez de `5` — determinismo: com fechamento 5, a
   compra do dia só caía na competência "atual" em cerca de 5 dos ~31 dias do mês
   (`FaturaDatas.competencia` empurra para o mês seguinte quando o dia da compra passa do
   fechamento).
2. `assertVisible: ".*Pago:.*25,00"` → `".*Pago:.*25,00.*"` (pago/restante moram no mesmo nó de
   texto; Maestro casa regex por igualdade total).
3. `assertVisible: "5,0%"` → `".*Meta Smoke, 5%.*"` (progresso arredondado para inteiro pelo
   componente; a asserção antiga nunca poderia ter passado).
4. `assertVisible: ".*50,00"` → `".*Cofre: Meta Smoke.*50,00.*"` (nó do cofre termina em texto
   fixo depois do valor).
5. Navegação para Relatórios: `tapOn: "Voltar"` + `tapOn: "Relatórios"` substituído por toque por
   coordenada na aba Análises — o pop de "Voltar" retornava para a fatura, não para o hub de
   Ajustes, porque a pilha do `more` mantinha a fatura embaixo de Contas.

### Verificações manuais sem achado (comportamento correto confirmado)

`orcamentos.tsx` (BUG-0083, 404 vs. backend fora do ar), FAB de investimentos (BUG-0085), folha
dentro de folha em `metas.tsx`, CTA "Depositar" (sem corte de texto, alvo ~49pt), `CabecalhoDeTela`
sem padding duplicado, `ui/ProgressBar` (0%/100%/cor de entidade, dois temas), Aparência (três
modos), `perfil.tsx` (olho/medidor de força; caminho de erro verificado por API+código, não por
UI), reconciliação financeira (extrato fechando exato em R$ 825,00).

## O que foi corrigido

- Quatro bugs de app (BUG-0092 a BUG-0095) — ver `docs/BUGFIX_LOG.md`.
- Cinco correções de manutenção em `mobile/.maestro/financial-critical.yaml`.

Nenhuma dessas correções está commitada nesta rodada (`commit: pendente` em todas as entradas do
`BUGFIX_LOG.md` referentes a este relatório).

## O que ficou pendente

- **BACKLOG-0102 (novo):** layout do card de contas fixas (três botões de ação) não verificado em
  tela estreita (~320dp, ex.: iPhone SE).
- **BACKLOG-0078 (atualizado):** VoiceOver/TalkBack realmente ligado no dispositivo não foi
  testado; a acessibilidade foi verificada por dump de árvore do Maestro (mesma fonte que o leitor
  de tela consome, mas não o leitor em si).
- **Reduce Motion:** não exercitado nesta rodada (mesma nota em BACKLOG-0078).
- **Android:** nada rodado (sem `adb` disponível nesta máquina); toda a verificação é iOS.
- **BACKLOG-0100 (sem alteração de escopo):** divergência de nome "Análises"/"Relatórios"
  reconfirmada visualmente, aguardando decisão do dono do produto.
- **BACKLOG-0099 (sem alteração de escopo):** `NovaTransacaoModal` exercitado em runtime sem
  defeito, mas continua fora do alcance do trinco `padraoVisual.test.ts`.
- **Commits:** as correções desta rodada (4 bugs de app + ajustes de flow) ainda não foram
  commitadas — fora do escopo deste agente (`docs-reporter` não commita).

## Recomendação final

A série de padronização visual (13 PRs, `9c1335be`..`ba199be`) está confirmada funcional em
runtime nos quatro flows críticos de Maestro, com os defeitos encontrados durante a própria
verificação já corrigidos no código (pendente apenas de commit). Recomenda-se: (1) commitar as
quatro correções de bug de app e os ajustes de flow desta rodada; (2) tratar BACKLOG-0102 antes de
release para aparelhos mais estreitos; (3) agendar um passe real de VoiceOver/TalkBack e Reduce
Motion como item separado, já que o método de dump de árvore comprovadamente encontra defeitos
reais (BUG-0094) mas não substitui o teste com o leitor de tela ligado.

## Status final

PASS_COM_RESSALVA — os quatro flows passam e o critério de aceite de BACKLOG-0098 está cumprido;
ressalvas são as pendências listadas acima (tela estreita, VoiceOver/TalkBack real, Reduce Motion,
Android, commit das correções).
