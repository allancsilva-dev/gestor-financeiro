import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import RegrasCategoriaScreen from '../../app/(app)/more/regras-categoria';
import regraCategoriaService from '../services/regraCategoriaService';
import { categoriaService } from '../services/categoriaService';
import { RegraCategoria } from '../types';

jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
}));

jest.mock('../services/regraCategoriaService', () => ({
  __esModule: true,
  default: { listar: jest.fn(), criar: jest.fn(), remover: jest.fn() },
}));

jest.mock('../services/categoriaService', () => ({
  categoriaService: { listar: jest.fn() },
}));

const servico = regraCategoriaService as unknown as {
  listar: jest.Mock; criar: jest.Mock; remover: jest.Mock;
};
const categorias = categoriaService as unknown as { listar: jest.Mock };

const regra = (over: Partial<RegraCategoria> = {}): RegraCategoria => ({
  id: 1,
  padrao: 'mercado da esquina',
  tipoCasamento: 'CONTEM',
  tipoTransacao: null,
  categoriaId: 3,
  categoriaNome: 'Alimentação',
  categoriaIcone: '🍽️',
  prioridade: 100,
  ...over,
});

let client: QueryClient | null = null;

const renderizar = () => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(
    <QueryClientProvider client={client}>
      <RegrasCategoriaScreen />
    </QueryClientProvider>,
  );
};

beforeEach(() => {
  jest.clearAllMocks();
  servico.listar.mockResolvedValue([]);
  categorias.listar.mockResolvedValue([{ id: 3, nome: 'Alimentação', icone: '🍽️', cor: '#000000', ativo: true }]);
});

afterEach(() => {
  client?.clear();
  client = null;
});

describe('tela de regras de categorização', () => {
  it('explica a regra em português, não no vocabulário do backend', async () => {
    servico.listar.mockResolvedValue([regra()]);

    renderizar();

    expect(await screen.findByText(/Quando a descrição tiver “mercado da esquina”/)).toBeTruthy();
    // O chip de escolha e a linha da regra usam o mesmo rótulo: a lista é a segunda ocorrência.
    expect(screen.getAllByText(/🍽️ Alimentação/).length).toBeGreaterThan(1);
  });

  it('só libera criar depois de texto suficiente e categoria escolhida', async () => {
    renderizar();

    const botao = await screen.findByTestId('regra-criar');
    expect(botao.props.accessibilityState?.disabled).toBe(true);

    fireEvent.changeText(screen.getByLabelText('Texto que aparece na descrição'), 'uber');
    expect(screen.getByTestId('regra-criar').props.accessibilityState?.disabled).toBe(true);

    fireEvent.press(await screen.findByText('🍽️ Alimentação'));
    fireEvent.press(screen.getByTestId('regra-criar'));

    await waitFor(() => expect(servico.criar).toHaveBeenCalledWith({
      padrao: 'uber',
      categoriaId: 3,
      tipoCasamento: 'CONTEM',
    }));
  });

  it('apagar pede confirmação e explica que o passado não muda', async () => {
    const alerta = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    servico.listar.mockResolvedValue([regra()]);

    renderizar();
    fireEvent.press(await screen.findByText('Apagar'));

    expect(alerta).toHaveBeenCalled();
    expect(String(alerta.mock.calls[0][1])).toMatch(/já foi lançado não muda/);
    expect(servico.remover).not.toHaveBeenCalled();
    alerta.mockRestore();
  });

  it('erro do backend aparece na tela', async () => {
    servico.criar.mockRejectedValue(Object.assign(new Error('Limite de regras de categorização atingido'), {
      userMessage: 'Limite de regras de categorização atingido',
      status: 422,
    }));

    renderizar();
    fireEvent.changeText(await screen.findByLabelText('Texto que aparece na descrição'), 'uber');
    fireEvent.press(await screen.findByText('🍽️ Alimentação'));
    fireEvent.press(screen.getByTestId('regra-criar'));

    expect(await screen.findByText('Limite de regras de categorização atingido')).toBeTruthy();
  });
});
