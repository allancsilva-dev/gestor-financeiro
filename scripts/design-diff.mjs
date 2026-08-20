#!/usr/bin/env node
// Harness de fidelidade visual — compara um screenshot do simulador com a
// referência de design. Node puro: PNG é zlib + defilter, sem dependência nova.
//
//   node scripts/design-diff.mjs palette <img.png> [n]
//   node scripts/design-diff.mjs sample  <img.png> <x> <y> [raio]
//   node scripts/design-diff.mjs column  <img.png> <x> [passo]
//   node scripts/design-diff.mjs row     <img.png> <y> [passo]
//   node scripts/design-diff.mjs edges   <img.png> <x0> <x1> <y>
//   node scripts/design-diff.mjs diff    <ref.png> <shot.png> <pontos.json>
import { readFileSync, writeFileSync } from 'node:fs';
import { inflateSync } from 'node:zlib';

// ── decode PNG ────────────────────────────────────────────────────────────
const CHANNELS = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 };

function paeth(a, b, c) {
  const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}

export function readPNG(path) {
  const buf = readFileSync(path);
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error(`${path}: não é PNG`);
  let off = 8, ihdr = null, plte = null;
  const idat = [];
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString('ascii', off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === 'IHDR') {
      ihdr = {
        width: data.readUInt32BE(0), height: data.readUInt32BE(4),
        depth: data[8], colorType: data[9], interlace: data[12],
      };
    } else if (type === 'PLTE') plte = data;
    else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    off += 12 + len;
  }
  if (!ihdr) throw new Error(`${path}: sem IHDR`);
  if (ihdr.interlace) throw new Error(`${path}: PNG interlaced não suportado`);
  const { width, height, depth, colorType } = ihdr;
  if (depth !== 8 && depth !== 16) throw new Error(`${path}: bit depth ${depth} não suportado`);

  const ch = CHANNELS[colorType];
  const bpp = Math.ceil((depth * ch) / 8);
  const stride = Math.ceil((depth * ch * width) / 8);
  const raw = inflateSync(Buffer.concat(idat));

  const lines = Buffer.alloc(height * stride);
  let prev = Buffer.alloc(stride);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    const src = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    const cur = lines.subarray(y * stride, (y + 1) * stride);
    src.copy(cur);
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? cur[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
      if (filter === 1) cur[i] = (cur[i] + a) & 0xff;
      else if (filter === 2) cur[i] = (cur[i] + b) & 0xff;
      else if (filter === 3) cur[i] = (cur[i] + ((a + b) >> 1)) & 0xff;
      else if (filter === 4) cur[i] = (cur[i] + paeth(a, b, c)) & 0xff;
    }
    prev = cur;
  }

  // normaliza tudo para RGBA8
  const out = Buffer.alloc(width * height * 4);
  const step = depth === 16 ? 2 : 1;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const s = y * stride + x * bpp;
      const d = (y * width + x) * 4;
      let r, g, b, a = 255;
      if (colorType === 0) { r = g = b = lines[s]; }
      else if (colorType === 2) { r = lines[s]; g = lines[s + step]; b = lines[s + 2 * step]; }
      else if (colorType === 3) { const i = lines[s] * 3; r = plte[i]; g = plte[i + 1]; b = plte[i + 2]; }
      else if (colorType === 4) { r = g = b = lines[s]; a = lines[s + step]; }
      else { r = lines[s]; g = lines[s + step]; b = lines[s + 2 * step]; a = lines[s + 3 * step]; }
      out[d] = r; out[d + 1] = g; out[d + 2] = b; out[d + 3] = a;
    }
  }
  return { width, height, data: out };
}

// ── helpers ───────────────────────────────────────────────────────────────
const hex = (r, g, b) => '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('');

export function px(img, x, y) {
  const i = (Math.round(y) * img.width + Math.round(x)) * 4;
  return [img.data[i], img.data[i + 1], img.data[i + 2], img.data[i + 3]];
}

