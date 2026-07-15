import { Meta } from '../types';

export function acoesDaMeta(meta: Meta) {
  const reservada = Number(meta.valorReservado ?? 0);
  return {
    editar: meta.status !== 'ARQUIVADA',
    adicionar: meta.status === 'ATIVA',
    resgatar: meta.status !== 'ARQUIVADA' && reservada > 0,
    excluir: meta.status === 'ATIVA' && reservada === 0,
  };
}
