jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

import * as SecureStore from 'expo-secure-store';
import { clearAccessToken, getUsuarioCache, setAccessToken, setUsuarioCache } from '../store/auth';

describe('auth SecureStore', () => {
  beforeEach(() => jest.clearAllMocks());

  it('persiste e remove access token', async () => {
    await setAccessToken('access');
    await clearAccessToken();
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('accessToken', 'access');
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('accessToken');
  });

  it('serializa usuário e tolera cache inválido', async () => {
    await setUsuarioCache({ id: 7 });
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('usuario', '{"id":7}');
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('{invalido');
    await expect(getUsuarioCache()).resolves.toBeNull();
  });
});
