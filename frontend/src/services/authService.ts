import api from './api';
import type { LoginRequest, LoginResponse, Usuario } from '../types';

export const authService = {
  async login(data: LoginRequest): Promise<LoginResponse> {
    const response = await api.post<LoginResponse>('/auth/login', data);
    
    // ✅ NOVO: Salvar refresh token
    if (response.data.success && response.data.refreshToken) {
      localStorage.setItem('refreshToken', response.data.refreshToken);
      console.log('✅ Refresh token salvo no localStorage');
    }
    
    return response.data;
  },

  async register(usuario: Omit<Usuario, 'id'>): Promise<Usuario> {
    const response = await api.post<Usuario>('/auth/register', usuario);
    return response.data;
  },

  async getMe(): Promise<Usuario> {
    const response = await api.get<Usuario>('/usuarios/me');
    return response.data;
  },

  // ✅ NOVO: Renovar access token
  async refreshToken(): Promise<string | null> {
    try {
      const refreshToken = localStorage.getItem('refreshToken');

      if (!refreshToken) {
        console.error('❌ Refresh token não encontrado');
        return null;
      }

      console.log('🔄 Renovando access token...');

      const response = await api.post('/auth/refresh-token', { refreshToken });

      const novoAccessToken = response.data.accessToken || response.data.token;
      localStorage.setItem('token', novoAccessToken);

      console.log('✅ Access token renovado');

      return novoAccessToken;

    } catch (error: any) {
      console.error('❌ Erro ao renovar token:', error.response?.data || error.message);
      
      // Limpar tudo se refresh token expirou
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      
      return null;
    }
  },

  // ✅ NOVO: Logout com revogação de refresh token
  async logout(): Promise<void> {
    try {
      const refreshToken = localStorage.getItem('refreshToken');

      if (refreshToken) {
        await api.post('/auth/logout', { refreshToken });
        console.log('✅ Refresh token revogado');
      }

    } catch (error) {
      console.error('⚠️ Erro ao revogar refresh token:', error);
    } finally {
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
    }
  },
};