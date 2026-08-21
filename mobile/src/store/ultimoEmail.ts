import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';

/**
 * Último e-mail que entrou com sucesso neste aparelho. Só serve para
 * pré-preencher o login — nenhuma senha é guardada aqui.
 */
const ULTIMO_EMAIL_KEY = 'ultimoEmail';

const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';
const volatileStore: Record<string, string | undefined> = {};

export const salvarUltimoEmail = async (email: string) => {
  if (isLocalE2E) { volatileStore[ULTIMO_EMAIL_KEY] = email; return; }
  await SecureStore.setItemAsync(ULTIMO_EMAIL_KEY, email);
};

export const lerUltimoEmail = async (): Promise<string> => {
  const valor = isLocalE2E
    ? volatileStore[ULTIMO_EMAIL_KEY] ?? null
    : await SecureStore.getItemAsync(ULTIMO_EMAIL_KEY);
  return valor ?? '';
};

export const limparUltimoEmail = async () => {
  if (isLocalE2E) { delete volatileStore[ULTIMO_EMAIL_KEY]; return; }
  await SecureStore.deleteItemAsync(ULTIMO_EMAIL_KEY);
};
