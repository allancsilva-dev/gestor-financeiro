import api from './api';
import { LoginRequest, LoginResponse, Usuario } from '../types';

export const authService = {
  async login(data: LoginRequest): Promise<LoginResponse> {
    const response = await api.post<LoginResponse>('/auth/login', data);
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
};