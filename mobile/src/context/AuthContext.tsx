import React, { createContext, useContext, useEffect, useState } from 'react';
import { Usuario } from '../types';
import { authService } from '../services/authService';
import { getAccessToken, getUsuarioCache, clearAccessToken, clearUsuarioCache } from '../store/auth';
import api from '../services/api';

interface AuthContextType {
  usuario: Usuario | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  needsOnboarding: boolean;
  login: (email: string, password: string) => Promise<Usuario>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    restoreSession();
  }, []);

  const restoreSession = async () => {
    try {
      const token = await getAccessToken();
      if (!token) { setIsLoading(false); return; }

      const cached = await getUsuarioCache();
      if (cached) setUsuario(cached as Usuario);

      const { data } = await api.get<Usuario>('/v1/usuarios/me');
      setUsuario(data);
    } catch {
      await clearAccessToken();
      await clearUsuarioCache();
      setUsuario(null);
    } finally {
      setIsLoading(false);
    }
  };

  const login = async (email: string, password: string): Promise<Usuario> => {
    const user = await authService.login(email, password);
    setUsuario(user);
    return user;
  };

  const logout = async () => {
    await authService.logout();
    setUsuario(null);
  };

  const value: AuthContextType = {
    usuario,
    isAuthenticated: usuario !== null,
    isLoading,
    needsOnboarding: usuario !== null && !usuario.onboardingCompleto,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextType => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth deve ser usado dentro do AuthProvider');
  return ctx;
};
