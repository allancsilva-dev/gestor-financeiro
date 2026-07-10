import api from './api';
import { InsightsResponse } from '../types';

const insightsService = {
  buscar: () =>
    api.get<InsightsResponse>('/v1/insights').then(r => r.data),
};

export default insightsService;
