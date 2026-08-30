#!/usr/bin/env node
// Gera src/domain/emissoresDataset.gen.ts a partir do pacote `logos-bancos-br`.
//
// Por que existe: o catálogo curado em src/domain/emissores.ts cobre as marcas
// que o usuário do app costuma digitar, com hex de fonte conhecida. Ele não
// cobre as outras ~450 instituições que o pacote traz logo. Este script produz
// o índice secundário — nome normalizado -> ISPB — que atende quem digita
// "Crefisa", "Unicred", "Banestes".
//
// Regras que o gerador respeita, e que o teste do domínio blinda:
//   - chave AMBÍGUA não entra: se dois ISPBs com LOGOS DIFERENTES normalizam
//     para a mesma chave, a chave é descartada. A identidade do logo é o
//     sha256 da fonte que o pacote publica, não o nome do arquivo: PicPay tem
//     três CNPJs, cada um com o seu PNG, mas os três são o mesmo desenho —
//     comparar por caminho os trataria como conflito e derrubaria "picpay";
//   - a cor sai do próprio SVG da marca (primeiro fill não-neutro), nunca de
//     memória. Sem SVG legível, sem cor — o hash do nome continua valendo;
//   - o catálogo curado tem precedência no runtime, então uma chave daqui
//     nunca sobrescreve um emissor conhecido.
//
// Rodar: node scripts/gerar-emissores-dataset.mjs
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const raizPacote = path.dirname(require.resolve('logos-bancos-br/package.json'));
const saida = new URL('../src/domain/emissoresDataset.gen.ts', import.meta.url);

const lerJson = (rel) => JSON.parse(fs.readFileSync(path.join(raizPacote, rel), 'utf8'));
// bancos.json é { banks: [...] } e instituicoes-pix.json é { institutions: [...] }.
const comoLista = (x) => {
  if (Array.isArray(x)) return x;
  for (const chave of ['banks', 'institutions', 'bancos', 'instituicoes']) {
    if (Array.isArray(x?.[chave])) return x[chave];
  }
  throw new Error('formato inesperado no JSON do pacote: ' + Object.keys(x ?? {}).join(','));
};

const registros = [
  ...comoLista(lerJson('data/bancos.json')),
  ...comoLista(lerJson('data/instituicoes-pix.json')),
].filter((r) => r && r.ispb && r.logo && r.logo.png);

// ── normalização de nome ────────────────────────────────────────────────
// O nome do dataset é razão social ("PICPAY INSTITUIÇÃO DE PAGAMENTO S.A."),
// não o nome que o usuário digita. Estas são as palavras que sobram em quase
// toda razão social e não identificam ninguém.
const RUIDO = new Set([
  'sa', 's', 'a', 'ltda', 'me', 'epp', 'eireli', 'banco', 'bco', 'banc',
  'multiplo', 'multipla', 'comercial', 'investimento', 'investimentos',
  'instituicao', 'pagamento', 'pagamentos', 'ip', 'sociedade', 'credito',
  'financiamento', 'corretora', 'distribuidora', 'titulos', 'valores',
  'mobiliarios', 'cambio', 'cooperativa', 'central', 'cooperativo',
  'em', 'liquidacao', 'extrajudicial', 'cfi', 'dtvm', 'ctvm', 'scfi', 'scd',
  'cc', 'ccc', 'de', 'do', 'da', 'dos', 'das', 'e', 'o', 'os', 'as', 'para',
  'nacional', 'brasil', 'brasileiro', 'brasileira', 's/a', 'sam', 'holding',
  'financeira', 'financeiro', 'agencia', 'fomento', 'estado', 'municipal',
]);

const semAcento = (v) =>
  v.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

const palavrasUteis = (nome) =>
  semAcento(nome)
    .replace(/[^a-z0-9]+/g, ' ')
    .split(' ')
    .filter((p) => p && !RUIDO.has(p) && !/^\d+$/.test(p));

