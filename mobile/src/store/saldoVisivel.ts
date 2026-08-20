import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';

// Olho de ocultar saldo: a escolha vale por dispositivo e sobrevive ao
// fechamento do app — mesmo padrão de homeChecklist.
const SALDO_OCULTO_KEY = 'saldoOculto';

const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';
const volatileStore: Record<string, string | undefined> = {};

export const setSaldoOculto = async (oculto: boolean) => {
  const valor = oculto ? '1' : '0';
  if (isLocalE2E) { volatileStore[SALDO_OCULTO_KEY] = valor; return; }
  await SecureStore.setItemAsync(SALDO_OCULTO_KEY, valor);
};

export const isSaldoOculto = async (): Promise<boolean> => {
  if (isLocalE2E) return volatileStore[SALDO_OCULTO_KEY] === '1';
  return (await SecureStore.getItemAsync(SALDO_OCULTO_KEY)) === '1';
};
