import api from './api';

export interface ImportResult {
  total: number;
  importadas: number;
  ignoradas: number;
  erros: number;
}

export const importService = {
  importarCsv: async (file: File): Promise<ImportResult> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post('/importar/csv', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
};
