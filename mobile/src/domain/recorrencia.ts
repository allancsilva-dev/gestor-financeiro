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

/**
 * Frequência cuja série sai de uma data de primeira cobrança em vez do dia do mês.
 *
 * Toda frequência exceto MENSAL (V73). Em MENSAL todo mês tem ocorrência, então o mês de
 * partida é irrelevante e o CHECK `ck_contas_fixas_ancora_por_frequencia` proíbe âncora
 * ali. Nas demais é a âncora que fixa o aniversário — sem ela, "todo 15 de março"
 * cadastrado em setembro cairia em setembro.
 */
export function usaAncora(frequencia: FrequenciaRecorrencia): boolean {
  return frequencia !== 'MENSAL';
}

const diaNoMes = (ano: number, mes: number) => new Date(ano, mes + 1, 0).getDate();
const semHora = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());

/** Avança N meses preservando o dia pedido, encurtando-o em mês curto (31/01 + 1 = 28/02). */
const somaMeses = (base: Date, meses: number, diaVencimento: number) => {
  const total = base.getMonth() + meses;
  const ano = base.getFullYear() + Math.floor(total / 12);
  const mes = ((total % 12) + 12) % 12;
  return new Date(ano, mes, Math.min(diaVencimento, diaNoMes(ano, mes)));
};

/**
 * Data da próxima cobrança.
 *
 * Com âncora a série sai dela e anda em passos da frequência — em semanal/quinzenal
 * preservando o dia da semana, e de bimestral a anual preservando o mês do aniversário.
 * Sem âncora (o caso de MENSAL) o dia se ajusta a meses mais curtos e, se já passou
 * neste mês, a cobrança cai no passo seguinte.
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

  // Com âncora (V73), a série sai dela e não de "hoje": é o que permite escolher o mês
  // de uma anual ("todo 15 de março") e o que impede uma edição em setembro de antecipar
  // o aniversário. Espelha CalendarioRecorrencia.primeiraAPartirDe.
  if (ancora) {
    let ocorrencia = semHora(ancora);
    // Teto defensivo: só frequências não-mensais chegam aqui, então o passo mínimo é
    // de 2 meses e 1200 saltos cobrem séculos. Sem ele, um passo mal formado travaria.
    let limite = 1200;
    while (ocorrencia < hojeSemHora && limite-- > 0) {
      ocorrencia = somaMeses(ocorrencia, passo, diaVencimento);
    }
    return ocorrencia;
  }

  const ano = hoje.getFullYear();
  const mes = hoje.getMonth();
  let proxima = new Date(ano, mes, Math.min(diaVencimento, diaNoMes(ano, mes)));

  if (proxima < hojeSemHora) {
    proxima = somaMeses(proxima, passo, diaVencimento);
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

const desdeOMes = (ancora?: Date | null) => (ancora ? `, a partir de ${MES[ancora.getMonth()]}` : '');

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
    // O mês importa tanto quanto o dia: "a cada 3 meses, dia 15" não diz se cai em
    // março ou em abril. Com âncora (V73) o app sabe, e calar isso faria o rótulo
    // esconder justamente o que o dono escolheu.
    case 'BIMESTRAL':
      return `A cada 2 meses, dia ${diaVencimento}${desdeOMes(ancora)}`;
    case 'TRIMESTRAL':
      return `A cada 3 meses, dia ${diaVencimento}${desdeOMes(ancora)}`;
    case 'SEMESTRAL':
      return `A cada 6 meses, dia ${diaVencimento}${desdeOMes(ancora)}`;
    case 'ANUAL':
      return ancora
        ? `Todo dia ${diaVencimento} de ${MES[ancora.getMonth()]}`
        : `Uma vez por ano, dia ${diaVencimento}`;
  }
}
