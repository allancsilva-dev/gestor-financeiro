import api from './api';
import { FaturaResponse } from '../types';

const faturaService = {
  buscarAtual: (contaId: number) =>
    api.get<FaturaResponse>(`/v1/faturas/conta/${contaId}/atual`).then((r) => r.data),

  buscarPorMes: (contaId: number, mes: number, ano: number) =>
    api.get<FaturaResponse>(`/v1/faturas/conta/${contaId}`, { params: { mes, ano } }).then((r) => r.data),

  pagarFatura: (id: number, valor: number, carteiraId: number, idempotencyKey?: string) =>
    api.put<FaturaResponse>(
      `/v1/faturas/${id}/pagar`,
      { valor, carteiraId },
      idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined
    ).then((r) => r.data),
};

export default faturaService;
