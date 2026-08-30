/**
 * Emoji oferecido no seletor de ícone de categoria.
 *
 * O campo `icone` da categoria é desenhado como TEXTO no app mobile (tile de
 * `ui/IconTile`). Gravar aqui o slug do ícone Lucide fazia a lista do app
 * mostrar "moradia"/"cart"/"tag" no lugar do ícone — por isso o valor salvo é
 * sempre emoji, e o ícone Lucide fica só na tela do web.
 *
 * Todo item cabe em `@Size(max = 10)`, o limite do backend, que conta unidades
 * UTF-16. Cópia espelhada de EMOJIS_CATEGORIA em
 * mobile/src/domain/iconeCategoria.ts — as duas listas existem porque web e
 * mobile não compartilham pacote.
 */
export const EMOJIS_CATEGORIA: readonly string[] = [
  '🛒', '🍔', '☕', '🍺', '🏠', '💡', '🚿', '🔥',
  '🌐', '📱', '🚌', '🚗', '🏍️', '✈️', '🏥', '💊',
  '🏋️', '📚', '🎮', '🎬', '🎵', '👕', '💇', '🐾',
  '🔁', '🎁', '🙏', '💰', '🧰', '📈', '💱', '🏦',
  '🧾', '🛡️', '💼', '🧸', '🔧', '🧼', '💻', '📦',
  '🏷️', '📌',
];

export const EMOJI_CATEGORIA_PADRAO = '🏷️';
