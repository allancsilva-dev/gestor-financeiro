import * as SecureStore from 'expo-secure-store';

const ACCESS_TOKEN_KEY = 'accessToken';
const USUARIO_KEY = 'usuario';

export const setAccessToken = async (token: string) => {
  await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, token);
};

export const getAccessToken = async (): Promise<string | null> => {
  return SecureStore.getItemAsync(ACCESS_TOKEN_KEY);
};

export const clearAccessToken = async () => {
  await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
};

export const setUsuarioCache = async (usuario: object) => {
  await SecureStore.setItemAsync(USUARIO_KEY, JSON.stringify(usuario));
};

export const getUsuarioCache = async (): Promise<object | null> => {
  const raw = await SecureStore.getItemAsync(USUARIO_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
};

export const clearUsuarioCache = async () => {
  await SecureStore.deleteItemAsync(USUARIO_KEY);
};
