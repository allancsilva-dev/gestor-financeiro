import api from './api';
import { Conta, ContaRequest, PagedResponse } from '../types';

export const contaService = {
  listar: () =>
    api.get<PagedResponse<Conta>>('/v1/contas/minhas?page=0&size=20')
       .then(r => r.data),

  criar: (data: ContaRequest) =>
    api.post<Conta>('/v1/contas', data).then(r => r.data),

  atualizar: (id: number, data: ContaRequest) =>
    api.put<Conta>(`/v1/contas/${id}`, data).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/contas/${id}`),
};

export default contaService;
