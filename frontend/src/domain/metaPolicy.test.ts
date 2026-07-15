import { describe, expect, it } from 'vitest';
import { acoesDaMeta } from './metaPolicy';
import type { Meta } from '../services/metaService';

const meta = (status: Meta['status'], valorReservado = 0): Meta => ({
  id: 1, nome: 'Reserva', valorTotal: 100, valorMensal: 10, status, valorReservado,
});

describe('política de ações de metas', () => {
  it('deixa arquivada somente leitura', () => {
    expect(acoesDaMeta(meta('ARQUIVADA', 100))).toEqual({ editar: false, adicionar: false, resgatar: false, excluir: false });
  });

  it('permite editar e resgatar concluída, sem aporte ou exclusão', () => {
    expect(acoesDaMeta(meta('CONCLUIDA', 100))).toEqual({ editar: true, adicionar: false, resgatar: true, excluir: false });
  });

  it('só permite excluir ativa sem reserva', () => {
    expect(acoesDaMeta(meta('ATIVA'))).toEqual({ editar: true, adicionar: true, resgatar: false, excluir: true });
  });
});
