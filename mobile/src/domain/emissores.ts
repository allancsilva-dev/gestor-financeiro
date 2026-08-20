// Identidade visual do cartão físico por emissor.
//
// Três camadas, nesta precedência:
//   1. `cartao.cor` escolhida pelo usuário vence sempre;
//   2. catálogo por alias exato — nunca `includes`, que casaria "Inter" dentro
//      de "Intermedium" e pintaria o cartão errado em silêncio;
//   3. fallback determinístico por hash do nome, para o emissor fora do
//      catálogo nunca aparecer cinza ou quebrado.
//
// O campo `fonte` diz de onde veio a cor:
//   'oficial'     — hex publicado pela marca ou por referência de marca;
//   'aproximacao' — a cor dominante da marca é fato público (Inter é laranja),
//                   o hex exato não foi confirmado. É editável pelo usuário.
// Nenhum hex entra aqui "de memória" sem um destes dois rótulos.

export type FonteCor = 'oficial' | 'aproximacao';

export interface Emissor {
  slug: string;
  /** Wordmark impresso no cartão. */
  rotulo: string;
  /** Chaves normalizadas que resolvem para este emissor. */
  aliases: string[];
  /** Cor de marca; o gradiente do cartão é derivado dela. */
  base: string;
  /** Tile do logo. Sem valor, usa `base`. */
  /** Tile do logo. Sem valor, usa `base` rebaixada ao teto de luminância. */
  logoBg?: string;
  /** Só faz sentido junto de `logoBg`: sem ele, a tinta é derivada. */
  logoFg?: string;
  /** Monograma do tile — 1 a 2 caracteres, como a marca se escreve. */
  glifo: string;
  fonte: FonteCor;
}

