import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ContasFixasScreen from '../../app/(app)/more/contas-fixas';
import { contaFixaService } from '../services/contaFixaService';
import { RecorrenciaSugestao } from '../types';

jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');
jest.mock('@expo/vector-icons/MaterialCommunityIcons', () => 'MaterialCommunityIcons');

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
}));

jest.mock('../services/contaFixaService', () => ({
  contaFixaService: {
    listar: jest.fn(),
    falhasPendentes: jest.fn(),
    listarSugestoes: jest.fn(),
    confirmarSugestao: jest.fn(),
    descartarSugestao: jest.fn(),
  },
}));

jest.mock('../services/categoriaService', () => ({
  categoriaService: { listar: jest.fn().mockResolvedValue([]) },
}));

jest.mock('../services/contaFinanceiraService', () => ({
  __esModule: true,
  default: { listarParaCaixa: jest.fn().mockResolvedValue([]), listarTodas: jest.fn().mockResolvedValue([]) },
  contaPodeMovimentarCaixa: () => true,
  contaGerenciada: () => false,
}));

const servico = contaFixaService as unknown as {
  listar: jest.Mock;
  falhasPendentes: jest.Mock;
  listarSugestoes: jest.Mock;
  confirmarSugestao: jest.Mock;
  descartarSugestao: jest.Mock;
};

const sugestao = (over: Partial<RecorrenciaSugestao> = {}): RecorrenciaSugestao => ({
  id: 9,
  descricao: 'Netflix.com',
  tipo: 'SAIDA',
  valorMedio: 39.9,
  diaTipico: 10,
  ocorrencias: 3,
  primeiraData: '2026-05-10',
  ultimaData: '2026-07-10',
  categoriaId: 2,
  categoriaNome: 'Assinaturas',
  ...over,
});

let client: QueryClient | null = null;

const renderizar = () => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(
    <QueryClientProvider client={client}>
      <ContasFixasScreen />
    </QueryClientProvider>,
  );
};

beforeEach(() => {
  jest.clearAllMocks();
  servico.listar.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 });
  servico.falhasPendentes.mockResolvedValue([]);
  servico.listarSugestoes.mockResolvedValue([]);
});

afterEach(() => {
  client?.clear();
  client = null;
});

describe('sugestões de recorrência', () => {
  it('mostra o padrão detectado com o que sustenta a sugestão', async () => {
    servico.listarSugestoes.mockResolvedValue([sugestao()]);

    renderizar();

    expect(await screen.findByText('Netflix.com')).toBeTruthy();
    expect(screen.getByText(/3 vezes · todo dia 10/)).toBeTruthy();
    // A promessa que o texto faz ao usuário: nada é lançado sem ele mandar.
    expect(screen.getByText(/continua sem\s+lançar sozinho/)).toBeTruthy();
  });

  it('confirmar transforma em recorrência', async () => {
    servico.listarSugestoes.mockResolvedValue([sugestao()]);
    servico.confirmarSugestao.mockResolvedValue({ id: 1 });

    renderizar();
    fireEvent.press(await screen.findByText('É recorrente'));

    await waitFor(() => expect(servico.confirmarSugestao).toHaveBeenCalledWith(9));
  });

  it('descartar não cria nada', async () => {
    servico.listarSugestoes.mockResolvedValue([sugestao()]);
    servico.descartarSugestao.mockResolvedValue(undefined);

    renderizar();
    fireEvent.press(await screen.findByText('Não é'));

    await waitFor(() => expect(servico.descartarSugestao).toHaveBeenCalledWith(9));
    expect(servico.confirmarSugestao).not.toHaveBeenCalled();
  });

  it('sem padrão detectado o bloco não aparece', async () => {
    renderizar();

    await waitFor(() => expect(servico.listarSugestoes).toHaveBeenCalled());
    expect(screen.queryByText('Isto se repete todo mês')).toBeNull();
  });
});
