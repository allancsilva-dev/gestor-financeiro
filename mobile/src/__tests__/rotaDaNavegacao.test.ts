jest.mock('expo-router', () => ({ useRouter: jest.fn() }));
jest.mock('../services/metricasService', () => ({ __esModule: true, default: {} }));

import { rotaDaNavegacao } from '../components/ComposicaoMetricaModal';
import { NavegacaoOrigem } from '../types';

const nav = (destino: NavegacaoOrigem['destino'], id: number | null = null,
             filtros: Record<string, string> | null = null): NavegacaoOrigem =>
  ({ destino, id, filtros });

describe('rotaDaNavegacao (PR-F3-08): destino do backend → rota do app', () => {
  it('mapeia cada destino suportado', () => {
    expect(rotaDaNavegacao(nav('EXTRATO_CONTA', 7))).toBe('/more/carteiras?contaId=7');
    expect(rotaDaNavegacao(nav('TRANSACAO', 42))).toBe('/transacoes?transacaoId=42');
    expect(rotaDaNavegacao(nav('FATURA', 3))).toBe('/more/faturas');
    expect(rotaDaNavegacao(nav('META', 9))).toBe('/metas');
    expect(rotaDaNavegacao(nav('INVESTIMENTO', 4))).toBe('/more/investimentos');
    expect(rotaDaNavegacao(nav('TRANSACOES', null, {
      inicio: '2026-07-01', fim: '2026-07-31', tipo: 'ENTRADA',
    }))).toBe('/transacoes?inicio=2026-07-01&fim=2026-07-31&tipo=ENTRADA');
  });

  it('sem destino valido nao inventa link', () => {
    expect(rotaDaNavegacao(nav('EXTRATO_CONTA', null))).toBeNull();
    expect(rotaDaNavegacao(nav('TRANSACAO', null))).toBeNull();
    expect(rotaDaNavegacao(nav('DESCONHECIDO' as any))).toBeNull();
  });
});
