import { Meta } from '../types';

export function acoesDaMeta(meta: Meta) {
  const reservada = Number(meta.valorReservado ?? 0);
  return {
    editar: meta.status !== 'ARQUIVADA',
    adicionar: meta.status === 'ATIVA',
    resgatar: meta.status !== 'ARQUIVADA' && reservada > 0,
    excluir: meta.status === 'ATIVA' && reservada === 0,
    // Cofre real com cofre criado tem extrato próprio (PR-F3-11)
    verExtratoCofre: meta.modalidade === 'COFRE_REAL' && meta.cofreId != null,
  };
}

// Duração em dias entre início e conclusão; null sem datas completas
export function duracaoDaMetaConcluidaEmDias(meta: Meta): number | null {
  if (meta.status !== 'CONCLUIDA' || !meta.dataInicio || !meta.dataConclusao) return null;
  const inicio = new Date(`${meta.dataInicio}T12:00:00`);
  const fim = new Date(`${meta.dataConclusao}T12:00:00`);
  const dias = Math.round((fim.getTime() - inicio.getTime()) / 86400000);
  return dias >= 0 ? dias : null;
}
