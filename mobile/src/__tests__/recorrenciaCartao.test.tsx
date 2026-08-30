import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import NovaTransacaoModal from '../components/NovaTransacaoModal';
import { contaFixaService } from '../services/contaFixaService';
import { transacaoService } from '../services/transacaoService';
import { proximaCobranca, rotuloProximaCobranca } from '../domain/recorrencia';

jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');
jest.mock('@expo/vector-icons/MaterialCommunityIcons', () => 'MaterialCommunityIcons');

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));

jest.mock('../services/contaFixaService', () => ({
  contaFixaService: { criar: jest.fn().mockResolvedValue({ id: 1 }) },
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
  },
}));

jest.mock('../services/cartaoService', () => ({
  __esModule: true,
  default: {
    listarTodos: jest.fn().mockResolvedValue([{ id: 9, nome: 'Cartão Nubank' }]),
    listar: jest.fn().mockResolvedValue({ content: [{ id: 9, nome: 'Cartão Nubank' }] }),
    criar: jest.fn(),
  },
}));

const recorrencia = contaFixaService as unknown as { criar: jest.Mock };
const transacoes = transacaoService as unknown as { criar: jest.Mock };

function renderModal() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <NovaTransacaoModal visible onClose={jest.fn()} />
    </QueryClientProvider>,
  );
}

async function preencherSaidaNoCartao() {
  fireEvent.changeText(screen.getByTestId('transaction-value'), '6000');
  fireEvent.changeText(screen.getByTestId('transaction-description'), 'Netflix');
  fireEvent.changeText(screen.getByTestId('transaction-date'), '15/08/2026');
  await waitFor(() => expect(screen.getByText(/Lazer/)).toBeTruthy());
  fireEvent.press(screen.getByText(/Lazer/));
  fireEvent.press(screen.getByText('Cartão'));
  await waitFor(() => expect(screen.getByText('Cartão Nubank')).toBeTruthy());
  fireEvent.press(screen.getByText('Cartão Nubank'));
  fireEvent.press(screen.getByTestId('more-details-toggle'));
}

describe('assinatura no cartão pelo botão Nova', () => {
  beforeEach(() => jest.clearAllMocks());

  it('marcado "Repete todo mês", cria recorrência no cartão em vez de lançamento avulso', async () => {
    renderModal();
    await preencherSaidaNoCartao();

    fireEvent(screen.getByTestId('transaction-recurring'), 'valueChange', true);
    fireEvent.press(screen.getByText('Salvar'));

    await waitFor(() => expect(recorrencia.criar).toHaveBeenCalledTimes(1));
    expect(transacoes.criar).not.toHaveBeenCalled();
    expect(recorrencia.criar).toHaveBeenCalledWith(
      expect.objectContaining({
        descricao: 'Netflix',
        valor: 60,
        diaVencimento: 15,
        cartaoId: 9,
        tipo: 'SAIDA',
        recorrente: true,
        execucaoAutomatica: true,
      }),
    );
    // Assinatura não carrega destino de caixa junto
    expect(recorrencia.criar.mock.calls[0][0].carteiraId).toBeUndefined();
  });

  it('assinatura esconde o parcelamento: são coisas excludentes', async () => {
    renderModal();
    await preencherSaidaNoCartao();

    expect(screen.getByText('Parcelado')).toBeTruthy();
    fireEvent(screen.getByTestId('transaction-recurring'), 'valueChange', true);
    expect(screen.queryByText('Parcelado')).toBeNull();
  });

  it('mostra quando cai a primeira cobrança antes de salvar', async () => {
    renderModal();
    await preencherSaidaNoCartao();
    fireEvent(screen.getByTestId('transaction-recurring'), 'valueChange', true);

    expect(screen.getByText(`Primeira cobrança em ${rotuloProximaCobranca(15)}`)).toBeTruthy();
  });

  it('sem o switch, segue criando lançamento avulso', async () => {
    renderModal();
    await preencherSaidaNoCartao();
    fireEvent.press(screen.getByText('Salvar'));

    await waitFor(() => expect(transacoes.criar).toHaveBeenCalledTimes(1));
    expect(recorrencia.criar).not.toHaveBeenCalled();
  });
});

describe('proximaCobranca', () => {
  it('cai neste mês quando o dia ainda não passou', () => {
    expect(proximaCobranca(20, new Date(2026, 7, 5))).toEqual(new Date(2026, 7, 20));
  });

  it('vai para o mês seguinte quando o dia já passou', () => {
    expect(proximaCobranca(5, new Date(2026, 7, 20))).toEqual(new Date(2026, 8, 5));
  });

  it('ajusta o dia 31 a meses mais curtos', () => {
    expect(proximaCobranca(31, new Date(2026, 1, 10))).toEqual(new Date(2026, 1, 28));
  });

  it('atravessa a virada do ano', () => {
    expect(proximaCobranca(5, new Date(2026, 11, 20))).toEqual(new Date(2027, 0, 5));
  });

  it('cobra hoje quando o dia é hoje', () => {
    expect(proximaCobranca(10, new Date(2026, 7, 10))).toEqual(new Date(2026, 7, 10));
  });
});
