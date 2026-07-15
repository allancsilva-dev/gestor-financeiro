import api from './api';

export interface OnboardingStatus {
  onboardingCompleto: boolean;
}

// Espelha OnboardingFinalizarRequest do backend — caminho canônico e transacional (ADR-0002)
export interface OnboardingFinalizarPayload {
  carteira: {
    nome: string;
    tipo: string;
    saldo: number;
    banco?: string;
  };
  conta: {
    nome: string;
    tipo: string;
    limiteTotal?: number;
    diaFechamento?: number;
    diaVencimento?: number;
    cor?: string;
    banco?: string;
  };
  categorias: {
    nome: string;
    cor?: string;
    icone?: string;
    valorEsperado?: number;
  }[];
  renda?: {
    nome: string;
    valor: number;
    diaVencimento: number;
  };
  meta?: {
    nome: string;
    valorTotal: number;
    valorMensal?: number;
    dataLimite?: string;
  };
}

export interface UsuarioOnboarding {
  id: number;
  nome: string;
  email: string;
  onboardingCompleto: boolean;
}

export const onboardingService = {
  getStatus: async (): Promise<OnboardingStatus> => {
    const response = await api.get<OnboardingStatus>('/onboarding/status');
    return response.data;
  },

  // Idempotente: repetir após sucesso retorna 200 com o usuário atual, sem recriar dados
  finalizar: async (payload: OnboardingFinalizarPayload): Promise<UsuarioOnboarding> => {
    const response = await api.post<UsuarioOnboarding>('/onboarding/finalizar', payload);
    return response.data;
  },

  completar: async (): Promise<OnboardingStatus> => {
    const response = await api.post<OnboardingStatus>('/onboarding/completar');
    return response.data;
  },
};
