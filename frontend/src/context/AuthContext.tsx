import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { Usuario, AuthContextType } from '../types';
import { authService } from '../services/authService';
import toast from 'react-hot-toast';

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [usuario, setUsuario] = useState<Usuario | null>(null);

  useEffect(() => {
    // Carrega token do localStorage quando inicia
    const storedToken = localStorage.getItem('token');
    if (storedToken) {
      setToken(storedToken);
      loadUser();
    }
  }, []);

  const loadUser = async () => {
    try {
      const user = await authService.getMe();
      setUsuario(user);
    } catch (error) {
      console.error('Erro ao carregar usuário:', error);
      logout();
    }
  };

  const login = async (email: string, senha: string) => {
    try {
      const response = await authService.login({ email, senha });
      
      if (response.success && response.token) {
        setToken(response.token);
        localStorage.setItem('token', response.token);
        
        // Carrega dados do usuário
        const user = await authService.getMe();
        setUsuario(user);
        
        toast.success('Login realizado com sucesso!');
      }
    } catch (error: any) {
      const message = error.response?.data?.message || 'Email ou senha incorretos';
      toast.error(message);
      throw error;
    }
  };

  const logout = () => {
    setToken(null);
    setUsuario(null);
    localStorage.removeItem('token');
    toast.success('Logout realizado!');
  };

  return (
    <AuthContext.Provider
      value={{
        token,
        usuario,
        login,
        logout,
        isAuthenticated: !!token,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth deve ser usado dentro de AuthProvider');
  }
  return context;
}