import api from './api';

export interface Ativo {
  id?: number;
  ticker: string;
  nome: string;
  tipo: string;
  quantidade: number;
  custoTotal: number;
  valorAtual: number | null;
  precoMedio: number;
  lucroPrejuizo: number;
  rentabilidade: number;
  valorMercado: number | null;
  cotacaoEm: string | null;
  liquidez: 'IMEDIATA' | 'D1' | 'D2' | 'CARENCIA' | 'BLOQUEADA';
  custodiaId: number | null;
}

export interface Movimentacao {
  id?: number;
  tipo: string;
  data: string;
  quantidade: number;
  precoUnitario: number;
  valorTotal: number;
  conciliacao: 'CONCILIADA' | 'EXTERNO';
  operacaoId: number | null;
}

export interface MovimentacaoInput {
  tipo: string;
  data: string;
  quantidade: number;
  precoUnitario: number;
  carteiraId?: number;
  externa: boolean;
}

/** Envelope de paginação do backend (Spring Page). */
interface Pagina<T> {
  content?: T[];
}

export const investimentoService = {
  listar: async (): Promise<Ativo[]> => {
    const response = await api.get<Pagina<Ativo>>('/investimentos', { params: { size: 100 } });
    return response.data.content ?? [];
  },

  criar: async (data: Partial<Ativo>): Promise<Ativo> => {
    const response = await api.post('/investimentos', data);
    return response.data;
  },

  atualizar: async (id: number, data: Partial<Ativo>): Promise<Ativo> => {
    const response = await api.put(`/investimentos/${id}`, data);
    return response.data;
  },

  deletar: async (id: number): Promise<void> => {
    await api.delete(`/investimentos/${id}`);
  },

  listarMovimentacoes: async (ativoId: number): Promise<Movimentacao[]> => {
    const response = await api.get<Pagina<Movimentacao>>(
      `/investimentos/${ativoId}/movimentacoes`,
      { params: { size: 100 } }
    );
    return response.data.content ?? [];
  },

  // Idempotency-Key evita duplicar posição e caixa no duplo clique (BACKLOG-0081).
  adicionarMovimentacao: async (
    ativoId: number,
    data: MovimentacaoInput,
    idempotencyKey?: string
  ): Promise<Movimentacao> => {
    const response = await api.post(
      `/investimentos/${ativoId}/movimentacoes`,
      data,
      idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined
    );
    return response.data;
  },
};
