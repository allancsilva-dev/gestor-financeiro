import type { TextStyle } from 'react-native';

// Escala visual do app. Valores derivados da referência
// (mobile/.design/referencia-home.png, 591px ↔ 360dp) e documentados em
// DESIGN.md. Antes disso tudo era literal inline em cada tela.

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 24,
  xxxl: 32,
} as const;

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 24,
  pill: 999,
} as const;

// `weight` sai como string porque o RN tipa fontWeight assim
export const typography = {
  display: { fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1 },
  displayCents: { fontSize: 22, fontWeight: '800' },
  // Valor de destaque de sub-tela (total da fatura): menor que o hero da home
  subDisplay: { fontSize: 28, lineHeight: 34, fontWeight: '800', letterSpacing: -0.6 },
  greeting: { fontSize: 22, lineHeight: 28, fontWeight: '800', letterSpacing: -0.4 },
  // Título de tela de topo (Carteira, Ajustes): era literal inline em cada tela
  screenTitle: { fontSize: 26, lineHeight: 32, fontWeight: '800', letterSpacing: -0.6 },
  section: { fontSize: 20, lineHeight: 26, fontWeight: '700', letterSpacing: -0.3 },
  cardTitle: { fontSize: 15, lineHeight: 20, fontWeight: '700' },
  // Título de linha de lista (`ui/ListRow`): 600, um degrau abaixo do card
  rowTitle: { fontSize: 15, lineHeight: 20, fontWeight: '600' },
  label: { fontSize: 14, lineHeight: 18, fontWeight: '500' },
  body: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
  meta: { fontSize: 12, lineHeight: 16, fontWeight: '500' },
  value: { fontSize: 15, lineHeight: 20, fontWeight: '700' },
  // Texto digitado em `ui/Field` — mesmo corpo de `value`, peso de leitura
  input: { fontSize: 15, lineHeight: 20, fontWeight: '500' },
  // Pill de status (`ui/Badge`): o menor tipo do app ainda legível, 12 é o piso
  badge: { fontSize: 12, lineHeight: 16, fontWeight: '700' },
  chip: { fontSize: 14, lineHeight: 18, fontWeight: '600' },
  button: { fontSize: 15, lineHeight: 20, fontWeight: '700' },
  tabLabel: { fontSize: 11, lineHeight: 14, fontWeight: '600' },
} as const;

// Todo valor monetário usa isto — largura de dígito estável entre atualizações.
// Sem `as const`: TextStyle não aceita tupla readonly em fontVariant.
export const numeric: Pick<TextStyle, 'fontVariant'> = { fontVariant: ['tabular-nums'] };

// Só geometria. A cor sai da paleta — `colors.sombra` para superfícies e
// `colors.fabGlow` para o brilho do FAB —, porque sombra é um valor de tema:
// no escuro ela quase some, no claro ela carrega a elevação do card.
export const shadow = {
  none: {},
  card: {
    shadowOpacity: 0.08,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 8 },
    elevation: 3,
  },
  glow: {
    shadowOpacity: 0.5,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 8,
  },
} as const;

// Padding lateral padrão de tela — a referência alinha tudo nesta guia
export const screenPadding = spacing.lg;

// Raio do card. Fica fora da escala `radius` de propósito: é um papel medido
// na referência (DESIGN.md fala em 16–20), não um degrau da escala.
export const cardRadius = 18;

// A tab bar é um painel flutuante: o conteúdo rola por baixo dela. Medidas
// conferidas em mobile/.design/referencia-home.png. O espaço a reservar no fim
// das telas roláveis vem de `useTabBarSpace()`, que soma a safe area.
export const tabBar = {
  altura: 69,
  margem: 15,
} as const;

// Diâmetro do FAB, medido na referência (DESIGN.md). O componente usava 56.
export const fabSize = 53;
