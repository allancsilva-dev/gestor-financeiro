import { QueryClient } from '@tanstack/react-query';
import {
  CHAVES_AFETADAS_POR_TRANSACAO,
  invalidarAposTransacao,
} from '../hooks/useInvalidarAposTransacao';

const chavesInvalidadas = (spy: jest.SpyInstance) =>
  spy.mock.calls.map(([arg]) => JSON.stringify((arg as { queryKey: unknown[] }).queryKey));

describe('invalidação após escrita de transação (BACKLOG-0095)', () => {
  it('invalida as duas queries da Home', () => {
    const queryClient = new QueryClient();
    const spy = jest.spyOn(queryClient, 'invalidateQueries').mockImplementation(() => Promise.resolve());

    invalidarAposTransacao(queryClient);

    const chaves = chavesInvalidadas(spy);
    expect(chaves).toContain(JSON.stringify(['home']));
    expect(chaves).toContain(JSON.stringify(['operacoes']));
  });

  it('invalida toda a lista compartilhada mais as chaves extras da edição', () => {
    const queryClient = new QueryClient();
    const spy = jest.spyOn(queryClient, 'invalidateQueries').mockImplementation(() => Promise.resolve());

    invalidarAposTransacao(queryClient, [['parcelas', 7], ['anexos', 7]]);

    const chaves = chavesInvalidadas(spy);
    expect(chaves).toHaveLength(CHAVES_AFETADAS_POR_TRANSACAO.length + 2);
    expect(chaves).toContain(JSON.stringify(['parcelas', 7]));
    expect(chaves).toContain(JSON.stringify(['anexos', 7]));
  });

  it('não repete chave na lista compartilhada', () => {
    const chaves = CHAVES_AFETADAS_POR_TRANSACAO.map(k => JSON.stringify(k));
    expect(new Set(chaves).size).toBe(chaves.length);
  });
});
