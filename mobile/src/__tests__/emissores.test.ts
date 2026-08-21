import {
  EMISSORES,
  identidadeDoCartao,
  normalizarEmissor,
  resolverEmissor,
  tokensDoEmissor,
  contraste,
  gradienteDe,
  TINTA_CLARA,
  TINTA_ESCURA,
} from '../domain/emissores';

describe('normalizarEmissor', () => {
  it('remove acento, caixa, pontuação e prefixo genérico', () => {
    expect(normalizarEmissor('Itaú')).toBe('itau');
    expect(normalizarEmissor('Banco Inter')).toBe('inter');
    expect(normalizarEmissor('  C6 Bank ')).toBe('c6');
    expect(normalizarEmissor('Cartão Nubank')).toBe('nubank');
    expect(normalizarEmissor(null)).toBe('');
  });
});

describe('resolverEmissor', () => {
  it('casa por alias exato', () => {
    expect(resolverEmissor({ banco: 'Nubank' })?.slug).toBe('nubank');
    expect(resolverEmissor({ banco: 'Itaú Unibanco' })?.slug).toBe('itau');
  });

  it('cai para o nome do cartão quando o banco está vazio', () => {
    expect(resolverEmissor({ banco: null, nome: 'Nubank Ultravioleta' })?.slug).toBe('nubank');
  });

  it('NÃO casa por substring — o bug que o alias exato existe para evitar', () => {
    // "intermedium" é alias do Inter; "Banco Internacional" não é ninguém.
    expect(resolverEmissor({ banco: 'Banco Internacional' })).toBeNull();
    expect(resolverEmissor({ banco: 'Nubankzinho' })).toBeNull();
    expect(resolverEmissor({ banco: 'Intermedium' })?.slug).toBe('inter');
  });

  it('emissor desconhecido devolve null', () => {
    expect(resolverEmissor({ banco: 'Banco Que Não Existe' })).toBeNull();
    expect(resolverEmissor(null)).toBeNull();
  });
});