// ── cor da marca, tirada do SVG ─────────────────────────────────────────
const ehNeutro = (hex) => {
  const n = hex.length === 4
    ? hex.slice(1).split('').map((c) => parseInt(c + c, 16))
    : [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  const [r, g, b] = n;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  // Cinza, branco e preto não identificam marca nenhuma.
  return max - min < 24 || max < 30 || min > 235;
};

// Parte dos SVGs pinta com atributo (`fill="#11C76F"`) e parte com classe CSS
// declarada num bloco <style> (é o caso dos exportados do Illustrator, como o
// do PagSeguro). Ler só o atributo deixava essas marcas sem cor nenhuma.
const classesDoStyle = (svg) => {
  const mapa = new Map();
  for (const [, bloco] of svg.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)) {
    for (const [, nome, hex] of bloco.matchAll(
      /\.([A-Za-z0-9_-]+)\s*\{[^}]*?fill\s*:\s*(#[0-9a-fA-F]{3,6})/g,
    )) {
      mapa.set(nome, hex);
    }
  }
  return mapa;
};

const corDoSvg = (relSvg) => {
  if (!relSvg) return null;
  const arquivo = path.join(raizPacote, relSvg);
  if (!fs.existsSync(arquivo)) return null;
  const svg = fs.readFileSync(arquivo, 'utf8');
  const porClasse = classesDoStyle(svg);

  // Cores de marca na ORDEM em que o documento pinta. Ordem de declaração das
  // classes no <style> não serve — só a ordem dos elementos diz o que vem
  // antes.
  const sequencia = [];
  for (const m of svg.matchAll(
    /fill="(#[0-9a-fA-F]{3,6})"|class="([A-Za-z0-9_ -]+)"/g,
  )) {
    if (m[1]) {
      if (!ehNeutro(m[1])) sequencia.push(m[1].toUpperCase());
      continue;
    }
    for (const nome of m[2].split(/\s+/)) {
      const hex = porClasse.get(nome);
      if (hex && !ehNeutro(hex)) sequencia.push(hex.toUpperCase());
    }
  }
  if (sequencia.length === 0) return null;

  // A cor da marca é a que MAIS aparece, com a ordem de pintura desempatando.
  // Medido contra os hexes curados de 8 marcas conhecidas: a mais frequente
  // erra menos que "a primeira do documento" (distância RGB média 87 contra
  // 117) — a primeira forma às vezes é um detalhe, não o corpo do logo.
  const contagem = new Map();
  for (const hex of sequencia) contagem.set(hex, (contagem.get(hex) ?? 0) + 1);
  let melhor = sequencia[0];
  for (const [hex, vezes] of contagem) {
    const atual = contagem.get(melhor);
    if (vezes > atual || (vezes === atual && sequencia.indexOf(hex) < sequencia.indexOf(melhor))) {
      melhor = hex;
    }
  }
  return melhor;
};

// ── medidas do PNG do logo ──────────────────────────────────────────────
// O render precisa de três fatos sobre cada asset, e nenhum está no manifesto:
//
//   1. PREENCHIMENTO — os PNGs não têm margem uniforme: 104 dos 162 são
//      full-bleed e o resto vem com borda transparente (o menor tem só 41% de
//      conteúdo). Renderizar todos no mesmo tamanho faz a marca com margem
//      aparecer com ~metade do corpo das outras. O fator é o lado do bbox
//      opaco dividido pelo lado do PNG; o render divide o tamanho da imagem
//      por ele para o CONTEÚDO ficar do mesmo tamanho em toda marca.
//
//   2. PLACA vs MARCA — 99 dos 162 assets JÁ são uma placa opaca (o quadrado
//      do BB, o squircle do Itaú, o círculo do Bradesco). Esses vão para a
//      tela full-bleed, sem nada atrás: o fundo é parte do desenho. Os outros
//      63 são marca solta sobre transparente (o "nu" do Nubank, o wordmark do
//      C6). Separar os dois é o que permite tirar o fundo branco que o cartão
//      pintava atrás de TODO logo — e que num asset de placa virava um
//      quadrado branco com a placa da marca dentro.
//
//   3. LUZ E COR DA MARCA — só para a marca solta. Quem sabe o fundo é o
//      runtime (o gradiente do cartão, ou o card do tema no carrossel), então
//      o gerador publica o material do cálculo — dois percentis de luminância
//      e a cor do matiz dominante — e o domínio decide entre cor original e
//      tint. Ver `MARCA_LOGO` no arquivo gerado.
//
// Por que decodificar o PNG aqui, e não derivar do SVG:
//   - o pacote publica 162 PNGs e só 123 SVGs — 39 assets não teriam medida;
//   - quem o app renderiza é o PNG; o viewBox do SVG é outro documento e não
//     tem obrigação de compartilhar a margem do bitmap;
//   - o manifesto não expõe bbox (`logo` traz só png/svg/source), então medir
//     é o único caminho.
// E por que um decodificador próprio, sem dependência: os 162 são uniformes
// (256x256, 8 bits, cor indexada, sem entrelaçamento), o que cabe em ~40 linhas
// de `node:zlib`. `sharp` traria binário nativo e `pngjs` mais superfície de
// supply chain para um script que roda à mão. O decodificador ABORTA em
// qualquer outro formato — se o pacote mudar o pipeline de imagem, a geração
// falha em vez de emitir número errado em silêncio.

/** Percorre os chunks do PNG devolvendo IHDR, PLTE, tRNS e os IDAT concatenados. */
const chunksPng = (buf) => {
  let off = 8; // pula a assinatura
  let ihdr = null, plte = null, trns = null;
  const idat = [];
  while (off + 8 <= buf.length) {
    const tamanho = buf.readUInt32BE(off);
    const tipo = buf.toString('ascii', off + 4, off + 8);
    const dados = buf.subarray(off + 8, off + 8 + tamanho);
    if (tipo === 'IHDR') {
      ihdr = { largura: buf.readUInt32BE(off + 8), altura: buf.readUInt32BE(off + 12), bits: dados[8], tipoCor: dados[9], entrelacado: dados[12] };
    } else if (tipo === 'PLTE') plte = Buffer.from(dados);
    else if (tipo === 'tRNS') trns = Buffer.from(dados);
    else if (tipo === 'IDAT') idat.push(dados);
    else if (tipo === 'IEND') break;
    off += 12 + tamanho; // tamanho + tipo(4) + dados + crc(4)
  }
  return { ihdr, plte, trns, idat: Buffer.concat(idat) };
};

// Limiar de "o pixel existe". Acima de zero para ignorar poeira de
// antialiasing. Medido: o resultado é idêntico com 1, 8 e 16 — as bordas
// destes assets são duras.
const LIMIAR_ALFA = 8;
// Para medir COR só valem pixels francamente opacos: a borda antialiasada
// mistura o desenho com o vazio e puxaria a luminância para o fundo.
const LIMIAR_ALFA_COR = 128;
// Placa = bbox quase todo opaco E sem furo por onde o cartão vaze por dentro
// do desenho. Medido nos 162: os dois grupos ficam longe do limiar — placa de
// 0,78 para cima (o círculo puro dá π/4 ≈ 0,785), marca solta de 0,63 para
// baixo. O teste de furo é o que separa placa de moldura vazada.
const DENSIDADE_PLACA_MIN = 0.72;
const FUROS_PLACA_MAX = 0.02;
// Fração da área que precisa contrastar com o fundo para a MARCA SOLTA ir sem
// tint. Define quais percentis o dataset publica: com 0,6, o percentil 40 (60%
// da área está acima dele) e o 60 (60% está abaixo) respondem exatamente "60%
// da marca contrasta?" — um número por direção, sem guardar histograma.
const FRACAO_LEGIVEL_MIN = 0.6;
// O mesmo para a PLACA, que não pode ser tingida: nela basta uma PARTE do
// desenho se separar do fundo — o quadrado amarelo do BB some no card branco,
// mas o glifo azul dentro dele não. A fração é pequena porque é assim que
// marca funciona: o wordmark branco do `bari` ocupa 3% do asset preto e é o
// que se lê quando ele cai num cartão preto, e o "A" branco do asaas, 8%, é o
// que se lê num cartão azul. Daí os percentis 2 e 98 — detalhe fino conta,
// poeira de antialiasing não.
const FRACAO_LEGIVEL_PLACA = 0.02;
// Uma "placa" sem variação interna nenhuma não é placa para efeito de render:
// é uma silhueta. O BRB é o caso — um desenho preto chapado, sem detalhe que
// se separe do fundo —, e como silhueta ele pode ser tingido, que é o único
// jeito de ele aparecer num cartão escuro. Abaixo desta razão de contraste
// entre os extremos do desenho, o asset entra como marca solta.
const CONTRASTE_VARIACAO_PLACA_MIN = 1.2;
// Abaixo disto o pixel é cinza/branco/preto e não indica matiz de marca nenhum.
const SATURACAO_MIN_COR = 0.15;

/** Luminância relativa WCAG de um RGB 0-255. Mesma fórmula de emissores.ts. */
const luminanciaRgb = (r, g, b) => {
  const [lr, lg, lb] = [r, g, b].map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
};

/** Matiz HSL em graus [0, 360). */
const matizRgb = (r, g, b) => {
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  if (d === 0) return 0;
  const h = max === r ? (g - b) / d + (g < b ? 6 : 0)
    : max === g ? (b - r) / d + 2
      : (r - g) / d + 4;
  return h * 60;
};

const hexDeRgb = (r, g, b) =>
  '#' + [r, g, b].map((v) => Math.round(v).toString(16).padStart(2, '0').toUpperCase()).join('');

/** Pixels do PNG indexado, já desfiltrados: um índice de paleta por pixel. */
const indicesDoPng = (arquivo) => {
  const buf = fs.readFileSync(arquivo);
  const { ihdr, plte, trns, idat } = chunksPng(buf);
  if (!ihdr) throw new Error(`PNG sem IHDR: ${arquivo}`);
  if (ihdr.bits !== 8 || ihdr.tipoCor !== 3 || ihdr.entrelacado !== 0) {
    throw new Error(
      `PNG fora do formato esperado (8 bits, cor indexada, sem entrelaçamento): ${arquivo} ` +
      `— bits=${ihdr.bits} tipoCor=${ihdr.tipoCor} entrelacado=${ihdr.entrelacado}`,
    );
  }
  if (!plte) throw new Error(`PNG indexado sem PLTE: ${arquivo}`);
  const { largura: w, altura: h } = ihdr;
  const bruto = zlib.inflateSync(idat);
  // Cor indexada de 8 bits => 1 byte por pixel, então o filtro anda de 1 em 1.
  const passo = 1, linhaBytes = w * passo;
  const linha = Buffer.alloc(linhaBytes);
  const anterior = Buffer.alloc(linhaBytes);
  const indices = Buffer.alloc(w * h);
  let p = 0;

  for (let y = 0; y < h; y++) {
    const filtro = bruto[p++];
    bruto.copy(linha, 0, p, p + linhaBytes);
    p += linhaBytes;
    // Desfaz o filtro por linha (PNG 9.2): 0 none, 1 sub, 2 up, 3 average, 4 paeth.
    for (let i = 0; i < linhaBytes; i++) {
      const a = i >= passo ? linha[i - passo] : 0;
      const b = anterior[i];
      const c = i >= passo ? anterior[i - passo] : 0;
      let v = linha[i];
      if (filtro === 1) v += a;
      else if (filtro === 2) v += b;
      else if (filtro === 3) v += (a + b) >> 1;
      else if (filtro === 4) {
        const est = a + b - c;
        const da = Math.abs(est - a), db = Math.abs(est - b), dc = Math.abs(est - c);
        v += da <= db && da <= dc ? a : db <= dc ? b : c;
      } else if (filtro !== 0) throw new Error(`filtro PNG desconhecido (${filtro}): ${arquivo}`);
      linha[i] = v & 0xff;
    }
    linha.copy(anterior);
    linha.copy(indices, y * w);
  }
  // tRNS pode ser mais curto que a paleta; os índices restantes são opacos.
  const alfa = (indice) => (!trns ? 255 : indice < trns.length ? trns[indice] : 255);
  const rgb = (indice) => [plte[indice * 3], plte[indice * 3 + 1], plte[indice * 3 + 2]];
  return { largura: w, altura: h, indices, alfa, rgb };
};

/**
 * Mede um asset: `{ preenchimento, solido, marca }`.
 *
 * `preenchimento` é o MAIOR lado do bbox opaco / lado do PNG: com
 * `resizeMode="contain"` num box quadrado, é a dimensão maior que limita a
 * escala — usar a menor deixaria um wordmark largo vazar na horizontal.
 *
 * `solido` separa placa de marca solta; `luzP40`/`luzP60` e `cor` são o
 * material com que o domínio decide, em runtime e já sabendo o fundo, entre cor
 * original, tint e tile de apoio. `cor` é `null` na placa, que nunca é tingida.
 */
const medirPng = (arquivo) => {
  const { largura: w, altura: h, indices, alfa, rgb } = indicesDoPng(arquivo);

  let x0 = w, y0 = h, x1 = -1, y1 = -1, opacos = 0;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (alfa(indices[y * w + x]) < LIMIAR_ALFA) continue;
      opacos++;
      if (x < x0) x0 = x;
      if (x > x1) x1 = x;
      if (y < y0) y0 = y;
      if (y > y1) y1 = y;
    }
  }
  if (x1 < 0) throw new Error(`PNG totalmente transparente: ${arquivo}`);
  const larguraBbox = x1 - x0 + 1, alturaBbox = y1 - y0 + 1;
  const areaBbox = larguraBbox * alturaBbox;
  const preenchimento = Math.max(larguraBbox, alturaBbox) / w;

  // Furo = transparente DENTRO do desenho. Inunda o transparente a partir da
  // borda da imagem: o que não for alcançado está cercado por opaco. Sem isso,
  // uma moldura vazada (anel, letra oca grande) passaria por placa e o
  // gradiente do cartão apareceria por dentro dela.
  const visto = new Uint8Array(w * h);
  const pilha = [];
  for (let x = 0; x < w; x++) pilha.push(x, x + (h - 1) * w);
  for (let y = 0; y < h; y++) pilha.push(y * w, y * w + w - 1);
  while (pilha.length) {
    const i = pilha.pop();
    if (visto[i] || alfa(indices[i]) >= LIMIAR_ALFA) continue;
    visto[i] = 1;
    const x = i % w, y = (i - x) / w;
    if (x > 0) pilha.push(i - 1);
    if (x < w - 1) pilha.push(i + 1);
    if (y > 0) pilha.push(i - w);
    if (y < h - 1) pilha.push(i + w);
  }
  let furos = 0;
  for (let y = y0; y <= y1; y++) {
    for (let x = x0; x <= x1; x++) {
      const i = y * w + x;
      if (alfa(indices[i]) < LIMIAR_ALFA && !visto[i]) furos++;
    }
  }

  const denso = opacos / areaBbox >= DENSIDADE_PLACA_MIN && furos / areaBbox < FUROS_PLACA_MAX;

  // Pesa por índice de paleta: são no máximo 256 cores, então a distribuição
  // sai exata sem guardar um valor por pixel.
  const peso = new Map();
  for (let y = y0; y <= y1; y++) {
    for (let x = x0; x <= x1; x++) {
      const indice = indices[y * w + x];
      if (alfa(indice) < LIMIAR_ALFA_COR) continue;
      peso.set(indice, (peso.get(indice) ?? 0) + 1);
    }
  }
  const total = [...peso.values()].reduce((a, b) => a + b, 0);
  if (total === 0) throw new Error(`asset sem pixel opaco acima do limiar de cor: ${arquivo}`);

  const porLuz = [...peso]
    .map(([indice, n]) => [luminanciaRgb(...rgb(indice)), n])
    .sort((a, b) => a[0] - b[0]);
  const percentil = (fracao) => {
    const alvo = total * fracao;
    let acumulado = 0;
    for (const [luz, n] of porLuz) {
      acumulado += n;
      if (acumulado >= alvo) return luz;
    }
    return porLuz[porLuz.length - 1][0];
  };

  const luzes = {
    luzP02: percentil(FRACAO_LEGIVEL_PLACA),
    luzP40: percentil(1 - FRACAO_LEGIVEL_MIN),
    luzP60: percentil(FRACAO_LEGIVEL_MIN),
    luzP98: percentil(1 - FRACAO_LEGIVEL_PLACA),
  };

  const contrasteInterno =
    (Math.max(luzes.luzP98, luzes.luzP02) + 0.05) / (Math.min(luzes.luzP98, luzes.luzP02) + 0.05);
  const solido = denso && contrasteInterno >= CONTRASTE_VARIACAO_PLACA_MIN;

  // Placa não é tingida — o desenho traz o próprio fundo —, então ela não
  // precisa de cor, só das luzes que dizem se some no fundo.
  if (solido) return { preenchimento, solido, cor: null, ...luzes };

  // Cor do tint: média do matiz dominante, ponderada por área. Sem nenhum
  // pixel cromático (wordmark preto do C6), a média dos neutros — o domínio
  // clareia depois, e o resultado é um cinza claro, não um matiz inventado.
  const faixas = new Map();
  for (const [indice, n] of peso) {
    const [r, g, b] = rgb(indice);
    const max = Math.max(r, g, b), min = Math.min(r, g, b);
    const saturacao = max === 0 ? 0 : (max - min) / max;
    const chave = saturacao < SATURACAO_MIN_COR ? 'neutro' : Math.floor(matizRgb(r, g, b) / 30) % 12;
    const faixa = faixas.get(chave) ?? { n: 0, r: 0, g: 0, b: 0 };
    faixa.n += n; faixa.r += r * n; faixa.g += g * n; faixa.b += b * n;
    faixas.set(chave, faixa);
  }
  const cromaticas = [...faixas].filter(([chave]) => chave !== 'neutro');
  const [, dominante] = (cromaticas.length ? cromaticas : [...faixas])
    .sort((a, b) => b[1].n - a[1].n)[0];

  return {
    preenchimento,
    solido,
    cor: hexDeRgb(dominante.r / dominante.n, dominante.g / dominante.n, dominante.b / dominante.n),
    ...luzes,
  };
};

