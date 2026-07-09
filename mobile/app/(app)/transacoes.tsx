import React, { useMemo, useState } from 'react';
import { View, Text, FlatList, RefreshControl, TouchableOpacity } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import { transacaoService } from '../../src/services/transacaoService';
import { useTheme } from '../../src/theme';
import { formatCurrency, formatDate } from '../../src/utils/format';
import { TipoTransacao } from '../../src/types';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import ListRow from '../../src/components/ui/ListRow';
import Chip from '../../src/components/ui/Chip';
import Card from '../../src/components/ui/Card';

export default function Transacoes() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();

  const [filtro, setFiltro] = useState<'TODOS' | TipoTransacao>('TODOS');

  const { data: paginatedData, isLoading, isError, refetch, isRefetching } = useQuery({
    queryKey: ['transacoes'],
    queryFn: () => transacaoService.listar(0, 20),
  });

  const transacoesFiltradas = useMemo(() => {
    const lista = paginatedData?.content ?? [];
    if (filtro === 'ENTRADA') return lista.filter(t => t.tipo === 'ENTRADA');
    if (filtro === 'SAIDA') return lista.filter(t => t.tipo === 'SAIDA');
    return lista;
  }, [paginatedData, filtro]);

  const { somaEntradas, somaSaidas } = useMemo(() => {
    let somaEntradas = 0;
    let somaSaidas = 0;
    for (const t of transacoesFiltradas) {
      const v = Number(t.valorTotal ?? 0);
      if (t.tipo === 'ENTRADA') somaEntradas += v;
      else somaSaidas += v;
    }
    return { somaEntradas, somaSaidas };
  }, [transacoesFiltradas]);

  const total = transacoesFiltradas.length;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 4 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Transações</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>
          {total} {total === 1 ? 'transação' : 'transações'}
        </Text>
      </View>

      {/* Resumo do filtro: Entradas · Saídas */}
      <View style={{ flexDirection: 'row', gap: 10, paddingHorizontal: 16, paddingTop: 12, paddingBottom: 8 }}>
        <Card radius={14} style={{ flex: 1, padding: 12 }}>
          <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Entradas</Text>
          <Text style={{ color: colors.success, fontSize: 16, fontWeight: '800', marginTop: 3, fontVariant: ['tabular-nums'] }}>
            + {formatCurrency(somaEntradas)}
          </Text>
        </Card>
        <Card radius={14} style={{ flex: 1, padding: 12 }}>
          <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Saídas</Text>
          <Text style={{ color: colors.danger, fontSize: 16, fontWeight: '800', marginTop: 3, fontVariant: ['tabular-nums'] }}>
            − {formatCurrency(somaSaidas)}
          </Text>
        </Card>
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
        data={transacoesFiltradas}
        keyExtractor={item => item.id.toString()}
        contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 32 }}
        refreshControl={<RefreshControl refreshing={isRefetching} onRefresh={refetch} tintColor={colors.brand} />}
        ListEmptyComponent={isLoading ? (
          <View style={{ paddingTop: 8, gap: 8 }}>
            {[1, 2, 3, 4, 5].map(i => <SkeletonBox key={i} width="100%" height={64} />)}
          </View>
        ) : isError ? (
          <View style={{ alignItems: 'center', padding: 48 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Erro ao carregar transações</Text>
            <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8 }} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View style={{ alignItems: 'center', padding: 48 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Nenhuma transação encontrada</Text>
            <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Toque no + para lançar a primeira</Text>
          </View>
        )}
        renderItem={({ item: t }) => (
          <ListRow
            icon={t.categoria?.icone || (t.tipo === 'ENTRADA' ? '↑' : '↓')}
            iconTone={t.tipo === 'ENTRADA' ? 'success' : 'danger'}
            title={t.descricao}
            subtitle={`${formatDate(t.data)} · ${t.categoria?.nome ?? 'Sem categoria'}`}
            value={`${t.tipo === 'ENTRADA' ? '+' : '−'} ${formatCurrency(Number(t.valorTotal ?? 0))}`}
            valueTone={t.tipo === 'ENTRADA' ? 'success' : 'danger'}
          />
        )}
      />
    </View>
  );
}
