# Design

Sistema visual do Gestor Financeiro. Fonte canônica: este documento. Implementação:
`mobile/src/theme/colors.ts` (paleta), `mobile/src/theme/tokens.ts` (escala) e as medições em
`mobile/.design/MEDICOES-*.md`, tiradas das referências com `scripts/design-diff.mjs`.

## Theme

Escuro por padrão de fato (é o tema das referências), com o claro em paridade total de tokens.
O usuário escolhe em **Ajustes → Aparência**: `Sistema · Claro · Escuro`. A escolha vale por
aparelho (`src/store/temaPreferido.ts`) e chega às telas por `useTheme()` / `useEsquema()`
(`src/theme/index.ts`), que caem no `useColorScheme()` do SO quando a preferência é `sistema`.

Escuro = azul-noite profundo + ciano. Claro = cinza-lavanda claro + ciano escurecido para AA.

## Color

Estratégia: **restrained** — neutros frios + **ciano como acento único de marca** (~10% da
superfície: FAB, tab ativa, chip ativo, links). Violeta sobrevive só como `accent` (saudação da
home, brilhos). Verde/vermelho/laranja são semânticos de dinheiro, nunca decorativos.

Entidades com cor própria não usam a marca: metas tingem o card com `paletaDaMeta()`
(`src/theme/metaCores.ts`) e cartões com a cor do emissor (`src/domain/emissores.ts`).

### Dark (referência)

| Token | Valor | Uso |
|---|---|---|
| `bg` | `#080c18` | fundo de tela |
| `card` | `#111b34` | cards, sheets |
| `border` | `rgba(255,255,255,0.07)` | divisores, contorno de card |
| `textPrimary` | `#ffffff` | títulos, valores |
| `textSecondary` | `#bac5d9` | subtítulos, metadados |
| `textMuted` | `#8690a9` | decorativo |
| `brand` | `#17b3ff` | FAB, tab ativa, indicador |
| `brandFg` | `#4cc4ff` | texto/ícone de marca sobre o fundo (AA) |
| `brandBg` | `rgba(23,179,255,0.13)` | tiles/chips de marca |
| `accent` | `#a78bfa` | violeta de apoio (saudação, brilhos) |
| `success` / `danger` | `#34d399` / `#fb7185` | dinheiro que entra / que sai |
| `warning` | `#fbbf24` | alertas, faturas |
| `navBg` | `#0c1526` | painel flutuante da tab bar |
| `trilha` | `#21314f` | trilha de barra de progresso (travada em `tema.test.ts`) |
| `overlay` | `rgba(255,255,255,0.08)` | superfície sutil sobre o fundo |
| `*Bg` | cor a 12% alpha | fundos de badge/tile semânticos |

### Light

Mesmas chaves, reequilibradas: `bg #eef2f8`, `card #ffffff`, `textPrimary #0b1220`,
`brand #0a86c9`, `success #0f7f45`, `danger #c32348`. A paridade de chaves e o contraste AA de
`textPrimary`, `textSecondary`, `danger`, `success` e `brandFg` sobre `bg` são travados por
`mobile/src/__tests__/tema.test.ts` nos dois temas.

## Typography

System font (SF Pro / Roboto). Sem fonte customizada. Tokens em `src/theme/tokens.ts` — a tela
faz spread (`{ ...typography.value, ...numeric, color: colors.success }`), não literal solto.

- `display` 34/800 — saldo do hero
- `screenTitle` 26/800 — título de tela de topo
- `greeting` 22/800 · `section` 20/700
- `cardTitle` 15/700 · `label` 14/500 · `body` 14/500 · `meta` 12/500
- Valores monetários: sempre bold + `numeric` (tabular-nums), verde entra / vermelho sai

## Components

- **Card**: `colors.card`, radius 16–20, sombra suave no claro e borda hairline no escuro. Nunca
  card dentro de card.