// média num quadrado, para não pegar ruído de compressão/antialias
export function sample(img, x, y, radius = 2) {
  let r = 0, g = 0, b = 0, n = 0;
  for (let dy = -radius; dy <= radius; dy++) {
    for (let dx = -radius; dx <= radius; dx++) {
      const cx = Math.round(x) + dx, cy = Math.round(y) + dy;
      if (cx < 0 || cy < 0 || cx >= img.width || cy >= img.height) continue;
      const [pr, pg, pb] = px(img, cx, cy);
      r += pr; g += pg; b += pb; n++;
    }
  }
  return [Math.round(r / n), Math.round(g / n), Math.round(b / n)];
}

const dist = (a, b) => Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]), Math.abs(a[2] - b[2]));

// ── comandos ──────────────────────────────────────────────────────────────
function cmdPalette(path, n = 20) {
  const img = readPNG(path);
  const counts = new Map();
  for (let i = 0; i < img.data.length; i += 4) {
    const k = (img.data[i] << 16) | (img.data[i + 1] << 8) | img.data[i + 2];
    counts.set(k, (counts.get(k) || 0) + 1);
  }
  const total = img.width * img.height;
  const top = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, n);
  console.log(`${path}  ${img.width}x${img.height}  ${counts.size} cores distintas`);
  for (const [k, c] of top) {
    console.log(`  ${hex(k >> 16, (k >> 8) & 0xff, k & 0xff)}  ${(c / total * 100).toFixed(2)}%  (${c}px)`);
  }
}

function cmdSample(path, x, y, radius = 2) {
  const img = readPNG(path);
  const [r, g, b] = sample(img, +x, +y, +radius);
  console.log(`${hex(r, g, b)}  rgb(${r},${g},${b})  @ ${x},${y} r=${radius}`);
}

// varre uma coluna procurando transições — acha topo/base de card, divisores
function cmdColumn(path, x, step = 1) {
  const img = readPNG(path);
  let prev = sample(img, +x, 0, 1);
  console.log(`coluna x=${x} (${img.width}x${img.height}) — transições:`);
  console.log(`  y=0      ${hex(...prev)}`);
  for (let y = +step; y < img.height; y += +step) {
    const cur = sample(img, +x, y, 1);
    if (dist(prev, cur) > 8) console.log(`  y=${String(y).padEnd(7)}${hex(...cur)}`);
    prev = cur;
  }
}

function cmdRow(path, y, step = 1) {
  const img = readPNG(path);
  let prev = sample(img, 0, +y, 1);
  console.log(`linha y=${y} (${img.width}x${img.height}) — transições:`);
  console.log(`  x=0      ${hex(...prev)}`);
  for (let x = +step; x < img.width; x += +step) {
    const cur = sample(img, x, +y, 1);
    if (dist(prev, cur) > 8) console.log(`  x=${String(x).padEnd(7)}${hex(...cur)}`);
    prev = cur;
  }
}

// primeira e última transição num intervalo — mede largura de elemento
function cmdEdges(path, x0, x1, y) {
  const img = readPNG(path);
  const base = sample(img, +x0, +y, 1);
  const hits = [];
  let prev = base;
  for (let x = +x0 + 1; x <= +x1; x++) {
    const cur = sample(img, x, +y, 1);
    if (dist(prev, cur) > 12) hits.push({ x, from: hex(...prev), to: hex(...cur) });
    prev = cur;
  }
  console.log(`bordas em y=${y}, x ${x0}..${x1}:`);
  for (const h of hits) console.log(`  x=${String(h.x).padEnd(6)}${h.from} → ${h.to}`);
}

// pontos.json: [{ nome, ref:[x,y], shot:[x,y], tol? }]
function cmdDiff(refPath, shotPath, pontosPath) {
  const ref = readPNG(refPath), shot = readPNG(shotPath);
  const pontos = JSON.parse(readFileSync(pontosPath, 'utf8'));
  let falhas = 0;
  console.log(`ref  ${ref.width}x${ref.height}\nshot ${shot.width}x${shot.height}\n`);
  for (const p of pontos) {
    const a = sample(ref, p.ref[0], p.ref[1], p.raio ?? 2);
    const b = sample(shot, p.shot[0], p.shot[1], p.raio ?? 2);
    const d = dist(a, b), tol = p.tol ?? 6;
    const ok = d <= tol;
    if (!ok) falhas++;
    console.log(`${ok ? 'ok  ' : 'FALHA'} ${p.nome.padEnd(28)} ref ${hex(...a)}  shot ${hex(...b)}  Δ${d} (tol ${tol})`);
  }
  console.log(`\n${pontos.length - falhas}/${pontos.length} pontos dentro da tolerância`);
  process.exit(falhas ? 1 : 0);
}

