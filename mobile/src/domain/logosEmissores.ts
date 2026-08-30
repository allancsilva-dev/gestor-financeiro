// Ponte única para o mapa de logos do pacote `logos-bancos-br`.
//
// Existe isolado por dois motivos:
//   1. o entry `logos-bancos-br/react-native` é um mapa estático de
//      `require()` de PNG — importá-lo embarca os 162 logos distintos (~1 MB)
//      no bundle, e é bom que esse custo tenha um só ponto de entrada;
//   2. o preset `jest-expo` não transforma esse arquivo ESM dentro de
//      node_modules. Com a ponte aqui, o teste mocka ESTE módulo e o domínio
//      continua testável sem tocar em `transformIgnorePatterns`.
//
// Nenhuma requisição de rede: os assets são locais, então o app funciona
// offline e nada sobre qual banco o usuário tem sai do aparelho.
import { logos } from 'logos-bancos-br/react-native';
import { LOGO_MEDIDO, PREENCHIMENTO_LOGO, type EntradaLogo } from './emissoresDataset.gen';

/**
 * Asset do Metro para o logo do emissor, pronto para `<Image source>`.
 * `null` quando o ISPB não tem logo no pacote.
 */
export const logoDoIspb = (ispb: string | null | undefined): number | null => {
  if (!ispb) return null;
  return logos[ispb] ?? null;
};

/**
 * Quanto do PNG do logo é conteúdo opaco, em (0, 1]. `1` = asset full-bleed.
 *
 * Vem do dataset gerado, não do pacote: é um número medido em build time
 * (scripts/gerar-emissores-dataset.mjs decodifica o alfa dos PNGs), então o app
 * não decodifica imagem nenhuma em runtime e o mock de `logos` não precisa
 * saber deste fator.
 *
 * Serve para o CONTEÚDO da marca ter o mesmo tamanho em todo emissor: os assets
 * do pacote não têm margem uniforme (104 dos 162 são full-bleed, o menor tem
 * 41% de conteúdo), então renderizar todos no mesmo tamanho faz a marca com
 * margem sair com ~metade do corpo das outras. Divida o tamanho da imagem por
 * este fator — veja `tamanhoLogoNoTile`.
 */
export const preenchimentoDoIspb = (ispb: string | null | undefined): number => {
  if (!ispb) return 1;
  return PREENCHIMENTO_LOGO[ispb] ?? 1;
};

/**
 * Medidas de render do asset, ou `null` quando o emissor não tem logo.
 *
 * Os 162 assets do pacote se dividem em dois desenhos diferentes, e tratá-los
 * igual foi o que obrigava o cartão a pintar um tile branco atrás de todo logo:
 *
 *   - 99 são PLACA: quadrado, squircle ou círculo opaco, com a cor da marca já
 *     dentro do desenho (o quadrado amarelo do BB, o squircle azul do Itaú, o
 *     círculo do Bradesco). Fundo atrás deles é ruído — vira um quadrado branco
 *     com a placa da marca sobrando no meio. Vão full-bleed, sem nada atrás;
 *   - 63 são MARCA SOLTA sobre transparente (o "nu" do Nubank, o wordmark do
 *     C6). Essas dependem do fundo para existir.
 *
 * O que fazer com cada uma depende do fundo, que só o runtime conhece: veja
 * `estiloDoLogo` em emissores.ts.
 */
export const entradaLogoDoIspb = (ispb: string | null | undefined): EntradaLogo | null => {
  if (!ispb) return null;
  return LOGO_MEDIDO[ispb] ?? null;
};

/** Valores de `EntradaLogo[0]`, o tipo de desenho medido no asset. */
export const TIPO_PLACA = 0;
export const TIPO_MARCA = 1;

/** `true` quando o asset traz a própria placa e dispensa fundo. */
export const logoSolidoDoIspb = (ispb: string | null | undefined): boolean =>
  entradaLogoDoIspb(ispb)?.[0] === TIPO_PLACA;

/**
 * Fração do tile que o conteúdo do asset ocupa, por tipo de desenho.
 *
 * PLACA vai a 1: a arte É a placa, então ela encosta na borda do tile e o
 * `borderRadius` + `overflow: 'hidden'` do tile arredondam o canto, como o
 * ícone de app na home do celular. O bbox dos assets não é perfeitamente
 * centrado, então a placa mais torta das 99 (`30723871`, 0,43% do lado) tem
 * essa ponta comida pelo corte: 0,14pt num tile de 32pt, menos de meio pixel
 * em 3x, e ainda por cima no canto que o `borderRadius` já arredonda.
 *
 * MARCA fica em 0,86: sem placa atrás, ela pousa direto no gradiente do cartão
 * e precisa de respiro — encostar na borda faria a marca brigar com o wordmark
 * ao lado. Aqui a folga não é só óptica: a marca solta é bem mais descentrada
 * (`02038232`, 6,9% do lado), e o teto sem corte nenhum é 1/(1+2·0,069) =
 * 0,8783 — 0,86 fica abaixo dele.
 */
export const ALVO_PLACA = 1;
export const ALVO_MARCA = 0.86;

/**
 * Lado da `<Image>` para o conteúdo ocupar `alvo` do tile.
 *
 * Fica MAIOR que o tile quando o asset tem margem — é a margem transparente que
 * transborda, não o desenho. O container do tile precisa de `overflow: 'hidden'`
 * para essa sobra não invadir o layout ao redor.
 *
 * Arredonda para baixo, não para o inteiro mais próximo: para cima, o pixel a
 * mais é multiplicado por 1/preenchimento e chega a empurrar o conteúdo 0,17pt
 * para fora do tile em alguns assets. Para baixo, o desvio some no mesmo lugar
 * — abaixo de meio pixel de conteúdo.
 */
export const tamanhoLogoNoTile = (
  ladoDoTile: number,
  preenchimento: number,
  alvo: number,
): number => Math.floor((ladoDoTile * alvo) / preenchimento);
