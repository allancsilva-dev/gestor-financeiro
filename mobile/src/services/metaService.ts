import api from './api';
import { Meta, MetaRequest, MetaProgresso, PagedResponse } from '../types';

export const metaService = {
  listar: () =>
    api.get<PagedResponse<Meta>>('/v1/metas/minhas?page=0&size=20')
       .then(r => r.data),

  buscarProgresso: (id: number) =>
    api.get<MetaProgresso>(`/v1/metas/${id}/progresso`).then(r => r.data),

  criar: (data: MetaRequest) =>
    api.post<Meta>('/v1/metas', data).then(r => r.data),

  atualizar: (id: number, data: MetaRequest) =>
    api.put<Meta>(`/v1/metas/${id}`, data).then(r => r.data),

  // adicionar/remover enviam { valor } no body
  adicionarValor: (id: number, valor: number) =>
    api.put<Meta>(`/v1/metas/${id}/adicionar`, { valor }).then(r => r.data),

  removerValor: (id: number, valor: number) =>
    api.put<Meta>(`/v1/metas/${id}/remover`, { valor }).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/metas/${id}`),
};
