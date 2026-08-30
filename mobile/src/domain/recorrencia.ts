/**
 * Data da próxima cobrança de uma recorrência mensal, espelhando
 * ContaFixaService.calcularProximoVencimento no backend: o dia se ajusta a meses
 * mais curtos e, se já passou neste mês, a cobrança cai no mês seguinte.
 *
 * O app só exibe — a fonte de verdade continua sendo o backend (ADR-0001).
 */
export function proximaCobranca(diaVencimento: number, hoje: Date = new Date()): Date {
  const diaNoMes = (ano: number, mes: number) => new Date(ano, mes + 1, 0).getDate();

  const ano = hoje.getFullYear();
  const mes = hoje.getMonth();
  let proxima = new Date(ano, mes, Math.min(diaVencimento, diaNoMes(ano, mes)));

  const hojeSemHora = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());
  if (proxima < hojeSemHora) {
    const anoSeguinte = mes === 11 ? ano + 1 : ano;
    const mesSeguinte = mes === 11 ? 0 : mes + 1;
    proxima = new Date(
      anoSeguinte,
      mesSeguinte,
      Math.min(diaVencimento, diaNoMes(anoSeguinte, mesSeguinte)),
    );
  }
  return proxima;
}

/** Rótulo DD/MM/AAAA da próxima cobrança, formatado no fuso local (toISOString viraria o dia). */
export function rotuloProximaCobranca(diaVencimento: number, hoje: Date = new Date()): string {
  const d = proximaCobranca(diaVencimento, hoje);
  const dois = (n: number) => String(n).padStart(2, '0');
  return `${dois(d.getDate())}/${dois(d.getMonth() + 1)}/${d.getFullYear()}`;
}
