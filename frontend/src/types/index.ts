export interface Usuario {
  id: number;
  nome: string;
  email: string;
  senha?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

// Type específico para registro
export interface RegisterRequest {
  nome: string;
  email: string;
  password: string;
}

export interface LoginResponse {
  message: string;
  success: boolean;
  token: string;
  refreshToken?: string;
  accessToken?: string;
}

export interface AuthContextType {
  token: string | null;
  usuario: Usuario | null;
  login: (email: string, senha: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
  isLoading: boolean;
}