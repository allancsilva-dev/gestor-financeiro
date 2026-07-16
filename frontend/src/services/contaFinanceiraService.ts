import api from './api';
import type { PagedResponse } from '../types';

export type NaturezaContaFinanceira = 'ATIVO' | 'PASSIVO';
export type SubtipoContaFinanceira =
  | 'DINHEIRO' | 'CORRENTE' | 'POUPANCA' | 'PAGAMENTO'
  | 'COFRE' | 'CUSTODIA' | 'CARTAO';
export type LiquidezContaFinanceira = 'IMEDIATA' | 'D1' | 'D2' | 'CARENCIA' | 'BLOQUEADA';
export type OrigemDadosConta = 'MANUAL' | 'CSV' | 'OFX' | 'INTEGRACAO' | 'AJUSTE';
export type EstadoConciliacaoConta = 'CONCILIADA' | 'PENDENTE';
export type TipoContaFinanceira = 'DINHEIRO' | 'CONTA_BANCARIA' | 'POUPANCA' | 'CARTAO';

export interface ContaFinanceira {
  id: number;
  nome: string;
  tipo: TipoContaFinanceira;
  saldo: number;
  banco?: string;
  natureza: NaturezaContaFinanceira;
  subtipo: SubtipoContaFinanceira;
  liquidez: LiquidezContaFinanceira;
  origemDados: OrigemDadosConta;
  estadoConciliacao: EstadoConciliacaoConta;
  moeda: string;
}

export interface ContaFinanceiraInput {
  nome: string;
  tipo: Exclude<TipoContaFinanceira, 'CARTAO'>;
  saldo: number;
  banco?: string;
}

export interface AjusteContaFinanceiraInput {
  tipo: 'ENTRADA' | 'SAIDA';
  valor: number;
  descricao?: string;
}

export interface MovimentoContaFinanceira {
  id: number;
  carteiraId: number;
  carteiraNome: string;
  tipo: string;
  valor: number;
  valorAssinado: number;
  origem: string;
  referenciaTipo?: string;
  referenciaId?: number;
  descricao: string;
  dataMovimento: string;
  saldoResultante: number;
  moeda: string;
  operacaoId?: number;
}

export interface ReconciliacaoContaFinanceira {
  carteiraId: number;
  usuarioId: number;
  saldoMaterializado: number;
  saldoLedger: number;
  diferenca: number;
  status: 'OK' | 'DIVERGENTE';
}

export const contaPodeMovimentarCaixa = (conta: ContaFinanceira) =>
  conta.natureza === 'ATIVO'
  && conta.liquidez === 'IMEDIATA'
  && !['CARTAO', 'COFRE', 'CUSTODIA'].includes(conta.subtipo)
  && conta.estadoConciliacao === 'CONCILIADA';

export const contaGerenciada = (conta: ContaFinanceira) =>
  ['CARTAO', 'COFRE', 'CUSTODIA'].includes(conta.subtipo);

const baseUrl = '/contas-financeiras';

const contaFinanceiraService = {
  listar: async (page = 0, size = 20): Promise<PagedResponse<ContaFinanceira>> => {
    const response = await api.get<PagedResponse<ContaFinanceira>>(`${baseUrl}/minhas`, {
      params: { page, size },
    });
    return response.data;
  },

  listarTodas: async (page = 0, size = 100): Promise<ContaFinanceira[]> =>
    (await contaFinanceiraService.listar(page, size)).content ?? [],

  listarParaCaixa: async (): Promise<ContaFinanceira[]> =>
    (await contaFinanceiraService.listarTodas()).filter(contaPodeMovimentarCaixa),

  buscarPorId: async (id: number): Promise<ContaFinanceira> =>
    (await api.get<ContaFinanceira>(`${baseUrl}/${id}`)).data,

  criar: async (conta: ContaFinanceiraInput): Promise<ContaFinanceira> =>
    (await api.post<ContaFinanceira>(baseUrl, conta)).data,

  atualizar: async (id: number, conta: ContaFinanceiraInput): Promise<ContaFinanceira> =>
    (await api.put<ContaFinanceira>(`${baseUrl}/${id}`, conta)).data,

  deletar: async (id: number): Promise<void> => { await api.delete(`${baseUrl}/${id}`); },

  ajustar: async (id: number, ajuste: AjusteContaFinanceiraInput): Promise<ContaFinanceira> =>
    (await api.post<ContaFinanceira>(`${baseUrl}/${id}/ajustes`, ajuste)).data,

  listarMovimentos: async (id: number, page = 0, size = 20): Promise<PagedResponse<MovimentoContaFinanceira>> =>
    (await api.get<PagedResponse<MovimentoContaFinanceira>>(`${baseUrl}/${id}/movimentos`, {
      params: { page, size },
    })).data,

  reconciliarTodas: async (): Promise<ReconciliacaoContaFinanceira[]> =>
    (await api.get<ReconciliacaoContaFinanceira[]>(`${baseUrl}/minhas/reconciliacao`)).data,

  reconciliar: async (id: number): Promise<ReconciliacaoContaFinanceira> =>
    (await api.get<ReconciliacaoContaFinanceira>(`${baseUrl}/${id}/reconciliacao`)).data,
};

export default contaFinanceiraService;
