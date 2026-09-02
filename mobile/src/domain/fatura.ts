/**
 * Espelho de exibição do FaturaDatas.java. O app só mostra em qual fatura uma compra
 * vai cair — a fonte de verdade continua sendo o backend (ADR-0001).
 */

const diaNoMes = (ano: number, mes: number) => new Date(ano, mes + 1, 0).getDate();

const diaValidoOuFimDoMes = (dia: number | null | undefined, ano: number, mes: number) =>
  Math.min(dia ?? diaNoMes(ano, mes), diaNoMes(ano, mes));

/**
 * Competência da fatura que recebe uma compra: depois do fechamento, a compra já cai
 * na fatura do mês seguinte.
 */
export function competenciaDaCompra(
  dataCompra: Date,
  diaFechamento: number | null | undefined,
): { ano: number; mes: number } {
  const ano = dataCompra.getFullYear();
  const mes = dataCompra.getMonth();
  const fechamento = diaValidoOuFimDoMes(diaFechamento, ano, mes);

  if (dataCompra.getDate() > fechamento) {
    const total = mes + 1;
    return { ano: ano + Math.floor(total / 12), mes: total % 12 };
  }
  return { ano, mes };
}

/**
 * Vencimento da fatura de uma competência. Quando o dia de vencimento é anterior ou
 * igual ao de fechamento, a fatura vence no mês seguinte ao da competência.
 */
export function vencimentoDaFatura(
  competencia: { ano: number; mes: number },
  diaFechamento: number | null | undefined,
  diaVencimento: number | null | undefined,
): Date {
  const fechamento = diaValidoOuFimDoMes(diaFechamento, competencia.ano, competencia.mes);
  const vencimento = diaVencimento ?? 10;

  let ano = competencia.ano;
  let mes = competencia.mes;
  if (vencimento <= fechamento) {
    const total = mes + 1;
    ano += Math.floor(total / 12);
    mes = total % 12;
  }
  return new Date(ano, mes, Math.min(vencimento, diaNoMes(ano, mes)));
}

/** Data em que a compra vira dinheiro saindo do caixa: o vencimento da fatura. */
export function vencimentoDaCompraNoCartao(
  dataCompra: Date,
  diaFechamento: number | null | undefined,
  diaVencimento: number | null | undefined,
): Date {
  return vencimentoDaFatura(
    competenciaDaCompra(dataCompra, diaFechamento),
    diaFechamento,
    diaVencimento,
  );
}
