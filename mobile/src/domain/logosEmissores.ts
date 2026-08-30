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
import { PREENCHIMENTO_LOGO } from './emissoresDataset.gen';

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
 * Fração do tile que o conteúdo da marca deve ocupar, igual para todo emissor.
 *
 * 0,95 é o teto prático, escolhido para a marca encher o tile: o anel que sobra
 * é de 0,9pt no tile de 36pt do cartão. Não dá para ir a 1 — o bbox dos assets
 * não é perfeitamente centrado, e o mais torto (`02038232`, 9% de assimetria
 * vertical) passa a ser cortado pelo `overflow: 'hidden'` do tile. Medido nos
 * 162: em 0,95 o pior caso ainda sobra 1,4% do lado do tile; em 1,0 falta 1,2%.
 *
 * O anel não é decoração: com logo real o tile é branco justamente porque os
 * PNGs são coloridos sobre transparente, e é ele que separa a marca do
 * gradiente do cartão.
 */
export const ALVO_LOGO_NO_TILE = 0.95;

/**
 * Lado da `<Image>` para o conteúdo da marca ocupar `ALVO_LOGO_NO_TILE` do tile.
 *
 * Fica MAIOR que o tile quando o asset tem margem — é a margem transparente que
 * transborda, não o desenho. O container do tile precisa de `overflow: 'hidden'`
 * para essa sobra não invadir o layout ao redor.
 */
export const tamanhoLogoNoTile = (ladoDoTile: number, preenchimento: number): number =>
  Math.round((ladoDoTile * ALVO_LOGO_NO_TILE) / preenchimento);
