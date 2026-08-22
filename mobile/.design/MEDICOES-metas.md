# Medições — referência da tela Metas

Fonte: `mobile/.design/referencia-metas.png` (591×1280, JPEG do WhatsApp reencodado).
Escala: **591px ↔ 360dp (fator 1.6417)** — a mesma da home e da carteira.
Ferramenta: `node scripts/design-diff.mjs` + recortes ampliados.

Ressalva herdada: áreas sólidas são confiáveis; texto fino colorido perde croma na
subamostragem do JPEG. Onde houver dúvida vale o token do tema, não o pixel lido.

**Fora de escopo:** o banner "IA pronta para analisar suas metas" (y 210–325 na
referência) não é implementado — o sistema não tem IA. A comparação de fidelidade
ignora essa faixa.

## Geometria (px medido → dp)

| Elemento | px | dp |
|---|---|---|
| Título "Metas" | x 27, cap y 105–129 | x **16**, fonte **~20**, weight 800 — **não implementado**, ver ressalva |
| Botão `+` do header | x 512–565, y 90–143 | **33** de diâmetro, margem direita **16** — **não implementado**, ver ressalva |
| Eyebrow "OBJETIVOS" | x 43–139, y 361–372 | fonte **~10**, tracking ~1.2, cap 6.7 |
| Título "Suas metas ativas" | x 44–348, cap y 392–422 | fonte **~25**, weight **500** (a leitura inicial de 700 foi corrigida na calibração) |
| Parágrafo de apoio | 2 linhas, y 446 e 477 | fonte **14**, lineHeight **19** |
| Card de meta: caixa | x 30–560, y 535–790 | x **18**, largura **323**, altura **155** |
| Card: padding lateral | 27 | **16** |
| Card: padding vertical | 14 / 16 | **10** |
| Gap entre cards | 789 → 843 = 54 | **33** |
| Tile do anel | x 56–190, y 549–683 | **82×82**, radius **~18** |
| Anel: diâmetro externo | 109 | **66** |
| Anel: espessura do traço | 8 | **5** |
| Emoji dentro do anel | 33×21 | fonte **~22** |
| Título da meta | cap y 558–584 | fonte **~18**, weight 700 |
| Percentual | cap y 560–579, x 475–533 | fonte **~17**, weight 800 |
| Valor reservado | y 608–628 | fonte **~16**, weight 700 |
| "de R$ …" | y 610–628 | fonte **~15**, weight 500, secundário |
| Barra de progresso | y 648–656, x 208–533 | altura **6**, radius pill, largura **198** |
| Divisor | y 701–702 | hairline **1**, 11dp abaixo do tile |
| Lixeira | 20×23 | glyph **~13**, Ionicons size 20 |
| Lápis | 22×22 | glyph **~13**, Ionicons size 20 |
| Pill "+ Depositar" | x 384–533, y 721–774 | **91×33**, radius pill |
| Rótulo de ritmo | y 752–770 | fonte **~13**, weight 600 |

Conferência da barra: card 1 preenche 208→419 de 208→533 = **64.9%** ("65%") e
card 2 preenche 208→474 = **81.8%** ("82%"). A régua da referência é honesta.

## Cores medidas

| Ponto | Medido | Leitura |
|---|---|---|
| Fundo da página | `#080c18` | `colors.bg` ✓ exato |
| Base do card (área neutra) | `#151d31` | `colors.card` `#111b34` + ~2% branco |
| Divisor do card | `#1f273b` sobre `#151d31` | `rgba(255,255,255,0.06)` |
| Borda do tile | `#3b3e4d` sobre `#182034` | `rgba(255,255,255,0.10)` |
| Fundo do tile | `#20243d` | `rgba(255,255,255,0.03)` |
| Trilha da barra / do anel (roxo) | `#44366b` | cor da meta a ~28% sobre o card — o código usa **0.36** (`metaCores.ts`), calibrado para a trilha não sumir no tema claro |
| Preenchimento (roxo) | `#9b53dd` → `#ca95fd` | **gradiente**, clareia para a direita |
| Trilha da barra / do anel (verde) | `#175055` | idem |
| Preenchimento (verde) | `#04b296` → `#65dbca` | idem |
| Pill "Depositar" | `#f7f9fa` | branco, texto escuro |
| Rótulo de ritmo (atenção) | `#a47436` medido | croma comido pelo JPEG → `colors.warning` |
| Rótulo de ritmo (no trilho) | `#0e74a2` medido | idem → `colors.brandFg` |
| Tab bar | `#0c1526` | `colors.navBg` ✓ exato |

## Gradiente do card (grade 5×5)

Card 1 (roxo), y 545/600/660/720/780 × x 35/150/300/450/555:

```
        x=35      x=150     x=300     x=450     x=555
y=545   #232c3b   #22273c   #1e263a   #392a61   #403466
y=600   #192135   #20243f   #192135   #372861   #33295d
y=660   #191f37    (anel)   #161733   #32235c   #342858
y=720   #2b224f   #29224c   #151d31    (pill)   #131b2f
y=780   #342f4d   #2c2050   #141c30   #141d2e   #1c2534
```

