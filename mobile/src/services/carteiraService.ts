import api from './api';
import { Carteira, CarteiraRequest, MovimentoCarteira, PagedResponse } from '../types';

export const carteiraService = {
  listar: () =>
    api.get<PagedResponse<Carteira>>('/v1/carteiras/minhas?page=0&size=20')
       .then(r => r.data),

  buscarPorId: (id: number) =>
    api.get<Carteira>(`/v1/carteiras/${id}`).then(r => r.data),

  criar: (data: CarteiraRequest) =>
    api.post<Carteira>('/v1/carteiras', data).then(r => r.data),

  atualizar: (id: number, data: CarteiraRequest) =>
    api.put<Carteira>(`/v1/carteiras/${id}`, data).then(r => r.data),

  // Ajuste manual de saldo — registra movimento no ledger da carteira
  ajustarSaldo: (id: number, tipo: 'ENTRADA' | 'SAIDA', valor: number, descricao?: string) =>
    api.post<Carteira>(`/v1/carteiras/${id}/ajustes`, { tipo, valor, descricao }).then(r => r.data),

  // Extrato do ledger da carteira (movimentos com saldo resultante)
  listarMovimentos: (id: number, page = 0, size = 20) =>
    api.get<PagedResponse<MovimentoCarteira>>(
      `/v1/carteiras/${id}/movimentos?page=${page}&size=${size}&sort=dataMovimento,desc`
    ).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/carteiras/${id}`),
};
