import type { Meta } from '../services/metaService';

export function acoesDaMeta(meta: Meta) {
  const status = meta.status ?? (meta.ativa === false ? 'CONCLUIDA' : 'ATIVA');
  const reservada = Number(meta.valorReservado ?? 0);
  return {
    editar: status !== 'ARQUIVADA',
    adicionar: status === 'ATIVA',
    resgatar: status !== 'ARQUIVADA' && reservada > 0,
    excluir: status === 'ATIVA' && reservada === 0,
  };
}