Leitura: **não é gradiente linear**. A base é neutra (`#151d31`) e há dois brilhos
radiais na cor da meta — um no **topo-direita** e outro na **base-esquerda**. O
centro do card fica neutro. Card 2 repete o padrão na cor verde-água
(`#084347` à direita, `#0c343e` na base-esquerda).

Modelo adotado: base `colors.card` + duas `RadialGradient` (react-native-svg) em
posição fixa, opacidade calibrada contra este arquivo.

## Achados que corrigem a leitura a olho

- O anel **não** começa às 12h exatas: o traço arranca alguns graus à direita do
  topo e segue horário, com `strokeLinecap="round"` e um halo de brilho por baixo.
- O tile atrás do anel é bem maior que o `IconTile` de 44 usado hoje: **82dp**,
  com borda de 1px e fundo quase transparente.
- O ícone dentro do anel é **emoji** (🏷️ na referência), coerente com o princípio
  "emoji é o sistema de ícones" do PRODUCT.md.
- O percentual é **menor** que o título da meta (17 contra 18), ao contrário do que
  o peso da cor sugere.
- A barra tem gradiente próprio; sólido puro fica opaco demais.

## Calibração contra o render (2026-08-20)

Sete rodadas no simulador (iPhone 17 Pro, Release, backend local), medindo o
screenshot contra a referência com os dois cards normalizados na mesma largura.

**A referência é 360dp; o aparelho é 402dp.** Em dp cru o card estica 12% e o texto
não — a composição deixa de ser a da foto. Entrou `src/theme/escala.ts`: `e(v)`
converte medida-da-referência para a tela atual (`largura / 360`). Todas as medidas
desta tela passam por `e()`.

**`width="100%"` no `Svg` resolve contra a caixa de padding**, não contra a borda do
card: o brilho parava a 326dp de um card de 361.7dp (exatamente 361.7 − 2×17.9 de
padding) e deixava uma costura vertical visível. O tamanho passou a vir do
`onLayout` do card.

**SF Pro é ~9% mais larga que a fonte do mock** com a mesma altura de maiúscula
(cap 4.8% da largura do card nos dois; avanço 39.6% na referência contra 43.4% no
render). Isso estourava a linha de valores e quebrava o rótulo de ritmo em duas
linhas. Onde a linha precisa caber, venceu a largura:

| Elemento | 1ª tentativa | Final | Motivo |
|---|---|---|---|
| Valor reservado | `e(16)` | `e(13)` | linha de valores cortava na borda do card |
| "de R$ …" | `e(15)` | `e(12)` | idem |
| Texto da pill | `e(15)` | `e(13)` | pill 34.4% da largura do card contra 28.3% da referência |
| Rótulo de ritmo | `e(13)` | `e(10)` | quebrava em duas linhas; a referência cabe em uma |
| Lixeira / lápis | `size 20` | `e(16)` | glifos da referência medem 12–13dp, não 20 |
| Título da seção | weight 700 | weight 500 | traço da referência é fino |

Ficam iguais à referência por altura de maiúscula: título da meta `e(18)`,
percentual `e(17)`, título da seção `e(25)`, eyebrow `e(10)`.

O rótulo do card 2 ("Atingindo em 16m · dezembro de 2027") quebra em duas linhas —
**a referência também quebra** nesse card. É comportamento, não defeito.

| Ponto | Referência | Render final |
|---|---|---|
| Fundo da página | `#080c18` | `#080c18` exato |
| Centro do card | `#151d31` | `#161d3a` |
| Tinta topo-direita | `#3b2866` | `#392a69` |
| Margem lateral do card | 18dp | `e(18)` = 20.0dp medidos |
| Largura do card | 323dp | 361.7dp = `e(323)` ✓ |
| Altura da pill | 33dp | 10.0% da largura do card nos dois ✓ |

Divergência assumida: o rótulo de atenção usa `colors.warning` (`#fbbf24`), mais
amarelo que o âmbar da foto (`#a47436` medido, com croma comido pelo JPEG). Token do
tema vence pixel de JPEG, como na calibração da carteira.

## Ressalva: o header não segue esta medição

O header desta tela usa `ui/CabecalhoDeTela` — `typography.screenTitle` (26/800) e ação circular
de 36 —, não os 20/800 e 33 medidos acima.

É decisão de produto, tomada quando o padrão foi fechado: conviviam quatro corpos de título de
tela (26 na Carteira, 23 em Contas, 22 na Fatura, 20 aqui) e um tamanho por tela não sobrevive a
treze telas. A escala tipográfica do `DESIGN.md` é a régua; a medição do mock vale para o que é
específico desta tela.

**O card de meta continua medido** — geometria, cores e a calibração acima valem integralmente.
A única outra divergência é o texto da pill "Depositar", que passou a usar `typography.button`
(15/700) ao adotar `ui/Botao`, contra os `e(13)` calibrados; a geometria da pill (`e(33)` × `e(91)`)
foi mantida.
