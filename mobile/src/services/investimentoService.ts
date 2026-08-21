import api from './api';
import { Ativo, AtivoRequest, MovimentacaoAtivo, MovimentacaoAtivoRequest } from '../types';

/** Envelope de paginação do backend (Spring Page). */
interface Pagina<T> { content?: T[] }

const investimentoService = {
  listar: () =>
    api.get<Pagina<Ativo>>('/v1/investimentos', { params: { size: 100 } })
      .then(r => r.data.content ?? []),

  criar: (data: AtivoRequest) =>
    api.post<Ativo>('/v1/investimentos', data).then(r => r.data),

  atualizar: (id: number, data: AtivoRequest) =>
    api.put<Ativo>(`/v1/investimentos/${id}`, data).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/investimentos/${id}`),

  listarMovimentacoes: (ativoId: number) =>
    api.get<Pagina<MovimentacaoAtivo>>(`/v1/investimentos/${ativoId}/movimentacoes`, { params: { size: 100 } })
      .then(r => r.data.content ?? []),

  // Idempotency-Key evita duplicar posição e caixa no duplo clique (BACKLOG-0081).
  adicionarMovimentacao: (ativoId: number, data: MovimentacaoAtivoRequest, idempotencyKey?: string) =>
    api.post<MovimentacaoAtivo>(
      `/v1/investimentos/${ativoId}/movimentacoes`,
      data,
      idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined,
    ).then(r => r.data),
};

export default investimentoService;
