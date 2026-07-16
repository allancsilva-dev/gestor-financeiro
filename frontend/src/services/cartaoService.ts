import api from './api';
import type { PagedResponse } from '../types';

export interface Cartao {
  id: number;
  contaFinanceiraId: number;
  nome: string;
  limiteTotal: number;
  saldoDevedor: number;
  limiteDisponivel: number;
  diaFechamento: number;
  diaVencimento: number;
  ativo: boolean;
  cor?: string;
  banco?: string;
}

export interface CartaoInput {
  nome: string;
  limiteTotal: number;
  diaFechamento: number;
  diaVencimento: number;
  cor?: string;
  banco?: string;
}

const cartaoService = {
  listar: async (page = 0, size = 100): Promise<PagedResponse<Cartao>> =>
    (await api.get<PagedResponse<Cartao>>('/cartoes', { params: { page, size } })).data,
  listarTodos: async (): Promise<Cartao[]> => (await cartaoService.listar()).content ?? [],
  criar: async (cartao: CartaoInput): Promise<Cartao> => (await api.post<Cartao>('/cartoes', cartao)).data,
  atualizar: async (id: number, cartao: CartaoInput): Promise<Cartao> => (await api.put<Cartao>(`/cartoes/${id}`, cartao)).data,
  deletar: async (id: number): Promise<void> => { await api.delete(`/cartoes/${id}`); },
};

export default cartaoService;
