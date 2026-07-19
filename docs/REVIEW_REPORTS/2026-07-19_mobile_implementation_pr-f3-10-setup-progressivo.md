# Relatorio de Revisao

**Arquivo:** 2026-07-19_mobile_implementation_pr-f3-10-setup-progressivo.md

**PR:** PR-F3-10 — Setup progressivo mobile (Fase 3, sexto PR do Bloco B — consumo mobile, fecha o Bloco B)

**Commit:** `f0b27de` (main)

---

## Objetivo

Registrar a implementacao do PR-F3-10, sexto e ultimo PR planejado do Bloco B da Fase 3 ("Experiencia
simples") no app mobile (Expo/React Native). O PR fecha o ciclo aberto pelo PR-F3-09: o wizard de
onboarding foi reduzido a uma unica etapa (apenas a conta principal), e cartao, categorias e metas
deixaram de ser oferecidos dentro do onboarding — este PR entrega a contrapartida, criando esses
cadastros sob demanda, no momento exato em que o usuario precisa deles, sem bloquear o fluxo principal.

## Escopo verificado

Relato de implementacao mobile consolidado apos ciclo de implementacao, complementado por leitura direta
do commit pelo `docs-reporter` (`git show --stat` e `git show` do diff completo de todos os 7 arquivos
tocados — ferramentas de inspecao somente leitura, nenhuma edicao de codigo feita por este agente). Nao ha
evidencia, nas informacoes recebidas, de acionamento dedicado de `quality-reviewer`, `security-auditor` ou
`lgpd-auditor` especificamente para este PR (mesma ressalva ja registrada para PR-F3-01 a PR-F3-09).

Escopo tecnico coberto pela sessao, confirmado por leitura direta do diff:

- **`mobile/src/components/NovaTransacaoModal.tsx` (+157/-1 conforme `git show --stat`):**
  - Estado novo de setup: `criandoPacote`, `novaCategoriaNome`, `criandoCategoria`, `criarCartaoAberto`,
    `novoCartao` (`{nome: 'Cartão Principal', limite: '', fechamento: '5', vencimento: '12'}`),
    `criandoCartao`, `setupError`.
  - `criarPacoteInicial`: itera `CATEGORIAS_INICIAIS` chamando `categoriaService.criar` sequencialmente
    (`await` dentro de `for...of`, uma chamada por categoria, nao em paralelo), depois invalida a query
    `['categorias']`. Erro cai em `setupError` com fallback fixo `'Não foi possível criar as categorias.
    Tente novamente.'`.
  - `criarCategoriaUnica`: valida `nome.trim().length < 2`, cria via `categoriaService.criar({nome, cor:
    CATEGORY_COLORS[8], icone: '📌'})`, invalida `categorias` e chama `selecionarCategoria(criada.id)`
    (funcao ja existente, marca a categoria como escolha manual).
  - `criarCartaoRapido`: valida nome (>=2 chars), limite (`parseCurrencyBR` finito e > 0) e
    `isValidDayOfMonth` (novo import de `../utils/validate`) para fechamento e vencimento; cria via
    `cartaoService.criar({nome, limiteTotal, diaFechamento, diaVencimento})`, invalida a query
    `['cartoes']`, seleciona o cartao criado (`setCartaoId(criado.id)`) e fecha o formulario inline
    (`setCriarCartaoAberto(false)`).
  - UI: bloco condicional `categorias.length === 0` com botao "Criar pacote inicial (9 categorias)"
    (`testID="create-category-pack"`, `ActivityIndicator` durante `criandoPacote`) e alternativa "Ou crie
    uma" com `Field` (`testID="quick-category-name"`) + botao "Criar" (`accessibilityLabel="Criar
    categoria"`). Bloco condicional `cartoes.length === 0 && formaPagamento === 'CARTAO' && tipo ===
    'SAIDA'`: antes de abrir, CTA "Criar cartão agora" (`testID="create-card-cta"`); apos tocar, formulario
    inline com `Field` de nome/limite/fechamento/vencimento e botao "Salvar cartão".
    `setupError` renderizado em vermelho logo acima de `erroForm`, sem impedir o restante do formulario de
    ser preenchido ou submetido.
  - `resetForm` foi estendido para limpar os campos de setup (`criarCartaoAberto`, `novaCategoriaNome`,
    `setupError`, `novoCartao` de volta aos defaults) junto com o reset ja existente do formulario
    principal.
- **`mobile/src/domain/categoriasIniciais.ts` (novo, 17 linhas):** exporta `CATEGORIAS_INICIAIS: 
  CategoriaRequest[]`, as mesmas 9 categorias que o wizard de onboarding anterior ao PR-F3-09 oferecia
  (Alimentação 🍔, Transporte 🚗, Moradia 🏠, Saúde 🏥, Educação 📚, Lazer 🎮, Vestuário 👕, Assinaturas 📱,
  Outros 📦), com cor de `CATEGORY_COLORS` por indice. Comentario no arquivo referencia explicitamente o
  PR-F3-10 e o "antigo wizard" como origem do conjunto.
- **`mobile/app/(app)/metas.tsx` (+9/-2):** o vazio de metas com `statusFiltro === 'ATIVA'` troca o texto
  estatico "Toque no + para criar a primeira" por `TouchableOpacity` "Criar primeira meta"
  (`accessibilityLabel="Criar primeira meta"`) que chama `abrirCriarMeta` (funcao ja existente,
  reaproveitada — nao houve criacao de logica nova de abertura de formulario).
- **`mobile/app/(app)/more/contas-fixas.tsx` (+10/-2):** o vazio de recorrencias troca o texto de
  "Toque no + para cadastrar..." para "Cadastre salário, aluguel ou outros valores recorrentes." e
  adiciona `TouchableOpacity` "Cadastrar recorrência" (`accessibilityLabel="Cadastrar primeira
  recorrência"` — rotulo de acessibilidade difere do texto visivel do botao) que chama `limparCriar()`
  seguido de `setModalCriarVisible(true)` (mesmo caminho que o FAB ja existente usa).
- **`mobile/app/(app)/index.tsx` (+50):** checklist discreto "Complete seu setup":
  - `checklistOculto` inicia `true` (default seguro contra flash visual) e um `useEffect` chama
    `isHomeChecklistDismissed()` para definir o valor real assim que resolver.
  - `checklistItens` so e montado quando `!checklistOculto` **e** `metricasQuery.data`,
    `compromissosQuery.data` e `transacoesQuery.data` ja existem — nenhuma query nova e disparada; os
    dados vem das 4 queries que a home ja fazia desde o PR-F3-07 (`metricas`, `compromissos`,
    `transacoes` — a 4ª, `insights`, nao alimenta o checklist).
  - Tres condicoes, cada uma independente: `transacoesQuery.data.content.length === 0` -> "Lance sua
    primeira movimentação" (abre `NovaTransacaoModal` via `setLancarAberto(true)`); `previstos.length ===
    0` (nenhum item `PREVISTO` em `compromissosQuery.data.itens`, variavel `previstos` ja existente do
    PR-F3-07) -> "Cadastre suas contas fixas" (navega `/more/contas-fixas`); `Number(metricasQuery.data
    .reservado) === 0` -> "Crie sua primeira meta" (navega `/metas`).
  - Card so renderiza quando `checklistItens.length > 0`; cada item some individualmente assim que sua
    condicao deixa de ser verdadeira (proxima renderizacao da home, sem estado adicional de "concluido").
  - Botao "Ocultar" (`accessibilityLabel="Ocultar checklist de setup"`) esconde o card imediatamente
    (`setChecklistOculto(true)`) e persiste via `dismissHomeChecklist().catch(() => {})` — falha de
    persistencia e silenciosa, o card ja ficou oculto na sessao atual independentemente do resultado.
- **`mobile/src/store/homeChecklist.ts` (novo, 19 linhas):** `dismissHomeChecklist`/
  `isHomeChecklistDismissed`, chave fixa `'homeChecklistDismissed'` em `expo-secure-store`. Mesmo padrao
  de fallback volatil em memoria (`volatileStore`, objeto em modulo) para o modo `local-e2e`
  (`Constants.expoConfig?.extra?.appEnv === 'local-e2e'`) ja usado por `lancamentoPrefs.ts` (PR-F3-05) e
  pelo store de autenticacao — dispensa e por dispositivo, sem sincronizacao com backend nem entre
  dispositivos.
- **`mobile/src/__tests__/homeChecklist.test.ts` (novo, 24 linhas, 2 testes):** mocka `expo-secure-store`
  inteiro (`setItemAsync`/`getItemAsync`/`deleteItemAsync`); primeiro teste confirma que
  `dismissHomeChecklist()` chama `SecureStore.setItemAsync('homeChecklistDismissed', '1')`; segundo teste
  confirma que `isHomeChecklistDismissed()` retorna `true` quando o mock resolve `'1'` e `false` quando
  resolve `null`.
- Sem mudanca de backend e sem migration neste PR — consome `categoriaService.criar` e
  `cartaoService.criar` (servicos ja existentes) e as 4 queries ja fixadas pelo PR-F3-07 na home.

## Arquivos lidos

Todos os 7 arquivos do commit foram lidos integralmente via `git show` (diff completo):

- `mobile/src/components/NovaTransacaoModal.tsx` (diff completo)
- `mobile/src/domain/categoriasIniciais.ts` (novo — arquivo completo)
- `mobile/app/(app)/metas.tsx` (diff completo)
- `mobile/app/(app)/more/contas-fixas.tsx` (diff completo)
- `mobile/app/(app)/index.tsx` (diff completo)
- `mobile/src/store/homeChecklist.ts` (novo — arquivo completo)
- `mobile/src/__tests__/homeChecklist.test.ts` (novo — arquivo completo)

## Comandos executados

Comandos reportados como executados pelo ciclo de implementacao (nao reexecutados pelo `docs-reporter`,
que nao tem permissao para rodar build/teste de `mobile/`):

| Comando | Resultado |
|---|---|
| `npx tsc --noEmit` | limpo, sem erros |
| Jest (suite mobile) | 33/33 PASS (12 suites, 1 nova — `homeChecklist.test.ts`) |

Comandos de inspecao executados diretamente pelo `docs-reporter` (somente leitura, sem alterar codigo):

| Comando | Resultado |
|---|---|
| `git show f0b27de --stat` | Confirma os 7 arquivos alterados: `mobile/app/(app)/index.tsx` (+50), `mobile/app/(app)/metas.tsx` (+9/-2), `mobile/app/(app)/more/contas-fixas.tsx` (+10/-2), `mobile/src/__tests__/homeChecklist.test.ts` (novo, +24), `mobile/src/components/NovaTransacaoModal.tsx` (+157/-1), `mobile/src/domain/categoriasIniciais.ts` (novo, +17), `mobile/src/store/homeChecklist.ts` (novo, +19); total 283 insercoes, 3 delecoes |
| `git show f0b27de -- mobile/src/domain/categoriasIniciais.ts mobile/src/store/homeChecklist.ts mobile/src/__tests__/homeChecklist.test.ts` | Confirma os 3 arquivos novos integralmente: pacote de 9 categorias, store de dispensa com fallback `local-e2e`, e os 2 testes Jest |
| `git show f0b27de -- 'mobile/app/(app)/metas.tsx' 'mobile/app/(app)/more/contas-fixas.tsx'` | Confirma os 2 CTAs nos vazios (`Criar primeira meta`/`Cadastrar recorrência`), reaproveitando funcoes de abertura ja existentes |
| `git show f0b27de -- 'mobile/src/components/NovaTransacaoModal.tsx'` | Confirma os 3 fluxos de criacao contextual (pacote de categorias, categoria unica, cartao rapido), o estado `setupError` e a extensao de `resetForm` |
| `git show f0b27de -- 'mobile/app/(app)/index.tsx'` | Confirma o checklist derivado somente das 3 das 4 queries ja existentes da home, sem request novo, e o botao "Ocultar" com persistencia so no dispositivo |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | INFORMATIVO | O pacote de categorias criado sequencialmente (`for...of` com `await` por item) faz 9 chamadas de rede em serie a `categoriaService.criar`, e nao ha rollback caso uma chamada falhe no meio (categorias ja criadas antes da falha permanecem). Comportamento aceitavel para o volume (9 itens, acao pontual), mas nao ha teste cobrindo o cenario de falha parcial. | `git show f0b27de -- mobile/src/components/NovaTransacaoModal.tsx` (funcao `criarPacoteInicial`) |
| 2 | INFORMATIVO | Divergencia pequena entre texto visivel e `accessibilityLabel` no CTA de contas-fixas: o botao mostra "Cadastrar recorrência" mas o `accessibilityLabel` e "Cadastrar primeira recorrência" — nao e um bug (leitores de tela usam o label, nao o texto visivel), mas registra-se a diferenca para consistencia futura entre os dois textos. | `git show f0b27de -- 'mobile/app/(app)/more/contas-fixas.tsx'` |
| 3 | MEDIA (cobertura de teste, mesma classe de risco do PR-F3-09) | Maestro/simulador iOS **nao foi executado** nesta rodada para nenhum dos tres fluxos de criacao contextual novos (cartao rapido, pacote de categorias, categoria unica) nem para o checklist discreto da home ("Complete seu setup", incluindo os 3 itens condicionais e o botao "Ocultar"). O unico teste automatizado novo (`homeChecklist.test.ts`, 2 casos) cobre apenas a camada de persistencia (`SecureStore`), nao a logica de derivacao dos itens do checklist a partir das queries da home nem a interacao do usuario com os CTAs dentro de `NovaTransacaoModal`. Acumula com a mesma pendencia critica ja registrada para o onboarding reescrito do PR-F3-09 — agora sao duas rodadas consecutivas de mudanca no primeiro-uso do app (onboarding + setup progressivo) sem nenhuma validacao end-to-end real. | Declarado explicitamente pela sessao de implementacao; ausencia de arquivo Maestro atualizado para os fluxos novos em `mobile/.maestro/`; `homeChecklist.test.ts` cobre somente `store/homeChecklist.ts`, nao `index.tsx` nem `NovaTransacaoModal.tsx` |
| 4 | BAIXA (evidencia de UX) | Evidencia visual do fluxo em tema claro/escuro nao foi capturada nesta rodada — acumula com a mesma pendencia dos PR-F3-05 a PR-F3-09 (agora 6 PRs consecutivos de UI sem validacao visual formal). | Declarado explicitamente pela sessao de implementacao |
| 5 | BAIXA (rastreabilidade) | Nao ha evidencia recebida pelo `docs-reporter` de execucao dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR especifico — mesma ressalva ja registrada para PR-F3-01 a PR-F3-09. O PR cria registros novos (categorias, cartao) via servicos ja existentes e ja auditados (`categoriaService`, `cartaoService`), o que reduz o risco pratico, mas a ausencia formal permanece registrada. | Ausencia de relatorio de auditoria dedicado a PR-F3-10 em `docs/REVIEW_REPORTS/` |
| 6 | BAIXA (decisao de produto documentada) | Cartao de credito nao entra no checklist da home — decisao intencional e documentada pela sessao de implementacao (fatura so existe apos uso do cartao, logo nao e derivavel com confianca das 4 queries da home sem request extra). Nao e um bug; registrado para rastreabilidade da decisao. | Declarado explicitamente pela sessao de implementacao; confirmado pela ausencia de qualquer referencia a cartao em `checklistItens` no diff de `mobile/app/(app)/index.tsx` |

## O que foi corrigido

Nao ha bug a corrigir neste PR — trata-se de features novas de UX (criacao contextual e checklist
discreto) consumindo servicos, telas e o contrato de queries ja existentes. Nenhuma entrada foi criada em
`docs/BUGFIX_LOG.md` (confirmado: sem bug, sem entrada, conforme instrucao recebida para esta rodada).

## O que ficou pendente

- Execucao do Maestro (novo ou atualizado) cobrindo os tres fluxos de criacao contextual dentro de
  `NovaTransacaoModal` (cartao rapido, pacote de categorias, categoria unica) e o checklist discreto da
  home (os 3 itens condicionais e o botao "Ocultar") — achado #3, classificado como **MEDIA**, acumulado
  em prioridade com a execucao ja pendente e classificada como CRITICA para o onboarding reescrito do
  PR-F3-09 (ambos cobrem, juntos, o fluxo completo de primeiro uso do app: onboarding minimo + setup
  progressivo).
- Evidencia visual do fluxo em tema claro/escuro (achado #4), acumulada com PR-F3-05 a PR-F3-09.
- Confirmacao formal de `quality-reviewer`/`security-auditor`/`lgpd-auditor` dedicada a este PR especifico
  (achado #5).
- Avaliar se o comportamento sem rollback do pacote de categorias (achado #1) precisa de tratamento mais
  robusto (ex.: relatar quais categorias foram criadas antes da falha) — nao critico para o volume atual
  de 9 itens.
- `CHANGELOG.md` e `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md` nao foram atualizados com a entrada do
  PR-F3-10: esses dois arquivos nao constam na lista de "Arquivos sob sua responsabilidade" do
  `docs-reporter`, e a edicao direta desses arquivos esta fora da permissao concedida a este agente nesta
  sessao (restricao informada explicitamente pelo solicitante, na mesma linha do PR-F3-01 a PR-F3-09). O
  texto completo que deveria compor as duas entradas fica registrado abaixo, para aplicacao por quem tiver
  permissao. `BACKLOG-0089` foi ampliado nesta rodada para cobrir tambem o PR-F3-10 (dez PRs no total,
  fechando o Bloco B da Fase 3, PR-F3-05 a PR-F3-10).

### Texto pronto para `docs/CHANGELOG.md`

```
## [Fase 3 — PR-F3-10] - 2026-07-19

### Setup progressivo mobile (sexto PR do Bloco B — consumo mobile, fecha o Bloco B)
- `NovaTransacaoModal`: pagamento com cartão sem nenhum cartão cadastrado mostra CTA "Criar
  cartão agora" que abre formulário inline (nome default "Cartão Principal", limite,
  fechamento dia 5, vencimento dia 12) e seleciona o cartão recém-criado.
