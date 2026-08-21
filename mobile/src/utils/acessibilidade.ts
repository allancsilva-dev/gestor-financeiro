/**
 * Props para ícone puramente decorativo dentro de um controle que já tem texto.
 *
 * Um `Touchable` é `accessible` por padrão: o iOS funde os filhos num nó só e
 * monta o rótulo concatenando o que encontra. O glifo de um `@expo/vector-icons`
 * é um `<Text>`, então entra na conta — o rótulo do botão "Carteira" virava
 * `", Carteira"`, que o leitor de tela anuncia e a busca por texto não
 * encontra. Esconder o ícone deixa o rótulo igual ao texto visível.
 */
export const iconeDecorativo = {
  accessibilityElementsHidden: true,
  importantForAccessibility: 'no-hide-descendants',
} as const;
