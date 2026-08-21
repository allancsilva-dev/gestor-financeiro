import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';

/**
 * Tema escolhido pelo usuário. Vale por dispositivo e sobrevive ao fechamento do
 * app — mesmo padrão de `saldoVisivel` e `lancamentoPrefs`. `sistema` mantém o
 * comportamento antigo: seguir o `useColorScheme()` do SO.
 */
export type TemaPreferido = 'sistema' | 'claro' | 'escuro';

const TEMA_KEY = 'temaPreferido';

const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';
const volatileStore: Record<string, string | undefined> = {};

const valido = (v: string | null | undefined): v is TemaPreferido =>
  v === 'sistema' || v === 'claro' || v === 'escuro';

export const setTemaPreferido = async (tema: TemaPreferido) => {
  if (isLocalE2E) { volatileStore[TEMA_KEY] = tema; return; }
  await SecureStore.setItemAsync(TEMA_KEY, tema);
};

export const getTemaPreferido = async (): Promise<TemaPreferido> => {
  const valor = isLocalE2E
    ? volatileStore[TEMA_KEY] ?? null
    : await SecureStore.getItemAsync(TEMA_KEY);
  return valido(valor) ? valor : 'sistema';
};