// As chaves vêm do MESMO entry que o app importa (`logos-bancos-br/react-native`),
// não de bancos.json: é esse mapa que `logoDoIspb` consulta, e ele indexa tanto
// por ISPB quanto por código COMPE. Lê como texto porque o entry é ESM com
// require() de PNG — importá-lo aqui não roda fora do Metro.
const fonteRn = fs.readFileSync(path.join(raizPacote, 'react-native.js'), 'utf8');
const medidaPorArquivo = new Map();
const preenchimentos = [];
const logosMedidos = [];
for (const [, chave, arquivo] of fonteRn.matchAll(
  /'(\d{4,8})': require\('\.\/logos\/png\/(\d{8})\.png'\)/g,
)) {
  if (!medidaPorArquivo.has(arquivo)) {
    medidaPorArquivo.set(
      arquivo,
      medirPng(path.join(raizPacote, 'logos', 'png', `${arquivo}.png`)),
    );
  }
  const medida = medidaPorArquivo.get(arquivo);
  // 1 é o padrão do runtime (asset full-bleed): não vale uma linha no arquivo.
  if (medida.preenchimento < 1) preenchimentos.push([chave, medida.preenchimento]);
  logosMedidos.push([chave, medida]);
}
if (medidaPorArquivo.size === 0) {
  throw new Error('nenhum logo lido de react-native.js — o formato do entry mudou');
}
preenchimentos.sort(([a], [b]) => a.localeCompare(b));
logosMedidos.sort(([a], [b]) => a.localeCompare(b));
const placas = [...medidaPorArquivo.values()].filter((m) => m.solido).length;
const marcasSoltas = medidaPorArquivo.size - placas;

