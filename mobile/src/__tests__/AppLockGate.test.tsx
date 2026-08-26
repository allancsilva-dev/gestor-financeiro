import React from 'react';
import { AppState } from 'react-native';
import { act, render, screen, waitFor } from '@testing-library/react-native';
import * as LocalAuthentication from 'expo-local-authentication';
import * as ScreenCapture from 'expo-screen-capture';
import Constants from 'expo-constants';
import AppLockGate from '../components/AppLockGate';
import { refreshAccessToken } from '../services/api';

const authState = {
  isAuthenticated: false,
  isLoading: true,
};

jest.mock('../context/AuthContext', () => ({
  useAuth: () => authState,
}));

jest.mock('../theme', () => ({
  useTheme: () => ({
    bg: '#fff',
    brandBg: '#eee',
    textPrimary: '#111',
    textSecondary: '#222',
    textMuted: '#777',
    card: '#fff',
    danger: '#f00',
    border: '#ddd',
    brand: '#00f',
    brandFg: '#00f',
  }),
}));

jest.mock('../services/api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
  refreshAccessToken: jest.fn(),
}));

jest.mock('expo-local-authentication', () => ({
  hasHardwareAsync: jest.fn(),
  isEnrolledAsync: jest.fn(),
  authenticateAsync: jest.fn(),
}));

jest.mock('expo-screen-capture', () => ({
  preventScreenCaptureAsync: jest.fn().mockResolvedValue(undefined),
  allowScreenCaptureAsync: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('expo-constants', () => ({
  __esModule: true,
  default: { expoConfig: { extra: { appEnv: 'test' } } },
}));

describe('AppLockGate', () => {
  beforeEach(() => {
    authState.isAuthenticated = false;
    authState.isLoading = true;
    jest.clearAllMocks();
    (LocalAuthentication.hasHardwareAsync as jest.Mock).mockResolvedValue(true);
    (LocalAuthentication.isEnrolledAsync as jest.Mock).mockResolvedValue(true);
    (LocalAuthentication.authenticateAsync as jest.Mock).mockResolvedValue({ success: false });
    (refreshAccessToken as jest.Mock).mockResolvedValue('access-novo');
  });

  // O desbloqueio é o único momento garantido em que o app tem o usuário na
  // frente. Renovar aqui é o que mantém a sessão viva sem pedir senha.
  const abrirComSessaoRestaurada = async () => {
    const view = render(<AppLockGate><>Conteúdo privado</></AppLockGate>);
    authState.isAuthenticated = true;
    view.rerender(<AppLockGate><>Conteúdo privado</></AppLockGate>);
    authState.isLoading = false;
    view.rerender(<AppLockGate><>Conteúdo privado</></AppLockGate>);
    return view;
  };

  // No Android o overlay de privacidade não impede a miniatura de recentes nem o
  // print — quem faz isso é o FLAG_SECURE que expo-screen-capture liga.
  it('protege a tela enquanto há sessão e libera no logout', async () => {
    const view = render(<AppLockGate><>Conteúdo privado</></AppLockGate>);
    authState.isAuthenticated = true;
    view.rerender(<AppLockGate><>Conteúdo privado</></AppLockGate>);

    await waitFor(() => expect(ScreenCapture.preventScreenCaptureAsync).toHaveBeenCalled());

    authState.isAuthenticated = false;
    view.rerender(<AppLockGate><>Conteúdo privado</></AppLockGate>);

    await waitFor(() => expect(ScreenCapture.allowScreenCaptureAsync).toHaveBeenCalled());
  });

  it('libera captura somente no ambiente local-e2e', async () => {
    const extra = Constants.expoConfig!.extra as { appEnv: string };
    extra.appEnv = 'local-e2e';

    authState.isAuthenticated = true;
    authState.isLoading = false;
    const view = render(<AppLockGate><>Conteúdo privado</></AppLockGate>);

    await waitFor(() => expect(ScreenCapture.allowScreenCaptureAsync).toHaveBeenCalled());
    expect(ScreenCapture.preventScreenCaptureAsync).not.toHaveBeenCalled();

    view.unmount();
    extra.appEnv = 'test';
  });

  it('não bloqueia primeira autenticação iniciada após checagem da sessão', () => {
    const view = render(<AppLockGate><>Onboarding</></AppLockGate>);

    authState.isLoading = false;
    view.rerender(<AppLockGate><>Onboarding</></AppLockGate>);
    authState.isAuthenticated = true;
    view.rerender(<AppLockGate><>Onboarding</></AppLockGate>);

    expect(screen.queryByText('App bloqueado')).toBeNull();
    expect(LocalAuthentication.authenticateAsync).not.toHaveBeenCalled();
  });

  it('bloqueia sessão restaurada na abertura do app', async () => {
    const view = render(<AppLockGate><>Conteúdo privado</></AppLockGate>);

    authState.isAuthenticated = true;
    view.rerender(<AppLockGate><>Conteúdo privado</></AppLockGate>);
    authState.isLoading = false;
    view.rerender(<AppLockGate><>Conteúdo privado</></AppLockGate>);

    expect(screen.getByText('App bloqueado')).toBeTruthy();
    await waitFor(() => expect(LocalAuthentication.authenticateAsync).toHaveBeenCalledTimes(1));
  });

  it('bloqueia ao retornar após tempo configurado', async () => {
    authState.isLoading = false;
    const now = jest.spyOn(Date, 'now');
    now.mockReturnValueOnce(1_000).mockReturnValueOnce(61_001);
    let onAppStateChange: ((state: any) => void) | undefined;
    jest.spyOn(AppState, 'addEventListener').mockImplementation((_event, listener) => {
      onAppStateChange = listener;
      return { remove: jest.fn() };
    });

    const view = render(<AppLockGate><>Conteúdo privado</></AppLockGate>);
    authState.isAuthenticated = true;
    view.rerender(<AppLockGate><>Conteúdo privado</></AppLockGate>);
    expect(screen.queryByText('App bloqueado')).toBeNull();

    await act(async () => {
      onAppStateChange?.('background');
      onAppStateChange?.('active');
    });

    expect(screen.getByText('App bloqueado')).toBeTruthy();
    await waitFor(() => expect(LocalAuthentication.authenticateAsync).toHaveBeenCalledTimes(1));
    now.mockRestore();
  });

  it('renova a sessão antes de liberar a UI quando a digital passa', async () => {
    (LocalAuthentication.authenticateAsync as jest.Mock).mockResolvedValue({ success: true });

    await abrirComSessaoRestaurada();

    await waitFor(() => expect(refreshAccessToken).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByText('App bloqueado')).toBeNull());
  });

  it('não renova quando a digital é cancelada', async () => {
    (LocalAuthentication.authenticateAsync as jest.Mock).mockResolvedValue({ success: false });

    await abrirComSessaoRestaurada();

    await waitFor(() => expect(LocalAuthentication.authenticateAsync).toHaveBeenCalled());
    expect(refreshAccessToken).not.toHaveBeenCalled();
    expect(screen.getByText('App bloqueado')).toBeTruthy();
  });

  it('libera a UI mesmo se o refresh falhar por rede (tolerância offline)', async () => {
    (LocalAuthentication.authenticateAsync as jest.Mock).mockResolvedValue({ success: true });
    (refreshAccessToken as jest.Mock).mockRejectedValue(new Error('Network Error'));

    await abrirComSessaoRestaurada();

    await waitFor(() => expect(refreshAccessToken).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByText('App bloqueado')).toBeNull());
  });
});
