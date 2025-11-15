import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { Usuario, AuthContextType } from '../types';
import { authService } from '../services/authService';
import toast from 'react-hot-toast';

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  // 1. ADICIONADO ESTADO DE CARREGAMENTO
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // 2. LÓGICA DE VERIFICAÇÃO ATUALIZADA
    async function checkAuthStatus() {
      const storedToken = localStorage.getItem('token');
      if (storedToken) {
        setToken(storedToken);
        try {
          // Precisamos validar o token e carregar o usuário
          const user = await authService.getMe();
          setUsuario(user);
        } catch (error) {
          // Token falhou (expirado/inválido)
          console.error('Falha ao validar token:', error);
          setToken(null);
          setUsuario(null);
          localStorage.removeItem('token');
        }
      }
      // 3. TERMINOU DE CARREGAR (COM OU SEM TOKEN)
      setIsLoading(false);
    }
    
    checkAuthStatus();
  }, []);

    const login = async (email: string, senha: string) => {
    try {
      const response = await authService.login({ email, senha });
      
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
      {/* 5. SÓ RENDERIZA O APP QUANDO NÃO ESTIVER CARREGANDO INICIALMENTE
          Isso previne o "flash" da tela de login
      */}
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