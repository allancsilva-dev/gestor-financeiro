import api from './api';
import { PagedResponse, Parcela } from '../types';

const parcelaService = {
  listarPorTransacao: (transacaoId: number, page = 0, size = 50) =>
    api.get<PagedResponse<Parcela>>(`/v1/parcelas/transacao/${transacaoId}`, {
      params: { page, size, sort: 'numeroParcela,asc' },
    }).then(r => r.data),

  pagar: (id: number) =>
    api.put<Parcela>(`/v1/parcelas/${id}/pagar`).then(r => r.data),

  despagar: (id: number) =>
    api.put<Parcela>(`/v1/parcelas/${id}/despagar`).then(r => r.data),
};

export default parcelaService;
