import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import NovaTransacaoModal from '../components/NovaTransacaoModal';
import { contaFixaService } from '../services/contaFixaService';
import { transacaoService } from '../services/transacaoService';

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

const ALERTA_LIMITE = {
  codigo: 'LIMITE_ESTOURADO',
  titulo: 'Limite do cartão estourado',
  mensagem: 'O cartão Cartão Nubank passou do limite. A cobrança foi lançada normalmente e entra na fatura.',
  destino: 'CARTAO',
  destinoId: 9,
};

function renderModal(onClose = jest.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <NovaTransacaoModal visible onClose={onClose} />
    </QueryClientProvider>,
  );
}

async function preencherSaidaNoCartao() {
  fireEvent.changeText(screen.getByTestId('transaction-value'), '15000');
  fireEvent.changeText(screen.getByTestId('transaction-description'), 'Notebook');
  fireEvent.changeText(screen.getByTestId('transaction-date'), '15/08/2026');
  await waitFor(() => expect(screen.getByText(/Lazer/)).toBeTruthy());
  fireEvent.press(screen.getByText(/Lazer/));
  fireEvent.press(screen.getByText('Cartão'));
  await waitFor(() => expect(screen.getByText('Cartão Nubank')).toBeTruthy());
  fireEvent.press(screen.getByText('Cartão Nubank'));
}

/**
 * BACKLOG-0125 — o backend avisa, nunca bloqueia. O app precisa mostrar o aviso sem
 * dar a entender que o lançamento falhou.
 */
describe('alerta de limite estourado', () => {
  let alertSpy: jest.SpyInstance;

  beforeEach(() => {
    jest.clearAllMocks();
    alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  });

  afterEach(() => alertSpy.mockRestore());

  it('mostra o aviso quando a compra no cartão estoura o limite', async () => {
    transacoes.criar.mockResolvedValueOnce({ id: 1, alertas: [ALERTA_LIMITE] });
    renderModal();
    await preencherSaidaNoCartao();

    fireEvent.press(screen.getByText('Salvar'));

    await waitFor(() => expect(alertSpy).toHaveBeenCalledWith(
      ALERTA_LIMITE.titulo, ALERTA_LIMITE.mensagem));
  });

  it('o lançamento é dado como salvo mesmo com aviso: avisar não é bloquear', async () => {
    transacoes.criar.mockResolvedValueOnce({ id: 1, alertas: [ALERTA_LIMITE] });
    const onClose = jest.fn();
    renderModal(onClose);
    await preencherSaidaNoCartao();

    fireEvent.press(screen.getByText('Salvar'));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(screen.queryByText(/Erro ao salvar/)).toBeNull();
  });

  it('sem alerta no corpo, nada é mostrado', async () => {
    renderModal();
    await preencherSaidaNoCartao();

    fireEvent.press(screen.getByText('Salvar'));

    await waitFor(() => expect(transacoes.criar).toHaveBeenCalled());
    expect(alertSpy).not.toHaveBeenCalled();
  });

  it('assinatura de cartão que estoura o limite também avisa', async () => {
    recorrencia.criar.mockResolvedValueOnce({ id: 1, alertas: [ALERTA_LIMITE] });
    renderModal();
    await preencherSaidaNoCartao();
    fireEvent.press(screen.getByTestId('more-details-toggle'));
    fireEvent(screen.getByTestId('transaction-recurring'), 'valueChange', true);

    fireEvent.press(screen.getByText('Salvar'));

    await waitFor(() => expect(recorrencia.criar).toHaveBeenCalled());
    expect(alertSpy).toHaveBeenCalledWith(ALERTA_LIMITE.titulo, ALERTA_LIMITE.mensagem);
  });
});
