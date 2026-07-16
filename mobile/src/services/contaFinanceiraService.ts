import api from './api';
import {
  AjusteContaFinanceiraRequest,
  ContaFinanceira,
  ContaFinanceiraRequest,
  MovimentoCarteira,
  PagedResponse,
  ReconciliacaoCarteira,
} from '../types';

const baseUrl = '/v1/contas-financeiras';

export const contaPodeMovimentarCaixa = (conta: ContaFinanceira) =>
  conta.natureza === 'ATIVO'
  && conta.liquidez === 'IMEDIATA'
  && conta.estadoConciliacao === 'CONCILIADA'
  && !['CARTAO', 'COFRE', 'CUSTODIA'].includes(conta.subtipo);

export const contaGerenciada = (conta: ContaFinanceira) =>
  ['CARTAO', 'COFRE', 'CUSTODIA'].includes(conta.subtipo);

const contaFinanceiraService = {
  listar: (page = 0, size = 100) =>
    api.get<PagedResponse<ContaFinanceira>>(`${baseUrl}/minhas`, { params: { page, size } }).then(r => r.data),

  listarTodas: () =>
    api.get<PagedResponse<ContaFinanceira>>(`${baseUrl}/minhas`, { params: { page: 0, size: 100 } })
      .then(r => r.data.content ?? []),

  listarParaCaixa: async () => (await contaFinanceiraService.listarTodas()).filter(contaPodeMovimentarCaixa),

  buscarPorId: (id: number) => api.get<ContaFinanceira>(`${baseUrl}/${id}`).then(r => r.data),
  criar: (data: ContaFinanceiraRequest) => api.post<ContaFinanceira>(baseUrl, data).then(r => r.data),
  atualizar: (id: number, data: ContaFinanceiraRequest) => api.put<ContaFinanceira>(`${baseUrl}/${id}`, data).then(r => r.data),
  ajustar: (id: number, data: AjusteContaFinanceiraRequest) =>
    api.post<ContaFinanceira>(`${baseUrl}/${id}/ajustes`, data).then(r => r.data),
  listarMovimentos: (id: number, page = 0, size = 20) =>
    api.get<PagedResponse<MovimentoCarteira>>(`${baseUrl}/${id}/movimentos`, {
      params: { page, size, sort: 'dataMovimento,desc' },
    }).then(r => r.data),
  reconciliar: (id: number) => api.get<ReconciliacaoCarteira>(`${baseUrl}/${id}/reconciliacao`).then(r => r.data),
  reconciliarTodas: () => api.get<ReconciliacaoCarteira[]>(`${baseUrl}/minhas/reconciliacao`).then(r => r.data),
  deletar: (id: number) => api.delete(`${baseUrl}/${id}`),
};

export default contaFinanceiraService;
