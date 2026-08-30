// Ícone do item de lista (transação, conta fixa, parcela, orçamento).
//
// O sistema de ícones do app é emoji renderizado como texto dentro de
// `ui/IconTile` (DESIGN.md, seção Color). O campo `categoria.icone` do backend
// é um VARCHAR livre, e três origens já gravaram lixo nele:
//
//   1. o frontend web grava o SLUG do ícone Lucide — `String(categoria.id)`
//      truncado em 10 chars ('moradia', 'alimentaca') e o literal 'tag'/'cart'.
//      O mobile desenhava a string inteira: era o "só letras" do relato;
//   2. o form de categoria do mobile nunca enviou ícone, e as listas caíam no
//      fallback '↑'/'↓': era o "só símbolos";
//   3. o web manda `icone: ''` por default, e os call sites que usavam `??`
//      (nullish) não disparavam o fallback com string vazia: era o "não mostra
//      nada".
//
// Por isso a validação aqui é por FORMA, não por lista de valores conhecidos:
// qualquer coisa que não pareça emoji é descartada e o nome da categoria vira
// a fonte do ícone. Nada é gravado no banco — a correção é na leitura, então
// vale para as categorias legadas sem migration.

/** Último recurso quando não há categoria nem palavra reconhecida. */
export const EMOJI_GENERICO = '🏷️';

const NAO_EMOJI = new Set(['null', 'undefined', 'nan', 'none']);

/**
 * Emoji começa fora do ASCII e nunca contém letra ou dígito ASCII. As duas
 * condições juntas separam '🍔' de 'moradia' (ASCII puro) e também de 'Água',
 * que começa fora do ASCII mas é palavra — o caso que uma checagem só do
 * primeiro caractere deixaria passar.
 */
export const ehEmoji = (valor: string | null | undefined): boolean => {
  const v = (valor ?? '').trim();
  if (!v || NAO_EMOJI.has(v.toLowerCase())) return false;
  if (/[a-zA-Z0-9]/.test(v)) return false;
  return (v.codePointAt(0) ?? 0) > 0x7f;
};

