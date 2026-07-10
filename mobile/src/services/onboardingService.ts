import api from './api';
import { TipoCarteira, TipoConta, Usuario } from '../types';

export interface OnboardingStatus {
  onboardingCompleto: boolean;
}

export interface OnboardingFinalizarRequest {
  carteira: {
    nome: string;
    tipo: TipoCarteira;
    saldo: number;
    banco?: string;
  };
  conta: {
    nome: string;
    tipo: TipoConta;
    limiteTotal?: number;
    diaFechamento?: number;
    diaVencimento?: number;
    cor?: string;
    banco?: string;
  };
  categorias: Array<{
    nome: string;
    cor?: string;
    icone?: string;
    valorEsperado?: number;
  }>;
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

export const onboardingService = {
  getStatus: async (): Promise<OnboardingStatus> => {
    const { data } = await api.get<OnboardingStatus>('/v1/onboarding/status');
    return data;
  },

  completar: async (): Promise<OnboardingStatus> => {
    const { data } = await api.post<OnboardingStatus>('/v1/onboarding/completar');
    return data;
  },

  finalizar: async (request: OnboardingFinalizarRequest): Promise<Usuario> => {
    const { data } = await api.post<Usuario>('/v1/onboarding/finalizar', request);
    return data;
  },
};
