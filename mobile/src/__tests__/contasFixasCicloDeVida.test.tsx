import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ContasFixasScreen from '../../app/(app)/more/contas-fixas';
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
    deletar: jest.fn().mockResolvedValue(undefined),
    reativar: jest.fn().mockResolvedValue(undefined),
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

const servico = contaFixaService as unknown as {
  listar: jest.Mock;
  deletar: jest.Mock;
  reativar: jest.Mock;
};

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
  categoria: { id: 4, nome: 'Moradia', icone: 'moradia', cor: '#8B5CF6' },
  ...over,
} as ContaFixa);

const NETFLIX = contaFixa({
  id: 7,
  nome: 'Netflix',
  valorPlanejado: 44.9,
  cartao: { id: 9, nome: 'Itaú' },
});

const CANCELADA = contaFixa({ id: 7, nome: 'Netflix', ativo: false, cartao: { id: 9, nome: 'Itaú' } });

// avancarOcorrencia encerra uma conta de um mês só com ativo=false + PAGO: veio ao fim do
// ciclo, não foi cancelada. Cai na mesma listagem e precisa ser distinguida na tela.
const CONCLUIDA = contaFixa({
  id: 8, nome: 'IPTU 2026', ativo: false, recorrente: false, status: 'PAGO',
});

function renderTela(ativas: ContaFixa[], canceladas: ContaFixa[] = []) {
  servico.listar.mockImplementation((opcoes?: { ativo?: boolean }) =>
    Promise.resolve({ content: opcoes?.ativo === false ? canceladas : ativas }));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ContasFixasScreen />
    </QueryClientProvider>,
  );
}

/** Dispara o botão destrutivo do último Alert.alert, como o usuário faria. */
function confirmarNoAlert() {
  const botoes = (Alert.alert as unknown as jest.Mock).mock.calls.at(-1)?.[2];
  botoes.find((b: { style?: string }) => b.style === 'destructive').onPress();
}

/**
 * `contaFixaService.deletar` e `.reativar` existiam desde sempre e nenhuma tela os
 * chamava — código morto. Sem eles na UI não havia como parar de cobrar uma assinatura,
 * que era a queixa central do dono.
 */
describe('ciclo de vida da recorrência', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
  });

  it('cancela avisando que a fatura já lançada não muda', async () => {
    renderTela([NETFLIX]);
    await waitFor(() => expect(screen.getByText('Netflix')).toBeTruthy());

    fireEvent.press(screen.getByLabelText('Cancelar Netflix'));

    const [titulo, corpo] = (Alert.alert as unknown as jest.Mock).mock.calls.at(-1)!;
    expect(titulo).toBe('Cancelar assinatura?');
    // A promessa que o dono precisa ler antes de confirmar: cancelar não estorna.
    expect(corpo).toContain('já entraram na fatura continuam');

    confirmarNoAlert();
    await waitFor(() => expect(servico.deletar).toHaveBeenCalledWith(7));
  });

  it('só busca as canceladas quando a aba é aberta', async () => {
    renderTela([NETFLIX], [CANCELADA]);
    await waitFor(() => expect(screen.getByText('Netflix')).toBeTruthy());
    expect(servico.listar).not.toHaveBeenCalledWith({ ativo: false });

    fireEvent.press(screen.getByText('Canceladas'));

    await waitFor(() => expect(servico.listar).toHaveBeenCalledWith({ ativo: false }));
    await waitFor(() => expect(screen.getByText('Cancelada')).toBeTruthy());
  });

  it('reativa a partir da aba de canceladas', async () => {
    renderTela([], [CANCELADA]);
    fireEvent.press(screen.getByText('Canceladas'));
    await waitFor(() => expect(screen.getByLabelText('Reativar Netflix')).toBeTruthy());

    fireEvent.press(screen.getByLabelText('Reativar Netflix'));

    await waitFor(() => expect(servico.reativar).toHaveBeenCalledWith(7));
  });

  it('não oferece Reativar para uma conta que chegou ao fim do ciclo', async () => {
    renderTela([], [CONCLUIDA]);
    fireEvent.press(screen.getByText('Canceladas'));
    await waitFor(() => expect(screen.getByText('IPTU 2026')).toBeTruthy());

    expect(screen.getByText('Concluída')).toBeTruthy();
    expect(screen.queryByLabelText('Reativar IPTU 2026')).toBeNull();
  });

  it('a aba Assinaturas mostra só o que cai na fatura de um cartão', async () => {
    renderTela([NETFLIX, contaFixa({ id: 1, nome: 'Aluguel' })]);
    await waitFor(() => expect(screen.getByText('Aluguel')).toBeTruthy());

    fireEvent.press(screen.getByText('Assinaturas'));

    expect(screen.getByText('Netflix')).toBeTruthy();
    expect(screen.queryByText('Aluguel')).toBeNull();
  });

  it('diz que não deu para carregar em vez de anunciar "nada cancelado"', async () => {
    servico.listar.mockImplementation((opcoes?: { ativo?: boolean }) =>
      opcoes?.ativo === false
        ? Promise.reject(new Error('offline'))
        : Promise.resolve({ content: [NETFLIX] }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <ContasFixasScreen />
      </QueryClientProvider>,
    );
    await waitFor(() => expect(screen.getByText('Netflix')).toBeTruthy());

    fireEvent.press(screen.getByText('Canceladas'));

    // "Nada cancelado" afirmaria que não existe cancelada nenhuma — e a busca nem chegou
    // a acontecer. Não conseguir perguntar é diferente de não ter resposta.
    await waitFor(() =>
      expect(screen.getByText('Não deu para carregar suas recorrências')).toBeTruthy());
    expect(screen.queryByText('Nada cancelado')).toBeNull();
  });

  it('mantém o resumo nas ativas quando a aba é Canceladas', async () => {
    renderTela([NETFLIX], [CANCELADA]);
    await waitFor(() => expect(screen.getByText(/a receber/)).toBeTruthy());
    const resumo = screen.getByText(/a receber/).props.children;

    fireEvent.press(screen.getByText('Canceladas'));
    await waitFor(() => expect(servico.listar).toHaveBeenCalledWith({ ativo: false }));

    // Somar a lista exibida faria a aba Canceladas anunciar um total que não vai acontecer.
    expect(screen.getByText(/a receber/).props.children).toEqual(resumo);
  });
});
