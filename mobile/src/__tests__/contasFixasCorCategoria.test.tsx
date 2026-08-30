import React from 'react';
import { StyleSheet, View } from 'react-native';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ContasFixasScreen from '../../app/(app)/more/contas-fixas';
import IconTile from '../components/ui/IconTile';
import { contaFixaService } from '../services/contaFixaService';
import type { ContaFixa } from '../types';

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
    listarFalhasPendentes: jest.fn().mockResolvedValue([]),
    listarSugestoes: jest.fn().mockResolvedValue([]),
    criar: jest.fn(),
    atualizar: jest.fn(),
    confirmarSugestao: jest.fn(),
    descartarSugestao: jest.fn(),
    marcarComoPaga: jest.fn(),
    pularMes: jest.fn(),
  },
}));

jest.mock('../services/categoriaService', () => ({
  categoriaService: { listar: jest.fn().mockResolvedValue([]), criar: jest.fn() },
}));

jest.mock('../services/contaFinanceiraService', () => ({
  __esModule: true,
  default: { listarParaCaixa: jest.fn().mockResolvedValue([]), listarTodas: jest.fn().mockResolvedValue([]) },
  contaPodeMovimentarCaixa: () => true,
  contaGerenciada: () => false,
}));

jest.mock('../services/cartaoService', () => ({
  __esModule: true,
  default: {
    listarTodos: jest.fn().mockResolvedValue([]),
    listar: jest.fn().mockResolvedValue({ content: [] }),
    criar: jest.fn(),
  },
}));

const ROXO = '#8B5CF6';

const contaFixa = (over: Partial<ContaFixa> = {}): ContaFixa => ({
  id: 1,
  nome: 'Aluguel',
  valorPlanejado: 1800,
  diaVencimento: 10,
  status: 'PENDENTE',
  recorrente: true,
  ativo: true,
  tipo: 'SAIDA',
  execucaoAutomatica: false,
  categoria: { id: 4, nome: 'Moradia', icone: 'moradia', cor: ROXO },
  ...over,
} as ContaFixa);

const listar = (contaFixaService as unknown as { listar: jest.Mock }).listar;

async function renderLista(item: ContaFixa) {
  listar.mockResolvedValue({ content: [item] });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ContasFixasScreen />
    </QueryClientProvider>,
  );
  await waitFor(() => expect(screen.getByText('Aluguel')).toBeTruthy());
  // O primeiro IconTile da árvore é o do card da lista; o do modal de pagar só
  // monta depois que uma conta é selecionada.
  return screen.UNSAFE_getAllByType(IconTile)[0];
}

/**
 * A lista de recorrências pintava o tile pelo tom semântico do status e ignorava
 * a cor da categoria — contra o contrato do próprio IconTile ("entidade com cor
 * própria não usa a paleta semântica") e contra o detalhe da mesma tela.
 */
describe('cor da categoria no card da lista de recorrências', () => {
  beforeEach(() => jest.clearAllMocks());

  it('passa a cor da categoria para o IconTile, não o tom por status', async () => {
    const tile = await renderLista(contaFixa());
    expect(tile.props.cor).toBe(ROXO);
  });

  it('pinta o tile com a cor da categoria a 12,5% e tinge o emoji nela', async () => {
    await renderLista(contaFixa());
    const emoji = screen.getByText('🏠');
    expect(emoji.props.style.color).toBe(ROXO);
    // O fundo é a mesma cor com alfa 20 (12,5%), como no resto do app.
    const quadrado = screen.UNSAFE_getAllByType(IconTile)[0].findByType(View);
    expect(StyleSheet.flatten(quadrado.props.style)).toEqual(
      expect.objectContaining({ backgroundColor: `${ROXO}20` }),
    );
  });

  it('a cor vence mesmo em ATRASADO, e o status segue no Badge ao lado', async () => {
    const tile = await renderLista(contaFixa({ status: 'ATRASADO' }));
    expect(tile.props.cor).toBe(ROXO);
    expect(tile.props.tone).toBe('danger');
    expect(screen.getByText('Atrasado')).toBeTruthy();
  });

  it('sem cor de categoria, continua no tom por status', async () => {
    const tile = await renderLista(
      contaFixa({ status: 'ATRASADO', categoria: { id: 4, nome: 'Moradia', icone: 'moradia' } as ContaFixa['categoria'] }),
    );
    expect(tile.props.cor).toBeUndefined();
    expect(tile.props.tone).toBe('danger');
  });
});
