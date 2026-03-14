import api from './api';
import { clearAccessToken, setAccessToken } from './api';
import type { LoginRequest, LoginResponse, Usuario, RegisterRequest } from '../types';

export const authService = {
  async login(data: LoginRequest): Promise<LoginResponse> {
    const response = await api.post<LoginResponse>('/auth/login', data);

    const accessToken = response.data.accessToken || response.data.token;
    if (response.data.success && accessToken) {
      setAccessToken(accessToken);
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
      const response = await api.post('/auth/refresh-token', {});
      const novoAccessToken = response.data.accessToken || response.data.token;

      if (!novoAccessToken) {
        return null;
      }

      setAccessToken(novoAccessToken);

      return novoAccessToken;

    } catch (error: any) {
      console.error('Erro ao renovar token:', error.response?.data || error.message);

      clearAccessToken();
      
      return null;
    }
  },

  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout', {});

    } catch (error) {
      console.error('Erro ao revogar refresh token:', error);
    } finally {
      clearAccessToken();
    }
  },
};