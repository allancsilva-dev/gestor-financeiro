import api from './api';
import { TipoTransacao, Transacao, TransacaoRequest, PagedResponse } from '../types';

export interface FiltroPeriodo {
  inicio: string;
  fim: string;
  tipo?: TipoTransacao;
  q?: string;
  page?: number;
  size?: number;
}

export const transacaoService = {
  listar: (page = 0, size = 20) =>
    api.get<PagedResponse<Transacao>>(
      `/v1/transacoes/minhas?page=${page}&size=${size}&sort=data,desc`
    ).then(r => r.data),

  listarPorPeriodo: ({ inicio, fim, tipo, q, page = 0, size = 20 }: FiltroPeriodo) =>
    api.get<PagedResponse<Transacao>>('/v1/transacoes/periodo', {
      params: {
        inicio, fim, page, size, sort: 'data,desc',
        ...(tipo ? { tipo } : {}),
        ...(q?.trim() ? { q: q.trim() } : {}),
      },
    }).then(r => r.data),

  buscarPorId: (id: number) =>
    api.get<Transacao>(`/v1/transacoes/${id}`).then(r => r.data),

  criar: (data: TransacaoRequest) =>
    api.post<Transacao>('/v1/transacoes', data).then(r => r.data),

  atualizar: (id: number, data: TransacaoRequest) =>
    api.put<Transacao>(`/v1/transacoes/${id}`, data).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/transacoes/${id}`),
};
