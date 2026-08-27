import React, { createContext, useContext, useEffect, useState } from 'react';
import { Usuario } from '../types';
import { authService } from '../services/authService';
import { revogarDispositivoDePush } from '../notificacoes/push';
import {
  getAccessToken,
  getUsuarioCache,
  clearAccessToken,
  clearRefreshToken,
  clearCsrfToken,
  clearUsuarioCache,
  setUsuarioCache,
} from '../store/auth';
import api, { setOnSessionExpired } from '../services/api';

interface AuthContextType {
  usuario: Usuario | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  needsOnboarding: boolean;
  login: (email: string, password: string) => Promise<Usuario>;
  logout: () => Promise<void>;
  updateUsuario: (usuario: Usuario) => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    restoreSession();
  }, []);

  // api.ts avisa quando o refresh foi recusado pelo servidor. Sem isso o app
  // ficava montado como autenticado sobre credenciais já descartadas, com toda
  // tela em erro e nenhum caminho de volta ao login a não ser deslogar na mão.
  useEffect(() => {
    setOnSessionExpired(() => {
      void clearUsuarioCache();
      setUsuario(null);
    });
    return () => setOnSessionExpired(null);
  }, []);

  const restoreSession = async () => {
    try {
      const token = await getAccessToken();
      if (!token) { setIsLoading(false); return; }

      const cached = await getUsuarioCache();
      if (cached) setUsuario(cached as Usuario);

      const { data } = await api.get<Usuario>('/v1/usuarios/me');
      setUsuario(data);
    } catch (err) {
      // Erro de rede/timeout mantém a sessão local (usuário do cache já foi
      // aplicado acima) e a próxima abertura tenta de novo.
      // Quando o servidor respondeu negando a auth, a sessão acabou: o
      // interceptor já tentou renovar antes de o erro chegar aqui.
      const status = (err as { response?: { status?: number } }).response?.status;
      if (status === 401 || status === 403) {
        await clearAccessToken();
        await clearRefreshToken();
        await clearCsrfToken();
        await clearUsuarioCache();
        setUsuario(null);
      }
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
    // Antes de derrubar a sessão: sair da conta desliga o push neste aparelho, senão o próximo
    // dono da tela receberia aviso da vida financeira de outra pessoa.
    await revogarDispositivoDePush();
    await authService.logout();
    setUsuario(null);
  };

  const updateUsuario = async (user: Usuario) => {
    setUsuario(user);
    await setUsuarioCache(user);
  };

  const value: AuthContextType = {
    usuario,
    isAuthenticated: usuario !== null,
    isLoading,
    needsOnboarding: usuario !== null && !usuario.onboardingCompleto,
    login,
    logout,
    updateUsuario,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextType => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth deve ser usado dentro do AuthProvider');
  return ctx;
};
