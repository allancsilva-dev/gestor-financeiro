import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AlertasDeGasto from '../components/AlertasDeGasto';
import insightsService from '../services/insightsService';
import { dispensarAlerta, listarDispensados } from '../store/alertasDispensados';
import { InsightsResponse } from '../types';

jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');

jest.mock('../services/insightsService', () => ({
  __esModule: true,
  default: { buscar: jest.fn() },
}));

jest.mock('../store/alertasDispensados', () => {
  const real = jest.requireActual('../store/alertasDispensados');
  return {
    ...real,
    listarDispensados: jest.fn().mockResolvedValue([]),
    dispensarAlerta: jest.fn(),
  };
});

const servico = insightsService as unknown as { buscar: jest.Mock };
const dispensar = dispensarAlerta as jest.Mock;
const listar = listarDispensados as jest.Mock;

const insights = (over: Partial<InsightsResponse> = {}): InsightsResponse => ({
  gastoMesAtual: 2400,
  gastoMedioMensal: 1500,
  variacaoPercentual: 60,
  previsaoSaldoFinal: 800,
  categoriasAlerta: [
    { categoriaNome: 'Mercado', gastoAtual: 900, gastoMedio: 500, variacaoPercentual: 80, acimaMedia: true },
    { categoriaNome: 'Transporte', gastoAtual: 100, gastoMedio: 300, variacaoPercentual: -66, acimaMedia: false },
  ],
  recomendacoes: ['Mercado subiu 80% sobre a média dos últimos três meses'],
  resumo: 'Você gastou R$ 2.400,00 neste mês',
  ...over,
});

let client: QueryClient | null = null;

const renderizar = () => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(
    <QueryClientProvider client={client}>
      <AlertasDeGasto />
    </QueryClientProvider>,
  );
};

beforeEach(() => {
  jest.clearAllMocks();
  listar.mockResolvedValue([]);
});

afterEach(() => {
  client?.clear();
  client = null;
});

describe('alertas de gasto', () => {
  it('mostra só a categoria que estourou a média, com o quanto e o quanto era', async () => {
    servico.buscar.mockResolvedValue(insights());

    renderizar();

    expect(await screen.findByText('Mercado')).toBeTruthy();
    expect(screen.getByText(/neste mês · média/)).toBeTruthy();
    // Categoria abaixo da média não é alerta: economizar não precisa de aviso.
    expect(screen.queryByText('Transporte')).toBeNull();
  });

  it('avisa quando o mês fecha negativo no ritmo atual', async () => {
    servico.buscar.mockResolvedValue(insights({ previsaoSaldoFinal: -320.5 }));

    renderizar();

    expect(await screen.findByText('No ritmo atual, o mês fecha negativo')).toBeTruthy();
  });

  it('dispensar tira o alerta da tela e registra a preferência', async () => {
    servico.buscar.mockResolvedValue(insights());
    dispensar.mockResolvedValue(['categoria:mercado']);

    renderizar();
    fireEvent.press(await screen.findByText('Ok, entendi'));

    await waitFor(() => expect(dispensar).toHaveBeenCalledWith('categoria:mercado'));
    await waitFor(() => expect(screen.queryByText('Mercado')).toBeNull());
  });

  it('alerta já dispensado não volta no mesmo mês', async () => {
    servico.buscar.mockResolvedValue(insights());
    listar.mockResolvedValue(['categoria:mercado']);

    renderizar();

    expect(await screen.findByText('Nada fora do padrão')).toBeTruthy();
    expect(screen.queryByText('Mercado')).toBeNull();
  });

  it('falha do endpoint não derruba o relatório — o bloco some', async () => {
    servico.buscar.mockRejectedValue(new Error('rede'));

    const { toJSON } = renderizar();

    await waitFor(() => expect(toJSON()).toBeNull());
  });
});
