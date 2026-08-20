// Tokens canônicos: DESIGN.md.
// Os valores dark saem de medição em mobile/.design/referencia-home.png, com
// scripts/design-diff.mjs — não de leitura a olho.
//
// Estratégia: ciano como marca única (FAB, tab ativa, chip ativo, botões,
// links). Violeta sobrevive só como `accent` da saudação. Verde/vermelho/
// laranja são semânticos de dinheiro — entradas, saídas e faturas.
//
// Ressalva de fidelidade: a referência é um JPEG do WhatsApp. Áreas sólidas
// (fundo, cards, ciano, campo de busca, tile da aba ativa) foram medidas com
// precisão. As cores de texto fino colorido — violeta, verde, vermelho e
// laranja — perdem croma na subamostragem do JPEG e não são recuperáveis do
// arquivo; os valores abaixo respeitam o matiz medido, com saturação plena.
export const DARK_COLORS = {
  bg: '#080c18',
  card: '#111b34',
  top: '#111b34',
  border: 'rgba(255,255,255,0.07)',
  textPrimary: '#ffffff',
  textSecondary: '#bac5d9',
  textMuted: '#8690a9',
  brand: '#17b3ff',
  brandDeep: '#0a84c8',
  brandText: '#ffffff',
  brandFg: '#4cc4ff',
  brandBg: 'rgba(23,179,255,0.13)',
  brand2: '#5cd0ff',
  success: '#34d399',
  successOnLight: '#0f6b32',
  warning: '#fbbf24',
  danger: '#fb7185',
  dangerOnLight: '#a5152a',
  info: '#38bdf8',
  successBg: 'rgba(52,211,153,0.12)',
  warningBg: 'rgba(251,191,36,0.12)',
  dangerBg: 'rgba(251,113,133,0.12)',
  infoBg: 'rgba(56,189,248,0.12)',
  // a tab bar é um painel flutuante, com margem lateral e cantos arredondados —
  // não uma faixa colada na base
  navBg: '#0c1526',
  navBorder: 'rgba(255,255,255,0.06)',
  // Trilha da barra de progresso. Medida na referencia como #202f4c; subimos o
  // brilho o minimo para cruzar 1.5:1 contra o fundo (o medido da 1.46, e o
  // JPEG da referencia tem erro de medida proprio). colors.border e fino demais
  // e some. Travado por src/__tests__/tema.test.ts nos dois temas.
  trilha: '#21314f',
  skeletonBase: '#111b34',
  skeletonHighlight: '#1b2745',
  accent: '#a78bfa',
  accentBg: 'rgba(167,139,250,0.16)',
  chipBg: '#111b34',
  chipBorder: 'rgba(255,255,255,0.09)',
  // card de saldo: gradiente diagonal medido ponta a ponta
  heroFrom: '#0c1325',
  heroTo: '#13213d',
  heroSurface: '#101a33',
  fieldBg: '#171b27',
  overlay: 'rgba(255,255,255,0.08)',
  fabFrom: '#3ac4fd',
  fabTo: '#11a2e9',
  fabGlow: 'rgba(23,179,255,0.55)',
  tabActiveBg: '#0d2b43',
};

export const LIGHT_COLORS: typeof DARK_COLORS = {
  bg: '#eef2f8',
  card: '#ffffff',
  top: '#ffffff',
  border: 'rgba(9,20,38,0.08)',
  textPrimary: '#0b1220',
  textSecondary: '#55637a',
  textMuted: '#616d81',
  brand: '#0a86c9',
  brandDeep: '#076aa0',
  brandText: '#ffffff',
  brandFg: '#0a6288',
  brandBg: 'rgba(10,134,201,0.13)',
  brand2: '#17b3ff',
  success: '#0f7f45',
  successOnLight: '#0f6b32',
  warning: '#9a5b00',
  danger: '#c32348',
  dangerOnLight: '#a5152a',
  info: '#0b7ca8',
  successBg: 'rgba(15,127,69,0.12)',
  warningBg: 'rgba(154,91,0,0.12)',
  dangerBg: 'rgba(195,35,72,0.12)',
  infoBg: 'rgba(11,124,168,0.12)',
  navBg: '#ffffff',
  navBorder: 'rgba(9,20,38,0.08)',
  trilha: '#bcc6d4',
  skeletonBase: '#e2e7f0',
  skeletonHighlight: '#f4f7fb',
  accent: '#6d28d9',
  accentBg: 'rgba(109,40,217,0.13)',
  chipBg: '#ffffff',
  chipBorder: 'rgba(9,20,38,0.12)',
  heroFrom: '#ffffff',
  heroTo: '#f4f8fd',
  heroSurface: '#ffffff',
  fieldBg: '#f2f5fa',
  overlay: 'rgba(9,20,38,0.06)',
  // no claro o FAB usa a marca clara: o ciano do dark não passa contraste aqui
  fabFrom: '#1a9ad9',
  fabTo: '#0a86c9',
  fabGlow: 'rgba(10,134,201,0.45)',
  tabActiveBg: 'rgba(10,134,201,0.12)',
};

export type AppColors = typeof DARK_COLORS;
