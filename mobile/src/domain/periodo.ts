/**
 * Período e competência — a régua de tempo do app.
 *
 * `iso()`, a lista de meses e a aritmética de mês estavam reescritas inline em
 * seis telas (Análises, Transações, Contas fixas, Orçamentos, Fatura, Carteira),
 * cada uma com a sua variação. Tudo aqui trabalha em **hora local**: a
 * competência que o usuário vê é a do fuso dele, e converter para UTC no meio do
 * caminho move lançamentos de mês.
 */

export const MESES = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
] as const;

/** Data em `YYYY-MM-DD`, pelos componentes locais — nunca `toISOString()`. */
export const iso = (d: Date): string =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Competência em `YYYY-MM`, o formato que o backend usa para contas fixas. */
export const competenciaIso = (mes: number, ano: number): string =>
  `${ano}-${String(mes).padStart(2, '0')}`;

export interface Intervalo {
  inicio: string;
  fim: string;
}

/** Primeiro e último dia do mês de `ref`. */
export const intervaloDoMes = (ref: Date): Intervalo => ({
  inicio: iso(new Date(ref.getFullYear(), ref.getMonth(), 1)),
  fim: iso(new Date(ref.getFullYear(), ref.getMonth() + 1, 0)),
});

/** Move `delta` meses mantendo o dia 1 — a âncora de toda navegação de mês. */
export const somarMeses = (ref: Date, delta: number): Date =>
  new Date(ref.getFullYear(), ref.getMonth() + delta, 1);

export const competenciaDe = (ref: Date): { mes: number; ano: number } => ({
  mes: ref.getMonth() + 1,
  ano: ref.getFullYear(),
});

export const competenciaAtual = (hoje: Date = new Date()) => competenciaDe(hoje);

export const ehCompetenciaCorrente = (mes: number, ano: number, hoje: Date = new Date()): boolean =>
  mes === hoje.getMonth() + 1 && ano === hoje.getFullYear();

/** "Março de 2026" — o rótulo de competência das telas de fatura e orçamento. */
export const rotuloDeCompetencia = (mes: number, ano: number): string =>
  `${MESES[mes - 1]} de ${ano}`;

export type Periodo = 'mes' | 'mesPassado' | 'tresMeses' | 'ano';

export const PERIODOS: { key: Periodo; label: string }[] = [
  { key: 'mes', label: 'Este mês' },
  { key: 'mesPassado', label: 'Mês passado' },
  { key: 'tresMeses', label: '3 meses' },
  { key: 'ano', label: 'Este ano' },
];

/**
 * Intervalo de um período nomeado. Os que incluem hoje terminam **hoje**, não no
 * fim do mês: relatório de "este mês" com fim no futuro devolve zero nos dias que
 * ainda não aconteceram e achata a média.
 */
export const intervaloDoPeriodo = (periodo: Periodo, hoje: Date = new Date()): Intervalo => {
  const ano = hoje.getFullYear();
  const mes = hoje.getMonth();
  switch (periodo) {
    case 'mes':
      return { inicio: iso(new Date(ano, mes, 1)), fim: iso(hoje) };
    case 'mesPassado':
      return {
        inicio: iso(new Date(ano, mes - 1, 1)),
        fim: iso(new Date(ano, mes, 0)),
      };
    case 'tresMeses':
      return { inicio: iso(new Date(ano, mes - 2, 1)), fim: iso(hoje) };
    case 'ano':
      return { inicio: iso(new Date(ano, 0, 1)), fim: iso(hoje) };
  }
};
