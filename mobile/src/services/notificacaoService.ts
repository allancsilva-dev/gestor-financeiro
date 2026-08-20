import api from './api';
import { Notificacao, PagedResponse } from '../types';

const notificacaoService = {
  listar: (page = 0, size = 20) =>
    api.get<PagedResponse<Notificacao>>('/v1/notificacoes', { params: { page, size } })
      .then(r => r.data),

  contarNaoLidas: () =>
    api.get<{ naoLidas: number }>('/v1/notificacoes/nao-lidas/contagem').then(r => r.data.naoLidas),

  marcarComoLida: (id: number) =>
    api.patch<Notificacao>(`/v1/notificacoes/${id}/lida`).then(r => r.data),

  marcarTodasComoLidas: () =>
    api.post<{ atualizadas: number }>('/v1/notificacoes/marcar-todas-lidas').then(r => r.data),
};

export default notificacaoService;
