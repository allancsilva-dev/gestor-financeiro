import api from './api';

export interface Anexo {
  id: number;
  nome: string;
  tipo: string;
  tamanho: number;
  dataUpload: string;
}

export const anexoService = {
  listar: async (transacaoId: number): Promise<Anexo[]> => {
    const response = await api.get(`/anexos/${transacaoId}`);
    return response.data;
  },

  upload: async (transacaoId: number, file: File): Promise<Anexo> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post(`/anexos/${transacaoId}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  downloadUrl: (id: number): string => {
    return `${api.defaults.baseURL}/anexos/${id}/download`;
  },

  deletar: async (id: number): Promise<void> => {
    await api.delete(`/anexos/${id}`);
  },
};
