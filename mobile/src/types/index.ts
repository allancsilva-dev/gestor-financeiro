export interface Usuario {
  id: number;
  nome: string;
  email: string;
}

export interface LoginResponse {
  message: string;
  success: boolean;
  accessToken?: string;
  token?: string;
  usuario?: Usuario;
}

export interface PagedResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export type AsyncStatus = 'idle' | 'loading' | 'success' | 'error';

export interface AsyncState<T> {
  status: AsyncStatus;
  data: T | null;
  error: string | null; // sempre mensagem amigável em pt-BR — nunca erro técnico
}

// Erro enriquecido pelo interceptor — componentes usam userMessage, nunca o erro bruto
export interface ApiErrorWithMessage extends Error {
  userMessage: string;
}

// Tipos de domínio usados no dashboard
export interface DashboardResumo {
  totalEntradas: number;
  totalSaidas: number;
  saldo: number; // saldo do mês (entradas - saídas)
  saldoCarteiras: number; // patrimônio total das carteiras
  totalCategorias: number;
  totalContas: number;
  totalMetas: number;
  totalContasFixas: number;
}

export interface Transacao {
  id: number;
  descricao: string;
  valor: number;
  valorTotal: number;
  tipo: 'ENTRADA' | 'SAIDA';
  data: string;
  categoria?: { id: number; nome: string; cor: string };
}

// Status de badges — padrão Nexos
export type BadgeStatus = 'ativo' | 'pendente' | 'inativo' | 'cancelado';
