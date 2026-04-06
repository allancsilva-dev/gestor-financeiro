import api from './api';
import { Carteira, CarteiraRequest, PagedResponse } from '../types';

export const carteiraService = {
  listar: () =>
    api.get<PagedResponse<Carteira>>('/v1/carteiras/minhas?page=0&size=20')
       .then(r => r.data),

  buscarPorId: (id: number) =>
    api.get<Carteira>(`/v1/carteiras/${id}`).then(r => r.data),

  criar: (data: CarteiraRequest) =>
    api.post<Carteira>('/v1/carteiras', data).then(r => r.data),

  atualizar: (id: number, data: CarteiraRequest) =>
    api.put<Carteira>(`/v1/carteiras/${id}`, data).then(r => r.data),

  // adicionar/remover enviam { valor } no body
  adicionarValor: (id: number, valor: number) =>
    api.post<Carteira>(`/v1/carteiras/${id}/adicionar`, { valor }).then(r => r.data),

  removerValor: (id: number, valor: number) =>
    api.post<Carteira>(`/v1/carteiras/${id}/remover`, { valor }).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/carteiras/${id}`),
};
