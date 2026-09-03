import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import NovaTransacaoModal from '../components/NovaTransacaoModal';
import ContasFixasScreen from '../../app/(app)/more/contas-fixas';
import cartaoService from '../services/cartaoService';

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
    criar: jest.fn().mockResolvedValue({ id: 1 }),
    atualizar: jest.fn(),
    listar: jest.fn().mockResolvedValue({ content: [] }),
    listarFalhasPendentes: jest.fn().mockResolvedValue([]),
    listarSugestoes: jest.fn().mockResolvedValue([]),
    marcarComoPaga: jest.fn(),
    pularMes: jest.fn(),
  },
}));

jest.mock('../services/transacaoService', () => ({
  transacaoService: {
    criar: jest.fn().mockResolvedValue({ id: 1 }),
    sugerirCategoria: jest.fn().mockResolvedValue(null),
  },
}));

jest.mock('../services/categoriaService', () => ({
  categoriaService: {
    listar: jest.fn().mockResolvedValue([{ id: 7, nome: 'Lazer', icone: '🎬' }]),
    criar: jest.fn(),
  },
}));

jest.mock('../services/contaFinanceiraService', () => ({
  __esModule: true,
  default: {
    listarParaCaixa: jest.fn().mockResolvedValue([
      { id: 3, nome: 'Conta corrente', saldo: 1000, principal: true },
    ]),
    listarTodas: jest.fn().mockResolvedValue([]),
  },
  contaPodeMovimentarCaixa: () => true,
  contaGerenciada: () => false,
}));

// A API devolve uma Page; listarTodos() é quem desembrulha para array.
const PAGE = { content: [{ id: 9, nome: 'Cartão Nubank', diaFechamento: 10, diaVencimento: 20 }] };

jest.mock('../services/cartaoService', () => ({
  __esModule: true,
  default: {
    listar: jest.fn(),
    listarTodos: jest.fn(),
    criar: jest.fn(),
  },
}));

const cartoes = cartaoService as unknown as { listar: jest.Mock; listarTodos: jest.Mock };

/**
 * A chave ['cartoes'] é compartilhada entre a tela de Recorrências e o modal de Nova
 * Transação, e invalidada por Carteira e Fatura. Quem escreve no cache primeiro define
 * o formato para todos os leitores.
 *
 * Já quebrou em produção: o modal gravava a Page de `listar()` e a tela de Recorrências
 * lia esperando o array de `listarTodos()`, então `cartoes.find` era `undefined` e o app
 * fechava com "undefined is not a function" ao abrir Recorrências depois de ter aberto o
 * modal. Crash só no app — o teste de cada tela isolada passava, porque cada uma
 * populava o próprio cache.
 */
describe('cache compartilhado da chave [cartoes]', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    cartoes.listar.mockResolvedValue(PAGE);
    cartoes.listarTodos.mockResolvedValue(PAGE.content);
  });

  it('as duas telas usam o mesmo queryFn para a mesma chave', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={client}>
        <NovaTransacaoModal visible onClose={jest.fn()} />
      </QueryClientProvider>,
    );
    await waitFor(() => expect(cartoes.listarTodos).toHaveBeenCalled());

    expect(cartoes.listar).not.toHaveBeenCalled();
    expect(Array.isArray(client.getQueryData(['cartoes']))).toBe(true);
  });

  it('a tela de Recorrências lê o cache escrito pelo modal sem quebrar', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    // o modal abre primeiro e popula o cache
    const modal = render(
      <QueryClientProvider client={client}>
        <NovaTransacaoModal visible onClose={jest.fn()} />
      </QueryClientProvider>,
    );
    await waitFor(() => expect(client.getQueryData(['cartoes'])).toBeDefined());
    modal.unmount();

    // e a tela de Recorrências monta em cima do cache já preenchido
    render(
      <QueryClientProvider client={client}>
        <ContasFixasScreen />
      </QueryClientProvider>,
    );

    expect(await screen.findByText('Recorrências')).toBeTruthy();
  });
});
