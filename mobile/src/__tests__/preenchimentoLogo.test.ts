import { logos } from 'logos-bancos-br/react-native';
import { LOGO_MEDIDO, PREENCHIMENTO_LOGO } from '../domain/emissoresDataset.gen';
import {
  ALVO_MARCA, ALVO_PLACA, entradaLogoDoIspb, logoSolidoDoIspb, preenchimentoDoIspb,
  tamanhoLogoNoTile, TIPO_MARCA, TIPO_PLACA,
} from '../domain/logosEmissores';
import {
  CONTRASTE_MARCA_MIN, CONTRASTE_PLACA_MIN, contraste, contrasteDeLuz, EMISSORES,
  estiloDoLogo, identidadeDoCartao, luminancia,
} from '../domain/emissores';

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

// Os dois desenhos que o pacote mistura, e que o render trata diferente:
// placa própria (o asset já traz o fundo) e marca solta sobre transparente.
const PLACAS = { PicPay: '22896431', Itau: '60701190', BB: '00000000', Bradesco: '60746948' } as const;
const MARCAS = { Nubank: '18236120', C6: '31872495', Sicoob: '02038232' } as const;

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

/**
 * O segundo bug, e o motivo deste ramo existir: o tile do cartão pintava branco
 * atrás de TODO logo. Nos assets que já são uma placa da marca isso virava um
 * quadrado branco com a placa sobrando dentro (o caso do Itaú). Separar placa
 * de marca solta é o que permite tirar o fundo.
 */
