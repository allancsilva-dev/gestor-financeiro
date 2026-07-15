import api from './api';
import { Meta, MetaRequest, MetaProgresso, PagedResponse, StatusMeta } from '../types';

export const metaService = {
  // ausência de status equivale a ATIVA no backend
  listar: (status?: StatusMeta) =>
    api.get<PagedResponse<Meta>>(`/v1/metas/minhas?page=0&size=20${status ? `&status=${status}` : ''}`)
       .then(r => r.data),

  buscarProgresso: (id: number) =>
    api.get<MetaProgresso>(`/v1/metas/${id}/progresso`).then(r => r.data),

  criar: (data: MetaRequest) =>
    api.post<Meta>('/v1/metas', data).then(r => r.data),

  atualizar: (id: number, data: MetaRequest) =>
    api.put<Meta>(`/v1/metas/${id}`, data).then(r => r.data),

  // reserva debita a carteira de origem; resgate credita de volta (carteira obrigatória no backend)
  adicionarValor: (id: number, valor: number, carteiraId: number) =>
    api.put<Meta>(`/v1/metas/${id}/adicionar`, { valor, carteiraId }).then(r => r.data),

  removerValor: (id: number, valor: number, carteiraId: number) =>
    api.put<Meta>(`/v1/metas/${id}/remover`, { valor, carteiraId }).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/metas/${id}`),
};
