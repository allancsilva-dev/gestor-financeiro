import api from './api';

export const dashboardService = {
  obterResumo: async (usuarioId: number) => {
    const response = await api.get(`/dashboard/resumo/${usuarioId}`);
    return response.data;
  }
};