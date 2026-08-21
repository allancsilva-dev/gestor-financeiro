import api from './api';
import {
  setAccessToken,
  clearAccessToken,
  getRefreshToken,
  setRefreshToken,
  clearRefreshToken,
  setCsrfToken,
  clearCsrfToken,
  setUsuarioCache,
  clearUsuarioCache,
} from '../store/auth';
import { LoginResponse, Usuario } from '../types';
import { normalizarEmail } from '../utils/validate';

export interface RegistroPayload {
  nome: string;
  email: string;
  password: string;
  confirmPassword: string;
  aceitaTermos: boolean;
}

export const authService = {
  /**
   * Cria a conta. O backend responde `{id, nome, email}` sem token — quem chama
   * faz `login()` em seguida. E-mail vai normalizado (trim + minúsculas) para
   * não gerar contas duplicadas por diferença de caixa.
   */
  async registrar(payload: RegistroPayload): Promise<void> {
    await api.post('/auth/register', {
      ...payload,
      nome: payload.nome.trim(),
      email: normalizarEmail(payload.email),
    });
  },

  async login(email: string, password: string): Promise<Usuario> {
    const { data } = await api.post<LoginResponse>('/auth/login', { email, password });
    const token = data.accessToken ?? data.token;
    if (!token) throw new Error('Token não recebido do servidor.');
    await setAccessToken(token);
    if (data.refreshToken) await setRefreshToken(data.refreshToken);
    if (data.csrfToken) await setCsrfToken(data.csrfToken);
    if (!data.usuario) throw new Error('Dados do usuário não retornados pelo servidor.');
    await setUsuarioCache(data.usuario);
    return data.usuario;
  },

  async logout(): Promise<void> {
    // Mobile revoga refresh token pelo body; cookie/CSRF são contrato web.
    // Best-effort: mesmo se a chamada falhar, a sessão local é sempre limpa.
    try {
      const refreshToken = await getRefreshToken();
      await api.post('/auth/logout', refreshToken ? { refreshToken } : null);
    } catch {
      // sem conexão ou token já revogado — segue com a limpeza local
    }
    await clearAccessToken();
    await clearRefreshToken();
    await clearCsrfToken();
    await clearUsuarioCache();
  },
};
