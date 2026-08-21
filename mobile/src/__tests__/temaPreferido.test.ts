jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

import * as SecureStore from 'expo-secure-store';
import { getTemaPreferido, setTemaPreferido } from '../store/temaPreferido';

describe('temaPreferido', () => {
  beforeEach(() => jest.clearAllMocks());

  it('persiste a escolha no dispositivo', async () => {
    await setTemaPreferido('escuro');
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('temaPreferido', 'escuro');
  });

  it('recupera a escolha persistida', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('claro');
    await expect(getTemaPreferido()).resolves.toBe('claro');
  });

  it('cai em "sistema" quando não há escolha ou o valor é lixo', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue(null);
    await expect(getTemaPreferido()).resolves.toBe('sistema');
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('sepia');
    await expect(getTemaPreferido()).resolves.toBe('sistema');
  });
});
