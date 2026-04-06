import api from './api';
import { Categoria, CategoriaRequest } from '../types';

export const categoriaService = {
  // retorna array direto — categorias raramente passam de 100
  listar: () =>
    api.get<{ content: Categoria[] }>('/v1/categorias/minhas?page=0&size=100')
       .then(r => r.data.content),

  criar: (data: CategoriaRequest) =>
    api.post<Categoria>('/v1/categorias', data).then(r => r.data),

  atualizar: (id: number, data: CategoriaRequest) =>
    api.put<Categoria>(`/v1/categorias/${id}`, data).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/categorias/${id}`),
};