// cores dominantes dentro de um retangulo — robusto contra ruido de JPEG,
// e a forma certa de ler tanto superficie quanto cor de texto
function cmdRegion(path, x0, y0, x1, y1, n = 8) {
  const img = readPNG(path);
  const counts = new Map();
  let total = 0;
  for (let y = +y0; y <= +y1; y++) {
    for (let x = +x0; x <= +x1; x++) {
      if (x < 0 || y < 0 || x >= img.width || y >= img.height) continue;
      const [r, g, b] = px(img, x, y);
      const k = (r << 16) | (g << 8) | b;
      counts.set(k, (counts.get(k) || 0) + 1);
      total++;
    }
  }
  const top = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, +n);
  console.log(`${x0},${y0} .. ${x1},${y1}  (${total}px, ${counts.size} cores)`);
  for (const [k, c] of top) {
    console.log(`  ${hex(k >> 16, (k >> 8) & 0xff, k & 0xff)}  ${(c / total * 100).toFixed(1)}%`);
  }
}

// cor de tinta de um texto: dentro do retangulo, a cor mais distante da
// superficie dominante entre as que aparecem o bastante para nao ser ruido de
// JPEG. Media das mais extremas, para o antialias nao puxar o valor.
function cmdInk(path, x0, y0, x1, y1) {
  const img = readPNG(path);
  const counts = new Map();
  let total = 0;
  for (let y = +y0; y <= +y1; y++) {
    for (let x = +x0; x <= +x1; x++) {
      if (x < 0 || y < 0 || x >= img.width || y >= img.height) continue;
      const [r, g, b] = px(img, x, y);
      const k = (r << 16) | (g << 8) | b;
      counts.set(k, (counts.get(k) || 0) + 1);
      total++;
    }
  }
  const unpack = k => [k >> 16, (k >> 8) & 0xff, k & 0xff];
  const entries = [...counts.entries()];
  const bgKey = entries.sort((a, b) => b[1] - a[1])[0][0];
  const bg = unpack(bgKey);
  const d2 = c => (c[0]-bg[0])**2 + (c[1]-bg[1])**2 + (c[2]-bg[2])**2;

  // descarta cores raras demais: provavelmente artefato de compressao
  const minCount = Math.max(2, Math.floor(total * 0.0004));
  const cand = entries
    .filter(([, c]) => c >= minCount)
    .map(([k, c]) => ({ c: unpack(k), n: c, d: d2(unpack(k)) }))
    .sort((a, b) => b.d - a.d);

  const topo = cand.slice(0, 12);
  let r = 0, g = 0, b = 0, n = 0;
  for (const t of topo) { r += t.c[0]*t.n; g += t.c[1]*t.n; b += t.c[2]*t.n; n += t.n; }
  // texto fino mistura com o fundo em quase todo pixel; o pico de croma e a
  // melhor estimativa da cor real da tinta colorida
  const chroma = c => Math.max(...c) - Math.min(...c);
  const maisSaturado = cand.slice().sort((a, b) => chroma(b.c) - chroma(a.c))[0];

  console.log(`superficie ${hex(...bg)}`);
  console.log(`tinta      ${hex(Math.round(r/n), Math.round(g/n), Math.round(b/n))}  (media das ${topo.length} mais extremas)`);
  console.log(`extremo    ${topo.length ? hex(...topo[0].c) : '-'}`);
  console.log(`saturado   ${maisSaturado ? hex(...maisSaturado.c) : '-'}  croma ${maisSaturado ? chroma(maisSaturado.c) : 0}`);
}

const [cmd, ...args] = process.argv.slice(2);
const cmds = { palette: cmdPalette, region: cmdRegion, ink: cmdInk, sample: cmdSample, column: cmdColumn, row: cmdRow, edges: cmdEdges, diff: cmdDiff };
if (!cmds[cmd]) {
  console.error('uso: design-diff.mjs <palette|region|sample|column|row|edges|diff> ...');
  process.exit(2);
}
cmds[cmd](...args);
