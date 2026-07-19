import api from './api';
import { Compromissos } from '../types';

// Compromissos próximos (PR-F3-01): total idêntico à métrica Comprometido;
// itens PREVISTO (contas fixas) ficam fora do total. Horizonte default no
// backend é o fim do mês da referência.
const compromissosService = {
  listar: (ate?: string) =>
    api.get<Compromissos>('/v1/compromissos', {
      params: ate ? { ate } : undefined,
    }).then(r => r.data),
};

export default compromissosService;
