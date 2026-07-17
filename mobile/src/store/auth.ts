import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';
const CSRF_TOKEN_KEY = 'csrfToken';
const USUARIO_KEY = 'usuario';
const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';
const volatileStore: Record<string, string | undefined> = {};

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

export const setCsrfToken = async (token: string) => {
  await setItem(CSRF_TOKEN_KEY, token);
};

export const getCsrfToken = async (): Promise<string | null> => {
  return getItem(CSRF_TOKEN_KEY);
};

export const clearCsrfToken = async () => {
  await deleteItem(CSRF_TOKEN_KEY);
};

export const setAccessToken = async (token: string) => {
  await setItem(ACCESS_TOKEN_KEY, token);
};

export const getAccessToken = async (): Promise<string | null> => {
  return getItem(ACCESS_TOKEN_KEY);
};

export const clearAccessToken = async () => {
  await deleteItem(ACCESS_TOKEN_KEY);
};

export const setRefreshToken = async (token: string) => {
  await setItem(REFRESH_TOKEN_KEY, token);
};

export const getRefreshToken = async (): Promise<string | null> => {
  return getItem(REFRESH_TOKEN_KEY);
};

export const clearRefreshToken = async () => {
  await deleteItem(REFRESH_TOKEN_KEY);
};

export const setUsuarioCache = async (usuario: object) => {
  await setItem(USUARIO_KEY, JSON.stringify(usuario));
};

export const getUsuarioCache = async (): Promise<object | null> => {
  const raw = await getItem(USUARIO_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
};

export const clearUsuarioCache = async () => {
  await deleteItem(USUARIO_KEY);
};
