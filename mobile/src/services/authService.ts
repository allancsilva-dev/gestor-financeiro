import api from './api';
import {
  setAccessToken,
  clearAccessToken,
  getRefreshToken,
  setRefreshToken,
  clearRefreshToken,
  getCsrfToken,
  setCsrfToken,
  clearCsrfToken,
  setUsuarioCache,
  clearUsuarioCache,
} from '../store/auth';
import { LoginResponse, Usuario } from '../types';

export const authService = {
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
    // Revoga o refresh token no servidor (cookie HttpOnly + double-submit CSRF).
    // Best-effort: mesmo se a chamada falhar, a sessão local é sempre limpa.
    try {
      const csrf = await getCsrfToken();
      const refreshToken = await getRefreshToken();
      await api.post('/auth/logout', refreshToken ? { refreshToken } : null, {
        headers: csrf ? { 'X-CSRF-Token': csrf } : undefined,
      });
    } catch {
      // sem conexão ou token já revogado — segue com a limpeza local
    }
    await clearAccessToken();
    await clearRefreshToken();
    await clearCsrfToken();
    await clearUsuarioCache();
  },
};
