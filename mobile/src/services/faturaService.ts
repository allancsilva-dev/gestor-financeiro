import api from './api';
import { FaturaResponse } from '../types';

const faturaService = {
  buscarAtual: (cartaoId: number) =>
    api.get<FaturaResponse>(`/v1/faturas/cartao/${cartaoId}/atual`).then((r) => r.data),

  buscarPorMes: (cartaoId: number, mes: number, ano: number) =>
    api.get<FaturaResponse>(`/v1/faturas/cartao/${cartaoId}`, { params: { mes, ano } }).then((r) => r.data),

  pagarFatura: (id: number, valor: number, carteiraId: number, idempotencyKey?: string) =>
    api.put<FaturaResponse>(
      `/v1/faturas/${id}/pagar`,
      { valor, carteiraId },
      idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined
    ).then((r) => r.data),
};

export default faturaService;
