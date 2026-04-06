import api from './api';
import { Transacao, TransacaoRequest, PagedResponse } from '../types';

export const transacaoService = {
  listar: (page = 0, size = 20) =>
    api.get<PagedResponse<Transacao>>(
      `/v1/transacoes/minhas?page=${page}&size=${size}&sort=data,desc`
    ).then(r => r.data),

  listarPorPeriodo: (inicio: string, fim: string, page = 0, size = 20) =>
    api.get<PagedResponse<Transacao>>(
      `/v1/transacoes/periodo?inicio=${inicio}&fim=${fim}&page=${page}&size=${size}&sort=data,desc`
    ).then(r => r.data),

  buscarPorId: (id: number) =>
    api.get<Transacao>(`/v1/transacoes/${id}`).then(r => r.data),

  criar: (data: TransacaoRequest) =>
    api.post<Transacao>('/v1/transacoes', data).then(r => r.data),

  atualizar: (id: number, data: TransacaoRequest) =>
    api.put<Transacao>(`/v1/transacoes/${id}`, data).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/transacoes/${id}`),
};
