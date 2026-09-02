import { FrequenciaRecorrencia } from '../types';

/**
 * Espelho de exibição do calendário de recorrência do backend
 * (CalendarioRecorrencia.java). O app só mostra — a fonte de verdade continua sendo o
 * backend (ADR-0001).
 */

const PASSO: Record<FrequenciaRecorrencia, { passo: number; unidade: 'DIAS' | 'MESES' }> = {
  SEMANAL: { passo: 7, unidade: 'DIAS' },
  QUINZENAL: { passo: 14, unidade: 'DIAS' },
  MENSAL: { passo: 1, unidade: 'MESES' },
  BIMESTRAL: { passo: 2, unidade: 'MESES' },
  TRIMESTRAL: { passo: 3, unidade: 'MESES' },
  SEMESTRAL: { passo: 6, unidade: 'MESES' },
  ANUAL: { passo: 12, unidade: 'MESES' },
};

export function isSubMensal(frequencia: FrequenciaRecorrencia): boolean {
  return PASSO[frequencia].unidade === 'DIAS';
}

const diaNoMes = (ano: number, mes: number) => new Date(ano, mes + 1, 0).getDate();
const semHora = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());

/**
 * Data da próxima cobrança.
 *
 * Em frequência múltipla de mês o dia se ajusta a meses mais curtos e, se já passou
 * neste mês, a cobrança cai no passo seguinte. Em semanal/quinzenal a série sai da
 * âncora e anda em dias, preservando o dia da semana.
 */
export function proximaCobranca(
  diaVencimento: number,
  hoje: Date = new Date(),
  frequencia: FrequenciaRecorrencia = 'MENSAL',
  ancora?: Date | null,
): Date {
  const hojeSemHora = semHora(hoje);
  const { passo, unidade } = PASSO[frequencia];

  if (unidade === 'DIAS') {
    let ocorrencia = semHora(ancora ?? hoje);
    while (ocorrencia < hojeSemHora) {
      ocorrencia = new Date(
        ocorrencia.getFullYear(),
        ocorrencia.getMonth(),
        ocorrencia.getDate() + passo,
      );
    }
    return ocorrencia;
  }

  const ano = hoje.getFullYear();
  const mes = hoje.getMonth();
  let proxima = new Date(ano, mes, Math.min(diaVencimento, diaNoMes(ano, mes)));

  if (proxima < hojeSemHora) {
    const total = mes + passo;
    const anoSeguinte = ano + Math.floor(total / 12);
    const mesSeguinte = ((total % 12) + 12) % 12;
    proxima = new Date(
      anoSeguinte,
      mesSeguinte,
      Math.min(diaVencimento, diaNoMes(anoSeguinte, mesSeguinte)),
    );
  }
  return proxima;
}

/** Rótulo DD/MM/AAAA da próxima cobrança, formatado no fuso local (toISOString viraria o dia). */
export function rotuloProximaCobranca(
  diaVencimento: number,
  hoje: Date = new Date(),
  frequencia: FrequenciaRecorrencia = 'MENSAL',
  ancora?: Date | null,
): string {
  const d = proximaCobranca(diaVencimento, hoje, frequencia, ancora);
  const dois = (n: number) => String(n).padStart(2, '0');
  return `${dois(d.getDate())}/${dois(d.getMonth() + 1)}/${d.getFullYear()}`;
}

const NOME: Record<FrequenciaRecorrencia, string> = {
  SEMANAL: 'Semanal',
  QUINZENAL: 'Quinzenal',
  MENSAL: 'Mensal',
  BIMESTRAL: 'Bimestral',
  TRIMESTRAL: 'Trimestral',
  SEMESTRAL: 'Semestral',
  ANUAL: 'Anual',
};

export const FREQUENCIAS: FrequenciaRecorrencia[] = [
  'SEMANAL', 'QUINZENAL', 'MENSAL', 'BIMESTRAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL',
];

export function nomeFrequencia(frequencia: FrequenciaRecorrencia): string {
  return NOME[frequencia];
}

const DIA_DA_SEMANA = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];
const MES = [
  'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
  'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro',
];

/**
 * Como a cobrança se repete, em português. "Todo dia 10" mentiria numa recorrência
 * semanal ou anual — o rótulo precisa acompanhar a frequência.
 */
export function rotuloCadencia(
  frequencia: FrequenciaRecorrencia,
  diaVencimento: number,
  ancora?: Date | null,
): string {
  switch (frequencia) {
    case 'SEMANAL':
      return ancora ? `Toda ${DIA_DA_SEMANA[ancora.getDay()]}` : 'Toda semana';
    case 'QUINZENAL':
      return 'A cada 14 dias';
    case 'MENSAL':
      return `Todo dia ${diaVencimento}`;
    case 'BIMESTRAL':
      return `A cada 2 meses, dia ${diaVencimento}`;
    case 'TRIMESTRAL':
      return `A cada 3 meses, dia ${diaVencimento}`;
    case 'SEMESTRAL':
      return `A cada 6 meses, dia ${diaVencimento}`;
    case 'ANUAL':
      return ancora
        ? `Todo dia ${diaVencimento} de ${MES[ancora.getMonth()]}`
        : `Uma vez por ano, dia ${diaVencimento}`;
  }
}
