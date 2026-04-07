import React, { createContext, useContext, useState } from 'react';
import { Usuario } from '../types';
import { authService } from '../services/authService';

interface AuthContextType {
  usuario: Usuario | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [usuario, setUsuario] = useState<Usuario | null>(null);

  const login = async (email: string, password: string) => {
    const user = await authService.login(email, password);
    setUsuario(user);
  };

  const logout = () => {
    authService.logout();
    setUsuario(null);
  };

  const value: AuthContextType = {
    usuario,
    isAuthenticated: usuario !== null,
    isLoading: false,
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
