import { logos } from 'logos-bancos-br/react-native';
import { PREENCHIMENTO_LOGO } from '../domain/emissoresDataset.gen';
import {
  ALVO_LOGO_NO_TILE, preenchimentoDoIspb, tamanhoLogoNoTile,
} from '../domain/logosEmissores';
import { identidadeDoCartao } from '../domain/emissores';

// Medidos no alfa dos PNGs do pacote (256x256): 166/256 = 0,6484 para quem tem
// margem transparente, 256/256 = 1 para os full-bleed. Ficam fixos aqui de
// propósito — se o pacote trocar o desenho, o teste avisa em vez de o app
// encolher a marca em silêncio.
const COM_MARGEM = {
  PicPay: '22896431',
  Nubank: '18236120',
  Itau: '60701190',
  PagBank: '08561701',
} as const;
const FULL_BLEED = { Inter: '00416968', Crefisa: '61033106' } as const;

/**
 * O bug: o logo do emissor saía com ~metade do tile branco. Os PNGs do pacote
 * não têm margem uniforme, então nenhuma constante de escala única resolve — a
 * correção é por asset, e são estes números que a sustentam.
 */
describe('preenchimento do logo do emissor', () => {
  it('todo fator está em (0, 1)', () => {
    const valores = Object.values(PREENCHIMENTO_LOGO);
    expect(valores.length).toBeGreaterThan(0);
    for (const fator of valores) {
      expect(Number.isFinite(fator)).toBe(true);
      // 1 é o padrão implícito: quem é full-bleed não ganha linha no arquivo.
      expect(fator).toBeGreaterThan(0);
      expect(fator).toBeLessThan(1);
    }
  });

  it('só indexa chaves que o pacote de logos realmente publica', () => {
    for (const chave of Object.keys(PREENCHIMENTO_LOGO)) {
      expect(logos[chave]).toBeDefined();
    }
  });

  it.each(Object.entries(COM_MARGEM))('%s tem margem: fator 0,6484', (_marca, ispb) => {
    expect(preenchimentoDoIspb(ispb)).toBeCloseTo(0.6484, 4);
  });

  it.each(Object.entries(FULL_BLEED))('%s é full-bleed: fator 1', (_marca, ispb) => {
    expect(PREENCHIMENTO_LOGO[ispb]).toBeUndefined();
    expect(preenchimentoDoIspb(ispb)).toBe(1);
  });

  it('ISPB ausente ou desconhecido cai em 1, nunca em 0', () => {
    expect(preenchimentoDoIspb(null)).toBe(1);
    expect(preenchimentoDoIspb(undefined)).toBe(1);
    expect(preenchimentoDoIspb('00000000000')).toBe(1);
  });
});

describe('tamanho do logo dentro do tile', () => {
  // Os dois call sites: o tile do cartão (32*k, k=1 em 287dp) e o rodapé da
  // parcela no carrossel da Home.
  const TILES = [16, 32, 44];
  const fatores = [...new Set(Object.values(PREENCHIMENTO_LOGO)), 1];

  it('a marca ocupa a MESMA fração do tile em todo asset', () => {
    for (const tile of TILES) {
      for (const fator of fatores) {
        const conteudo = tamanhoLogoNoTile(tile, fator) * fator;
        // Só o arredondamento da imagem para pixel inteiro separa um do outro.
        expect(conteudo).toBeCloseTo(tile * ALVO_LOGO_NO_TILE, 0);
      }
    }
  });

  it('a marca nunca excede o tile — nem a de menor fator', () => {
    const menor = Math.min(...Object.values(PREENCHIMENTO_LOGO));
    expect(menor).toBeCloseTo(0.4141, 4); // pior asset do pacote
    for (const tile of TILES) {
      for (const fator of [...fatores, menor]) {
        expect(tamanhoLogoNoTile(tile, fator) * fator).toBeLessThan(tile);
      }
    }
  });

  it('o asset full-bleed cabe direto no tile, sem ampliação', () => {
    expect(tamanhoLogoNoTile(32, 1)).toBe(30); // 32 * 0,95
    expect(tamanhoLogoNoTile(16, 1)).toBe(15);
  });

  it('o asset com margem cresce, e é a margem transparente que transborda', () => {
    // 32 * 0,95 / 0,6484 = 46,9 -> a imagem passa do tile de 32...
    expect(tamanhoLogoNoTile(32, 0.6484)).toBe(47);
    // ...mas o desenho dentro dela volta a 30, igual ao full-bleed.
    expect(Math.round(47 * 0.6484)).toBe(30);
  });

  it('0,95 é o teto: em 1,0 o asset mais descentrado seria cortado', () => {
    // `02038232` tem 9% de assimetria vertical de margem. Em ALVO=1 o desenho
    // sai 1,2% do lado do tile para fora e o `overflow: hidden` come a borda.
    expect(ALVO_LOGO_NO_TILE).toBeLessThan(1);
    expect(ALVO_LOGO_NO_TILE).toBeCloseTo(0.95, 4);
  });
});

describe('identidadeDoCartao expõe o fator junto do logo', () => {
  it('PicPay traz logo e o fator medido do asset', () => {
    const id = identidadeDoCartao({ nome: 'PicPay' });
    expect(id.logo).not.toBeNull();
    expect(id.logoPreenchimento).toBeCloseTo(0.6484, 4);
  });

  it('Inter traz logo full-bleed', () => {
    const id = identidadeDoCartao({ nome: 'Inter' });
    expect(id.logo).not.toBeNull();
    expect(id.logoPreenchimento).toBe(1);
  });

  it('emissor fora do catálogo não tem logo e o fator é neutro', () => {
    const id = identidadeDoCartao({ nome: 'Cartão do Zé' });
    expect(id.logo).toBeNull();
    expect(id.logoPreenchimento).toBe(1);
  });
});
