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

/**
 * Asset do Metro para o logo do emissor, pronto para `<Image source>`.
 * `null` quando o ISPB não tem logo no pacote.
 */
export const logoDoIspb = (ispb: string | null | undefined): number | null => {
  if (!ispb) return null;
  return logos[ispb] ?? null;
};
