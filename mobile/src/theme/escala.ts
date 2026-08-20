import { Dimensions } from 'react-native';

/**
 * Escala de fidelidade às referências de design.
 *
 * Os mocks em `mobile/.design/` foram desenhados numa tela de **360dp** de largura.
 * Em dp cru, num aparelho de 402dp o mesmo layout fica proporcionalmente menor que a
 * foto — o card estica, o texto não, e o conjunto deixa de ser a mesma composição.
 * `e()` converte um valor medido na referência para o equivalente na tela atual,
 * mantendo as proporções do mock em qualquer largura.
 */
export const LARGURA_DA_REFERENCIA = 360;

const fator = Dimensions.get('window').width / LARGURA_DA_REFERENCIA;

export const e = (dpNaReferencia: number): number =>
  Math.round(dpNaReferencia * fator * 10) / 10;

export const fatorDeEscala = fator;
