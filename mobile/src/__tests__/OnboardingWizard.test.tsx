import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import OnboardingScreen from '../../app/onboarding';
import { onboardingService } from '../services/onboardingService';
import * as rascunho from '../store/onboardingRascunho';

jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));

const mockReplace = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: mockReplace, push: jest.fn(), back: jest.fn() }),
}));

const mockUpdateUsuario = jest.fn().mockResolvedValue(undefined);
const mockLogout = jest.fn().mockResolvedValue(undefined);
jest.mock('../context/AuthContext', () => ({
  useAuth: () => ({ updateUsuario: mockUpdateUsuario, logout: mockLogout }),
}));

jest.mock('../context/TemaContext', () => ({ useTemaOpcional: () => undefined }));

jest.mock('../services/onboardingService', () => ({
  onboardingService: { finalizar: jest.fn(), status: jest.fn() },
}));

jest.mock('../store/onboardingRascunho', () => ({
  lerRascunho: jest.fn().mockResolvedValue(null),
  salvarRascunho: jest.fn().mockResolvedValue(undefined),
  limparRascunho: jest.fn().mockResolvedValue(undefined),
}));

const usuarioCompleto = { id: 1, nome: 'Ana', email: 'ana@x.com', onboardingCompleto: true };

const renderizar = async () => {
  render(<OnboardingScreen />);
  // deixa o efeito que lê o rascunho terminar antes de qualquer interação
  await act(async () => {});
};

const continuar = () => fireEvent.press(screen.getByTestId('onboarding-continuar'));
const pular = () => fireEvent.press(screen.getByTestId('onboarding-pular'));

beforeEach(() => {
  jest.clearAllMocks();
  (rascunho.lerRascunho as jest.Mock).mockResolvedValue(null);
});

describe('onboarding em etapas', () => {
  it('envia só a conta principal quando o usuário pula todo o resto', async () => {
    (onboardingService.finalizar as jest.Mock).mockResolvedValue(usuarioCompleto);
    await renderizar();

    fireEvent.changeText(screen.getByTestId('onboarding-account-balance'), '100000');
    continuar();                       // conta -> renda
    pular();                           // renda
    pular();                           // categorias
    pular();                           // cartão
    pular();                           // meta

    expect(screen.getByText('Tudo pronto?')).toBeTruthy();
    fireEvent.press(screen.getByTestId('onboarding-concluir'));

    await waitFor(() => expect(onboardingService.finalizar).toHaveBeenCalledTimes(1));
    expect(onboardingService.finalizar).toHaveBeenCalledWith({
      carteira: { nome: 'Conta Principal', subtipo: 'CORRENTE', saldo: 1000 },
    });
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/(app)/'));
    expect(rascunho.limparRascunho).toHaveBeenCalled();
  });

  it('monta um único payload com renda, categorias, cartão e meta', async () => {
    (onboardingService.finalizar as jest.Mock).mockResolvedValue(usuarioCompleto);
    await renderizar();

    fireEvent.changeText(screen.getByTestId('onboarding-account-name'), 'Nubank');
    fireEvent.changeText(screen.getByTestId('onboarding-account-balance'), '25000');
    continuar();

    fireEvent.changeText(screen.getByTestId('onboarding-income-value'), '450000');
    fireEvent.changeText(screen.getByTestId('onboarding-income-day'), '5');
    continuar();

    // categorias vêm todas marcadas; desmarca uma para conferir a seleção
    fireEvent.press(screen.getByLabelText('Vestuário'));
    continuar();

    fireEvent.changeText(screen.getByTestId('onboarding-card-name'), 'Roxinho');
    fireEvent.changeText(screen.getByTestId('onboarding-card-limit'), '300000');
    fireEvent.changeText(screen.getByTestId('onboarding-card-closing'), '20');
    fireEvent.changeText(screen.getByTestId('onboarding-card-due'), '27');
    continuar();

    fireEvent.changeText(screen.getByTestId('onboarding-goal-name'), 'Reserva');
    fireEvent.changeText(screen.getByTestId('onboarding-goal-value'), '1000000');
    continuar();

    fireEvent.press(screen.getByTestId('onboarding-concluir'));
    await waitFor(() => expect(onboardingService.finalizar).toHaveBeenCalledTimes(1));

    const payload = (onboardingService.finalizar as jest.Mock).mock.calls[0][0];
    expect(payload.carteira).toEqual({ nome: 'Nubank', subtipo: 'CORRENTE', saldo: 250 });
    expect(payload.renda).toEqual({ nome: 'Salário', valor: 4500, diaVencimento: 5 });
    expect(payload.cartao).toEqual({ nome: 'Roxinho', limiteTotal: 3000, diaFechamento: 20, diaVencimento: 27 });
    expect(payload.meta).toEqual({ nome: 'Reserva', valorTotal: 10000, dataLimite: undefined });
    expect(payload.categorias).toHaveLength(8);
    expect(payload.categorias.map((c: { nome: string }) => c.nome)).not.toContain('Vestuário');
  });

  it('não avança da conta principal com saldo inválido', async () => {
    await renderizar();
    fireEvent.changeText(screen.getByTestId('onboarding-account-name'), 'A');
    continuar();

    expect(screen.getByText('Informe o nome da conta (mínimo 2 caracteres).')).toBeTruthy();
    expect(screen.getByText('Sua conta principal')).toBeTruthy();
  });

  it('leva o erro de validação do backend para o passo dono do campo', async () => {
    (onboardingService.finalizar as jest.Mock).mockRejectedValue({
      userMessage: 'Dados inválidos: Nome deve ter no máximo 100 caracteres',
      status: 400,
      codigo: 'VALIDATION_ERROR',
      campos: { 'carteira.nome': 'Nome deve ter no máximo 100 caracteres' },
    });
    await renderizar();

    continuar(); pular(); pular(); pular(); pular();
    fireEvent.press(screen.getByTestId('onboarding-concluir'));

    await waitFor(() => expect(screen.getByText('Sua conta principal')).toBeTruthy());
    expect(screen.getByText('Nome deve ter no máximo 100 caracteres')).toBeTruthy();
  });

  it('oferece voltar ao login quando a sessão expira no envio', async () => {
    (onboardingService.finalizar as jest.Mock).mockRejectedValue({
      userMessage: 'Sessão expirada. Faça login novamente.',
      status: 401,
    });
    await renderizar();

    continuar(); pular(); pular(); pular(); pular();
    fireEvent.press(screen.getByTestId('onboarding-concluir'));

    await waitFor(() => expect(screen.getByText('Entrar de novo')).toBeTruthy());
    fireEvent.press(screen.getByText('Entrar de novo'));
    await waitFor(() => expect(mockLogout).toHaveBeenCalled());
    expect(mockReplace).toHaveBeenCalledWith('/(auth)/login');
  });

  it('restaura o rascunho de quem fechou o app no meio', async () => {
    (rascunho.lerRascunho as jest.Mock).mockResolvedValue({
      passo: 'cartao',
      conta: { nome: 'Caixa', tipo: 'DINHEIRO', saldo: '50,00' },
      categorias: ['Alimentação'],
      cartao: { nome: 'Inter', limite: '1.000,00', fechamento: '10', vencimento: '17' },
    });
    await renderizar();

    expect(screen.getByText('Cartão de crédito')).toBeTruthy();
    expect(screen.getByTestId('onboarding-card-name').props.value).toBe('Inter');
  });
});