export const EMISSORES: Emissor[] = [
  // ── cores confirmadas em fonte de marca ──────────────────────────────
  { slug: 'nubank', rotulo: 'NUBANK', aliases: ['nubank', 'nu', 'roxinho', 'nuconta', 'ultravioleta', 'nubankultravioleta'],
    base: '#820AD1', glifo: 'nu', fonte: 'oficial' },
  { slug: 'itau', rotulo: 'ITAÚ', aliases: ['itau', 'itauunibanco', 'itaupersonnalite', 'personnalite', 'iti', 'itaucard'],
    base: '#FF6200', glifo: 'i', fonte: 'oficial' },
  { slug: 'bradesco', rotulo: 'BRADESCO', aliases: ['bradesco', 'bradescard', 'next', 'bradesconext'],
    base: '#CC092F', glifo: 'b', fonte: 'oficial' },
  { slug: 'santander', rotulo: 'SANTANDER', aliases: ['santander', 'santanderbrasil', 'sx'],
    base: '#EA1D25', glifo: 'S', fonte: 'oficial' },
  { slug: 'bb', rotulo: 'BANCO DO BRASIL', aliases: ['bb', 'bancodobrasil', 'brasil', 'ourocard'],
    base: '#0061AA', logoBg: '#FEED08', logoFg: '#0061AA', glifo: 'BB', fonte: 'oficial' },
  { slug: 'caixa', rotulo: 'CAIXA', aliases: ['caixa', 'caixaeconomica', 'caixaeconomicafederal', 'cef'],
    base: '#015CA9', glifo: 'X', fonte: 'oficial' },
  { slug: 'picpay', rotulo: 'PICPAY', aliases: ['picpay'],
    base: '#11C76F', glifo: 'P', fonte: 'oficial' },
  { slug: 'c6', rotulo: 'C6 BANK', aliases: ['c6', 'c6bank', 'c6carbon', 'carbon'],
    base: '#242424', glifo: 'C6', fonte: 'oficial' },

  // ── matiz de marca é fato público; hex exato não confirmado ──────────
  { slug: 'inter', rotulo: 'INTER', aliases: ['inter', 'bancointer', 'intermedium'],
    base: '#FF7A00', glifo: 'i', fonte: 'aproximacao' },
  { slug: 'xp', rotulo: 'XP', aliases: ['xp', 'xpinvestimentos', 'rico'],
    base: '#1A1A1A', logoBg: '#FFC709', logoFg: '#1A1A1A', glifo: 'XP', fonte: 'aproximacao' },
  { slug: 'btg', rotulo: 'BTG PACTUAL', aliases: ['btg', 'btgpactual'],
    base: '#001E62', glifo: 'B', fonte: 'aproximacao' },
  { slug: 'mercadopago', rotulo: 'MERCADO PAGO', aliases: ['mercadopago', 'mercadolivre', 'meli', 'mp'],
    base: '#00B1EA', logoBg: '#FFE600', logoFg: '#00589E', glifo: 'mp', fonte: 'aproximacao' },
  { slug: 'neon', rotulo: 'NEON', aliases: ['neon', 'banconeon'],
    base: '#00A3FF', glifo: 'N', fonte: 'aproximacao' },
  { slug: 'original', rotulo: 'ORIGINAL', aliases: ['original', 'bancooriginal'],
    base: '#00A868', glifo: 'O', fonte: 'aproximacao' },
  { slug: 'will', rotulo: 'WILL BANK', aliases: ['will', 'willbank'],
    base: '#FFC800', glifo: 'w', fonte: 'aproximacao' },
  { slug: 'pan', rotulo: 'BANCO PAN', aliases: ['pan', 'bancopan', 'panamericano'],
    base: '#0068B3', glifo: 'P', fonte: 'aproximacao' },
  { slug: 'bmg', rotulo: 'BMG', aliases: ['bmg', 'bancobmg'],
    base: '#FF6B00', glifo: 'B', fonte: 'aproximacao' },
  { slug: 'safra', rotulo: 'SAFRA', aliases: ['safra', 'bancosafra', 'jsafra'],
    base: '#003057', logoBg: '#B99A5B', logoFg: '#003057', glifo: 'S', fonte: 'aproximacao' },
  { slug: 'sicredi', rotulo: 'SICREDI', aliases: ['sicredi'],
    base: '#3FA110', glifo: 'S', fonte: 'aproximacao' },
  { slug: 'sicoob', rotulo: 'SICOOB', aliases: ['sicoob'],
    base: '#003641', logoBg: '#7DB61C', logoFg: '#003641', glifo: 'S', fonte: 'aproximacao' },
  { slug: 'banrisul', rotulo: 'BANRISUL', aliases: ['banrisul'],
    base: '#0072BC', glifo: 'B', fonte: 'aproximacao' },
  { slug: 'brb', rotulo: 'BRB', aliases: ['brb', 'bancodebrasilia'],
    base: '#005CA9', glifo: 'BRB', fonte: 'aproximacao' },
  { slug: 'agibank', rotulo: 'AGIBANK', aliases: ['agibank', 'agi'],
    base: '#FF6600', glifo: 'A', fonte: 'aproximacao' },
  { slug: 'digio', rotulo: 'DIGIO', aliases: ['digio'],
    base: '#0F62FE', glifo: 'd', fonte: 'aproximacao' },
  { slug: 'porto', rotulo: 'PORTO', aliases: ['porto', 'portoseguro', 'portobank'],
    base: '#0057A6', glifo: 'P', fonte: 'aproximacao' },
  { slug: 'magalu', rotulo: 'MAGALU', aliases: ['magalu', 'magazineluiza', 'luizacred'],
    base: '#0086FF', glifo: 'M', fonte: 'aproximacao' },
  { slug: 'riachuelo', rotulo: 'RIACHUELO', aliases: ['riachuelo', 'midway'],
    base: '#E60012', glifo: 'R', fonte: 'aproximacao' },
  { slug: 'renner', rotulo: 'RENNER', aliases: ['renner', 'realize', 'realizecfi'],
    base: '#E30613', glifo: 'R', fonte: 'aproximacao' },
  { slug: 'carrefour', rotulo: 'CARREFOUR', aliases: ['carrefour', 'atacadao'],
    base: '#004E9F', glifo: 'C', fonte: 'aproximacao' },
  { slug: 'ame', rotulo: 'AME', aliases: ['ame', 'amedigital', 'americanas'],
    base: '#E6007E', glifo: 'a', fonte: 'aproximacao' },
];

// ── resolução ──────────────────────────────────────────────────────────

/** lowercase, sem acento, sem pontuação e sem os prefixos genéricos. */
export const normalizarEmissor = (valor: string | null | undefined): string =>
  (valor ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/\b(banco|cartao|cart|conta|bank)\b/g, '')
    .replace(/[^a-z0-9]/g, '');

const PORALIAS: Map<string, Emissor> = new Map();
for (const e of EMISSORES) {
  for (const alias of e.aliases) PORALIAS.set(alias, e);
}

/**
 * Quebra o texto em palavras e normaliza cada uma. "Itaú Gold" vira
 * ['itau','gold'] — palavras genéricas ('banco', 'cartão', 'conta') somem na
 * normalização e viram string vazia, que é descartada.
 */
export const tokensDoEmissor = (valor: string | null | undefined): string[] =>
  (valor ?? '')
    .split(/[\s\-_/.,]+/)
    .map(normalizarEmissor)
    .filter(Boolean);

