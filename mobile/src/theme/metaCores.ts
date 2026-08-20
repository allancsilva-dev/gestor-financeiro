import { Meta } from '../types';

/**
 * Paleta por meta — medida de `mobile/.design/referencia-metas.png`.
 *
 * O card de meta não usa a marca ciano: cada meta carrega a própria cor, que tinge
 * o anel, a barra, o percentual e os dois brilhos radiais do fundo. Tudo é derivado
 * de uma cor base única para que qualquer cor vinda do backend (`meta.cor`) produza
 * um card coerente.
 */
export interface PaletaMeta {
  /** Traço do anel e âncora de tudo mais. */
  base: string;
  /** Barra e anel têm gradiente próprio; sólido puro fica opaco demais. */
  fillDe: string;
  fillPara: string;
  /** Trilha da barra e do anel: a base a 30% sobre o card. */
  trilha: string;
  /** Brilho radial do topo-direita e da base-esquerda (opacos, medidos). */
  tintaTopo: string;
  tintaBase: string;
  /** Texto do percentual. */
  percentual: string;
}

// Fallback determinístico quando a meta não traz cor. As duas primeiras são as da
// referência (Viagem ao Japão / Reserva de Emergência).
const PALETA_PADRAO = ['#a855f7', '#07bd9e', '#38bdf8', '#fbbf24', '#fb7185', '#818cf8'];

const HEX = /^#?([0-9a-f]{6})$/i;

const paraRgb = (hex: string): [number, number, number] => {
  const m = HEX.exec(hex.trim());
  const v = m ? m[1] : 'a855f7';
  return [parseInt(v.slice(0, 2), 16), parseInt(v.slice(2, 4), 16), parseInt(v.slice(4, 6), 16)];
};

const paraHex = (rgb: [number, number, number]): string =>
  `#${rgb.map(c => Math.max(0, Math.min(255, Math.round(c))).toString(16).padStart(2, '0')).join('')}`;

/** Mistura linear entre duas cores; `t` = 0 devolve `a`, `t` = 1 devolve `b`. */
export const misturar = (a: string, b: string, t: number): string => {
  const [r1, g1, b1] = paraRgb(a);
  const [r2, g2, b2] = paraRgb(b);
  return paraHex([r1 + (r2 - r1) * t, g1 + (g2 - g1) * t, b1 + (b2 - b1) * t]);
};

/** Luminância relativa (WCAG), para decidir se o card é claro ou escuro. */
const luminancia = (hex: string): number => {
  const canal = (c: number) => {
    const v = c / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  const [r, g, b] = paraRgb(hex);
  return 0.2126 * canal(r) + 0.7152 * canal(g) + 0.0722 * canal(b);
};

export const corValida = (cor?: string | null): boolean => !!cor && HEX.test(cor.trim());

/**
 * Cor base da meta: a que veio do backend, senão uma da paleta escolhida pelo id
 * (estável entre renders e entre sessões).
 */
export const corBaseDaMeta = (meta: Pick<Meta, 'id' | 'cor'>): string => {
  if (corValida(meta.cor)) {
    const c = meta.cor!.trim();
    return c.startsWith('#') ? c.toLowerCase() : `#${c.toLowerCase()}`;
  }
  return PALETA_PADRAO[Math.abs(Math.trunc(meta.id)) % PALETA_PADRAO.length];
};

/**
 * Deriva a paleta inteira da cor base sobre a cor de card do tema.
 * Os fatores saem da medição: trilha ≈ base a 30% sobre o card, preenchimento indo
 * de 6% mais escuro a 42% clareado, brilhos a 30% (topo) e 22% (base).
 */
export const paletaDaMeta = (meta: Pick<Meta, 'id' | 'cor'>, card: string): PaletaMeta => {
  const base = corBaseDaMeta(meta);
  // No tema claro a cor da meta em cima do card branco não passa em AA; escurece.
  const cardClaro = luminancia(card) > 0.5;
  return {
    base,
    fillDe: misturar(base, '#000000', 0.06),
    fillPara: misturar(base, '#ffffff', 0.42),
    trilha: misturar(card, base, 0.36),
    tintaTopo: misturar(card, base, 0.3),
    tintaBase: misturar(card, base, 0.22),
    // AA sobre o card: o roxo cru fica em 4.3:1 no escuro e o ciano some no claro
    percentual: cardClaro ? misturar(base, '#000000', 0.45) : misturar(base, '#ffffff', 0.14),
  };
};
