import { describe, expect, it } from 'vitest';
import { rotaDaNavegacao } from './rotaDaNavegacao';
import type { NavegacaoOrigem } from '../services/metricasService';

const nav = (parcial: Partial<NavegacaoOrigem>): NavegacaoOrigem =>
  ({ destino: 'TRANSACOES', id: null, filtros: null, ...parcial } as NavegacaoOrigem);

describe('rotaDaNavegacao (PR-F3-12)', () => {
  it('conta → extrato em contas financeiras', () => {
    expect(rotaDaNavegacao(nav({ destino: 'EXTRATO_CONTA', id: 7 }))).toBe('/contas-financeiras?contaId=7');
  });

  it('parcela → transação focada', () => {
    expect(rotaDaNavegacao(nav({ destino: 'TRANSACAO', id: 42 }))).toBe('/transacoes?transacaoId=42');
  });

  it('fatura → Faturas', () => {
    expect(rotaDaNavegacao(nav({ destino: 'FATURA', id: 3 }))).toBe('/faturas');
  });

  it('meta → Metas', () => {
    expect(rotaDaNavegacao(nav({ destino: 'META', id: 5 }))).toBe('/metas');
  });

  it('posição → Investimentos', () => {
    expect(rotaDaNavegacao(nav({ destino: 'INVESTIMENTO', id: 9 }))).toBe('/investimentos');
  });

  it('extrato filtrável → Transações com filtros do backend', () => {
    expect(rotaDaNavegacao(nav({ destino: 'TRANSACOES', filtros: { inicio: '2026-07-01', fim: '2026-07-31', tipo: 'ENTRADA' } })))
      .toBe('/transacoes?inicio=2026-07-01&fim=2026-07-31&tipo=ENTRADA');
    expect(rotaDaNavegacao(nav({ destino: 'TRANSACOES', filtros: null }))).toBe('/transacoes');
  });

  it('destino sem ID obrigatório ou desconhecido → sem link (nunca inventa rota)', () => {
    expect(rotaDaNavegacao(nav({ destino: 'EXTRATO_CONTA', id: null }))).toBeNull();
    expect(rotaDaNavegacao(nav({ destino: 'TRANSACAO', id: null }))).toBeNull();
    expect(rotaDaNavegacao(nav({ destino: 'DESCONHECIDO' as never }))).toBeNull();
  });
});