/**
 * Resolve o emissor pelo campo banco e, se não achar, pelo nome do cartão —
 * muita gente cadastra "Nubank Ultravioleta" só no nome.
 *
 * Duas passadas: primeiro a string inteira normalizada, depois palavra por
 * palavra. A comparação é sempre por IGUALDADE com um alias, nunca por
 * substring: "Itaú Gold" resolve pelo token 'itau', mas "Banco Internacional"
 * vira ['internacional'], que não é alias de ninguém, e portanto NÃO cai no
 * Inter. Esse é o falso positivo que o match por token poderia reintroduzir e
 * que o teste blinda.
 */
export const resolverEmissor = (
  entrada: { banco?: string | null; nome?: string | null } | null | undefined,
): Emissor | null => {
  if (!entrada) return null;
  const candidatos = [entrada.banco, entrada.nome];

  for (const candidato of candidatos) {
    const chave = normalizarEmissor(candidato);
    if (chave && PORALIAS.has(chave)) return PORALIAS.get(chave)!;
  }
  for (const candidato of candidatos) {
    for (const token of tokensDoEmissor(candidato)) {
      if (PORALIAS.has(token)) return PORALIAS.get(token)!;
    }
  }
  return null;
};

// ── cor ────────────────────────────────────────────────────────────────

const clamp01 = (v: number) => Math.min(1, Math.max(0, v));

export const hexParaRgb = (hex: string): [number, number, number] => {
  const limpo = hex.replace('#', '');
  const full = limpo.length === 3 ? limpo.split('').map(c => c + c).join('') : limpo;
  return [
    parseInt(full.slice(0, 2), 16),
    parseInt(full.slice(2, 4), 16),
    parseInt(full.slice(4, 6), 16),
  ];
};

const rgbParaHex = (r: number, g: number, b: number) =>
  '#' + [r, g, b].map(v => Math.round(clamp01(v / 255) * 255).toString(16).padStart(2, '0')).join('');

const rgbParaHsl = (r: number, g: number, b: number): [number, number, number] => {
  const rn = r / 255, gn = g / 255, bn = b / 255;
  const max = Math.max(rn, gn, bn), min = Math.min(rn, gn, bn);
  const l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let h: number;
  if (max === rn) h = ((gn - bn) / d + (gn < bn ? 6 : 0)) / 6;
  else if (max === gn) h = ((bn - rn) / d + 2) / 6;
  else h = ((rn - gn) / d + 4) / 6;
  return [h, s, l];
};

const hslParaRgb = (h: number, s: number, l: number): [number, number, number] => {
  if (s === 0) { const v = l * 255; return [v, v, v]; }
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
  const p = 2 * l - q;
  const canal = (t: number) => {
    let tt = t; if (tt < 0) tt += 1; if (tt > 1) tt -= 1;
    if (tt < 1 / 6) return p + (q - p) * 6 * tt;
    if (tt < 1 / 2) return q;
    if (tt < 2 / 3) return p + (q - p) * (2 / 3 - tt) * 6;
    return p;
  };
  return [canal(h + 1 / 3) * 255, canal(h) * 255, canal(h - 1 / 3) * 255];
};

/** Devolve o mesmo matiz/saturação com outra luminosidade HSL. */
export const comLuz = (hex: string, luz: number): string => {
  const [h, s] = rgbParaHsl(...hexParaRgb(hex));
  return rgbParaHex(...hslParaRgb(h, s, clamp01(luz)));
};

