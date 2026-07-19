import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';

// Preferência de lançamento rápido (PR-F3-05): última forma de pagamento e
// conta/cartão usados ficam SOMENTE no dispositivo (premissa da Fase 3).
const LANCAMENTO_PREFS_KEY = 'lancamentoPrefs';

const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';
const volatileStore: Record<string, string | undefined> = {};

export interface LancamentoPrefs {
  formaPagamento: 'CARTEIRA' | 'CARTAO';
  carteiraId?: number;
  cartaoId?: number;
}

const setItem = async (key: string, value: string) => {
  if (isLocalE2E) { volatileStore[key] = value; return; }
  await SecureStore.setItemAsync(key, value);
};

const getItem = async (key: string): Promise<string | null> => {
  if (isLocalE2E) return volatileStore[key] ?? null;
  return SecureStore.getItemAsync(key);
};

const deleteItem = async (key: string) => {
  if (isLocalE2E) { delete volatileStore[key]; return; }
  await SecureStore.deleteItemAsync(key);
};

export const setLancamentoPrefs = async (prefs: LancamentoPrefs) => {
  await setItem(LANCAMENTO_PREFS_KEY, JSON.stringify(prefs));
};

export const getLancamentoPrefs = async (): Promise<LancamentoPrefs | null> => {
  const raw = await getItem(LANCAMENTO_PREFS_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (parsed?.formaPagamento !== 'CARTEIRA' && parsed?.formaPagamento !== 'CARTAO') return null;
    return parsed as LancamentoPrefs;
  } catch {
    return null;
  }
};

export const clearLancamentoPrefs = async () => {
  await deleteItem(LANCAMENTO_PREFS_KEY);
};
