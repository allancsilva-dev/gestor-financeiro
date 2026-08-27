import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import OrcamentoScreen from '../../app/(app)/more/orcamentos';
import { orcamentoService } from '../services/orcamentoService';
import { OrcamentoResponse, ApiErrorWithMessage } from '../types';

jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
}));

jest.mock('../services/orcamentoService', () => ({
  orcamentoService: {
    buscarAtual: jest.fn(),
    buscarPorMes: jest.fn(),
    criarOuAtualizar: jest.fn(),
  },
}));

jest.mock('../services/categoriaService', () => ({
  categoriaService: { listar: jest.fn().mockResolvedValue([]) },
}));

const erroApi = (status: number, mensagem: string): ApiErrorWithMessage =>
  Object.assign(new Error(mensagem), { userMessage: mensagem, status });

const orcamento = (over: Partial<OrcamentoResponse> = {}): OrcamentoResponse => ({
  id: 1,
  mes: new Date().getMonth() + 1,
  ano: new Date().getFullYear(),
  valorTotalPlanejado: 1000,
  valorTotalGasto: 250,
  categorias: [
    {
      id: 5,
      categoriaId: 5,
      categoriaNome: 'Alimentação',
      categoriaIcone: '🍽️',
      valorLimite: 1000,
      carryIn: 0,
      valorDisponivel: 1000,
      politicaRollover: 'NONE',
      valorGasto: 250,
      percentualGasto: 25,
    },
  ],
  ...over,
} as OrcamentoResponse);

let client: QueryClient;
afterEach(() => {
  client?.clear();
  client?.unmount();
});

const renderizar = () => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(
    <QueryClientProvider client={client}>
      <OrcamentoScreen />
    </QueryClientProvider>,
  );
};

describe('OrcamentoScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('mostra o consumo por categoria do mês corrente', async () => {
    (orcamentoService.buscarAtual as jest.Mock).mockResolvedValue(orcamento());
    renderizar();
    await waitFor(() => expect(screen.getByText('Alimentação')).toBeTruthy());
    expect(screen.getByText('Categorias')).toBeTruthy();
  });

  /**
   * O bug que este teste trava: o `queryFn` engolia qualquer erro do mês passado
   * com `.catch(() => null)` e a tela não lia `isError`. Falha de rede caía no
   * mesmo ramo de "sem orçamento", com o botão de criar por cima de um orçamento
   * que existe e não carregou — caminho direto para duplicar.
   */
  it('falha de rede vira erro com retry, não convite para criar de novo', async () => {
    (orcamentoService.buscarAtual as jest.Mock).mockRejectedValue(
      erroApi(500, 'Servidor indisponível'),
    );
    renderizar();

    await waitFor(() => expect(screen.getByText('Não deu para carregar o orçamento')).toBeTruthy());
    expect(screen.queryByText('Criar orçamento')).toBeNull();
    expect(screen.getByText('Tentar de novo')).toBeTruthy();
  });

  it('mês sem orçamento (404) continua sendo estado vazio, não erro', async () => {
    (orcamentoService.buscarAtual as jest.Mock).mockResolvedValue(
      orcamento({ categorias: [], valorTotalPlanejado: 0, valorTotalGasto: 0 }),
    );
    renderizar();

    await waitFor(() => expect(screen.getByText('Criar orçamento')).toBeTruthy());
    expect(screen.queryByText('Não deu para carregar o orçamento')).toBeNull();
  });

  it('mostra de onde veio o valor carregado do mês anterior', async () => {
    (orcamentoService.buscarAtual as jest.Mock).mockResolvedValue(orcamento({
      categorias: [{
        id: 5,
        categoriaId: 5,
        categoriaNome: 'Alimentação',
        categoriaIcone: '🍽️',
        categoriaCor: '#000000',
        valorLimite: 800,
        valorGasto: 900,
        percentualGasto: 95,
        carryIn: 150,
        valorDisponivel: 950,
        politicaRollover: 'SURPLUS_ONLY',
      }],
    }));

    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <OrcamentoScreen />
      </QueryClientProvider>,
    );

    // O gasto é medido contra o disponível, não contra o limite do mês.
    expect(await screen.findByText(/R\$\s?900,00 \/ R\$\s?950,00/)).toBeTruthy();
    expect(screen.getByText(/que sobraram/)).toBeTruthy();
  });
});
