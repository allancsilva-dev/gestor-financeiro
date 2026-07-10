import api from './api';
import { Ativo, AtivoRequest, MovimentacaoAtivo, MovimentacaoAtivoRequest } from '../types';

const investimentoService = {
  listar: () =>
    api.get<Ativo[]>('/v1/investimentos').then(r => r.data),

  criar: (data: AtivoRequest) =>
    api.post<Ativo>('/v1/investimentos', data).then(r => r.data),

  atualizar: (id: number, data: AtivoRequest) =>
    api.put<Ativo>(`/v1/investimentos/${id}`, data).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/investimentos/${id}`),

  listarMovimentacoes: (ativoId: number) =>
    api.get<MovimentacaoAtivo[]>(`/v1/investimentos/${ativoId}/movimentacoes`).then(r => r.data),

  adicionarMovimentacao: (ativoId: number, data: MovimentacaoAtivoRequest) =>
    api.post<MovimentacaoAtivo>(`/v1/investimentos/${ativoId}/movimentacoes`, data).then(r => r.data),
};

export default investimentoService;
