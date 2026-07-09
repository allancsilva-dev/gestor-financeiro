# Design

Sistema visual do Gestor Financeiro. Fonte canônica: protótipo `docs/Gestor Financeiro (standalone).html` (tokens extraídos do render real). Implementação mobile: `mobile/src/theme/colors.ts` (migrar brand de ciano → violeta).

## Theme

Claro por padrão, escuro por toggle (e respeitando o sistema). Claro = lavanda suave + cards brancos + violeta. Escuro = azul-noite profundo + mesmos violeta/verde/vermelho reequilibrados.

## Color

Estratégia: **restrained** — neutros lavanda + violeta como único acento de marca (~10% da superfície: FAB, tab ativa, links, progresso). Verde/vermelho são semânticos de dinheiro, nunca decorativos.

### Light (padrão)

| Token | Valor | Uso |
|---|---|---|
| `bg` | `#f0f2f8` | fundo de tela (lavanda neutra) |
| `card` | `#ffffff` | cards, sheets, tab bar |
| `border` | `rgba(0,0,0,0.07)` | divisores, contorno de card |
| `textPrimary` | `#1a1d23` | títulos, valores |
| `textSecondary` | `#6b7280` | subtítulos, metadados |
| `textMuted` | `#8a91a0` | placeholder/decorativo (o `#aab0bd` do protótipo falha AA — escurecido) |
| `brand` | `#7c5cfc` | FAB, botões primários, tab ativa |
| `brandDeep` | `#5546b8` | gradiente do card de saldo (com `brand`) |
| `brandText` | `#6d28d9` | texto/ícone violeta sobre claro (AA) |
| `brandBg` | `rgba(124,92,252,0.14)` | tiles/chips violeta |
| `success` | `#1a9e4a` | dinheiro que entra |
| `danger` | `#cc2233` | dinheiro que sai |
| `warning` | `#b36000` | alertas |
| `info` | `#0090c0` | informativo (ex-brand ciano, rebaixado) |
| `*Bg` | cor a 12% alpha | fundos de badge/tile semânticos |

### Dark

| Token | Valor | Uso |
|---|---|---|
| `bg` | `#0b0d14` | fundo |
| `card` | `#151827` | cards, tab bar |
| `border` | `rgba(255,255,255,0.08)` | divisores |
| `textPrimary` | `#e8edf5` | títulos, valores |
| `textSecondary` | `#9aa5b8` | subtítulos |
| `textMuted` | `#5d6b82` | decorativo |
| `brand` | `#9d85ff` | acento (mais claro p/ contraste) |
| `brandDeep` | `#5546b8` | gradientes |
| `brandText` | `#b8a6ff` | texto violeta sobre escuro |
| `success` / `danger` | `#2ed573` / `#ff6b7a` | dinheiro |

## Typography

System font (SF Pro / Roboto via `system-ui`). Sem fonte customizada — o protótipo é system-native.

- Display (saldo): 34–40, weight 800, tabular-nums
- Título de tela: 26–28, weight 700
- Título de card/item: 15–16, weight 600
- Corpo/metadado: 13–14, weight 400–500, `textSecondary`
- Valores monetários: sempre bold, tabular-nums, verde com `+` / vermelho com `−`

## Components

- **Card**: branco, radius 16–20, sombra suave `rgba(30,26,60,0.08) 0 8px 24px`; sem borda no claro, borda sutil no escuro. Nunca card dentro de card.
- **Card de saldo (hero)**: gradiente violeta `brand → brandDeep`, texto branco, radius 20–24, chip de variação `rgba(255,255,255,0.18)`.
- **Tile de categoria**: quadradinho 40–44, radius 12, fundo pastel (cor semântica ou da categoria a 12–14% alpha), emoji dentro.
- **Lista de transações**: linhas em card único com divisores, tile + nome (600) + metadado (data · categoria em `textSecondary`), valor à direita colorido.
- **FAB**: círculo 56, violeta `brand`, sombra com glow `rgba(124,92,252,0.5)`, centrado na tab bar.
- **Tab bar**: 5 slots (Início, Transações, +, Planejamento, Mais), ícone + label 11, ativo em violeta.
- **Chips/segmentos** (Todos · Entradas · Saídas): pill radius 999, ativo com borda/fundo brand.
- **Badges de status**: pill pequeno, fundo semântico a 12%, texto da cor plena (Ativa=info, Concluída=success).
- **Barra de progresso** (metas/orçamentos): trilha `border`, preenchimento brand ou success (100%), altura 6, radius 3.

## Layout

- Padding lateral de tela: 16–20
- Gap entre cards: 12–16
- Safe areas sempre respeitadas (notch, home indicator)
- Grid 2 colunas para atalhos/hub "Mais"

## Motion

Sutil e nativa: transições de navegação padrão da plataforma, press feedback (opacity/scale 0.97), skeleton shimmer no loading. Ease-out. Sem bounce. Respeitar Reduce Motion (crossfade no lugar).
