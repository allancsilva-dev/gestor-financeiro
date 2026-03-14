import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { Usuario, AuthContextType } from '../types';
import { authService } from '../services/authService';
import { clearAccessToken } from '../services/api';
import toast from 'react-hot-toast';

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [isLoading, setIsLoading] = useState(true);

useEffect(() => {
  async function checkAuthStatus() {
    try {
      const novoToken = await authService.refreshToken();

      if (novoToken) {
        setToken(novoToken);
        const user = await authService.getMe();
        setUsuario(user);
      } else {
        setToken(null);
        setUsuario(null);
      }
    } catch (error) {
      console.error('Falha ao restaurar sessão:', error);
      setToken(null);
      setUsuario(null);
      clearAccessToken();
    }

    setIsLoading(false);
  }
  
  checkAuthStatus();
}, []);

  const login = async (email: string, senha: string) => {
    try {
      const response = await authService.login({ 
        email, 
        password: senha
      });
      
      const novoToken = response.accessToken || response.token;

      if (response.success && novoToken) {
        setToken(novoToken);
        
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

  // ✅ ATUALIZADO: Logout com revogação de refresh token
  const logout = async () => {
    try {
      await authService.logout(); // ✅ Chama o novo método que revoga o refresh token
    } catch (error) {
      console.error('Erro ao fazer logout:', error);
    } finally {
      setToken(null);
      setUsuario(null);
      toast.success('Logout realizado!');
    }
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