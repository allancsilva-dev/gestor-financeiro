import {
  EMOJIS_CATEGORIA,
  EMOJI_GENERICO,
  ehEmoji,
  emojiDaCategoria,
  emojiSugerido,
} from '../domain/iconeCategoria';

describe('ehEmoji', () => {
  it('aceita emoji, inclusive com seletor de variação e ZWJ', () => {
    expect(ehEmoji('🍔')).toBe(true);
    expect(ehEmoji('🏋️')).toBe(true);
    expect(ehEmoji('🏋️‍♀️')).toBe(true);
    expect(ehEmoji('🏷️')).toBe(true);
    expect(ehEmoji(' 🚗 ')).toBe(true);
  });

  it('recusa o slug Lucide que o frontend web grava no campo icone', () => {
    // CategoriaDropdown.tsx gravava String(categoria.id).slice(0, 10)
    expect(ehEmoji('moradia')).toBe(false);
    expect(ehEmoji('alimentaca')).toBe(false);
    expect(ehEmoji('tag')).toBe(false);
    expect(ehEmoji('cart')).toBe(false);
    expect(ehEmoji('shopping-cart')).toBe(false);
  });

  it('recusa vazio, nulo e a string "null" que já viajou no JSON', () => {
    expect(ehEmoji('')).toBe(false);
    expect(ehEmoji('   ')).toBe(false);
    expect(ehEmoji(null)).toBe(false);
    expect(ehEmoji(undefined)).toBe(false);
    expect(ehEmoji('null')).toBe(false);
    expect(ehEmoji('undefined')).toBe(false);
  });

  it('recusa palavra acentuada, que começa fora do ASCII mas não é emoji', () => {
    // O caso que uma checagem só do primeiro caractere deixaria passar.
    expect(ehEmoji('Água')).toBe(false);
    expect(ehEmoji('Ônibus')).toBe(false);
    expect(ehEmoji('Órgão')).toBe(false);
  });

  it('aceita símbolo não-ASCII como as setas de fallback — quem escolhe é o call site', () => {
    expect(ehEmoji('↑')).toBe(true);
    expect(ehEmoji('↓')).toBe(true);
  });
});

describe('emojiSugerido', () => {
  it('deriva do nome da categoria, sem acento e sem caixa', () => {
    expect(emojiSugerido('Supermercado')).toBe('🛒');
    expect(emojiSugerido('MERCADO')).toBe('🛒');
    expect(emojiSugerido('Farmácia')).toBe('💊');
    expect(emojiSugerido('Alimentação')).toBe('🍔');
    expect(emojiSugerido('Salário')).toBe('💰');
    expect(emojiSugerido('Combustível')).toBe('🚗');
  });

  it('casa por palavra inteira dentro de nome composto', () => {
    expect(emojiSugerido('Mercado Livre')).toBe('🛒');
    expect(emojiSugerido('Conta de água')).toBe('🚿');
    expect(emojiSugerido('Plano de saúde')).toBe('🏥');
  });

  it('devolve null quando nenhuma palavra é reconhecida', () => {
    expect(emojiSugerido('Zzzzz')).toBeNull();
    expect(emojiSugerido('')).toBeNull();
    expect(emojiSugerido(null)).toBeNull();
  });
});

describe('emojiDaCategoria', () => {
  it('prefere o emoji gravado', () => {
    expect(emojiDaCategoria({ icone: '🎮', nome: 'Mercado' }, '↓')).toBe('🎮');
  });

  it('cai no nome quando o gravado é lixo do web — as "letras" do relato', () => {
    expect(emojiDaCategoria({ icone: 'moradia', nome: 'Moradia' }, '↓')).toBe('🏠');
    expect(emojiDaCategoria({ icone: 'tag', nome: 'Supermercado' }, '↓')).toBe('🛒');
    expect(emojiDaCategoria({ icone: '', nome: 'Farmácia' }, '↓')).toBe('💊');
  });

  it('cai no fallback do call site quando não há nada aproveitável', () => {
    expect(emojiDaCategoria({ icone: null, nome: 'Zzzzz' }, '↓')).toBe('↓');
    expect(emojiDaCategoria(null, '↑')).toBe('↑');
    expect(emojiDaCategoria(undefined, EMOJI_GENERICO)).toBe(EMOJI_GENERICO);
  });

  it('cabe no @Size(max = 10) do backend, que conta unidades UTF-16', () => {
    // CategoriaCreateRequest.java:17 — '👨‍👩‍👧‍👦' custa 11 e tomaria 400.
    const derivados = ['Supermercado', 'Academia', 'Moradia', 'Salário', 'Seguro', 'Pet']
      .map(n => emojiSugerido(n))
      .filter((e): e is string => e !== null);
    expect(derivados.length).toBeGreaterThan(0);
    for (const emoji of derivados) expect(emoji.length).toBeLessThanOrEqual(10);
  });
});

describe('EMOJIS_CATEGORIA', () => {
  it('toda a grade do seletor cabe no @Size(max = 10) do backend', () => {
    // Java conta unidades UTF-16: '👨‍👩‍👧‍👦' custa 11 e o POST volta 400.
    for (const emoji of EMOJIS_CATEGORIA) {
      expect(emoji.length).toBeLessThanOrEqual(10);
    }
  });

  it('a grade é toda de emoji e sem repetido', () => {
    for (const emoji of EMOJIS_CATEGORIA) expect(ehEmoji(emoji)).toBe(true);
    expect(new Set(EMOJIS_CATEGORIA).size).toBe(EMOJIS_CATEGORIA.length);
  });

  it('todo emoji sugerido por nome está na grade, para o seletor pré-marcar', () => {
    const nomes = ['Mercado', 'Moradia', 'Saúde', 'Farmácia', 'Salário', 'Pet', 'Viagem'];
    for (const nome of nomes) {
      const sugerido = emojiSugerido(nome);
      expect(sugerido).not.toBeNull();
      expect(EMOJIS_CATEGORIA).toContain(sugerido);
    }
  });
});