- Lançamento sem categorias mostra CTA "Criar pacote inicial (9 categorias)" — mesmo conjunto
  do antigo wizard de onboarding, extraído para `src/domain/categoriasIniciais.ts` — e
  alternativa de criar uma categoria única inline, selecionando a criada.
- Erros das ações de setup (criar cartão/categoria) têm mensagem própria e não bloqueiam o
  restante do formulário de lançamento.
- `metas.tsx`: vazio de metas ativas ganha botão "Criar primeira meta" abrindo o formulário
  direto. `contas-fixas.tsx`: vazio ganha botão "Cadastrar recorrência".
- Home: checklist discreto "Complete seu setup" derivado somente das 4 queries já existentes
  (zero requests extras) — primeira movimentação, contas fixas (sem itens PREVISTO) e primeira
  meta (Reservado == 0). Item some sozinho ao ser concluído; "Ocultar" persiste a dispensa só
  no dispositivo (`src/store/homeChecklist.ts`, novo, SecureStore com fallback local-e2e).
  Cartão fica de fora do checklist por não ser derivável com confiança das 4 queries (fatura só
  existe após uso) — coberto pelo CTA contextual no pagamento com cartão.
- Sem migration, sem mudança de backend (consome `categoriaService`/`cartaoService` já
  existentes e as 4 queries já fixadas pelo PR-F3-07 na home).
