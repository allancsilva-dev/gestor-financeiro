import api from './api';

export interface FaturaLancamento {
  transacaoId: number;
  descricao: string;
  valor: number;
  data: string;
  categoriaId: number | null;
  categoriaNome: string | null;
  categoriaCor: string;
  categoriaIcone: string;
  parcelaAtual: number | null;
  totalParcelas: number | null;
  tipo: 'COMPRA' | 'AJUSTE' | 'ESTORNO' | 'CREDITO_ANTERIOR' | 'SALDO_DEVEDOR_ANTERIOR';
}

export interface FaturaResponse {
  id: number;
  contaId: number;
  contaNome: string;
  mes: number;
  ano: number;
  dataFechamento: string;
  dataVencimento: string;
  valorTotal: number;
  valorPago: number;
  status: string;
  dataPagamento: string | null;
  lancamentos: FaturaLancamento[];
}

const faturaService = {
  buscarAtual: async (contaId: number): Promise<FaturaResponse> => {
    const response = await api.get<FaturaResponse>(`/faturas/conta/${contaId}/atual`);
    return response.data;
  },

  buscarPorMes: async (contaId: number, mes: number, ano: number): Promise<FaturaResponse> => {
    const response = await api.get<FaturaResponse>(`/faturas/conta/${contaId}`, { params: { mes, ano } });
    return response.data;
  },

  pagarFatura: async (
    faturaId: number,
    valor: number,
    carteiraId: number,
    idempotencyKey?: string
  ): Promise<FaturaResponse> => {
    const response = await api.put<FaturaResponse>(
      `/faturas/${faturaId}/pagar`,
      { valor, carteiraId },
      idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined
    );
    return response.data;
  },
};

export default faturaService;