- **Superfície com brilho** (`ui/SuperficieComBrilho`): base neutra + dois brilhos radiais (SVG),
  topo-direita e base-esquerda, centro neutro. É o fundo do card de meta e do bloco de conta em
  Ajustes. Não é gradiente linear — ver `MEDICOES-metas.md`.
- **Tile de categoria** (`ui/IconTile`): quadradinho 40–44, radius 12, fundo pastel, emoji dentro.
  Emoji é o sistema de ícones; nada de biblioteca colorida.
- **Lista** (`ui/ListRow`): linhas em card único com divisores, tile + título (600) + metadado,
  valor ou chevron à direita.
- **Cabeçalho de tela** (`ui/CabecalhoDeTela`): título grande à esquerda, ação circular à direita,
  safe area somada aqui. Não há header nativo (`headerShown: false` em todos os layouts).
- **Cabeçalho de seção** (`ui/CabecalhoSecao`): eyebrow curto + título + linha de orientação.
- **FAB**: círculo 53 com gradiente `fabFrom → fabTo` e glow, centrado na tab bar.
- **Tab bar**: painel flutuante (altura 69, margem 15, radius 24) com 5 slots — Início, Análises,
  **+**, Metas, Ajustes. Ativo em ciano, com tile e barra indicadora.
- **Chips/segmentos** (`ui/Chip`): pill radius 999, ativo com borda/fundo de marca, alvo ≥44.
- **Badges de status** (`ui/Badge`): pill pequeno, fundo semântico a 12%, texto da cor plena.
- **Barra de progresso**: trilha `colors.trilha`, altura 6, radius 3; preenchimento em gradiente
  (sólido puro fica opaco demais).
- **Botão** (`ui/Botao`): a única forma de botão do app — `primario` (fundo `brand`, texto
  `brandText`), `secundario` (borda `border`), `perigo` (`danger`) e `texto` (só `brandFg`). Altura
  52 (44 no `texto`), `radius.md`, `typography.button` em caixa normal — nunca `CAIXA ALTA` com
  `letterSpacing`. Estados obrigatórios no próprio componente: normal, pressionado (`activeOpacity`
  0.85), desabilitado (opacidade 0.6) e carregando (spinner no lugar do rótulo, `busy` no
  `accessibilityState`).
- **Campo de senha** (`ui/CampoSenha`): envolve `ui/Field` e acrescenta o olho (alvo 44) e o medidor
  opcional de 4 segmentos (`colors.trilha` → `danger`/`warning`/`success`), cujo texto diz o que
  falta para a regra do backend — nunca uma nota abstrata de "força".
- **Progresso de passos** (`ui/PassosProgresso`): N segmentos `radius.pill` de 4 de altura,
  preenchidos em `brand`. É o indicador de fluxo multi-etapa; barra contínua com porcentagem não
  serve, o usuário quer saber quantas telas faltam.
- **Estados**: `ui/SkeletonBox` com a forma do conteúdo real (nunca spinner no meio da tela),
  `ui/EstadoVazio` para vazio e erro, retry sempre por `refetch()` do react-query.

## Layout

- Padding lateral de tela: `screenPadding` (16)
- Gap entre cards: 12–16; fim de tela rolável sempre por `useTabBarSpace()`
- Safe areas por `useSafeAreaInsets()`, nunca `SafeAreaView`
- Grid 2 colunas para o hub de ferramentas em Ajustes

## Receita de tela

Raiz `View flex:1` em `colors.bg` → `ScrollView`/`FlatList` com `paddingBottom: useTabBarSpace()`
→ `CabecalhoDeTela` (+ `ui/BackButton` nas sub-telas de `more/`) → hero opcional → chips de
segmento → `CabecalhoSecao` → lista → modais `presentationStyle="pageSheet"` como irmãos do scroll,
com barra Cancelar / Título / ação e corpo em `ui/Field`.

