import { useEffect, useMemo, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { transacaoService } from '../services/transacaoService';
import { TipoTransacao } from '../types';

// Presets de período dos chips da home. `transacoes.tsx` navega por mês; aqui
// o recorte é por atalho, então só o acesso ao backend é comum aos dois.
export type PeriodoPreset = 'HOJE' | 'SETE_DIAS' | 'ESTE_MES';
export type TipoFiltro = 'TODOS' | TipoTransacao;

export const PERIODO_LABEL: Record<PeriodoPreset, string> = {
  HOJE: 'Hoje',
  SETE_DIAS: 'Últimos 7 dias',
  ESTE_MES: 'Este mês',
};

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export const intervaloDoPreset = (preset: PeriodoPreset, hoje = new Date()) => {
  if (preset === 'HOJE') return { inicio: iso(hoje), fim: iso(hoje) };
  if (preset === 'SETE_DIAS') {
    const inicio = new Date(hoje);
    inicio.setDate(inicio.getDate() - 6);
    return { inicio: iso(inicio), fim: iso(hoje) };
  }
  return {
    inicio: iso(new Date(hoje.getFullYear(), hoje.getMonth(), 1)),
    fim: iso(new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0)),
  };
};

/**
 * Filtro da seção "Operações": preset de período, tipo, categoria e busca com
 * debounce de 350ms — o mesmo intervalo já usado em `transacoes.tsx`, para o
 * backend não ser consultado a cada tecla.
 */
export function useOperacoesFiltro() {
  const [periodo, setPeriodo] = useState<PeriodoPreset>('ESTE_MES');
  const [tipo, setTipo] = useState<TipoFiltro>('TODOS');
  const [categoriaId, setCategoriaId] = useState<number | null>(null);
  const [busca, setBusca] = useState('');
  const [buscaAtiva, setBuscaAtiva] = useState('');

  useEffect(() => {
    const t = setTimeout(() => setBuscaAtiva(busca), 350);
    return () => clearTimeout(t);
  }, [busca]);

  const { inicio, fim } = useMemo(() => intervaloDoPreset(periodo), [periodo]);
  const tipoParam = tipo === 'TODOS' ? undefined : tipo;

  const query = useInfiniteQuery({
    queryKey: ['operacoes', inicio, fim, tipoParam ?? 'TODOS', buscaAtiva],
    queryFn: ({ pageParam }) =>
      transacaoService.listarPorPeriodo({ inicio, fim, tipo: tipoParam, q: buscaAtiva, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: last => (last.number + 1 < last.totalPages ? last.number + 1 : undefined),
  });

  const todas = useMemo(() => query.data?.pages.flatMap(p => p.content) ?? [], [query.data]);

  // Categoria filtra no cliente: o endpoint de período não aceita categoriaId,
  // e o recorte já está na página carregada.
  const itens = useMemo(
    () => (categoriaId == null ? todas : todas.filter(t => t.categoria?.id === categoriaId)),
    [todas, categoriaId],
  );

  const total = categoriaId == null ? (query.data?.pages[0]?.totalElements ?? 0) : itens.length;

  return {
    periodo, setPeriodo,
    tipo, setTipo,
    categoriaId, setCategoriaId,
    busca, setBusca,
    itens, total,
    query,
    filtrando: categoriaId != null || tipo !== 'TODOS' || buscaAtiva.length > 0,
  };
}
