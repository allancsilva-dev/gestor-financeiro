import fs from 'fs';
import path from 'path';

/**
 * O trinco do padrão visual.
 *
 * `DESIGN.md` descreve o sistema, mas descrição não impede regressão: as telas
 * antigas nasceram assim justamente porque nada barrava um `fontSize: 13` solto.
 * Este teste varre `app/**` e falha quando uma tela volta a inventar escala.
 *
 * É o mesmo mecanismo de `tema.test.ts`, que trava contraste — invariante de
 * design com teste, não com revisão de código.
 *
 * **Como usar:** ao migrar uma tela, apague a linha dela de `AINDA_NAO_MIGRADAS`.
 * A lista vazia é a definição de pronto. Um arquivo listado que já está limpo
 * também quebra o teste: exceção obsoleta some.
 */

const RAIZ = path.resolve(__dirname, '../..', 'app');

interface Regra {
  nome: string;
  re: RegExp;
  porque: string;
}

const REGRAS: Regra[] = [
  {
    nome: 'fontSize cru',
    re: /\bfontSize:\s*(\d+)/g,
    porque: 'use `typography.*` por spread — a escala está em src/theme/tokens.ts',
  },
  {
    nome: 'espaçamento cru',
    re: /\b(?:padding|margin|gap)[A-Za-z]*:\s*(\d+)/g,
    porque: 'use `spacing.*` / `screenPadding`',
  },
  {
    nome: 'raio cru',
    re: /\bborderRadius:\s*(\d+)/g,
    porque: 'use `radius.*` ou `cardRadius`',
  },
  {
    nome: 'hex literal',
    re: /'#[0-9a-fA-F]{3,8}'/g,
    porque: 'cor vem de `useTheme()`; só src/theme declara hex',
  },
  {
    nome: 'spinner de tela',
    re: /\bActivityIndicator\b/g,
    porque: 'use `SkeletonBox` com a forma do conteúdo (DESIGN.md, Estados)',
  },
  {
    nome: 'SafeAreaView',
    re: /\bSafeAreaView\b/g,
    porque: 'use `useSafeAreaInsets()` (DESIGN.md, Layout)',
  },
];

/**
 * Exceções com motivo, que não vencem. Não confundir com a lista de migração:
 * estas ficam.
 */
const EXCECOES_PERMANENTES: Record<string, string[]> = {
  // Portão de sessão: roda antes de existir qualquer conteúdo, então não há
  // forma de conteúdo para o skeleton imitar. É um splash, não um carregamento.
  'index.tsx': ['spinner de tela'],
};

/** Telas que ainda não passaram pela migração visual. Encolhe a cada PR. */
const AINDA_NAO_MIGRADAS = [
  '(app)/more/carteiras.tsx',
  '(app)/more/categorias.tsx',
  '(app)/more/contas-fixas.tsx',
  '(app)/more/orcamentos.tsx',
  '(app)/more/visao-financeira.tsx',
  '(app)/perfil.tsx',
];

const telas = (): string[] => {
  const achados: string[] = [];
  const anda = (dir: string) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) anda(p);
      else if (e.name.endsWith('.tsx')) achados.push(path.relative(RAIZ, p));
    }
  };
  anda(RAIZ);
  return achados.sort();
};

/**
 * Comentário não é código: `// fontSize: 13` explicando uma decisão não pode
 * quebrar o build. Vira espaço em branco para as linhas não escorregarem.
 */
const semComentarios = (src: string): string =>
  src
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '))
    .replace(/(^|[^:])\/\/.*$/gm, (m, antes) => antes + ' '.repeat(m.length - antes.length));

const infracoes = (arquivo: string): string[] => {
  const linhas = semComentarios(fs.readFileSync(path.join(RAIZ, arquivo), 'utf8')).split('\n');
  const permitidas = EXCECOES_PERMANENTES[arquivo] ?? [];
  const achados: string[] = [];

  linhas.forEach((linha, i) => {
    for (const regra of REGRAS) {
      if (permitidas.includes(regra.nome)) continue;
      for (const m of linha.matchAll(regra.re)) {
        // `padding: 0` é remoção de padding, não medida inventada
        if (m[1] === '0') continue;
        achados.push(`${arquivo}:${i + 1} ${regra.nome} (${m[0].trim()}) — ${regra.porque}`);
      }
    }
  });
  return achados;
};

const migradas = telas().filter((t) => !AINDA_NAO_MIGRADAS.includes(t));

describe('padrão visual das telas', () => {
  it.each(migradas)('%s usa a escala, não números inventados', (arquivo) => {
    expect(infracoes(arquivo)).toEqual([]);
  });

  it.each(AINDA_NAO_MIGRADAS)('%s ainda está na lista de migração e ainda precisa estar', (arquivo) => {
    // Se esta falhar, a tela foi migrada: apague a linha dela de AINDA_NAO_MIGRADAS.
    expect(fs.existsSync(path.join(RAIZ, arquivo))).toBe(true);
    expect(infracoes(arquivo).length).toBeGreaterThan(0);
  });

  it('não há exceção permanente apontando para arquivo que sumiu', () => {
    for (const arquivo of Object.keys(EXCECOES_PERMANENTES)) {
      expect({ arquivo, existe: fs.existsSync(path.join(RAIZ, arquivo)) })
        .toEqual({ arquivo, existe: true });
    }
  });
});
