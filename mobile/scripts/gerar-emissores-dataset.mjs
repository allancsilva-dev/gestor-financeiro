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
`;

fs.writeFileSync(saida, conteudo);
console.log(
  `gerado: ${entradas.length} chaves, ${descartadas} descartadas por ambiguidade, ` +
  `${registros.length} instituições com logo`,
);
