import { acoesDaMeta } from '../domain/metaPolicy';
import { Meta, StatusMeta } from '../types';

const meta = (status: StatusMeta, valorReservado = 0): Meta => ({
  id: 1, nome: 'Reserva', valorTotal: 100, valorReservado, status, ativa: status === 'ATIVA',
});

describe('política de ações de metas', () => {
  it('deixa arquivada somente leitura', () => {
    expect(acoesDaMeta(meta('ARQUIVADA', 100))).toEqual({ editar: false, adicionar: false, resgatar: false, excluir: false });
  });

  it('permite apenas edição e resgate financeiro na concluída', () => {
    expect(acoesDaMeta(meta('CONCLUIDA', 100))).toEqual({ editar: true, adicionar: false, resgatar: true, excluir: false });
  });

  it('permite excluir somente ativa vazia', () => {
    expect(acoesDaMeta(meta('ATIVA'))).toEqual({ editar: true, adicionar: true, resgatar: false, excluir: true });
  });
});
