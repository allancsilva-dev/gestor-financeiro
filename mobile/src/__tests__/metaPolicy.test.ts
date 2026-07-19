import { acoesDaMeta, duracaoDaMetaConcluidaEmDias } from '../domain/metaPolicy';
import { Meta, ModalidadeMeta, StatusMeta } from '../types';

const meta = (status: StatusMeta, valorReservado = 0,
              modalidade: ModalidadeMeta = 'RESERVA_VIRTUAL',
              extras: Partial<Meta> = {}): Meta => ({
  id: 1, nome: 'Reserva', valorTotal: 100, valorReservado, status,
  ativa: status === 'ATIVA', modalidade, ...extras,
});

describe('política de ações de metas', () => {
  it('deixa arquivada somente leitura', () => {
    expect(acoesDaMeta(meta('ARQUIVADA', 100))).toEqual({
      editar: false, adicionar: false, resgatar: false, excluir: false, verExtratoCofre: false,
    });
  });

  it('permite apenas edição e resgate financeiro na concluída', () => {
    expect(acoesDaMeta(meta('CONCLUIDA', 100))).toEqual({
      editar: true, adicionar: false, resgatar: true, excluir: false, verExtratoCofre: false,
    });
  });

  it('permite excluir somente ativa vazia', () => {
    expect(acoesDaMeta(meta('ATIVA'))).toEqual({
      editar: true, adicionar: true, resgatar: false, excluir: true, verExtratoCofre: false,
    });
  });

  it('so oferece extrato do cofre para cofre real com cofreId (PR-F3-11)', () => {
    expect(acoesDaMeta(meta('ATIVA', 50, 'COFRE_REAL', { cofreId: 9 })).verExtratoCofre).toBe(true);
    expect(acoesDaMeta(meta('ATIVA', 50, 'COFRE_REAL', { cofreId: null })).verExtratoCofre).toBe(false);
    expect(acoesDaMeta(meta('ATIVA', 50, 'RESERVA_VIRTUAL', { cofreId: 9 })).verExtratoCofre).toBe(false);
  });
});

describe('duração da meta concluída (PR-F3-11)', () => {
  it('conta dias entre início e conclusão', () => {
    expect(duracaoDaMetaConcluidaEmDias(meta('CONCLUIDA', 100, 'COFRE_REAL', {
      dataInicio: '2026-01-01', dataConclusao: '2026-07-19',
    }))).toBe(199);
  });

  it('retorna null sem datas completas ou fora de CONCLUIDA', () => {
    expect(duracaoDaMetaConcluidaEmDias(meta('CONCLUIDA', 100))).toBeNull();
    expect(duracaoDaMetaConcluidaEmDias(meta('ATIVA', 0, 'COFRE_REAL', {
      dataInicio: '2026-01-01', dataConclusao: '2026-07-19',
    }))).toBeNull();
  });
});
