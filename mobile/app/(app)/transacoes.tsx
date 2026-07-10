import React, { useEffect, useMemo, useState } from 'react';
import { View, Text, FlatList, RefreshControl, TouchableOpacity, TextInput, ActivityIndicator } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { transacaoService } from '../../src/services/transacaoService';
import relatorioService from '../../src/services/relatorioService';
import { useTheme } from '../../src/theme';
import { formatCurrency, formatDate } from '../../src/utils/format';
import { TipoTransacao, Transacao } from '../../src/types';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import ListRow from '../../src/components/ui/ListRow';
import Chip from '../../src/components/ui/Chip';
import Card from '../../src/components/ui/Card';
import EditarTransacaoModal from '../../src/components/EditarTransacaoModal';

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

function intervaloDoMes(ref: Date): { inicio: string; fim: string } {
  return {
    inicio: iso(new Date(ref.getFullYear(), ref.getMonth(), 1)),
    fim: iso(new Date(ref.getFullYear(), ref.getMonth() + 1, 0)),
  };
}

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

export default function Transacoes() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();

  const [mesRef, setMesRef] = useState(() => {
    const hoje = new Date();
    return new Date(hoje.getFullYear(), hoje.getMonth(), 1);
  });
  const [filtro, setFiltro] = useState<'TODOS' | TipoTransacao>('TODOS');
  const [busca, setBusca] = useState('');
  const [buscaAtiva, setBuscaAtiva] = useState('');
  const [selecionada, setSelecionada] = useState<Transacao | null>(null);

  // Debounce: só consulta o backend 350ms após parar de digitar
  useEffect(() => {
    const t = setTimeout(() => setBuscaAtiva(busca), 350);
    return () => clearTimeout(t);
  }, [busca]);

  const { inicio, fim } = intervaloDoMes(mesRef);
  const tipo = filtro === 'TODOS' ? undefined : filtro;

  const {
    data,
    isLoading,
    isError,
    refetch,
    isRefetching,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['transacoes', inicio, fim, tipo ?? 'TODOS', buscaAtiva],
    queryFn: ({ pageParam }) =>
      transacaoService.listarPorPeriodo({ inicio, fim, tipo, q: buscaAtiva, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: last => (last.number + 1 < last.totalPages ? last.number + 1 : undefined),
  });

  // Somatório do período vem do backend — nunca da página carregada
  const resumoQuery = useQuery({
    queryKey: ['relatorio', inicio, fim],
    queryFn: () => relatorioService.gerar(inicio, fim),
  });

  const transacoes = useMemo(() => data?.pages.flatMap(p => p.content) ?? [], [data]);
  const total = data?.pages[0]?.totalElements ?? 0;

  const mesLabel = capitalize(mesRef.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' }));
  const hoje = new Date();
  const ehMesAtual = mesRef.getFullYear() === hoje.getFullYear() && mesRef.getMonth() === hoje.getMonth();

  const mudarMes = (delta: number) =>
    setMesRef(m => new Date(m.getFullYear(), m.getMonth() + delta, 1));

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 4 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Transações</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 6 }}>
          <TouchableOpacity
            onPress={() => mudarMes(-1)}
            accessibilityRole="button"
            accessibilityLabel="Mês anterior"
            style={{ minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' }}
          >
            <Text style={{ color: colors.brandFg, fontSize: 20, fontWeight: '600' }}>‹</Text>
          </TouchableOpacity>
          <View style={{ alignItems: 'center' }}>
            <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700' }}>{mesLabel}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 1 }}>
              {isLoading ? ' ' : `${total} ${total === 1 ? 'transação' : 'transações'}`}
            </Text>
          </View>
          <TouchableOpacity
            onPress={() => mudarMes(1)}
            disabled={ehMesAtual}
            accessibilityRole="button"
            accessibilityLabel="Próximo mês"
            style={{ minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center', opacity: ehMesAtual ? 0.3 : 1 }}
          >
            <Text style={{ color: colors.brandFg, fontSize: 20, fontWeight: '600' }}>›</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Resumo do mês: totais do backend (todas as páginas, não só as carregadas) */}
      <View style={{ flexDirection: 'row', gap: 10, paddingHorizontal: 16, paddingTop: 8, paddingBottom: 8 }}>
        <Card radius={14} style={{ flex: 1, padding: 12 }}>
          <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Entradas no mês</Text>
          {resumoQuery.isLoading ? (
            <View style={{ marginTop: 4 }}><SkeletonBox width={90} height={18} /></View>
          ) : (
            <Text style={{ color: colors.success, fontSize: 16, fontWeight: '800', marginTop: 3, fontVariant: ['tabular-nums'] }}>
              + {formatCurrency(resumoQuery.data?.totalEntradas ?? 0)}
            </Text>
          )}
        </Card>
        <Card radius={14} style={{ flex: 1, padding: 12 }}>
          <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Saídas no mês</Text>
          {resumoQuery.isLoading ? (
            <View style={{ marginTop: 4 }}><SkeletonBox width={90} height={18} /></View>
          ) : (
            <Text style={{ color: colors.danger, fontSize: 16, fontWeight: '800', marginTop: 3, fontVariant: ['tabular-nums'] }}>
              − {formatCurrency(resumoQuery.data?.totalSaidas ?? 0)}
            </Text>
          )}
        </Card>
      </View>

      <View style={{ paddingHorizontal: 16, paddingTop: 4 }}>
        <TextInput
          value={busca}
          onChangeText={setBusca}
          placeholder="Buscar por descrição"
          placeholderTextColor={colors.textMuted}
          returnKeyType="search"
          accessibilityLabel="Buscar transações por descrição"
          style={{
            backgroundColor: colors.card,
            borderWidth: 1,
            borderColor: colors.border,
            borderRadius: 12,
            paddingHorizontal: 12,
            paddingVertical: 10,
            color: colors.textPrimary,
            fontSize: 14,
          }}
        />
      </View>

      <View style={{ flexDirection: 'row', gap: 8, paddingHorizontal: 16, paddingVertical: 10 }}>
        {(['TODOS', 'ENTRADA', 'SAIDA'] as Array<'TODOS' | TipoTransacao>).map(ch => (
          <Chip
            key={ch}
            label={ch === 'TODOS' ? 'Todos' : ch === 'ENTRADA' ? 'Entradas' : 'Saídas'}
            selected={filtro === ch}
            onPress={() => setFiltro(ch)}
          />
        ))}
      </View>

      <FlatList
        data={transacoes}
        keyExtractor={item => item.id.toString()}
        contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 32 }}
        refreshControl={<RefreshControl refreshing={isRefetching && !isFetchingNextPage} onRefresh={refetch} tintColor={colors.brand} />}
        onEndReached={() => { if (hasNextPage && !isFetchingNextPage) fetchNextPage(); }}
        onEndReachedThreshold={0.4}
        ListFooterComponent={isFetchingNextPage ? (
          <View style={{ paddingVertical: 16 }}>
            <ActivityIndicator color={colors.brand} />
          </View>
        ) : null}
        ListEmptyComponent={isLoading ? (
          <View style={{ paddingTop: 8, gap: 8 }}>
            {[1, 2, 3, 4, 5].map(i => <SkeletonBox key={i} width="100%" height={64} />)}
          </View>
        ) : isError ? (
          <View style={{ alignItems: 'center', padding: 48 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Erro ao carregar transações</Text>
            <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8, minHeight: 44, justifyContent: 'center' }} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text>
            </TouchableOpacity>
          </View>
        ) : buscaAtiva || filtro !== 'TODOS' ? (
          <View style={{ alignItems: 'center', padding: 48 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Nada encontrado</Text>
            <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4, textAlign: 'center' }}>
              Ajuste a busca ou os filtros para ver outras transações.
            </Text>
          </View>
        ) : (
          <View style={{ alignItems: 'center', padding: 48 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Nenhuma transação em {mesLabel}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Toque no + para lançar a primeira</Text>
          </View>
        )}
        renderItem={({ item: t }) => (
          <ListRow
            icon={t.categoria?.icone || (t.tipo === 'ENTRADA' ? '↑' : '↓')}
            iconTone={t.tipo === 'ENTRADA' ? 'success' : 'danger'}
            title={t.descricao}
            subtitle={`${formatDate(t.data)} · ${t.categoria?.nome ?? 'Sem categoria'}${t.parcelado && t.totalParcelas ? ` · ${t.totalParcelas}x` : ''}`}
            value={`${t.tipo === 'ENTRADA' ? '+' : '−'} ${formatCurrency(Number(t.valorTotal ?? 0))}`}
            valueTone={t.tipo === 'ENTRADA' ? 'success' : 'danger'}
            onPress={() => setSelecionada(t)}
            accessibilityLabel={`Editar transação ${t.descricao}`}
          />
        )}
      />

      <EditarTransacaoModal
        visible={selecionada != null}
        transacao={selecionada}
        onClose={() => setSelecionada(null)}
      />
    </View>
  );
}