- Commit: `f0b27de`. Validações: `npx tsc --noEmit` limpo; Jest 33/33 (12 suites, 1 nova).
  Maestro NÃO EXECUTADO nesta rodada para os fluxos de criação contextual e para o checklist —
  acumula com a pendência já classificada como crítica para o onboarding do PR-F3-09. Evidência
  visual claro/escuro também pendente.
```

### Texto pronto para `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`

```
# PR-F3-10 — Setup progressivo mobile

**Status local:** `PASS_COM_RESSALVA`
**Data:** 2026-07-19
**Commit:** `f0b27de`

- [x] CTA "Criar cartão agora" no pagamento com cartão sem cartão cadastrado (formulário inline
      nome/limite/fechamento/vencimento, seleciona o criado)
- [x] CTA "Criar pacote inicial (9 categorias)" no lançamento sem categorias
      (`src/domain/categoriasIniciais.ts`, mesmo conjunto do antigo wizard)
- [x] alternativa de criar categoria única inline, selecionando a criada
- [x] erros de setup com mensagem própria, não bloqueiam o restante do formulário
- [x] `metas.tsx`: botão "Criar primeira meta" no vazio de metas ativas
- [x] `contas-fixas.tsx`: botão "Cadastrar recorrência" no vazio de recorrências
- [x] home: checklist "Complete seu setup" derivado das 4 queries já existentes (zero requests
      extras), itens somem ao concluir, "Ocultar" persiste só no dispositivo
