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

// ── preenchimento do logo, medido no PNG ────────────────────────────────
// Os PNGs do pacote não têm margem uniforme: 104 dos 162 são full-bleed e o
// resto vem com borda transparente (o menor tem só 41% de conteúdo). Renderizar
// todos no mesmo tamanho faz a marca com margem aparecer com ~metade do corpo
// das outras. O fator abaixo é o lado do bbox opaco dividido pelo lado do PNG;
// o render divide o tamanho da imagem por ele para o CONTEÚDO ficar do mesmo
// tamanho em toda marca.
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

/** Percorre os chunks do PNG devolvendo IHDR, tRNS e os IDAT concatenados. */
const chunksPng = (buf) => {
  let off = 8; // pula a assinatura
  let ihdr = null, trns = null;
  const idat = [];
  while (off + 8 <= buf.length) {
    const tamanho = buf.readUInt32BE(off);
    const tipo = buf.toString('ascii', off + 4, off + 8);
    const dados = buf.subarray(off + 8, off + 8 + tamanho);
    if (tipo === 'IHDR') {
      ihdr = { largura: buf.readUInt32BE(off + 8), altura: buf.readUInt32BE(off + 12), bits: dados[8], tipoCor: dados[9], entrelacado: dados[12] };
    } else if (tipo === 'tRNS') trns = Buffer.from(dados);
    else if (tipo === 'IDAT') idat.push(dados);
    else if (tipo === 'IEND') break;
    off += 12 + tamanho; // tamanho + tipo(4) + dados + crc(4)
  }
  return { ihdr, trns, idat: Buffer.concat(idat) };
};

/**
 * Lado do bbox do conteúdo opaco / lado do PNG, em (0, 1].
 * É o MAIOR lado do bbox: com `resizeMode="contain"` num box quadrado, é a
 * dimensão maior que limita a escala — usar a menor deixaria um wordmark largo
 * vazar na horizontal.
 */
const preenchimentoDoPng = (arquivo) => {
  const buf = fs.readFileSync(arquivo);
  const { ihdr, trns, idat } = chunksPng(buf);
  if (!ihdr) throw new Error(`PNG sem IHDR: ${arquivo}`);
  if (ihdr.bits !== 8 || ihdr.tipoCor !== 3 || ihdr.entrelacado !== 0) {
    throw new Error(
      `PNG fora do formato esperado (8 bits, cor indexada, sem entrelaçamento): ${arquivo} ` +
      `— bits=${ihdr.bits} tipoCor=${ihdr.tipoCor} entrelacado=${ihdr.entrelacado}`,
    );
  }
  const { largura: w, altura: h } = ihdr;
  // Sem tRNS a paleta é toda opaca: o conteúdo ocupa o PNG inteiro.
  if (!trns) return 1;

  const bruto = zlib.inflateSync(idat);
  // Cor indexada de 8 bits => 1 byte por pixel, então o filtro anda de 1 em 1.
  const passo = 1, linhaBytes = w * passo;
  const linha = Buffer.alloc(linhaBytes);
  const anterior = Buffer.alloc(linhaBytes);
  let x0 = w, y0 = h, x1 = -1, y1 = -1, p = 0;

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
    for (let x = 0; x < w; x++) {
      const indice = linha[x];
      // tRNS pode ser mais curto que a paleta; os índices restantes são opacos.
      const alfa = indice < trns.length ? trns[indice] : 255;
      // Limiar acima de zero para ignorar poeira de antialiasing. Medido: o
      // resultado é idêntico com 1, 8 e 16 — as bordas destes assets são duras.
      if (alfa >= LIMIAR_ALFA) {
        if (x < x0) x0 = x;
        if (x > x1) x1 = x;
        if (y < y0) y0 = y;
        if (y > y1) y1 = y;
      }
    }
  }
  if (x1 < 0) throw new Error(`PNG totalmente transparente: ${arquivo}`);
  return Math.max(x1 - x0 + 1, y1 - y0 + 1) / w;
};

const LIMIAR_ALFA = 8;

// As chaves vêm do MESMO entry que o app importa (`logos-bancos-br/react-native`),
// não de bancos.json: é esse mapa que `logoDoIspb` consulta, e ele indexa tanto
// por ISPB quanto por código COMPE. Lê como texto porque o entry é ESM com
// require() de PNG — importá-lo aqui não roda fora do Metro.
const fonteRn = fs.readFileSync(path.join(raizPacote, 'react-native.js'), 'utf8');
const preenchimentoPorArquivo = new Map();
const preenchimentos = [];
for (const [, chave, arquivo] of fonteRn.matchAll(
  /'(\d{4,8})': require\('\.\/logos\/png\/(\d{8})\.png'\)/g,
)) {
  if (!preenchimentoPorArquivo.has(arquivo)) {
    preenchimentoPorArquivo.set(
      arquivo,
      preenchimentoDoPng(path.join(raizPacote, 'logos', 'png', `${arquivo}.png`)),
    );
  }
  const fator = preenchimentoPorArquivo.get(arquivo);
  // 1 é o padrão do runtime (asset full-bleed): não vale uma linha no arquivo.
  if (fator < 1) preenchimentos.push([chave, fator]);
}
if (preenchimentoPorArquivo.size === 0) {
  throw new Error('nenhum logo lido de react-native.js — o formato do entry mudou');
}
preenchimentos.sort(([a], [b]) => a.localeCompare(b));

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
 * Assets: ${preenchimentoPorArquivo.size} · com margem: ${preenchimentos.length} chaves
 * · menor fator: ${Math.min(...preenchimentoPorArquivo.values()).toFixed(4)}
 */
export const PREENCHIMENTO_LOGO: Readonly<Record<string, number>> = {
${linhasPreenchimento.join('\n')}
};
`;

fs.writeFileSync(saida, conteudo);
console.log(
  `gerado: ${entradas.length} chaves, ${descartadas} descartadas por ambiguidade, ` +
  `${registros.length} instituições com logo; ` +
  `preenchimento: ${preenchimentos.length} chaves com margem em ${preenchimentoPorArquivo.size} assets`,
);
