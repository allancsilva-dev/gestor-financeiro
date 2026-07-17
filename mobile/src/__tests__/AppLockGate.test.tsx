import React from 'react';
import { AppState } from 'react-native';
import { act, render, screen, waitFor } from '@testing-library/react-native';
import * as LocalAuthentication from 'expo-local-authentication';
import AppLockGate from '../components/AppLockGate';

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
}));

jest.mock('expo-local-authentication', () => ({
  hasHardwareAsync: jest.fn(),
  isEnrolledAsync: jest.fn(),
  authenticateAsync: jest.fn(),
}));

describe('AppLockGate', () => {
  beforeEach(() => {
    authState.isAuthenticated = false;
    authState.isLoading = true;
    jest.clearAllMocks();
    (LocalAuthentication.hasHardwareAsync as jest.Mock).mockResolvedValue(true);
    (LocalAuthentication.isEnrolledAsync as jest.Mock).mockResolvedValue(true);
    (LocalAuthentication.authenticateAsync as jest.Mock).mockResolvedValue({ success: false });
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
});