Cadeia de estados, nesta ordem: `params inválidos → isLoading → isError → vazio → conteúdo`.

## Receita de fluxo de entrada

As telas de `(auth)` e o `onboarding` não seguem a receita de tela acima — elas usam
`ui/TelaFluxo`, que é o mesmo esqueleto para todas: `colors.bg` → `KeyboardAvoidingView` →
linha de topo (voltar circular + `ui/PassosProgresso`) → título `screenTitle` + subtítulo `body` →
conteúdo em `ui/Entrance` → rodapé fixo com os `ui/Botao` do passo. Sem tab bar, sem `useTabBarSpace()`.

Regras do fluxo:

- **Uma decisão por tela.** Cadastro = identidade → senha → consentimento. Onboarding = conta
  principal (obrigatória) → renda → categorias → cartão → meta → revisão.
- **Erro mora no campo** (`Field.error` / `CampoSenha.error`), alimentado pelo `details` do backend
  via `src/utils/erros.ts`. A faixa de erro geral é só para falha de rede, regra de negócio sem
  campo e sessão expirada.
- **Opcional se pula em um toque**, com "Pular por agora" sempre na mesma posição do rodapé, e a
  revisão final mostra o que ficou de fora. O que foi pulado reaparece no checklist da home.
- **Nada se perde**: o rascunho do onboarding vive em `src/store/onboardingRascunho.ts` até o envio
  dar certo, e o envio é um único POST idempotente.

**Escala:** medidas em token cru (`spacing`/`typography`/`radius`). O `e()` de `src/theme/escala.ts`
converte pela largura da tela e só vale onde existe mock medido em `.design/` — hoje, a tela de
metas. Sem referência, escalar é inventar proporção.

## Motion

Sutil e nativa: transição de rota `fade` de 150–180ms, press feedback por `activeOpacity`
(0.7–0.9), `ui/Entrance` em cascata (fade + 12px, 340ms, ease-out) e shimmer no skeleton.
Sem bounce, sem haptics. Reduce Motion desliga a animação de rota e leva `Entrance`/`SkeletonBox`
direto ao estado final.

## Acessibilidade

**O texto visível é o rótulo.** Um `TouchableOpacity` com `accessible` (padrão) colapsa os
filhos num nó único: se ele tem `accessibilityLabel` próprio, o texto dos `<Text>` de dentro
some da árvore de acessibilidade — o leitor de tela anuncia só o rótulo e a busca por texto
(Maestro, testes) deixa de encontrar a palavra que está na tela.

Regra:

- Controle **só com texto**: nada de `accessibilityLabel`. O RN deriva o rótulo dos filhos e o
  texto continua encontrável.
- Controle **com ícone + texto**: `accessibilityLabel` **igual ao texto visível**. Sem ele, o iOS
  concatena o glifo da fonte de ícones no rótulo (`"\uf626, Carteira"`) — o leitor de tela anuncia
  o glifo e a busca por texto não acha. `accessibilityElementsHidden` no ícone **não** resolve:
  a composição do rótulo ignora essa prop.
- Controle **icon-only** (Fab, chevron, olho de senha, badge de sigla): `accessibilityLabel`
  é obrigatório — não há texto para derivar.
- Contexto extra ("abre a carteira", "cria a categoria digitada") vai em `accessibilityHint`,
  nunca sobrescrevendo o rótulo.
- `accessibilityRole` e `accessibilityState` continuam sempre.
- Rótulo **nunca** pode divergir do texto visível: era o que escondia "Carteira" atrás de
  "Abrir carteira" (BACKLOG-0096).

Vale para cards compostos (meta, fatura, parcela): o rótulo curado some, os textos do card
voltam à árvore, e a ação do card vira `accessibilityHint`. Nesses cards o nó ainda funde vários
textos num rótulo só, então automação por texto precisa de regex parcial (`.*Meta Smoke.*`) —
o Maestro casa por igualdade total.
