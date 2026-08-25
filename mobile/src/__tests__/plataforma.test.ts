import fs from 'fs';
import path from 'path';

/**
 * O trinco de plataforma.
 *
 * O app nasceu e foi verificado só em iOS, e o que quebrou no Android não foi
 * biblioteca faltando: foram três linhas que davam a iOS um tratamento e ao
 * Android `undefined`. Status bar sem estilo, `presentationStyle` sem safe area
 * e `KeyboardAvoidingView` sem `behavior` — cada uma invisível em revisão de
 * código, cada uma visível na primeira tela do aparelho.
 *
 * Este teste varre `app/**` e `src/**` e falha quando o padrão volta. É o mesmo
 * mecanismo de `padraoVisual.test.ts` (que trava a escala visual) e de
 * `tema.test.ts` (que trava contraste): invariante com teste, não com revisão.
 *
 * A diferença para `padraoVisual` é o alcance: aquele olha só `app/**`, mas os
 * modais que quebraram vivem em `src/components/`.
 */

const RAIZ = path.resolve(__dirname, '../..');
const PASTAS = ['app', 'src'];

interface Regra {
  nome: string;
  /** Recebe o fonte já sem comentários e devolve o que está errado. */
  achar: (src: string) => string[];
  porque: string;
}

/**
 * Corpo da tag de abertura de cada `<Modal ...>`, respeitando as chaves: um
 * `>` dentro de `{() => ...}` não fecha a tag, e uma regex ingênua cortaria ali.
 */
const tagsDeModal = (src: string): string[] => {
  const tags: string[] = [];
  let i = src.indexOf('<Modal');
  while (i !== -1) {
    let profundidade = 0;
    for (let j = i; j < src.length; j++) {
      const c = src[j];
      if (c === '{') profundidade++;
      else if (c === '}') profundidade--;
      else if (c === '>' && profundidade === 0) {
        tags.push(src.slice(i, j + 1));
        break;
      }
    }
    i = src.indexOf('<Modal', i + 1);
  }
  return tags;
};

const REGRAS: Regra[] = [
  {
    nome: 'modal sem statusBarTranslucent',
    achar: (src) =>
      tagsDeModal(src)
        .filter((tag) => tag.includes('presentationStyle') && !tag.includes('statusBarTranslucent'))
        .map(() => '<Modal presentationStyle=...>'),
    porque:
      '`presentationStyle` só existe no iOS; no Android o modal vira tela cheia e o cabeçalho ' +
      'sobe atrás do relógio. Some `statusBarTranslucent` para o comportamento ser o mesmo de ' +
      'API 33 a 36',
  },
  {
    nome: 'topo do modal sem inset',
    achar: (src) =>
      src.includes('statusBarTranslucent') && !src.includes('useModalTopInset')
        ? ['statusBarTranslucent']
        : [],
    porque:
      'com `statusBarTranslucent` o conteúdo começa em y=0 no Android — o cabeçalho precisa de ' +
      '`useModalTopInset()` (src/theme/index.ts)',
  },
  {
    nome: 'Android recebe undefined',
    achar: (src) => [...src.matchAll(/Platform\.OS === 'ios'\s*\?[^:]*:\s*undefined/g)].map((m) => m[0]),
    porque:
      'dar comportamento só ao iOS deixa o Android sem nada — foi assim que o teclado passou a ' +
      'cobrir os formulários. Use `Platform.select({ ios, android })` com valor para os dois',
  },
  {
    nome: 'sombra sem elevation',
    achar: (src) =>
      /shadow(Color|Opacity|Radius|Offset)/.test(src) &&
      !/elevation/.test(src) &&
      !/\.\.\.shadow\./.test(src)
        ? ['shadow* sem elevation']
        : [],
    porque:
      'as props `shadow*` não pintam no Android; a sombra de lá é `elevation`. Use os tokens ' +
      '`shadow.card` / `shadow.glow`, que já trazem as duas',
  },
];

/**
 * Exceções com motivo, que não vencem — não confundir com lista de migração.
 * Vazia hoje: nenhum arquivo precisa fugir das regras acima.
 */
const EXCECOES_PERMANENTES: Record<string, string[]> = {};

const fontes = (): string[] => {
  const achados: string[] = [];
  const anda = (dir: string) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      if (e.name === '__tests__' || e.name === 'node_modules') continue;
      const p = path.join(dir, e.name);
      if (e.isDirectory()) anda(p);
      else if (e.name.endsWith('.tsx') || e.name.endsWith('.ts')) achados.push(path.relative(RAIZ, p));
    }
  };
  for (const pasta of PASTAS) anda(path.join(RAIZ, pasta));
  return achados.sort();
};

/** Mesmo tratamento de `padraoVisual`: comentário explicando não quebra build. */
const semComentarios = (src: string): string =>
  src
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '))
    .replace(/(^|[^:])\/\/.*$/gm, (m, antes) => antes + ' '.repeat(m.length - antes.length));

const infracoes = (arquivo: string): string[] => {
  const src = semComentarios(fs.readFileSync(path.join(RAIZ, arquivo), 'utf8'));
  const permitidas = EXCECOES_PERMANENTES[arquivo] ?? [];
  const achados: string[] = [];

  for (const regra of REGRAS) {
    if (permitidas.includes(regra.nome)) continue;
    for (const ocorrencia of regra.achar(src)) {
      achados.push(`${arquivo} ${regra.nome} (${ocorrencia.trim()}) — ${regra.porque}`);
    }
  }
  return achados;
};

describe('o código não trata Android como sobra do iOS', () => {
  it.each(fontes())('%s', (arquivo) => {
    expect(infracoes(arquivo)).toEqual([]);
  });

  it('não há exceção permanente obsoleta', () => {
    for (const arquivo of Object.keys(EXCECOES_PERMANENTES)) {
      expect({ arquivo, existe: fs.existsSync(path.join(RAIZ, arquivo)) })
        .toEqual({ arquivo, existe: true });
    }
  });
});
