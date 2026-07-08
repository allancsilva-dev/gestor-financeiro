import axios, { AxiosError } from 'axios';
import { API_BASE_URL } from '../config/api.config';
import { getAccessToken } from '../store/auth';
import { ApiErrorWithMessage } from '../types';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  withCredentials: false,
  headers: { 'Content-Type': 'application/json' },
});

// Adiciona token em toda request autenticada
api.interceptors.request.use(async (config) => {
  const token = await getAccessToken();
  if (token && config.headers) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Log centralizado — sem dados sensíveis (nunca logar body, token ou dados pessoais)
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    console.error('[API Error]', {
      url: error.config?.url,
      status: error.response?.status,
      // NUNCA adicionar: body, headers com token, dados do usuário
    });

    const status = error.response?.status;

    // Mapeia para mensagem amigável em pt-BR — UI nunca recebe erro técnico
    let userMessage = 'Erro inesperado. Tente novamente.';
    if (!error.response) {
      userMessage = 'Sem conexão. Verifique sua internet.';
    } else if (status === 401) {
      userMessage = 'Sessão inválida. Faça login novamente.';
    } else if (status === 403) {
      userMessage = 'Você não tem permissão para esta ação.';
    } else if (status === 404) {
      userMessage = 'Registro não encontrado.';
    } else if (status === 422) {
      userMessage = 'Dados inválidos. Verifique os campos.';
    } else if (status && status >= 500) {
      userMessage = 'Erro no servidor. Tente novamente em instantes.';
    }

    const enrichedError = error as unknown as ApiErrorWithMessage;
    enrichedError.userMessage = userMessage;
    return Promise.reject(enrichedError);
  }
);

export default api;
