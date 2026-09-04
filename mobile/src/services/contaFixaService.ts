import api from './api';
import { ContaFixa, ContaFixaRequest, FalhaRecorrencia, PagedResponse, RecorrenciaSugestao } from '../types';

export const contaFixaService = {
  /**
   * Recorrências do titular. `ativo: false` traz as canceladas, que a aba "Canceladas"
   * precisa para oferecer Reativar — sem o filtro a cancelada some da única listagem que
   * existe e o endpoint de reativar fica inalcançável. Omitir a opção preserva o default
   * do backend (só ativas).
   */
  listar: (opcoes?: { ativo?: boolean }) =>
    api.get<PagedResponse<ContaFixa>>(
      '/v1/contas-fixas/minhas?page=0&size=20&sort=diaVencimento,asc'
      + (opcoes?.ativo === false ? '&ativo=false' : '')
    ).then(r => r.data),

  criar: (data: ContaFixaRequest) =>
    api.post<ContaFixa>('/v1/contas-fixas', data).then(r => r.data),

  atualizar: (id: number, data: ContaFixaRequest) =>
    api.put<ContaFixa>(`/v1/contas-fixas/${id}`, data).then(r => r.data),

  // Debita a carteira informada; assinatura de cartão não tem carteira e entra na fatura
  marcarComoPaga: (id: number, valor: number, carteiraId?: number) =>
    api.put<ContaFixa>(`/v1/contas-fixas/${id}/realizar`, { valor, carteiraId }).then(r => r.data),

  listarFalhasPendentes: () =>
    api.get<FalhaRecorrencia[]>('/v1/contas-fixas/falhas-pendentes').then(r => r.data),

  pularMes: (id: number) =>
    api.put<ContaFixa>(`/v1/contas-fixas/${id}/pular`).then(r => r.data),

  /** Volta a cobrar uma cancelada. 422 quando o cartão de destino foi excluído. */
  reativar: (id: number) =>
    api.put<ContaFixa>(`/v1/contas-fixas/${id}/reativar`).then(r => r.data),

  /** Cancela: desativa a recorrência. As cobranças já lançadas na fatura permanecem. */
  deletar: (id: number) =>
    api.delete(`/v1/contas-fixas/${id}`),

  /** Padrões de repetição detectados no histórico, aguardando decisão do titular. */
  listarSugestoes: () =>
    api.get<RecorrenciaSugestao[]>('/v1/recorrencias/sugestoes').then(r => r.data),

  confirmarSugestao: (id: number) =>
    api.post<ContaFixa>(`/v1/recorrencias/sugestoes/${id}/confirmar`).then(r => r.data),

  descartarSugestao: (id: number) =>
    api.post<void>(`/v1/recorrencias/sugestoes/${id}/descartar`).then(() => undefined),
};
