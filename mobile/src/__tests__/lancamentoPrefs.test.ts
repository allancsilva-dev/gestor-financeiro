jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

import * as SecureStore from 'expo-secure-store';
import { clearLancamentoPrefs, getLancamentoPrefs, setLancamentoPrefs } from '../store/lancamentoPrefs';

describe('lancamentoPrefs SecureStore (PR-F3-05)', () => {
  beforeEach(() => jest.clearAllMocks());

  it('persiste última forma de pagamento com conta no dispositivo', async () => {
    await setLancamentoPrefs({ formaPagamento: 'CARTEIRA', carteiraId: 3 });
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith(
      'lancamentoPrefs', JSON.stringify({ formaPagamento: 'CARTEIRA', carteiraId: 3 }));
  });

  it('recupera preferência de cartão persistida', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue(
      '{"formaPagamento":"CARTAO","cartaoId":9}');
    await expect(getLancamentoPrefs()).resolves.toEqual({ formaPagamento: 'CARTAO', cartaoId: 9 });
  });

  it('tolera valor ausente, JSON inválido e forma desconhecida', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue(null);
    await expect(getLancamentoPrefs()).resolves.toBeNull();
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('{quebrado');
    await expect(getLancamentoPrefs()).resolves.toBeNull();
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('{"formaPagamento":"PIX"}');
    await expect(getLancamentoPrefs()).resolves.toBeNull();
  });

  it('remove preferência', async () => {
    await clearLancamentoPrefs();
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('lancamentoPrefs');
  });
});
