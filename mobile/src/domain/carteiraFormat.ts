// Formatação e classificação da tela Carteira. Vive em domain/ e não dentro do
// componente para poder ser testado sem montar árvore de React Native.
//
// Nenhuma função aqui usa `new Date(iso)`: datas do backend são date-only e o
// construtor as interpreta em UTC, deslocando um dia no fuso de São Paulo
// (BUG-0067).

const MESES_CURTO = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const MESES_LONGO = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
  'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
const MESES_ABREV = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];

/** "26/jul" */
export const dataCurta = (iso: string): string => {
  const [, mes, dia] = iso.split('-').map(Number);
  return `${dia}/${MESES_CURTO[mes - 1] ?? ''}`;
};

/** "21 de julho" */
export const dataLonga = (iso: string): string => {
  const [, mes, dia] = iso.split('-').map(Number);
  return `${dia} de ${MESES_LONGO[mes - 1] ?? ''}`;
};

/** "Ago/26" */
export const competenciaCurta = (mes: number, ano: number): string =>
  `${MESES_ABREV[mes - 1] ?? ''}/${String(ano).slice(-2)}`;

export const prazoEmDias = (dias: number): string => {
  if (dias < 0) return `há ${Math.abs(dias)}d`;
  if (dias === 0) return 'hoje';
  if (dias === 1) return 'amanhã';
  return `em ${dias}d`;
};

export type PosicaoFatura = 'anterior' | 'atual' | 'proxima' | 'futura';

export const ROTULO_FATURA: Record<PosicaoFatura, string> = {
  anterior: 'Fatura anterior',
  atual: 'Fatura atual',
  proxima: 'Próxima fatura',
  futura: 'Fatura futura',
};

/**
 * Posição da competência em relação a hoje. A conta é em meses absolutos para
 * atravessar a virada de ano — comparar só o mês diria que janeiro vem antes
 * de dezembro.
 */
export const posicaoDaFatura = (
  fatura: { mes: number; ano: number },
  hojeMes: number,
  hojeAno: number,
): PosicaoFatura => {
  const delta = (fatura.ano - hojeAno) * 12 + (fatura.mes - hojeMes);
  if (delta < 0) return 'anterior';
  if (delta === 0) return 'atual';
  if (delta === 1) return 'proxima';
  return 'futura';
};
