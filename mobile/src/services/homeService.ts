import api from './api';
import { HomeResumo } from '../types';

// Agregado da home (V42): métricas, totais do mês, parcelas agendadas,
// categorias dos chips e contagem de não lidas num request só.
const homeService = {
  obter: () => api.get<HomeResumo>('/v1/home').then(r => r.data),
};

export default homeService;