describe('placa própria vs marca solta', () => {
  it('só indexa chaves que o pacote de logos realmente publica', () => {
    for (const chave of Object.keys(LOGO_MEDIDO)) {
      expect(logos[chave]).toBeDefined();
    }
  });

  it('mede todo asset publicado, sem buraco no mapa', () => {
    for (const chave of Object.keys(logos)) {
      expect(LOGO_MEDIDO[chave]).toBeDefined();
    }
  });

  it.each(Object.entries(PLACAS))('%s é placa e não tem cor de tint', (_marca, ispb) => {
    const entrada = entradaLogoDoIspb(ispb)!;
    expect(entrada[0]).toBe(TIPO_PLACA);
    expect(entrada[5]).toBeNull();
    expect(logoSolidoDoIspb(ispb)).toBe(true);
  });

  it.each(Object.entries(MARCAS))('%s é marca solta e traz cor e luz', (_marca, ispb) => {
    const entrada = entradaLogoDoIspb(ispb);
    expect(entrada).not.toBeNull();
    const [tipo, luzP02, luzP40, luzP60, luzP98, cor] = entrada!;
    expect(tipo).toBe(TIPO_MARCA);
    expect(cor).toMatch(/^#[0-9A-F]{6}$/);
    for (const luz of [luzP02, luzP40, luzP60, luzP98]) {
      expect(luz).toBeGreaterThanOrEqual(0);
      expect(luz).toBeLessThanOrEqual(1);
    }
    // Percentis, então em ordem: p02 <= p40 <= p60 <= p98.
    expect(luzP02).toBeLessThanOrEqual(luzP40);
    expect(luzP40).toBeLessThanOrEqual(luzP60);
    expect(luzP60).toBeLessThanOrEqual(luzP98);
    expect(logoSolidoDoIspb(ispb)).toBe(false);
  });

  it('emissor sem logo não é placa nem marca', () => {
    expect(logoSolidoDoIspb(null)).toBe(false);
    expect(entradaLogoDoIspb(null)).toBeNull();
    expect(logoSolidoDoIspb('00000000000')).toBe(false);
  });

  it('a cor da marca é a da própria marca, não inventada', () => {
    // Roxo oficial do Nubank, extraído do PNG pelo gerador.
    expect(entradaLogoDoIspb(MARCAS.Nubank)![5]).toBe('#820AD1');
  });
});

describe('tamanho do logo dentro do tile', () => {
  // Os dois call sites: o tile do cartão (32*k, k=1 em 287dp) e o rodapé da
  // parcela no carrossel da Home.
  const TILES = [16, 32, 44];
  const fatores = [...new Set(Object.values(PREENCHIMENTO_LOGO)), 1];

  it.each([['placa', ALVO_PLACA], ['marca', ALVO_MARCA]] as const)(
    'o conteúdo ocupa a MESMA fração do tile em todo asset (%s)',
    (_tipo, alvo) => {
      for (const tile of TILES) {
        for (const fator of fatores) {
          const conteudo = tamanhoLogoNoTile(tile, fator, alvo) * fator;
          // Só o arredondamento da imagem para pixel inteiro separa um do
          // outro, e ele é sempre para baixo: o conteúdo fica no alvo ou até um
          // ponto abaixo, nunca acima.
          expect(tile * alvo - conteudo).toBeGreaterThanOrEqual(0);
          expect(tile * alvo - conteudo).toBeLessThan(1);
        }
      }
    },
  );

  it('o conteúdo nunca excede o tile — nem no asset de menor fator', () => {
    const menor = Math.min(...Object.values(PREENCHIMENTO_LOGO));
    expect(menor).toBeCloseTo(0.4141, 4); // pior asset do pacote
    for (const alvo of [ALVO_PLACA, ALVO_MARCA]) {
      for (const tile of TILES) {
        for (const fator of [...fatores, menor]) {
          expect(tamanhoLogoNoTile(tile, fator, alvo) * fator).toBeLessThanOrEqual(tile);
        }
      }
    }
  });

  it('a placa preenche o tile; a marca solta guarda respiro', () => {
    expect(tamanhoLogoNoTile(32, 1, ALVO_PLACA)).toBe(32);
    expect(tamanhoLogoNoTile(32, 1, ALVO_MARCA)).toBe(27); // 32 * 0,86 -> 27,5
    expect(tamanhoLogoNoTile(16, 1, ALVO_PLACA)).toBe(16);
  });

  it('o asset com margem cresce, e é a margem transparente que transborda', () => {
    // 32 / 0,6484 = 49,3 -> a imagem passa do tile de 32...
    expect(tamanhoLogoNoTile(32, 0.6484, ALVO_PLACA)).toBe(49);
    // ...mas o desenho dentro dela volta ao tamanho do tile.
    expect(Math.round(49 * 0.6484)).toBe(32);
  });

  it('a marca solta fica abaixo do teto medido de descentramento do bbox', () => {
    // O bbox dos assets não é centrado no PNG e é a imagem, não o bbox, que o
    // tile centraliza. Teto sem corte = 1/(1 + 2·d), com d = pior deslocamento
    // em frações do lado do bbox: 0,0693 entre as marcas soltas.
    expect(ALVO_MARCA).toBeLessThan(1 / (1 + 2 * 0.0693));
    // A placa vai a 1 de propósito: o corte que sobra no pior asset (d =
    // 0,0043) é 0,14pt num tile de 32pt, dentro do canto já arredondado.
    expect(ALVO_PLACA).toBe(1);
    expect(32 * (ALVO_PLACA * (1 + 2 * 0.0043) - 1)).toBeLessThan(0.3);
  });
});

/**
 * A regra que substitui o tile branco: a marca solta vai com a cor original
 * quando contrasta com o fundo em que cai, e tingida no próprio matiz quando
 * não contrasta. Placa nunca é tingida — o desenho já traz o próprio fundo.
 */
describe('estilo do logo contra o fundo', () => {
  // O fundo real de cada tela: o topo do gradiente do cartão (que carrega a
  // cor da própria marca, e por isso é onde a marca some) e o card do tema
  // claro, onde o carrossel de parcelas desenha o mesmo logo em 16pt.
  const topoDoCartao = (nome: string) => identidadeDoCartao({ nome }).from;
  const FUNDO_CLARO = '#FFFFFF';
  const estiloDe = (ispb: string, fundo: string) => estiloDoLogo(entradaLogoDoIspb(ispb), fundo);

  it('sem logo não há estilo nenhum a aplicar', () => {
    const estilo = estiloDoLogo(null, FUNDO_CLARO);
    expect(estilo.solido).toBe(false);
    expect(estilo.tint).toBeNull();
    expect(estilo.apoio).toBeNull();
  });

  it.each(Object.entries(PLACAS))('%s é placa: nunca tingida', (_marca, ispb) => {
    for (const fundo of [topoDoCartao('Itaú'), FUNDO_CLARO, '#111B34']) {
      const estilo = estiloDe(ispb, fundo);
      expect(estilo.solido).toBe(true);
      expect(estilo.tint).toBeNull();
      expect(estilo.alvo).toBe(ALVO_PLACA);
    }
  });

  it('placa que contrasta com o fundo não ganha apoio', () => {
    // Squircle azul-marinho do Itaú sobre o cartão laranja: lê sozinho.
    expect(estiloDe(PLACAS.Itau, topoDoCartao('Itaú')).apoio).toBeNull();
    expect(estiloDe(PLACAS.BB, FUNDO_CLARO).apoio).toBeNull();
  });

  it('placa com detalhe fino lê sozinha, sem apoio', () => {
    // O wordmark branco do `bari` ocupa 3% do asset preto — e é justamente ele
    // que aparece no cartão escuro. Medir só a maioria condenaria a placa.
    expect(estiloDe('00556603', '#111B34').apoio).toBeNull();
  });

  it('placa chapada, sem detalhe nenhum, entra como marca e é tingida', () => {
    // O BRB é um desenho preto sem variação interna: como placa ele sumiria em
    // qualquer cartão escuro, então o dataset o classifica como marca solta.
    const FUNDO = '#111B34';
    const estilo = estiloDe('00000208', FUNDO);
    expect(estilo.solido).toBe(false);
    expect(estilo.tint).not.toBeNull();
    expect(contraste(estilo.tint!, FUNDO)).toBeGreaterThanOrEqual(CONTRASTE_MARCA_MIN);
  });

  it('quando a placa precisa de apoio, ela encolhe para o apoio aparecer', () => {
    // Placa cinza-média fictícia sobre um fundo da mesma luz: nem o extremo
    // claro nem o escuro dela se separam.
    const FUNDO = '#767676';
    const placaCega = [TIPO_PLACA, 0.2, 0.21, 0.21, 0.22, null] as const;
    const estilo = estiloDoLogo(placaCega, FUNDO);
    expect(estilo.apoio).not.toBeNull();
    expect(estilo.alvo).toBe(ALVO_MARCA); // encolhe: em ALVO_PLACA cobriria o apoio
    // O apoio se separa do fundo, mas continua sendo a cor dele — não branco.
    const separacao = contraste(estilo.apoio!, FUNDO);
    expect(separacao).toBeGreaterThan(1.2);
    expect(separacao).toBeLessThan(2);
    expect(estilo.apoio).not.toBe('#FFFFFF');
  });

  it.each([['Nubank', MARCAS.Nubank], ['C6', MARCAS.C6]])(
    '%s some no próprio cartão e por isso é tingida acima do piso',
    (nome, ispb) => {
      // O cartão herda a cor da marca, então é justo aqui que a marca solta
      // desaparece: roxo sobre roxo, preto sobre preto.
      const fundo = topoDoCartao(nome);
      const { tint } = estiloDe(ispb, fundo);
      expect(tint).not.toBeNull();
      expect(contraste(tint!, fundo)).toBeGreaterThanOrEqual(CONTRASTE_MARCA_MIN);
    },
  );

  it('marca que contrasta com o próprio cartão passa sem tint', () => {
    // Sicoob é verde-limão (#A3C40E) sobre um cartão verde-escuro: lê sozinha,
    // e tingir aqui só afastaria a cor da marca sem ganho nenhum.
    expect(estiloDe(MARCAS.Sicoob, topoDoCartao('Sicoob')).tint).toBeNull();
  });

  it('marca escura sobre fundo quase preto passa sem tint', () => {
    // Nada de tingir por reflexo: o roxo do Nubank lê num fundo neutro escuro,
    // e só o cartão roxo é que o apaga.
    expect(estiloDe(MARCAS.Nubank, '#131313').tint).toBeNull();
  });

  it('o tint preserva o matiz da marca em vez de virar branco', () => {
    const { tint } = estiloDe(MARCAS.Nubank, topoDoCartao('Nubank'));
    expect(tint).not.toBe('#FFFFFF');
    // Continua roxo: o canal azul e o vermelho passam do verde, como no #820AD1.
    const [r, g, b] = [1, 3, 5].map(i => parseInt(tint!.slice(i, i + 2), 16));
    expect(b).toBeGreaterThan(g);
    expect(r).toBeGreaterThan(g);
  });

  it('marca clara sobre fundo claro é escurecida, não clareada', () => {
    // Marca de luz alta: no cartão escuro passa direto, no card branco não.
    const clara = [TIPO_MARCA, 0.7, 0.8, 0.9, 0.95, '#FFE600'] as const;
    expect(estiloDoLogo(clara, topoDoCartao('Nubank')).tint).toBeNull();
    const { tint } = estiloDoLogo(clara, FUNDO_CLARO);
    expect(tint).not.toBeNull();
    expect(contraste(tint!, FUNDO_CLARO)).toBeGreaterThanOrEqual(CONTRASTE_MARCA_MIN);
  });

  it('todo asset publicado fica legível em qualquer um dos fundos reais', () => {
    // A varredura que fecha o buraco: não basta o emissor curado, porque o
    // usuário digita o nome de qualquer instituição do dataset.
    const fundos = new Set([FUNDO_CLARO, '#111B34']);
    for (const emissor of EMISSORES) {
      const id = identidadeDoCartao({ nome: emissor.rotulo });
      fundos.add(id.from);
      fundos.add(id.to);
    }
    for (const chave of Object.keys(LOGO_MEDIDO)) {
      const entrada = entradaLogoDoIspb(chave)!;
      for (const fundo of fundos) {
        const { tint, apoio, alvo } = estiloDoLogo(entrada, fundo);
        // Placa com apoio tem de encolher, ou o apoio fica coberto por ela.
        if (apoio) expect(alvo).toBe(ALVO_MARCA);
        const atras = apoio ?? fundo;
        if (tint) {
          expect(contraste(tint, atras)).toBeGreaterThanOrEqual(CONTRASTE_MARCA_MIN);
          continue;
        }
        // Sem tint, o desenho vai cru: ou ele já contrasta com o fundo, ou o
        // apoio entrou para dar o degrau. Um dos dois tem de valer.
        const [tipo, luzP02, luzP40, luzP60, luzP98] = entrada;
        const luzAtras = luminancia(atras);
        const ehPlaca = tipo === TIPO_PLACA;
        const piso = ehPlaca ? CONTRASTE_PLACA_MIN : CONTRASTE_MARCA_MIN;
        const [luzBaixa, luzAlta] = ehPlaca ? [luzP02, luzP98] : [luzP40, luzP60];
        const contrasta =
          (luzAlta > luzAtras && contrasteDeLuz(luzAlta, luzAtras) >= piso) ||
          (luzBaixa < luzAtras && contrasteDeLuz(luzBaixa, luzAtras) >= piso);
        expect(contrasta || apoio !== null).toBe(true);
      }
    }
  });
});

describe('identidadeDoCartao expõe o fator junto do logo', () => {
  it('PicPay traz logo, o fator medido e a marca de placa', () => {
    const id = identidadeDoCartao({ nome: 'PicPay' });
    expect(id.logo).not.toBeNull();
    expect(id.logoPreenchimento).toBeCloseTo(0.6484, 4);
    expect(id.logoSolido).toBe(true);
    expect(id.logoMedido![5]).toBeNull();
  });

  it('Inter traz logo full-bleed', () => {
    const id = identidadeDoCartao({ nome: 'Inter' });
    expect(id.logo).not.toBeNull();
    expect(id.logoPreenchimento).toBe(1);
    expect(id.logoSolido).toBe(true);
  });

  it('Nubank traz marca solta, para o render decidir o tint', () => {
    const id = identidadeDoCartao({ nome: 'Nubank' });
    expect(id.logo).not.toBeNull();
    expect(id.logoSolido).toBe(false);
    expect(id.logoMedido).not.toBeNull();
    expect(estiloDoLogo(id.logoMedido, id.from).tint).not.toBeNull();
  });

  it('asset de id 0 continua sendo um logo', () => {
    // O id do Metro é um número e pode ser 0 (é o caso do BB no mapa do
    // pacote). Testar `logo` em contexto booleano jogava esse cartão no
    // monograma e apagava a placa amarela.
    const id = identidadeDoCartao({ nome: 'BB' });
    expect(id.logo).toBe(0);
    expect(id.logoMedido).not.toBeNull();
    expect(id.logoSolido).toBe(true);
  });

  it('emissor fora do catálogo não tem logo e o fator é neutro', () => {
    const id = identidadeDoCartao({ nome: 'Cartão do Zé' });
    expect(id.logo).toBeNull();
    expect(id.logoPreenchimento).toBe(1);
    expect(id.logoSolido).toBe(false);
    expect(id.logoMedido).toBeNull();
  });
});
