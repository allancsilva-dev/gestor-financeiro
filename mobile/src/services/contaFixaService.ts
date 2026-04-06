import api from './api';
import { ContaFixa, ContaFixaRequest, PagedResponse } from '../types';

export const contaFixaService = {
  listar: () =>
    api.get<PagedResponse<ContaFixa>>(
      '/v1/contas-fixas/minhas?page=0&size=20&sort=diaVencimento,asc'
    ).then(r => r.data),

  criar: (data: ContaFixaRequest) =>
    api.post<ContaFixa>('/v1/contas-fixas', data).then(r => r.data),

  atualizar: (id: number, data: ContaFixaRequest) =>
    api.put<ContaFixa>(`/v1/contas-fixas/${id}`, data).then(r => r.data),

  // marcar como paga envia o valor pago no body
  marcarComoPaga: (id: number, valor: number) =>
    api.put<ContaFixa>(`/v1/contas-fixas/${id}/pagar`, { valor }).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/contas-fixas/${id}`),
};
