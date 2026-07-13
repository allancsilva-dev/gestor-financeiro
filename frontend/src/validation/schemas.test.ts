import { describe, expect, it } from 'vitest';
import {
  contaSchema,
  faturaPagamentoSchema,
  isoDateSchema,
  metaSchema,
  movimentacaoAtivoSchema,
  orcamentoSchema,
  transacaoSchema,
} from './schemas';

describe('schemas financeiros', () => {
  it('normaliza números e aceita uma transação válida', () => {
    const result = transacaoSchema.parse({
      descricao: 'Mercado', valor: '10.50', tipo: 'SAIDA', data: '2026-07-13',
      categoriaId: '2', contaId: '3', parcelado: false, totalParcelas: '',
    });
    expect(result.valor).toBe(10.5);
    expect(result.categoriaId).toBe(2);
  });

  it('rejeita valores inválidos e parcelamento incompleto', () => {
    expect(transacaoSchema.safeParse({ descricao: 'x', valor: 'NaN', tipo: 'SAIDA', data: '2026-02-30', categoriaId: '', contaId: '', parcelado: true }).success).toBe(false);
    expect(transacaoSchema.safeParse({ descricao: 'x', valor: -1, tipo: 'SAIDA', data: '2026-07-13', categoriaId: 1, contaId: 1, parcelado: true, totalParcelas: 49 }).success).toBe(false);
  });

  it('valida calendário real', () => {
    expect(isoDateSchema.safeParse('2024-02-29').success).toBe(true);
    expect(isoDateSchema.safeParse('2025-02-29').success).toBe(false);
  });

  it('valida dias e valores de cartão e meta', () => {
    expect(contaSchema.safeParse({ nome: 'Visa', tipo: 'CREDITO', limiteTotal: 0, diaFechamento: 0, diaVencimento: 32, cor: '#123456' }).success).toBe(false);
    expect(metaSchema.safeParse({ nome: 'Casa', valorTotal: 10, valorMensal: -1, cor: '#123456', dataPrevista: '2026-02-30' }).success).toBe(false);
  });

  it('valida quantidade, preço, orçamento e limite de fatura', () => {
    expect(movimentacaoAtivoSchema.safeParse({ tipo: 'COMPRA', data: '2026-07-13', quantidade: 0, precoUnitario: 10 }).success).toBe(false);
    expect(orcamentoSchema.safeParse({ mes: 7, ano: 2026, categorias: [] }).success).toBe(false);
    expect(faturaPagamentoSchema(100).safeParse({ valor: 101, carteiraId: 1 }).success).toBe(false);
  });
});