// ── índice ──────────────────────────────────────────────────────────────
/** @type {Map<string, Map<string, {ispb: string, cor: string|null}>>} */
const porChave = new Map();

const registrar = (chave, ispb, identidade, cor, preferido) => {
  // 2 caracteres porque marca real cabe em 2 ('C6', 'XP', 'BV'); a checagem
  // de ambiguidade é que impede uma sigla curta de resolver errado.
  if (!chave || chave.length < 2) return;
  if (!porChave.has(chave)) porChave.set(chave, new Map());
  const porLogo = porChave.get(chave);
  const atual = porLogo.get(identidade);
  // Entre CNPJs da mesma marca, fica o que tem código COMPE (banco de
  // verdade) e, empatado, o menor ISPB — só para o arquivo gerado ser estável
  // entre execuções.
  if (!atual || (preferido && !atual.preferido) || (preferido === atual.preferido && ispb < atual.ispb)) {
    porLogo.set(identidade, { ispb, cor, preferido });
  }
};

for (const r of registros) {
  // sha256 da fonte identifica o DESENHO; o nome do arquivo identifica só o
  // CNPJ. Sem sha, cai no caminho do arquivo.
  const identidade = r.logo?.source?.sha256 ?? r.logo.png;
  const cor = corDoSvg(r.logo.svg);
  const preferido = Boolean(r.compe);
  const nomes = [r.logo?.source?.brand, r.shortName, r.name].filter(Boolean);
  for (const nome of nomes) {
    const palavras = palavrasUteis(nome);
    if (palavras.length === 0) continue;
    registrar(palavras.join(''), r.ispb, identidade, cor, preferido);
    registrar(palavras[0], r.ispb, identidade, cor, preferido);
  }
}