describe('identidadeDoCartao', () => {
  it('reproduz o cartão da referência', () => {
    const id = identidadeDoCartao({ nome: 'Nubank Ultravioleta', banco: 'Nubank' });
    expect(id.rotulo).toBe('NUBANK');
    expect(id.glifo).toBe('nu');
    expect(id.emissor?.slug).toBe('nubank');
    expect(id.corDoUsuario).toBe(false);
  });

  it('cor escolhida pelo usuário vence o catálogo', () => {
    const padrao = identidadeDoCartao({ nome: 'Nubank', banco: 'Nubank' });
    const custom = identidadeDoCartao({ nome: 'Nubank', banco: 'Nubank', cor: '#0F62FE' });
    expect(custom.corDoUsuario).toBe(true);
    expect(custom.from).not.toBe(padrao.from);
    // O wordmark segue sendo o do emissor: muda a cor, não a identidade.
    expect(custom.rotulo).toBe('NUBANK');
  });

  it('ignora cor inválida e volta ao catálogo', () => {
    const id = identidadeDoCartao({ nome: 'Nubank', banco: 'Nubank', cor: 'roxo' });
    expect(id.corDoUsuario).toBe(false);
    expect(id.from).toBe(identidadeDoCartao({ nome: 'Nubank', banco: 'Nubank' }).from);
  });

  it('emissor fora do catálogo recebe identidade determinística, nunca vazia', () => {
    const a = identidadeDoCartao({ nome: 'Banco Fictício da Serra', banco: null });
    const b = identidadeDoCartao({ nome: 'Banco Fictício da Serra', banco: null });
    expect(a.from).toBe(b.from);
    expect(a.from).toMatch(/^#[0-9a-f]{6}$/i);
    // monograma ignora 'Banco' e conectivos
    expect(a.glifo).toBe('FS');
    expect(a.emissor).toBeNull();
  });

  it('nomes diferentes geram cores diferentes', () => {
    const a = identidadeDoCartao({ nome: 'Banco Alfa' });
    const b = identidadeDoCartao({ nome: 'Banco Beta' });
    expect(a.from).not.toBe(b.from);
  });

  it('cartão sem nome nenhum não quebra', () => {
    const id = identidadeDoCartao({});
    expect(id.rotulo).toBe('CARTÃO');
    expect(id.from).toMatch(/^#[0-9a-f]{6}$/i);
  });
});

describe('contraste do cartão (WCAG AA)', () => {
  const minimo = (from: string, to: string, tinta: string) =>
    Math.min(contraste(tinta, from), contraste(tinta, to));

  it.each(EMISSORES.map(e => [e.slug, e.base] as const))(
    'a tinta de %s passa 4.5:1 nas duas pontas do gradiente',
    (slug, base) => {
      const [from, to] = gradienteDe(base);
      const tinta = minimo(from, to, TINTA_CLARA) >= minimo(from, to, TINTA_ESCURA)
        ? TINTA_CLARA : TINTA_ESCURA;
      expect(minimo(from, to, tinta)).toBeGreaterThanOrEqual(4.5);
      expect(slug).toBeTruthy();
    },
  );

  it.each(EMISSORES.map(e => e.slug))('o tile do logo de %s é legível', slug => {
    // Valida o que de fato renderiza (identidade derivada), não o campo cru do
    // catálogo: a tinta do tile é calculada por contraste.
    const e = EMISSORES.find(x => x.slug === slug)!;
    const id = identidadeDoCartao({ nome: e.rotulo, banco: e.aliases[0] });
    expect(contraste(id.logoFg, id.logoBg)).toBeGreaterThanOrEqual(4.5);
  });

  it('cor custom clara do usuário não deixa o cartão ilegível', () => {
    const id = identidadeDoCartao({ nome: 'Claro', cor: '#FFE600' });
    expect(Math.min(contraste(id.tinta, id.from), contraste(id.tinta, id.to)))
      .toBeGreaterThanOrEqual(4.5);
  });
});

describe('catálogo', () => {
  it('não tem slug nem alias duplicado', () => {
    const slugs = EMISSORES.map(e => e.slug);
    expect(new Set(slugs).size).toBe(slugs.length);
    const aliases = EMISSORES.flatMap(e => e.aliases);
    expect(new Set(aliases).size).toBe(aliases.length);
  });

  it('todo alias já está normalizado', () => {
    for (const e of EMISSORES) {
      for (const a of e.aliases) expect(normalizarEmissor(a)).toBe(a);
    }
  });

  it('logoFg só aparece junto de logoBg — sozinho ele briga com a tinta derivada', () => {
    for (const e of EMISSORES) {
      if (e.logoFg) expect({ slug: e.slug, temBg: Boolean(e.logoBg) }).toEqual({ slug: e.slug, temBg: true });
    }
  });

  it('toda cor é hex de 6 dígitos e a fonte está declarada', () => {
    for (const e of EMISSORES) {
      expect(e.base).toMatch(/^#[0-9A-Fa-f]{6}$/);
      expect(['oficial', 'aproximacao']).toContain(e.fonte);
    }
  });
});

describe('resolverEmissor por token', () => {
  it('reconhece o emissor dentro de um nome composto', () => {
    expect(resolverEmissor({ nome: 'Itaú Gold' })?.slug).toBe('itau');
    expect(resolverEmissor({ nome: 'Nubank Roxinho' })?.slug).toBe('nubank');
    expect(resolverEmissor({ nome: 'Inter Black' })?.slug).toBe('inter');
    expect(resolverEmissor({ banco: 'Banco do Brasil Ourocard' })?.slug).toBe('bb');
    expect(resolverEmissor({ nome: 'meu cartão do Bradesco' })?.slug).toBe('bradesco');
  });

  it('NÃO reintroduz o falso positivo por substring', () => {
    // "Internacional" contém "inter"; por token vira ['internacional'],
    // que não é alias de ninguém.
    expect(resolverEmissor({ banco: 'Banco Internacional' })).toBeNull();
    expect(resolverEmissor({ nome: 'Internacional Premium' })).toBeNull();
    expect(resolverEmissor({ nome: 'Nubankzinho' })).toBeNull();
    expect(resolverEmissor({ nome: 'Santanderzão' })).toBeNull();
    // Intermedium é alias explícito do Inter e continua resolvendo.
    expect(resolverEmissor({ banco: 'Intermedium' })?.slug).toBe('inter');
  });

  it('o campo banco tem precedência sobre o nome', () => {
    expect(resolverEmissor({ banco: 'Itaú', nome: 'Nubank Ultravioleta' })?.slug).toBe('itau');
  });

  it('tokensDoEmissor descarta palavra genérica e pontuação', () => {
    expect(tokensDoEmissor('Banco Itaú - Gold')).toEqual(['itau', 'gold']);
    expect(tokensDoEmissor('Cartão do Banco')).toEqual(['do']);
    expect(tokensDoEmissor(null)).toEqual([]);
  });

  it('a prévia do formulário resolve enquanto o usuário digita o nome', () => {
    expect(identidadeDoCartao({ nome: 'Itaú Gold' }).rotulo).toBe('ITAÚ');
    expect(identidadeDoCartao({ nome: 'Itaú Gold' }).glifo).toBe('i');
  });
});

describe('cartão do onboarding: identidade vem do que é digitado', () => {
  it('reconhece o banco em nomes livres, não só no rótulo exato', () => {
    for (const digitado of ['itau', 'Itaú Gold', 'meu cartão itaucard', 'ITAÚ']) {
      expect(identidadeDoCartao({ nome: digitado }).emissor?.slug).toBe('itau');
    }
  });

  it('dá cor determinística e legível a um banco fora do catálogo', () => {
    const id = identidadeDoCartao({ nome: 'Banco Imaginário' });
    expect(id.emissor).toBeNull();
    expect(identidadeDoCartao({ nome: 'Banco Imaginário' }).from).toBe(id.from);
    expect(contraste(id.tinta, id.from)).toBeGreaterThanOrEqual(4.5);
  });

  it('Itaú traz a identidade laranja da marca, com tinta legível', () => {
    const id = identidadeDoCartao({ nome: 'Itaú' });
    expect(id.emissor?.slug).toBe('itau');
    expect(id.rotulo).toBe('ITAÚ');
    expect(id.emissor?.base).toBe('#FF6200');
    expect([TINTA_CLARA, TINTA_ESCURA]).toContain(id.tinta);
    expect(contraste(id.tinta, id.from)).toBeGreaterThanOrEqual(4.5);
  });
});
