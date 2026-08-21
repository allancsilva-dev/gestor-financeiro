import React from 'react';
import { QueryClient, useQueryClient } from '@tanstack/react-query';

/**
 * Cache que qualquer escrita de transação torna obsoleto.
 *
 * Fonte única (BACKLOG-0095): a lista ficava duplicada dentro de cada modal e
 * as duas chaves da Home — `['home']` (agregado) e `['operacoes']` (lista de
 * operações) — não estavam em nenhuma delas. A Home só atualizava porque o call
 * site que a monta passava um `onSaved` com `refetch()` manual; salvar pelo FAB
 * da tab bar ou pela tela de faturas deixava a Home com dados velhos.
 *
 * Atualizar a Home é responsabilidade da invalidação, não do call site: quem
 * chama `onSaved` cuida só de navegação/UI.
 */
export const CHAVES_AFETADAS_POR_TRANSACAO: readonly (readonly unknown[])[] = [
  ['home'],
  ['operacoes'],
  ['metricas'],
  ['compromissos'],
  ['transacoes'],
  ['transacoes-recentes'],
  ['relatorio'],
  ['dashboard-evolucao'],
  ['dashboard-comparacao-mensal'],
  ['dashboard-projecao'],
  ['carteiras'],
  ['contas'],
  ['contas-fatura'],
  // Compra no cartão mexe no passivo pareado: sem isto, limite disponível e
  // saldo devedor ficam obsoletos na Carteira. O prefixo ['cartoes'] também
  // cobre ['cartoes','carteira'].
  ['cartoes'],
  ['fatura'],
  ['categorias'],
];

export function invalidarAposTransacao(
  queryClient: QueryClient,
  chavesExtras: readonly (readonly unknown[])[] = [],
) {
  [...CHAVES_AFETADAS_POR_TRANSACAO, ...chavesExtras].forEach(queryKey => {
    queryClient.invalidateQueries({ queryKey: [...queryKey] });
  });
}

/** Versão hook: usa o QueryClient do provider. */
export function useInvalidarAposTransacao() {
  const queryClient = useQueryClient();
  return React.useCallback(
    (chavesExtras?: readonly (readonly unknown[])[]) =>
      invalidarAposTransacao(queryClient, chavesExtras),
    [queryClient],
  );
}
