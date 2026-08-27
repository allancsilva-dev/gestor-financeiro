import api from './api';
import { RegraCategoria, TipoCasamentoRegra } from '../types';

const baseUrl = '/v1/regras-categoria';

/**
 * Regras determinísticas de categorização. Não existe campo de expressão regular por decisão do
 * backend: o casamento é por texto (igual, começa com, contém) sobre a descrição normalizada.
 */
const regraCategoriaService = {
  listar: () => api.get<RegraCategoria[]>(baseUrl).then(r => r.data),

  criar: (dados: {
    padrao: string;
    categoriaId: number;
    tipoCasamento?: TipoCasamentoRegra;
    tipoTransacao?: 'ENTRADA' | 'SAIDA' | null;
    prioridade?: number;
  }) => api.post<RegraCategoria>(baseUrl, dados).then(r => r.data),

  remover: (id: number) => api.delete<void>(`${baseUrl}/${id}`).then(() => undefined),
};

export default regraCategoriaService;
