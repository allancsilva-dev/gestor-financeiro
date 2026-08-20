import { DARK_COLORS, LIGHT_COLORS } from '../theme/colors';
import { contraste } from '../domain/emissores';

/**
 * A barra de limite tem trilha e preenchimento. A trilha precisa ser visível
 * contra o fundo, senão a barra some — foi o que aconteceu no tema claro, onde
 * o valor inicial dava 1,19:1.
 *
 * O alvo é 1,5:1, que é a razão do valor medido na referência
 * (mobile/.design/MEDICOES-carteira.md, `#202f4c` sobre `#080c18`). Não uso 3:1
 * do WCAG 1.4.11 porque a barra é redundante: o mesmo estado está escrito ao
 * lado em texto ("28%", "R$ 8.631,62 de R$ 12.000,00") e no accessibilityValue.
 */
const MIN_TRILHA = 1.5;

describe('trilha da barra de progresso', () => {
  it.each([
    ['escuro', DARK_COLORS],
    ['claro', LIGHT_COLORS],
  ])('é visível sobre o fundo no tema %s', (_nome, cores) => {
    expect(contraste(cores.trilha, cores.bg)).toBeGreaterThanOrEqual(MIN_TRILHA);
  });
});

describe('paleta', () => {
  it('os dois temas declaram exatamente as mesmas chaves', () => {
    expect(Object.keys(LIGHT_COLORS).sort()).toEqual(Object.keys(DARK_COLORS).sort());
  });

  it('texto principal e secundário passam AA sobre o fundo nos dois temas', () => {
    for (const [nome, cores] of [['escuro', DARK_COLORS], ['claro', LIGHT_COLORS]] as const) {
      expect({ nome, ok: contraste(cores.textPrimary, cores.bg) >= 4.5 }).toEqual({ nome, ok: true });
      expect({ nome, ok: contraste(cores.textSecondary, cores.bg) >= 4.5 }).toEqual({ nome, ok: true });
    }
  });

  it('valor em vermelho e em verde passam AA sobre o fundo nos dois temas', () => {
    for (const [nome, cores] of [['escuro', DARK_COLORS], ['claro', LIGHT_COLORS]] as const) {
      expect({ nome, ok: contraste(cores.danger, cores.bg) >= 4.5 }).toEqual({ nome, ok: true });
      expect({ nome, ok: contraste(cores.success, cores.bg) >= 4.5 }).toEqual({ nome, ok: true });
    }
  });

  it('a ação em destaque (brandFg) passa AA sobre o fundo nos dois temas', () => {
    for (const [nome, cores] of [['escuro', DARK_COLORS], ['claro', LIGHT_COLORS]] as const) {
      expect({ nome, ok: contraste(cores.brandFg, cores.bg) >= 4.5 }).toEqual({ nome, ok: true });
    }
  });
});
