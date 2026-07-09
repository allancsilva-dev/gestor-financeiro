import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from '../config/api.config';
import { getAccessToken, setAccessToken, clearAccessToken, getCsrfToken, setCsrfToken, clearCsrfToken } from '../store/auth';
import { ApiErrorWithMessage } from '../types';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  // Necessário para o cookie HttpOnly do refresh token ser gravado/enviado
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

// Adiciona token em toda request autenticada
api.interceptors.request.use(async (config) => {
  const token = await getAccessToken();
  if (token && config.headers) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Renovação de access token: uma única chamada de refresh compartilhada
// entre todas as requests que tomaram 401 ao mesmo tempo.
let refreshPromise: Promise<string | null> | null = null;

const refreshAccessToken = (): Promise<string | null> => {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const csrf = await getCsrfToken();
        const { data } = await axios.post<{ accessToken?: string; csrfToken?: string }>(
          `${API_BASE_URL}/auth/refresh-token`,
          null,
          {
            withCredentials: true,
            timeout: 15000,
            headers: csrf ? { 'X-CSRF-Token': csrf } : undefined,
          }
        );
        const token = data.accessToken ?? null;
        if (token) await setAccessToken(token);
        if (data.csrfToken) await setCsrfToken(data.csrfToken);
        return token;
      } catch {
        await clearAccessToken();
        await clearCsrfToken();
        return null;
      } finally {
        refreshPromise = null;
      }
    })();
  }
  return refreshPromise;
};

// Log centralizado — sem dados sensíveis (nunca logar body, token ou dados pessoais)
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const status = error.response?.status;
    const originalConfig = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;

    // 401 fora do fluxo de auth: tenta renovar o token uma vez e repete a request
    const isAuthRoute = originalConfig?.url?.includes('/auth/');
    if (status === 401 && originalConfig && !originalConfig._retried && !isAuthRoute) {
      const novoToken = await refreshAccessToken();
      if (novoToken) {
        originalConfig._retried = true;
        originalConfig.headers.Authorization = `Bearer ${novoToken}`;
        return api(originalConfig);
      }
    }

    console.error('[API Error]', {
      url: error.config?.url,
      status,
      // NUNCA adicionar: body, headers com token, dados do usuário
    });

    // Mapeia para mensagem amigável em pt-BR — UI nunca recebe erro técnico
    let userMessage = 'Erro inesperado. Tente novamente.';
    if (!error.response) {
      userMessage = 'Sem conexão. Verifique sua internet.';
    } else if (status === 401) {
      userMessage = 'Sessão expirada. Faça login novamente.';
    } else if (status === 403) {
      userMessage = 'Você não tem permissão para esta ação.';
    } else if (status === 404) {
      userMessage = 'Registro não encontrado.';
    } else if (status === 400 || status === 422) {
      const details = (error.response?.data as { details?: Record<string, string> } | undefined)?.details;
      const firstDetail = details && Object.values(details)[0];
      userMessage = firstDetail ? `Dados inválidos: ${firstDetail}` : 'Dados inválidos. Verifique os campos.';
    } else if (status && status >= 500) {
      userMessage = 'Erro no servidor. Tente novamente em instantes.';
    }

    const enrichedError = error as unknown as ApiErrorWithMessage;
    enrichedError.userMessage = userMessage;
    return Promise.reject(enrichedError);
  }
);

export default api;
