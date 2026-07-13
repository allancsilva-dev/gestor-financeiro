jest.mock('../services/api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));
jest.mock('../store/auth', () => ({
  setAccessToken: jest.fn(), clearAccessToken: jest.fn(), getRefreshToken: jest.fn(),
  setRefreshToken: jest.fn(), clearRefreshToken: jest.fn(), setCsrfToken: jest.fn(),
  clearCsrfToken: jest.fn(), setUsuarioCache: jest.fn(), clearUsuarioCache: jest.fn(),
}));

import api from '../services/api';
import * as store from '../store/auth';
import { authService } from '../services/authService';

describe('authService', () => {
  beforeEach(() => jest.clearAllMocks());

  it('salva sessão completa no login', async () => {
    const usuario = { id: 1, nome: 'Ana', email: 'ana@example.com', onboardingCompleto: true } as any;
    (api.post as jest.Mock).mockResolvedValue({ data: { accessToken: 'a', refreshToken: 'r', csrfToken: 'c', usuario } });
    await expect(authService.login(usuario.email, 'senha123')).resolves.toEqual(usuario);
    expect(store.setAccessToken).toHaveBeenCalledWith('a');
    expect(store.setRefreshToken).toHaveBeenCalledWith('r');
    expect(store.setCsrfToken).toHaveBeenCalledWith('c');
  });

  it('limpa sessão mesmo quando logout remoto falha', async () => {
    (store.getRefreshToken as jest.Mock).mockResolvedValue('r');
    (api.post as jest.Mock).mockRejectedValue(new Error('offline'));
    await authService.logout();
    expect(store.clearAccessToken).toHaveBeenCalled();
    expect(store.clearRefreshToken).toHaveBeenCalled();
    expect(store.clearCsrfToken).toHaveBeenCalled();
    expect(store.clearUsuarioCache).toHaveBeenCalled();
  });
});
