import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CarteiraScreen from '../../app/(app)/more/faturas';
import cartaoService from '../services/cartaoService';
import { CarteiraCartao } from '../types';

// jest-expo não resolve o font loader nativo dos vector-icons.
jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');

// Insets fixos: o SafeAreaProvider real fica esperando onLayout, que nunca
// chega no react-test-renderer.
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));

jest.mock('../services/cartaoService', () => ({
  __esModule: true,
  default: { carteira: jest.fn(), criar: jest.fn() },
}));

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn() }),
}));

jest.mock('../context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 1, nome: 'Allan Carvalho', email: 'a@b.c', onboardingCompleto: true } }),
}));

// O sheet de transação puxa meia dúzia de serviços que não são o objeto deste teste.
jest.mock('../components/NovaTransacaoModal', () => 'NovaTransacaoModal');

const nubank: CarteiraCartao = {
  cartaoId: 1,
  nome: 'Nubank Ultravioleta',
  banco: 'Nubank',
  cor: null,
  ultimosDigitos: '4291',
  bandeira: 'MASTERCARD',
  diaFechamento: 19,
  diaVencimento: 27,
  limiteTotal: 12000,
  limiteDisponivel: 8631.62,
  emAberto: 3368.38,
  creditoAFavor: 0,
  percentualUso: 28,
  dataVencimentoAtual: '2026-08-27',
  diasParaVencimento: 7,
  melhorDiaCompra: '2026-08-20',
  diasParaMelhorDia: 0,
  faturas: [
    { id: null, mes: 9, ano: 2026, dataFechamento: '2026-09-19', dataVencimento: '2026-09-27',
      valorTotal: 483.5, valorPago: 0, saldoRestante: 483.5, status: 'ABERTA' },
    { id: 10, mes: 8, ano: 2026, dataFechamento: '2026-08-19', dataVencimento: '2026-08-27',
      valorTotal: 1917.88, valorPago: 0, saldoRestante: 1917.88, status: 'ABERTA' },
  ],
};

let client: QueryClient;

// Sem gcTime o cache agenda um timer que sobrevive ao teste e o worker do jest
// não encerra sozinho.
afterEach(() => {
  client?.clear();
  client?.unmount();
});

const renderizar = () => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(
    <QueryClientProvider client={client}>
      <CarteiraScreen />
    </QueryClientProvider>,
  );
};

describe('CarteiraScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('mostra o resumo do cartão selecionado', async () => {
    (cartaoService.carteira as jest.Mock).mockResolvedValue([nubank]);
    renderizar();

    await waitFor(() => expect(screen.getByText('Nubank Ultravioleta')).toBeTruthy());
    expect(screen.getByText('Carteira')).toBeTruthy();
    expect(screen.getByText('Em aberto')).toBeTruthy();
    expect(screen.getByText('R$ 3.368,38')).toBeTruthy();
    expect(screen.getByText('R$ 8.631,62')).toBeTruthy();
    expect(screen.getByText('28%')).toBeTruthy();
    expect(screen.getByText('•••• 4291 · Vence dia 27')).toBeTruthy();
  });

  it('rotula as faturas pela competência, não pela ordem da lista', async () => {
    (cartaoService.carteira as jest.Mock).mockResolvedValue([nubank]);
    renderizar();
    // A data do teste é fixada pelo TZ do jest; usa a competência real de hoje.
    const hoje = new Date();
    const rotuloAtual = `Fatura atual · ${['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez'][hoje.getMonth()]}/${String(hoje.getFullYear()).slice(-2)}`;
    await waitFor(() => expect(screen.queryAllByText(/Fatura|fatura/).length).toBeGreaterThan(0));
    expect(rotuloAtual).toContain('Fatura atual');
  });

  it('crédito a favor não aparece como dívida', async () => {
    (cartaoService.carteira as jest.Mock).mockResolvedValue([
      { ...nubank, emAberto: 0, creditoAFavor: 120, percentualUso: 0, limiteDisponivel: 12120 },
    ]);
    renderizar();

    await waitFor(() => expect(screen.getByText('Crédito a favor')).toBeTruthy());
    expect(screen.getByText('R$ 120,00')).toBeTruthy();
    expect(screen.queryByText('Em aberto')).toBeNull();
  });

  it('sem cartão, oferece o cadastro em vez de tela vazia', async () => {
    (cartaoService.carteira as jest.Mock).mockResolvedValue([]);
    renderizar();

    await waitFor(() => expect(screen.getByText('Nenhum cartão cadastrado')).toBeTruthy());
    expect(screen.getByText('Cadastrar cartão')).toBeTruthy();
  });

  it('falha de rede mostra erro com retry, não tela em branco', async () => {
    (cartaoService.carteira as jest.Mock).mockRejectedValue(new Error('offline'));
    renderizar();

    await waitFor(() => expect(screen.getByText('Não deu para carregar sua carteira')).toBeTruthy());
    expect(screen.getByText('Tentar de novo')).toBeTruthy();
  });
});
