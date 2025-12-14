import api from './api';
import type { LoginRequest, LoginResponse, Usuario, RegisterRequest } from '../types';

export const authService = {
  async login(data: LoginRequest): Promise<LoginResponse> {
    const response = await api.post<LoginResponse>('/auth/login', data);
    
    if (response.data.success && response.data.refreshToken) {
      localStorage.setItem('refreshToken', response.data.refreshToken);
    }
    
    return response.data;
  },

  async register(data: { nome: string; email: string; senha: string }): Promise<Usuario> {
    console.log('Dados recebidos:', JSON.stringify(data, null, 2));

    const registerData: RegisterRequest = {
      nome: data.nome,
      email: data.email,
      password: data.senha,
    };

    console.log('Enviando para backend:', JSON.stringify(registerData, null, 2));

    const response = await api.post<Usuario>('/auth/register', registerData);
    return response.data;
  },

  async getMe(): Promise<Usuario> {
    const response = await api.get<Usuario>('/usuarios/me');
    return response.data;
  },

  async refreshToken(): Promise<string | null> {
    try {
      const refreshToken = localStorage.getItem('refreshToken');

      if (!refreshToken) {
        console.error('Refresh token não encontrado');
        return null;
      }

      const response = await api.post('/auth/refresh-token', { refreshToken });
      const novoAccessToken = response.data.accessToken || response.data.token;
      
      localStorage.setItem('token', novoAccessToken);

      return novoAccessToken;

    } catch (error: any) {
      console.error('Erro ao renovar token:', error.response?.data || error.message);
      
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      
      return null;
    }
  },

  async logout(): Promise<void> {
    try {
      const refreshToken = localStorage.getItem('refreshToken');

      if (refreshToken) {
        await api.post('/auth/logout', { refreshToken });
      }

    } catch (error) {
      console.error('Erro ao revogar refresh token:', error);
    } finally {
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
    }
  },
};