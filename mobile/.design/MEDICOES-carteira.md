# Medições — referência da tela Carteira

Fonte: `mobile/.design/referencia-carteira.png` (591×1280, JPEG do WhatsApp reencodado).
Escala: **591px ↔ 360dp** (fator 1.6417), a mesma da referência da home.
Ferramenta: `node scripts/design-diff.mjs`.

Ressalva de fidelidade herdada de `colors.ts`: áreas sólidas são confiáveis;
texto fino colorido perde croma na subamostragem do JPEG. Para o gradiente do
cartão vale a **cor de marca oficial do emissor**, não o valor lido do arquivo.

## Geometria (px medido → dp)

| Elemento | px | dp |
|---|---|---|
| Padding lateral da tela (título, linhas de fatura, divisores) | 25–26 | **16** |
| Cartão: largura | 59 → 531 = 472 | **287** |
| Cartão: altura | 172 → 470 = 298 | **182** (razão 1.577) |
| Cartão: padding interno (borda → chip/logo) | 34 | **21** |
| Gap entre cartões | 531 → 548 = 17 | **10** |
| Carrossel: padding lateral (centralizado) | 59 | **36** = (360−287)/2 |
| Chip: largura | 93 → 143 = 50 | **30** |
| Dots: centro | y≈502 | 19dp abaixo do cartão |
| Painel resumo: conteúdo | x 47 → 543 | **28.6** (16 da tela + ~12 do painel) |
| Divisores do painel | x 26 → 565 | largura total, a 16dp |
| Barra de progresso: preenchimento | 47 → 189 de 47 → 543 | 28.6% ✓ ("28%") |

## Cores medidas

| Ponto | Medido | Token do tema |
|---|---|---|
| Fundo da página | `#080c18` | `colors.bg` ✓ exato |
| **Painel resumo** | `#080c18` | **sem fundo próprio** — é a página |
| Divisor do painel | `#162131` | `colors.border` (≈ `#191c28`; hairline, dentro do ruído) |
| Trilha da barra | `#202f4c` | — |
| Preenchimento da barra | `#9b27d4` → `#8909bc` | **cor do emissor** (Nubank `#820AD1`) |
| Linha "Próxima fatura" | `#1a2845` | `colors.card` clareado |
| Linha "Fatura atual" | `#1d1d41` | `colors.card` + emissor @ ~10% |
| Valor "Em aberto" | rosa | `colors.danger` `#fb7185` |

## Gradiente do cartão (grade 5×5, x 75–515 / y 190–455)

```
        x=75      x=180     x=300     x=420     x=515
y=190   #573e7a   #693792   #752ca5   #60218c   #4e1c74
y=260   #49376b   #583182   #612e90   #55207c   #461b6a
y=330   #39305a   #492c6e   #5b327e   #4e1f71   #411962
y=400   #2a2a49   #3d285d   #472b69   #452065   #3a1b5c
y=455   #21273d   #362954   #40295f   #3d215e   #381c5a
```

Leitura: mais claro no **topo-centro**, escurecendo para baixo e para as bordas
laterais — não é gradiente linear de 2 paradas. Modelo adotado:
1. base `LinearGradient` diagonal `from → to` derivados da cor de marca;
2. brilho diagonal em `rgba(255,255,255,·)`;
3. vinheta inferior em `rgba(0,0,0,·)`.

As porcentagens de clarear/escurecer são calibradas uma vez contra este arquivo
com `design-diff diff` e valem para todo o catálogo.

## Achados de layout que corrigem a leitura a olho

- O painel de resumo **não** é um card: é conteúdo solto sobre `colors.bg`.
- O carrossel é **centralizado** (`paddingHorizontal = (largura − 287)/2`), não
  alinhado à esquerda — por isso o próximo cartão espia 26dp na borda.
- A barra de limite e o destaque da fatura atual usam a **cor do emissor**,
  não a marca ciano do app.

## Calibração contra o render (2026-08-20)

Primeiro render no simulador (iPhone 17 Pro, Release, backend local) medido com
`design-diff sample`. O fundo bateu exato (`#080c18`), o cartão não:

| Ponto | Referência | 1º render | Correção |
|---|---|---|---|
| topo-centro | `#752ca5` | `#8718d0` | teto de luminância 0.16 → 0.105 |
| topo-esquerda | `#573e7a` | `#9e1ff1` | brilho estava na diagonal, virou faixa central horizontal |
| base-esquerda | `#21273d` | `#6607a4` | teto da base 0.07 → 0.032 e vinheta 0.22 → 0.34 |
| fundo da página | `#080c18` | `#080c18` | exato |

Leitura: um `LinearGradient` diagonal de duas paradas não reproduz a referência,
que é mais clara no **topo-centro** e cai para baixo **e** para as duas bordas.
A composição final são três camadas: base vertical, faixa de brilho horizontal
centrada e vinheta inferior.
