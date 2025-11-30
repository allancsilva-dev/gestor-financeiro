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
    const storedRefreshToken = localStorage.getItem('refreshToken');
    
    if (storedToken && storedRefreshToken) {
      setToken(storedToken);
      try {
        const user = await authService.getMe();
        setUsuario(user);
      } catch (error: any) {
        console.error('Falha ao validar token:', error);
        
        // Se for 401 ou 403, tentar renovar o token
        if (error.response?.status === 401 || error.response?.status === 403) {
          console.log('🔄 Tentando renovar token no checkAuthStatus...');
          
          try {
            const novoToken = await authService.refreshToken();
            
            if (novoToken) {
              setToken(novoToken);
              
              // Tentar buscar usuário novamente com novo token
              const user = await authService.getMe();
              setUsuario(user);
              
              console.log('✅ Token renovado e usuário carregado');
              setIsLoading(false);
              return;
            }
          } catch (refreshError) {
            console.error('❌ Erro ao renovar token:', refreshError);
          }
        }
        
        // Se falhou tudo, limpar localStorage
        setToken(null);
        setUsuario(null);
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
      }
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