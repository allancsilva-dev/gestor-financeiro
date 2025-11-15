export interface Usuario {
  id: number;
  nome: string;
  email: string;
  senha?: string;
}

export interface LoginRequest {
  email: string;
  senha: string;
}

export interface LoginResponse {
  message: string;
  success: boolean;
  token: string;
}

export interface AuthContextType {
  token: string | null;
  usuario: Usuario | null;
  login: (email: string, senha: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
  isLoading: boolean;
}