- [x] `src/store/homeChecklist.ts` (novo, SecureStore + fallback local-e2e)
- [x] decisão registrada: cartão fica fora do checklist (não derivável das 4 queries)
- [x] nenhuma migration, sem mudança de backend
- [x] `npx tsc --noEmit` limpo
- [x] Jest 33/33 (12 suites, 1 nova — `homeChecklist.test.ts`)
- [ ] Maestro executado para os 3 fluxos de criação contextual e para o checklist da home
      (ALTA PRIORIDADE — acumula com a pendência crítica já registrada para o onboarding do
      PR-F3-09; juntas cobrem todo o fluxo de primeiro uso do app)
- [ ] evidência visual do fluxo em tema claro/escuro (acumulado com PR-F3-05 a PR-F3-09)
- [ ] revisão dedicada de `quality-reviewer`/`security-auditor`/`lgpd-auditor` para este PR
      específico
- [ ] avaliar tratamento de falha parcial no pacote de 9 categorias (sem rollback hoje)
- [ ] `CHANGELOG.md`/checklist atualizados diretamente (aplicado manualmente a partir deste bloco)
```

## Recomendacao final

Implementacao coerente com a diretriz de produto da Fase 3 ("Experiencia simples"): fecha o ciclo aberto
pelo PR-F3-09 substituindo a oferta agrupada de cartao/categorias/meta dentro do onboarding por criacao
contextual no momento em que o usuario efetivamente precisa de cada cadastro, sem nunca bloquear o fluxo
principal de lancamento. A extracao do pacote de categorias para `src/domain/categoriasIniciais.ts`
reaproveita exatamente o conjunto que o wizard antigo oferecia, evitando divergencia de dados entre
versoes do app. O checklist da home foi implementado com disciplina de custo (zero requests adicionais,
reaproveitando as 4 queries ja fixadas pelo PR-F3-07) e a decisao de excluir cartao do checklist foi
justificada e documentada, nao e uma omissao. O risco central desta rodada e o mesmo da anterior: seis
PRs consecutivos de UI/UX no Bloco B sem nenhuma execucao real de Maestro/simulador, e este PR
especificamente introduz tres novos fluxos de criacao (com chamadas de rede reais a
`categoriaService`/`cartaoService`) que nunca foram exercitados fora do ambiente de desenvolvimento.
Recomenda-se tratar a execucao do Maestro cobrindo onboarding minimo (PR-F3-09) + setup progressivo
(PR-F3-10) como uma unica rodada prioritaria, dado que juntos formam o fluxo completo que qualquer conta
nova percorre.

## Status final

PASS_COM_RESSALVA — ressalvas: (1) Maestro/simulador iOS nao executado para os tres fluxos de criacao
contextual (cartao, pacote de categorias, categoria unica) nem para o checklist discreto da home,
achado de severidade **MEDIA**, acumulado com a pendencia ja classificada como CRITICA para o onboarding
reescrito do PR-F3-09; (2) evidencia visual claro/escuro pendente, acumulada com PR-F3-05 a PR-F3-09; (3)
`CHANGELOG.md` e o checklist de execucao de PRs nao puderam ser atualizados por este agente por restricao
de permissao de arquivo (texto pronto acima); (4) ausencia de evidencia direta de revisao/auditoria
dedicada a este PR especifico; (5) pacote de 9 categorias sem tratamento de falha parcial (sem rollback);
(6) decisao de excluir cartao do checklist da home — documentada e justificada, nao e um bug, mas
registrada para rastreabilidade.

---

> Relatorio mantido pelo `docs-reporter`.