const normalizar = (valor: string | null | undefined): string =>
  (valor ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();

// Palavra-chave → emoji. A busca é por palavra inteira dentro do nome, então
// 'Mercado Livre' e 'mercado' resolvem igual, e 'Farmácia' casa depois de
// perder o acento na normalização.
const SUGESTOES: ReadonlyArray<readonly [readonly string[], string]> = [
  [['mercado', 'supermercado', 'feira', 'hortifruti', 'padaria', 'acougue'], '🛒'],
  [['alimentacao', 'comida', 'restaurante', 'lanche', 'ifood', 'delivery', 'almoco', 'janta'], '🍔'],
  [['cafe', 'cafeteria', 'starbucks'], '☕'],
  [['mercadinho', 'conveniencia'], '🏪'],
  [['moradia', 'casa', 'aluguel', 'condominio', 'imovel', 'financiamento'], '🏠'],
  [['luz', 'energia', 'eletrica', 'enel', 'cemig', 'light'], '💡'],
  [['agua', 'saneamento', 'sabesp', 'esgoto'], '🚿'],
  [['gas', 'botijao'], '🔥'],
  [['internet', 'wifi', 'banda', 'provedor', 'vivo', 'claro', 'tim', 'oi'], '🌐'],
  [['telefone', 'celular', 'telefonia'], '📱'],
  [['transporte', 'uber', 'taxi', 'onibus', 'metro', 'passagem', 'mobilidade'], '🚌'],
  [['carro', 'combustivel', 'gasolina', 'etanol', 'posto', 'estacionamento', 'ipva', 'pedagio'], '🚗'],
  [['moto', 'motocicleta'], '🏍️'],
  [['saude', 'medico', 'consulta', 'exame', 'hospital', 'dentista', 'plano'], '🏥'],
  [['farmacia', 'remedio', 'medicamento'], '💊'],
  [['academia', 'gym', 'musculacao', 'crossfit', 'pilates'], '🏋️'],
  [['educacao', 'escola', 'faculdade', 'curso', 'livro', 'material', 'mensalidade'], '📚'],
  [['lazer', 'jogo', 'jogos', 'game', 'games', 'diversao'], '🎮'],
  [['cinema', 'filme', 'teatro', 'show', 'streaming', 'netflix', 'spotify', 'disney'], '🎬'],
  [['viagem', 'hotel', 'ferias', 'hospedagem', 'aereo'], '✈️'],
  [['vestuario', 'roupa', 'roupas', 'calcado', 'sapato', 'moda'], '👕'],
  [['beleza', 'salao', 'cabelo', 'barbearia', 'estetica'], '💇'],
  [['pet', 'cachorro', 'gato', 'veterinario', 'racao'], '🐾'],
  [['assinatura', 'assinaturas', 'app', 'apps', 'software'], '🔁'],
  [['presente', 'presentes', 'aniversario', 'natal'], '🎁'],
  [['doacao', 'caridade', 'dizimo', 'igreja', 'oferta'], '🙏'],
  [['salario', 'pagamento', 'holerite', 'remuneracao', 'renda'], '💰'],
  [['freelance', 'freela', 'bico', 'extra', 'servico'], '🧰'],
  [['investimento', 'investimentos', 'dividendo', 'rendimento', 'aplicacao', 'poupanca'], '📈'],
  [['transferencia', 'pix', 'ted', 'doc'], '💱'],
  [['emprestimo', 'divida', 'parcela', 'credito', 'juros'], '🏦'],
  [['imposto', 'impostos', 'taxa', 'taxas', 'tarifa', 'multa', 'tributo'], '🧾'],
  [['seguro', 'seguros', 'previdencia'], '🛡️'],
  [['trabalho', 'escritorio', 'empresa', 'negocio'], '💼'],
  [['filho', 'filhos', 'crianca', 'bebe', 'escolar'], '🧸'],
  [['reforma', 'manutencao', 'conserto', 'obra'], '🔧'],
  [['mercearia', 'limpeza', 'higiene'], '🧼'],
  [['tecnologia', 'eletronico', 'computador', 'celularnovo'], '💻'],
  [['outros', 'diversos', 'geral'], '📦'],
];

/**
 * Emoji derivado do nome da categoria. `null` quando nenhuma palavra do nome
 * está no mapa — quem chama decide o último recurso.
 */
export const emojiSugerido = (nome: string | null | undefined): string | null => {
  const alvo = normalizar(nome);
  if (!alvo) return null;
  const palavras = new Set(alvo.split(/[^a-z0-9]+/).filter(Boolean));
  for (const [chaves, emoji] of SUGESTOES) {
    for (const chave of chaves) {
      if (palavras.has(chave)) return emoji;
    }
  }
  return null;
};

/**
 * Ícone a mostrar para uma categoria, na ordem: emoji gravado > emoji
 * derivado do nome > `fallback`.
 *
 * `fallback` é do call site porque cada lista tem o seu ('↑'/'↓' na transação
 * sem categoria, '📌' na conta fixa, '💳' no lançamento de fatura).
 */
export const emojiDaCategoria = (
  categoria: { icone?: string | null; nome?: string | null } | null | undefined,
  fallback: string,
): string => {
  if (ehEmoji(categoria?.icone)) return (categoria!.icone as string).trim();
  return emojiSugerido(categoria?.nome) ?? fallback;
};

/**
 * Grade oferecida no seletor de ícone da categoria (e da meta).
 *
 * Todo item cabe em `@Size(max = 10)` — o limite do backend em
 * `CategoriaCreateRequest`/`CategoriaUpdateRequest`, que conta unidades UTF-16.
 * Emoji com sequência ZWJ longa (👨‍👩‍👧‍👦 custa 11) tomaria 400, por isso não
 * entra na grade; o teste de `iconeCategoria` falha se algum item passar de 10.
 *
 * O web tem a sua cópia em frontend/src/data/emojisCategoria.ts — as duas
 * listas existem porque os dois apps não compartilham pacote.
 */
export const EMOJIS_CATEGORIA: readonly string[] = [
  '🛒', '🍔', '☕', '🍺', '🏠', '💡', '🚿', '🔥',
  '🌐', '📱', '🚌', '🚗', '🏍️', '✈️', '🏥', '💊',
  '🏋️', '📚', '🎮', '🎬', '🎵', '👕', '💇', '🐾',
  '🔁', '🎁', '🙏', '💰', '🧰', '📈', '💱', '🏦',
  '🧾', '🛡️', '💼', '🧸', '🔧', '🧼', '💻', '📦',
  '🏷️', '📌',
];
