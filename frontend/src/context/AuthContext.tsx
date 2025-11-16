import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { Usuario, AuthContextType } from '../types';
import { authService } from '../services/authService';
import toast from 'react-hot-toast';

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function checkAuthStatus() {
      const storedToken = localStorage.getItem('token');
      if (storedToken) {
        setToken(storedToken);
        try {
          const user = await authService.getMe();
          setUsuario(user);
        } catch (error) {
          console.error('Falha ao validar token:', error);
          setToken(null);
          setUsuario(null);
          localStorage.removeItem('token');
        }
      }
      setIsLoading(false);
    }
    
    checkAuthStatus();
  }, []);

  // ✅ CORRIGIDO: Mudado de "senha" para "password"
  const login = async (email: string, senha: string) => {
    try {
      // ✅ ENVIA "password" para o backend
      const response = await authService.login({ 
        email, 
        password: senha  // ← MUDOU AQUI!
      });
      
      if (response.success && response.token) {
        setToken(response.token);
        localStorage.setItem('token', response.token);
        
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
        isLoading,
      }}
    >
      {!isLoading && children}
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