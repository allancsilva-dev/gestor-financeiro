import api from './api';
import { setAccessToken, clearAccessToken } from '../store/auth';
import { LoginResponse, Usuario } from '../types';

export const authService = {
  async login(email: string, password: string): Promise<Usuario> {
    const { data } = await api.post<LoginResponse>('/auth/login', { email, password });
    const token = data.accessToken ?? data.token;
    if (!token) throw new Error('Token não recebido do servidor.');
    setAccessToken(token);
    if (!data.usuario) throw new Error('Dados do usuário não retornados pelo servidor.');
    return data.usuario;
  },

  logout(): void {
    clearAccessToken();
  },
};