const entradas = [];
let descartadas = 0;
for (const [chave, porArquivo] of [...porChave].sort(([a], [b]) => a.localeCompare(b))) {
  if (porArquivo.size !== 1) { descartadas++; continue; }
  const { ispb, cor } = [...porArquivo.values()][0];
  entradas.push([chave, ispb, cor]);
}

const linhas = entradas.map(
  ([chave, ispb, cor]) => `  ${JSON.stringify(chave)}: ['${ispb}', ${cor ? `'${cor}'` : 'null'}],`,
);

// 4 casas: o fator é razão de contagem de pixels sobre 256, e 4 casas já
// reproduzem o valor exato de qualquer bbox inteiro nesse lado.
const linhasPreenchimento = preenchimentos.map(
  ([chave, fator]) => `  ${JSON.stringify(chave)}: ${Number(fator.toFixed(4))},`,
);

// Luminância é fração em (0, 1) e entra numa razão de contraste; 4 casas
// mantêm o erro de arredondamento duas ordens abaixo do limiar de decisão.
const luz = (v) => Number(v.toFixed(4));
const linhasLogo = logosMedidos.map(
  ([chave, m]) =>
    `  ${JSON.stringify(chave)}: [${m.solido ? 0 : 1}, ${luz(m.luzP02)}, ${luz(m.luzP40)}, ` +
    `${luz(m.luzP60)}, ${luz(m.luzP98)}, ${m.cor ? `'${m.cor}'` : 'null'}],`,
);

