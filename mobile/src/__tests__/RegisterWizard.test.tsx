import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import Register from '../../app/(auth)/register';
import { authService } from '../services/authService';

jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));

const mockReplace = jest.fn();
const mockPush = jest.fn();
const mockBack = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: mockReplace, push: mockPush, back: mockBack }),
}));

const mockLogin = jest.fn();
jest.mock('../context/AuthContext', () => ({ useAuth: () => ({ login: mockLogin }) }));
jest.mock('../context/TemaContext', () => ({ useTemaOpcional: () => undefined }));

jest.mock('../services/authService', () => ({
  authService: { registrar: jest.fn(), login: jest.fn() },
}));

jest.mock('../store/ultimoEmail', () => ({
  salvarUltimoEmail: jest.fn().mockResolvedValue(undefined),
  lerUltimoEmail: jest.fn().mockResolvedValue(''),
}));

const continuar = () => fireEvent.press(screen.getByTestId('register-continuar'));

const preencherIdentidade = (email = 'Ana@Exemplo.com') => {
  fireEvent.changeText(screen.getByTestId('register-name'), 'Ana Souza');
  fireEvent.changeText(screen.getByTestId('register-email'), email);
};

const preencherSenha = (senha = 'senha123', confirmacao = senha) => {
  fireEvent.changeText(screen.getByTestId('register-password'), senha);
  fireEvent.changeText(screen.getByTestId('register-confirm-password'), confirmacao);
};

beforeEach(() => jest.clearAllMocks());

describe('criação de conta em passos', () => {
  it('cria a conta, entra e segue para o onboarding com e-mail normalizado', async () => {
    (authService.registrar as jest.Mock).mockResolvedValue(undefined);
    mockLogin.mockResolvedValue({ id: 1, nome: 'Ana', email: 'ana@exemplo.com', onboardingCompleto: false });

    render(<Register />);
    preencherIdentidade();
    continuar();
    preencherSenha();
    continuar();
    fireEvent.press(screen.getByLabelText('Aceito a política de privacidade'));
    fireEvent.press(screen.getByTestId('register-submit'));

    await waitFor(() => expect(authService.registrar).toHaveBeenCalledTimes(1));
    expect(authService.registrar).toHaveBeenCalledWith({
      nome: 'Ana Souza',
      email: 'ana@exemplo.com',
      password: 'senha123',
      confirmPassword: 'senha123',
      aceitaTermos: true,
    });
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/onboarding'));
  });

  it('mostra erro por campo em vez de um aviso único no topo', async () => {
    render(<Register />);
    fireEvent.changeText(screen.getByTestId('register-name'), 'A');
    fireEvent.changeText(screen.getByTestId('register-email'), 'sem-arroba');
    continuar();

    expect(screen.getByText('Informe seu nome (mínimo 2 caracteres).')).toBeTruthy();
    expect(screen.getByText('Informe um e-mail válido.')).toBeTruthy();
    expect(screen.queryByTestId('register-password')).toBeNull();
  });

  it('barra o avanço quando a confirmação de senha não confere', () => {
    render(<Register />);
    preencherIdentidade();
    continuar();
    preencherSenha('senha123', 'senha124');
    continuar();

    expect(screen.getByText('As senhas não coincidem.')).toBeTruthy();
    expect(screen.queryByTestId('register-submit')).toBeNull();
  });

  it('exige o aceite da política antes de enviar', () => {
    render(<Register />);
    preencherIdentidade();
    continuar();
    preencherSenha();
    continuar();
    fireEvent.press(screen.getByTestId('register-submit'));

    expect(screen.getByText('É preciso aceitar a política de privacidade para criar a conta.')).toBeTruthy();
    expect(authService.registrar).not.toHaveBeenCalled();
  });

  it('mostra "Email já cadastrado" e volta ao passo do e-mail', async () => {
    (authService.registrar as jest.Mock).mockRejectedValue({
      userMessage: 'Email já cadastrado!',
      status: 422,
      codigo: 'BUSINESS_ERROR',
      campos: { email: 'Email já cadastrado!' },
    });

    render(<Register />);
    preencherIdentidade();
    continuar();
    preencherSenha();
    continuar();
    fireEvent.press(screen.getByLabelText('Aceito a política de privacidade'));
    fireEvent.press(screen.getByTestId('register-submit'));

    await waitFor(() => expect(screen.getByTestId('register-email')).toBeTruthy());
    expect(screen.getAllByText('Email já cadastrado!').length).toBeGreaterThan(0);
  });

  it('não trava o botão quando o login pós-cadastro falha', async () => {
    (authService.registrar as jest.Mock).mockResolvedValue(undefined);
    mockLogin.mockRejectedValue({ userMessage: 'Sem conexão. Verifique sua internet.' });

    render(<Register />);
    preencherIdentidade();
    continuar();
    preencherSenha();
    continuar();
    fireEvent.press(screen.getByLabelText('Aceito a política de privacidade'));
    fireEvent.press(screen.getByTestId('register-submit'));

    await waitFor(() => expect(screen.getByText('Sem conexão. Verifique sua internet.')).toBeTruthy());
    expect(screen.getByTestId('register-submit').props.accessibilityState).toMatchObject({ busy: false });
  });
});
