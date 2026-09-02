import {
  competenciaDaCompra,
  vencimentoDaCompraNoCartao,
  vencimentoDaFatura,
} from '../domain/fatura';

/**
 * Espelho de exibição do FaturaDatas.java. Se as duas pontas divergirem, o app promete
 * uma fatura e o backend lança em outra.
 */
describe('em qual fatura a compra cai', () => {
  it('compra antes do fechamento fica na competência do próprio mês', () => {
    // fecha dia 20; compra dia 15 de setembro
    expect(competenciaDaCompra(new Date(2026, 8, 15), 20)).toEqual({ ano: 2026, mes: 8 });
  });

  it('compra depois do fechamento cai na competência seguinte', () => {
    // fecha dia 10; compra dia 15 de setembro
    expect(competenciaDaCompra(new Date(2026, 8, 15), 10)).toEqual({ ano: 2026, mes: 9 });
  });

  it('compra depois do fechamento em dezembro vira o ano', () => {
    expect(competenciaDaCompra(new Date(2026, 11, 15), 10)).toEqual({ ano: 2027, mes: 0 });
  });

  it('fechamento nulo vale como fim do mês', () => {
    expect(competenciaDaCompra(new Date(2026, 8, 30), null)).toEqual({ ano: 2026, mes: 8 });
  });

  it('vencimento anterior ao fechamento cai no mês seguinte ao da competência', () => {
    // fecha 20, vence 10 -> a fatura de setembro vence em outubro
    expect(vencimentoDaFatura({ ano: 2026, mes: 8 }, 20, 10)).toEqual(new Date(2026, 9, 10));
  });

  it('vencimento posterior ao fechamento vence na própria competência', () => {
    // fecha 10, vence 20 -> a fatura de setembro vence em setembro
    expect(vencimentoDaFatura({ ano: 2026, mes: 8 }, 10, 20)).toEqual(new Date(2026, 8, 20));
  });

  /** O caso da Netflix no Itaú: cobra dia 15, cartão fecha 10 e vence 20. */
  it('assinatura cobrada depois do fechamento só sai do caixa no mês seguinte', () => {
    expect(vencimentoDaCompraNoCartao(new Date(2026, 8, 15), 10, 20))
      .toEqual(new Date(2026, 9, 20));
  });

  it('a mesma assinatura antes do fechamento sai no próprio mês', () => {
    expect(vencimentoDaCompraNoCartao(new Date(2026, 8, 5), 10, 20))
      .toEqual(new Date(2026, 8, 20));
  });

  it('clampa o vencimento em mês curto', () => {
    expect(vencimentoDaFatura({ ano: 2026, mes: 1 }, 5, 31)).toEqual(new Date(2026, 1, 28));
  });
});
