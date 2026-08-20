import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import FaturaScreen from '../../app/(app)/more/fatura';
import faturaService from '../services/faturaService';
import { FaturaResponse } from '../types';

jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));

const params: Record<string, string | undefined> = {};
jest.mock('expo-router', () => ({
  useLocalSearchParams: () => params,
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
}));

jest.mock('../services/faturaService', () => ({
  __esModule: true,
  default: { buscarAtual: jest.fn(), buscarPorMes: jest.fn(), pagarFatura: jest.fn() },
}));

jest.mock('../services/contaFinanceiraService', () => ({
  __esModule: true,
  default: { listarParaCaixa: jest.fn().mockResolvedValue([]) },
}));

const hoje = new Date();
const MES_ATUAL = hoje.getMonth() + 1;
const ANO_ATUAL = hoje.getFullYear();

const fatura = (over: Partial<FaturaResponse> = {}): FaturaResponse => ({
  id: 10,
  cartaoId: 1,
  cartaoNome: 'Nubank Ultravioleta',
  mes: MES_ATUAL,
  ano: ANO_ATUAL,
  dataFechamento: '2026-08-19',
  dataVencimento: '2026-08-27',
  valorTotal: 1917.88,
  valorPago: 0,
  status: 'ABERTA',
  dataPagamento: null,
  lancamentos: [
    {
      transacaoId: 1,
      descricao: 'Mercado do mês',
      valor: 1917.88,
      data: '2026-08-10',
      categoriaId: 1,
      categoriaNome: 'Compras',
      categoriaCor: '#fb7185',
      categoriaIcone: '🛒',
      parcelaAtual: null,
      totalParcelas: null,
      tipo: 'COMPRA',
    },
  ],
  ...over,
});

let client: QueryClient;
afterEach(() => {
  client?.clear();
  client?.unmount();
});

const renderizar = () => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(
    <QueryClientProvider client={client}>
      <FaturaScreen />
    </QueryClientProvider>,
  );
};

const definirParams = (p: Record<string, string | undefined>) => {
  for (const k of Object.keys(params)) delete params[k];
  Object.assign(params, p);
};

describe('FaturaScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('lista as compras da competência', async () => {
    (faturaService.buscarAtual as jest.Mock).mockResolvedValue(fatura());
    definirParams({ cartaoId: '1', mes: String(MES_ATUAL), ano: String(ANO_ATUAL), nome: 'Nubank Ultravioleta' });
    renderizar();

    await waitFor(() => expect(screen.getByText('Mercado do mês')).toBeTruthy());
    // aparece duas vezes: total da fatura e o lançamento
    expect(screen.getAllByText('R$ 1.917,88')).toHaveLength(2);
    expect(screen.queryByText('Nenhuma compra nesta fatura ainda')).toBeNull();
  });

  it('usa /atual na competência corrente — o mês quem decide é o servidor', async () => {
    (faturaService.buscarAtual as jest.Mock).mockResolvedValue(fatura());
    definirParams({ cartaoId: '1', mes: String(MES_ATUAL), ano: String(ANO_ATUAL) });
    renderizar();

    await waitFor(() => expect(faturaService.buscarAtual).toHaveBeenCalledWith(1));
    expect(faturaService.buscarPorMes).not.toHaveBeenCalled();
  });

  it('usa /mes&ano numa competência passada', async () => {
    (faturaService.buscarPorMes as jest.Mock).mockResolvedValue(fatura({ mes: 7, ano: 2026 }));
    definirParams({ cartaoId: '1', mes: '7', ano: '2026' });
    renderizar();

    await waitFor(() => expect(faturaService.buscarPorMes).toHaveBeenCalledWith(1, 7, 2026));
    expect(faturaService.buscarAtual).not.toHaveBeenCalled();
  });

  it('competência sem compras diz isso, em vez de ficar zerada e muda', async () => {
    (faturaService.buscarAtual as jest.Mock).mockResolvedValue(
      fatura({ id: null as never, valorTotal: 0, lancamentos: [] }),
    );
    definirParams({ cartaoId: '1', mes: String(MES_ATUAL), ano: String(ANO_ATUAL) });
    renderizar();

    await waitFor(() => expect(screen.getByText('Nenhuma compra nesta fatura ainda')).toBeTruthy());
    // as datas continuam visíveis: a competência existe, só não teve compra
    expect(screen.getByText(/Fecha .* · Vence /)).toBeTruthy();
  });

  it('erro de rede vira erro com retry, não fatura zerada', async () => {
    (faturaService.buscarAtual as jest.Mock).mockRejectedValue(new Error('offline'));
    definirParams({ cartaoId: '1', mes: String(MES_ATUAL), ano: String(ANO_ATUAL) });
    renderizar();

    await waitFor(() => expect(screen.getByText('Não deu para carregar a fatura')).toBeTruthy());
    expect(screen.getByText('Tentar de novo')).toBeTruthy();
    expect(screen.queryByText('Nenhuma compra nesta fatura ainda')).toBeNull();
  });

  it('param inválido não vira fatura zerada nem request', async () => {
    definirParams({ cartaoId: 'abc', mes: undefined, ano: undefined });
    renderizar();

    await waitFor(() => expect(screen.getByText('Não foi possível abrir esta fatura')).toBeTruthy());
    expect(faturaService.buscarAtual).not.toHaveBeenCalled();
    expect(faturaService.buscarPorMes).not.toHaveBeenCalled();
  });
});
