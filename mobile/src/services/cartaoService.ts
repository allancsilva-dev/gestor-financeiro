import api from './api';
import { Cartao, CartaoRequest, PagedResponse } from '../types';

const baseUrl = '/v1/cartoes';

const cartaoService = {
  listar: () => api.get<PagedResponse<Cartao>>(baseUrl, { params: { page: 0, size: 100 } }).then(r => r.data),
  listarTodos: async () => (await cartaoService.listar()).content ?? [],
  buscarPorId: (id: number) => api.get<Cartao>(`${baseUrl}/${id}`).then(r => r.data),
  criar: (data: CartaoRequest) => api.post<Cartao>(baseUrl, data).then(r => r.data),
  atualizar: (id: number, data: CartaoRequest) => api.put<Cartao>(`${baseUrl}/${id}`, data).then(r => r.data),
  deletar: (id: number) => api.delete(`${baseUrl}/${id}`),
};

export default cartaoService;
