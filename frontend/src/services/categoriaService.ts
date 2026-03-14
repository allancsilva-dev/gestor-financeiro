import api from './api';
import type { PagedResponse } from '../types';

export interface Categoria {
  id?: number;
  nome: string;
  cor: string;
  icone: string;
  valorEsperado: number;
  valorGasto?: number;
  ativo?: boolean;
}

export const categoriaService = {
  // Listar minhas categorias
  listarMinhas: async (page = 0, size = 20, signal?: AbortSignal) => {
    const response = await api.get<PagedResponse<Categoria>>('/categorias/minhas', {
      params: { page, size },
      signal,
    });
    return response.data.content ?? [];
  },

  listarMinhasPaginado: async (page = 0, size = 20, signal?: AbortSignal) => {
    const response = await api.get<PagedResponse<Categoria>>('/categorias/minhas', {
      params: { page, size },
      signal,
    });
    return response.data;
  },

  // Criar categoria
  criar: async (categoria: Categoria) => {
    const response = await api.post('/categorias', categoria);
    return response.data;
  },

  // Atualizar categoria
  atualizar: async (id: number, categoria: Categoria) => {
    const response = await api.put(`/categorias/${id}`, categoria);
    return response.data;
  },

  // Deletar categoria
  deletar: async (id: number) => {
    await api.delete(`/categorias/${id}`);
  }
};