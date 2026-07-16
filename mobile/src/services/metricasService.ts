import api from './api';
import { MetricasFinanceiras, MetricaId, OrigemMetrica } from '../types';

const metricasService = {
  obter: () => api.get<MetricasFinanceiras>('/v1/metricas').then(r => r.data),
  listarOrigens: (metrica: MetricaId) =>
    api.get<OrigemMetrica[]>(`/v1/metricas/${metrica}/origens`).then(r => r.data),
};

export default metricasService;
