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
  greeting: { fontSize: 22, lineHeight: 28, fontWeight: '800', letterSpacing: -0.4 },
  section: { fontSize: 20, lineHeight: 26, fontWeight: '700', letterSpacing: -0.3 },
  cardTitle: { fontSize: 15, lineHeight: 20, fontWeight: '700' },
  label: { fontSize: 14, lineHeight: 18, fontWeight: '500' },
  body: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
  meta: { fontSize: 12, lineHeight: 16, fontWeight: '500' },
  value: { fontSize: 15, lineHeight: 20, fontWeight: '700' },
  chip: { fontSize: 14, lineHeight: 18, fontWeight: '600' },
  button: { fontSize: 15, lineHeight: 20, fontWeight: '700' },
  tabLabel: { fontSize: 11, lineHeight: 14, fontWeight: '600' },
} as const;

// Todo valor monetário usa isto — largura de dígito estável entre atualizações.
// Sem `as const`: TextStyle não aceita tupla readonly em fontVariant.
export const numeric: Pick<TextStyle, 'fontVariant'> = { fontVariant: ['tabular-nums'] };

export const shadow = {
  none: {},
  card: {
    shadowColor: '#091426',
    shadowOpacity: 0.1,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
    elevation: 3,
  },
  // glow do FAB: a cor vem de colors.fabGlow, aqui só a geometria
  glow: {
    shadowOpacity: 1,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 6 },
    elevation: 10,
  },
} as const;

// Padding lateral padrão de tela — a referência alinha tudo nesta guia
export const screenPadding = spacing.lg;

// A tab bar é um painel flutuante: o conteúdo rola por baixo dela. Medidas
// conferidas em mobile/.design/referencia-home.png. O espaço a reservar no fim
// das telas roláveis vem de `useTabBarSpace()`, que soma a safe area.
export const tabBar = {
  altura: 69,
  margem: 15,
} as const;