/** Luminância relativa WCAG. */
export const luminancia = (hex: string): number => {
  const [r, g, b] = hexParaRgb(hex).map(v => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

export const contraste = (a: string, b: string): number => {
  const la = luminancia(a), lb = luminancia(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
};

export const TINTA_CLARA = '#FFFFFF';
export const TINTA_ESCURA = '#101010';

/**
 * O cartão é um gradiente: o texto de cima cai sobre `from` e o de baixo sobre
 * `to`. A tinta escolhida é a que tem o melhor contraste no PIOR dos dois
 * extremos — nunca a média, que esconderia uma ponta ilegível.
 */
export const tintaPara = (from: string, to: string): string => {
  const clara = Math.min(contraste(TINTA_CLARA, from), contraste(TINTA_CLARA, to));
  const escura = Math.min(contraste(TINTA_ESCURA, from), contraste(TINTA_ESCURA, to));
  return clara >= escura ? TINTA_CLARA : TINTA_ESCURA;
};

// Tetos do gradiente do cartão, em LUMINÂNCIA RELATIVA — não em
// lightness HSL. Contraste depende de luminância, e um verde e um azul com o
// mesmo L do HSL têm luminâncias muito diferentes (o canal verde pesa 0.72).
// Clampar por L deixava passar cartão verde/amarelo onde nenhuma tinta atinge
// 4.5:1. Com estes tetos, a tinta clara sempre passa AA nas duas pontas e o
// matiz da marca é preservado — que é como cartão real se parece: escuro, com
// a cor da marca dando o tom, não o brilho.
const Y_TOPO_MAX = 0.082;
const Y_BASE_MAX = 0.032;

/** Escurece mantendo matiz e saturação até a luminância caber no teto. */
export const limitarLuminancia = (hex: string, yMax: number): string => {
  if (luminancia(hex) <= yMax) return hex;
  const [h, s] = rgbParaHsl(...hexParaRgb(hex));
  let baixo = 0;
  let alto = rgbParaHsl(...hexParaRgb(hex))[2];
  for (let i = 0; i < 24; i++) {
    const meio = (baixo + alto) / 2;
    const candidato = rgbParaHex(...hslParaRgb(h, s, meio));
    if (luminancia(candidato) > yMax) alto = meio;
    else baixo = meio;
  }
  return rgbParaHex(...hslParaRgb(h, s, baixo));
};

/** Gradiente do cartão a partir de uma cor de marca. */
export const gradienteDe = (base: string): [string, string] => {
  const [, , l] = rgbParaHsl(...hexParaRgb(base));
  return [
    limitarLuminancia(comLuz(base, l * 1.05), Y_TOPO_MAX),
    limitarLuminancia(comLuz(base, l * 0.45), Y_BASE_MAX),
  ];
};

/** Hash estável: o mesmo nome sempre gera a mesma cor, em qualquer sessão. */
const hashMatiz = (texto: string): number => {
  let h = 2166136261;
  for (let i = 0; i < texto.length; i++) {
    h ^= texto.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return ((h >>> 0) % 360) / 360;
};

export interface IdentidadeCartao {
  rotulo: string;
  glifo: string;
  from: string;
  to: string;
  tinta: string;
  logoBg: string;
  logoFg: string;
  emissor: Emissor | null;
  /** true quando a cor veio de `cartao.cor`, não do catálogo. */
  corDoUsuario: boolean;
}

const HEX_VALIDO = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;

const iniciais = (nome: string): string => {
  const GENERICAS = new Set(['banco', 'cartao', 'cartão', 'conta', 'bank', 'da', 'de', 'do', 'dos', 'das']);
  const partes = nome.trim().split(/\s+/)
    .filter(Boolean)
    .filter(p => !GENERICAS.has(p.toLowerCase()));
  if (partes.length === 0) return nome.trim().slice(0, 2).toUpperCase() || '?';
  if (partes.length === 0) return '?';
  if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
  return (partes[0][0] + partes[1][0]).toUpperCase();
};

/**
 * Identidade final do cartão. Ordem: cor do usuário > catálogo > fallback.
 */
export const identidadeDoCartao = (
  cartao: { nome?: string | null; banco?: string | null; cor?: string | null } | null | undefined,
): IdentidadeCartao => {
  const emissor = resolverEmissor(cartao);
  const nome = cartao?.nome?.trim() || emissor?.rotulo || 'Cartão';
  const corUsuario = cartao?.cor && HEX_VALIDO.test(cartao.cor) ? cartao.cor : null;

  const base = corUsuario
    ?? emissor?.base
    // Fallback determinístico: matiz do hash, croma e luz fixos, para o cartão
    // de um emissor desconhecido sair harmônico em vez de cinza.
    ?? comLuz(rgbParaHex(...hslParaRgb(hashMatiz(normalizarEmissor(nome) || nome), 0.55, 0.4)), 0.4);

  const [from, to] = gradienteDe(base);
  // Tile sem cor propria usa a marca ja dentro do teto de luminancia: crua,
  // uma marca vibrante (vermelho Santander) nao aguenta glifo nenhum em AA.
  const logoBgFinal = emissor?.logoBg ?? limitarLuminancia(base, Y_TOPO_MAX);
  return {
    rotulo: emissor?.rotulo ?? nome.toUpperCase(),
    glifo: emissor?.glifo ?? iniciais(nome),
    from,
    to,
    tinta: tintaPara(from, to),
    logoBg: logoBgFinal,
    // Tinta do tile derivada por contraste: marca com fundo claro (laranja,
    // amarelo, verde) nao aguenta glifo branco e reprovaria AA.
    logoFg: emissor?.logoFg ?? tintaPara(logoBgFinal, logoBgFinal),
    emissor,
    corDoUsuario: corUsuario !== null,
  };
};

/** Swatches oferecidos no formulário de cartão. */
export const CORES_SUGERIDAS: string[] = EMISSORES.map(e => e.base).filter(
  (cor, i, todas) => todas.indexOf(cor) === i,
);