const conteudo = `/* eslint-disable */
// ARQUIVO GERADO por scripts/gerar-emissores-dataset.mjs — NÃO EDITAR À MÃO.
//
// Índice secundário de emissor: nome normalizado -> [ISPB, cor da marca].
// O catálogo curado de src/domain/emissores.ts tem precedência sobre este
// arquivo; aqui entram as instituições que o usuário pode digitar e que não
// valem uma entrada escrita à mão.
//
// A cor foi extraída do SVG oficial da própria marca (primeiro preenchimento
// não-neutro), não de memória. \`null\` = SVG sem cor identificável.
//
// Fonte: logos-bancos-br@${JSON.parse(fs.readFileSync(path.join(raizPacote, 'package.json'), 'utf8')).version}
// Instituições com logo: ${registros.length} · chaves: ${entradas.length} · descartadas por ambiguidade: ${descartadas}

export type EntradaDataset = readonly [ispb: string, cor: string | null];

export const EMISSORES_DATASET: Readonly<Record<string, EntradaDataset>> = {
${linhas.join('\n')}
};

/**
 * Quanto do PNG do logo é conteúdo opaco, por chave de \`logos-bancos-br/react-native\`
 * (ISPB e código COMPE, as mesmas chaves do mapa de assets).
 *
 * É o lado do bbox opaco / lado do PNG, em (0, 1]. Os assets do pacote não têm
 * margem uniforme — parte é full-bleed e parte vem com borda transparente —,
 * então renderizar todos no mesmo tamanho encolhe a marca com margem. Divida o
 * tamanho da imagem por este fator para o CONTEÚDO ficar do mesmo tamanho em
 * toda marca.
 *
 * Chave AUSENTE = 1 (asset full-bleed), o caso da maioria. Use
 * \`preenchimentoDoIspb\` em src/domain/logosEmissores.ts, que já aplica esse padrão.
 *
 * Medido decodificando o alfa dos PNGs (limiar ${LIMIAR_ALFA}/255), não estimado.
 * Assets: ${medidaPorArquivo.size} · com margem: ${preenchimentos.length} chaves
 * · menor fator: ${Math.min(...[...medidaPorArquivo.values()].map((m) => m.preenchimento)).toFixed(4)}
 */
export const PREENCHIMENTO_LOGO: Readonly<Record<string, number>> = {
${linhasPreenchimento.join('\n')}
};

/**
 * Como um asset de logo se comporta: tipo, quatro percentis de luminância e a
 * cor do tint.
 *
 * \`tipo\` 0 = placa própria, 1 = marca solta. \`cor\` só existe na marca solta —
 * placa nunca é tingida.
 */
export type EntradaLogo = readonly [
  tipo: 0 | 1,
  luzP02: number,
  luzP40: number,
  luzP60: number,
  luzP98: number,
  cor: string | null,
];

/**
 * Medidas de render de cada asset de logo, por chave de \`logos-bancos-br/react-native\`
 * (ISPB e código COMPE, as mesmas chaves do mapa de assets).
 *
 * Existe porque os ${medidaPorArquivo.size} assets não são o mesmo tipo de desenho, e tratá-los
 * igual obrigava o cartão a pintar um tile branco atrás de todo logo:
 *
 *   - ${placas} são PLACA (tipo 0): quadrado, squircle ou círculo opaco com a cor da
 *     marca já dentro (o quadrado do BB, o squircle do Itaú, o círculo do
 *     Bradesco). Vão full-bleed, sem fundo — só ganham um tile de apoio quando
 *     a própria placa some no fundo (placa preta em cartão preto);
 *   - ${marcasSoltas} são MARCA SOLTA (tipo 1) sobre transparente (o "nu" do Nubank, o
 *     wordmark do C6): ou vão na cor original, ou tingidas no matiz dominante.
 *
 * Os dois percentis de luminância WCAG respondem "${Math.round(FRACAO_LEGIVEL_MIN * 100)}% do desenho contrasta
 * com este fundo?" sem histograma: p40 tem ${Math.round(FRACAO_LEGIVEL_MIN * 100)}% da área ACIMA dele (serve
 * para fundo escuro) e p60 tem ${Math.round(FRACAO_LEGIVEL_MIN * 100)}% ABAIXO (fundo claro). Quem sabe o
 * fundo é o runtime — o gradiente do cartão numa tela, o card do tema em outra
 * —, então a decisão fica no domínio e aqui só o material medido.
 *
 * Medido nos PNGs: placa = densidade do bbox ≥ ${DENSIDADE_PLACA_MIN} e furos internos < ${FUROS_PLACA_MAX};
 * cor e luz só de pixels com alfa ≥ ${LIMIAR_ALFA_COR}/255. Use \`entradaLogoDoIspb\` e
 * \`estiloDoLogo\` em src/domain/logosEmissores.ts e emissores.ts, que já aplicam
 * esse padrão.
 */
export const LOGO_MEDIDO: Readonly<Record<string, EntradaLogo>> = {
${linhasLogo.join('\n')}
};
`;

fs.writeFileSync(saida, conteudo);
console.log(
  `gerado: ${entradas.length} chaves, ${descartadas} descartadas por ambiguidade, ` +
  `${registros.length} instituições com logo; ` +
  `preenchimento: ${preenchimentos.length} chaves com margem em ${medidaPorArquivo.size} assets; ` +
  `logo: ${placas} placas full-bleed e ${marcasSoltas} marcas soltas em ${logosMedidos.length} chaves`,
);
