import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from '../config/api.config';
import {
  getAccessToken,
  setAccessToken,
  clearAccessToken,
  getRefreshToken,
  setRefreshToken,
  clearRefreshToken,
  setCsrfToken,
  clearCsrfToken,
} from '../store/auth';
import { ApiErrorEnvelope, ApiErrorWithMessage } from '../types';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  // Mobile usa refresh token no SecureStore/body; cookie HttpOnly e CSRF são contrato web.
  withCredentials: false,
  headers: { 'Content-Type': 'application/json', 'X-Client-Type': 'mobile' },
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
        const refreshToken = await getRefreshToken();
        const { data } = await axios.post<{ accessToken?: string; csrfToken?: string; refreshToken?: string }>(
          `${API_BASE_URL}/auth/refresh-token`,
          refreshToken ? { refreshToken } : null,
          {
            withCredentials: false,
            timeout: 15000,
            headers: {
              'Content-Type': 'application/json',
              'X-Client-Type': 'mobile',
            },
          }
        );
        const token = data.accessToken ?? null;
        if (token) await setAccessToken(token);
        if (data.refreshToken) await setRefreshToken(data.refreshToken);
        if (data.csrfToken) await setCsrfToken(data.csrfToken);
        return token;
      } catch (err) {
        // Só descarta tokens quando o backend rejeitou o refresh (401/403).
        // Falha de rede/timeout preserva os tokens para tentar de novo depois.
        const status = (err as AxiosError).response?.status;
        if (status === 401 || status === 403) {
          await clearAccessToken();
          await clearRefreshToken();
          await clearCsrfToken();
        }
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

    if (__DEV__) {
      console.error('[API Error]', {
        url: error.config?.url,
        status,
        // NUNCA adicionar: body, headers com token, dados do usuário
      });
    }

    // Envelope do backend: { code, message, timestamp, requestId, details }
    const envelope = (error.response?.data ?? undefined) as ApiErrorEnvelope | undefined;
    const campos = envelope?.details ?? undefined;
    const mensagemDoBackend = typeof envelope?.message === 'string' ? envelope.message.trim() : '';
    const primeiroCampo = campos ? Object.values(campos)[0] : undefined;

    // Retry-After vem em segundos no 429 (LoginRateLimitFilter e ACCOUNT_LOCKED)
    const retryAfterHeader = error.response?.headers?.['retry-after'];
    const retryAfterSegundos = Number(retryAfterHeader);

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
      // Regra de precedência (PROB-0083): detalhe de campo > mensagem de negócio
      // > fallback. Antes, BusinessException sem `details` virava texto genérico
      // e escondia "Email já cadastrado!" / "Email ou senha incorretos".
      if (primeiroCampo) {
        userMessage = `Dados inválidos: ${primeiroCampo}`;
      } else if (mensagemDoBackend) {
        userMessage = mensagemDoBackend;
      } else {
        userMessage = 'Dados inválidos. Verifique os campos.';
      }
    } else if (status === 429) {
      // Rate limit e bloqueio de conta por tentativas: o backend já manda o
      // tempo na mensagem; sem ela, monta a partir do Retry-After.
      if (mensagemDoBackend) {
        userMessage = mensagemDoBackend;
      } else if (Number.isFinite(retryAfterSegundos) && retryAfterSegundos > 0) {
        userMessage = `Muitas tentativas. Aguarde ${Math.ceil(retryAfterSegundos)} segundos e tente de novo.`;
      } else {
        userMessage = 'Muitas tentativas. Aguarde um minuto e tente de novo.';
      }
    } else if (status && status >= 500) {
      userMessage = 'Erro no servidor. Tente novamente em instantes.';
    }

    const enrichedError = error as unknown as ApiErrorWithMessage;
    enrichedError.userMessage = userMessage;
    enrichedError.status = status;
    enrichedError.codigo = envelope?.code;
    enrichedError.campos = campos ?? undefined;
    if (Number.isFinite(retryAfterSegundos) && retryAfterSegundos > 0) {
      enrichedError.retryAfterSegundos = Math.ceil(retryAfterSegundos);
    }
    return Promise.reject(enrichedError);
  }
);

export default api;
