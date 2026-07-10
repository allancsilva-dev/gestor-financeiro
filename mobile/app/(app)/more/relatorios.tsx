import React, { useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import { useTheme } from '../../../src/theme';
import relatorioService from '../../../src/services/relatorioService';
import { formatCurrency, formatDate, formatPercent } from '../../../src/utils/format';
import Card from '../../../src/components/ui/Card';
import Chip from '../../../src/components/ui/Chip';
import IconTile from '../../../src/components/ui/IconTile';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

type Periodo = 'mes' | 'mesPassado' | 'tresMeses' | 'ano';

const PERIODOS: { key: Periodo; label: string }[] = [
  { key: 'mes', label: 'Este mês' },
  { key: 'mesPassado', label: 'Mês passado' },
  { key: 'tresMeses', label: '3 meses' },
  { key: 'ano', label: 'Este ano' },
];

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

function intervalo(periodo: Periodo): { inicio: string; fim: string } {
  const hoje = new Date();
  switch (periodo) {
    case 'mes':
      return { inicio: iso(new Date(hoje.getFullYear(), hoje.getMonth(), 1)), fim: iso(hoje) };
    case 'mesPassado':
      return {
        inicio: iso(new Date(hoje.getFullYear(), hoje.getMonth() - 1, 1)),
        fim: iso(new Date(hoje.getFullYear(), hoje.getMonth(), 0)),
      };
    case 'tresMeses':
      return { inicio: iso(new Date(hoje.getFullYear(), hoje.getMonth() - 2, 1)), fim: iso(hoje) };
    case 'ano':
      return { inicio: iso(new Date(hoje.getFullYear(), 0, 1)), fim: iso(hoje) };
  }
}

export default function RelatorioScreen() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const [periodo, setPeriodo] = useState<Periodo>('mes');
  const { inicio, fim } = intervalo(periodo);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['relatorio', inicio, fim],
    queryFn: () => relatorioService.gerar(inicio, fim),
  });

  const evolucaoQuery = useQuery({
    queryKey: ['dashboard-evolucao'],
    queryFn: () => relatorioService.evolucaoMensal(),
  });

  const comparacaoQuery = useQuery({
    queryKey: ['dashboard-comparacao-mensal'],
    queryFn: () => relatorioService.comparacaoMensal(),
  });

  const maiorGasto = data?.gastosPorCategoria[0]?.valorTotal ?? 0;
  const comparacao = comparacaoQuery.data;
  const mesAnterior = comparacao?.find(m => m.periodo.toLowerCase().includes('anterior')) ?? comparacao?.[0];
  const mesAtual = comparacao?.find(m => m.periodo.toLowerCase().includes('atual')) ?? comparacao?.[1];
  const saldoPeriodo = (item?: { entradas: number; saidas: number }) => item ? item.entradas - item.saidas : 0;
  const saldoAnterior = saldoPeriodo(mesAnterior);
  const saldoAtual = saldoPeriodo(mesAtual);
  const variacaoSaldo = saldoAtual - saldoAnterior;
  const variacaoPercentual = saldoAnterior === 0 ? null : (variacaoSaldo / Math.abs(saldoAnterior)) * 100;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Relatórios</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>
          {formatDate(inicio + 'T00:00:00')} até {formatDate(fim + 'T00:00:00')}
        </Text>
      </View>

      <View style={{ flexDirection: 'row', gap: 8, paddingHorizontal: 16, marginBottom: 16 }}>
        {PERIODOS.map(p => (
          <Chip key={p.key} label={p.label} selected={periodo === p.key} onPress={() => setPeriodo(p.key)} />
        ))}
      </View>

      {isLoading ? (
        <View style={{ paddingHorizontal: 16, gap: 12 }}>
          <SkeletonBox width="100%" height={84} borderRadius={18} />
          <SkeletonBox width="100%" height={200} borderRadius={18} />
          <SkeletonBox width="100%" height={160} borderRadius={18} />
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Erro ao gerar relatório</Text>
          <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4, textAlign: 'center' }}>Verifique sua conexão e tente novamente.</Text>
          <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 12, minHeight: 44, justifyContent: 'center' }} accessibilityRole="button">
            <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text>
          </TouchableOpacity>
        </View>
      ) : !data || data.totalTransacoes === 0 ? (
        <View style={{ alignItems: 'center', paddingHorizontal: 32, paddingVertical: 48 }}>
          <Text style={{ fontSize: 40, marginBottom: 12 }}>📊</Text>
          <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600', textAlign: 'center' }}>Nada por aqui neste período</Text>
          <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4, textAlign: 'center' }}>
            Lance transações ou escolha outro período para ver o resumo.
          </Text>
        </View>
      ) : (
        <ScrollView contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 96, gap: 16 }}>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {[
              { l: 'Entradas', v: data.totalEntradas, c: colors.success },
              { l: 'Saídas', v: data.totalSaidas, c: colors.danger },
              { l: 'Saldo', v: data.saldo, c: data.saldo >= 0 ? colors.success : colors.danger },
            ].map(k => (
              <Card key={k.l} radius={16} style={{ flex: 1, paddingHorizontal: 10, paddingVertical: 12 }}>
                <Text style={{ color: colors.textSecondary, fontSize: 11 }}>{k.l}</Text>
                <Text
                  numberOfLines={1}
                  adjustsFontSizeToFit
                  style={{ color: k.c, fontSize: 16, fontWeight: '700', marginTop: 4, fontVariant: ['tabular-nums'] }}
                >
                  {formatCurrency(k.v)}
                </Text>
              </Card>
            ))}
          </View>

          {evolucaoQuery.data && evolucaoQuery.data.some(m => m.entradas > 0 || m.saidas > 0) && (
            <Card radius={20}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700' }}>Evolução mensal</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 2, marginBottom: 14 }}>
                Entradas e saídas · últimos 6 meses
              </Text>
              {(() => {
                const meses = evolucaoQuery.data;
                const maior = Math.max(...meses.map(m => Math.max(m.entradas, m.saidas)), 1);
                const ALTURA = 96;
                return (
                  <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 8 }}>
                    {meses.map((m, i) => (
                      <View
                        key={`${m.mes}-${i}`}
                        accessible
                        accessibilityLabel={`${m.mes}: entradas ${formatCurrency(m.entradas)}, saídas ${formatCurrency(m.saidas)}`}
                        style={{ flex: 1, alignItems: 'center' }}
                      >
                        <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 3, height: ALTURA }}>
                          <View style={{ width: 9, borderRadius: 4, backgroundColor: colors.success, height: Math.max((m.entradas / maior) * ALTURA, m.entradas > 0 ? 4 : 2), opacity: m.entradas > 0 ? 1 : 0.2 }} />
                          <View style={{ width: 9, borderRadius: 4, backgroundColor: colors.danger, height: Math.max((m.saidas / maior) * ALTURA, m.saidas > 0 ? 4 : 2), opacity: m.saidas > 0 ? 1 : 0.2 }} />
                        </View>
                        <Text style={{ color: colors.textSecondary, fontSize: 10, marginTop: 6 }}>
                          {m.mes.replace('.', '')}
                        </Text>
                      </View>
                    ))}
                  </View>
                );
              })()}
              <View style={{ flexDirection: 'row', gap: 14, marginTop: 12 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
                  <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: colors.success }} />
                  <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Entradas</Text>
                </View>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
                  <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: colors.danger }} />
                  <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Saídas</Text>
                </View>
              </View>
            </Card>
          )}

          {mesAnterior && mesAtual && comparacao?.some(m => m.entradas > 0 || m.saidas > 0) && (
            <Card radius={20}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700' }}>Comparação mensal</Text>
                  <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 2 }}>
                    Mês atual contra mês anterior
                  </Text>
                </View>
                <View
                  style={{
                    borderRadius: 999,
                    backgroundColor: variacaoSaldo >= 0 ? colors.successBg : colors.dangerBg,
                    paddingHorizontal: 10,
                    paddingVertical: 5,
                  }}
                  accessible
                  accessibilityLabel={`Variação do saldo: ${variacaoSaldo >= 0 ? 'aumento' : 'queda'} de ${formatCurrency(Math.abs(variacaoSaldo))}`}
                >
                  <Text style={{ color: variacaoSaldo >= 0 ? colors.success : colors.danger, fontSize: 11, fontWeight: '700' }}>
                    {variacaoSaldo >= 0 ? '+' : '−'}{formatCurrency(Math.abs(variacaoSaldo))}
                  </Text>
                </View>
              </View>

              <View style={{ marginTop: 14, gap: 12 }}>
                {[
                  { item: mesAtual, label: 'Mês atual', saldo: saldoAtual },
                  { item: mesAnterior, label: 'Mês anterior', saldo: saldoAnterior },
                ].map(({ item, label, saldo }) => (
                  <View key={label} style={{ paddingTop: 12, borderTopWidth: 1, borderTopColor: colors.border }}>
                    <View style={{ flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', gap: 10 }}>
                      <Text style={{ color: colors.textSecondary, fontSize: 12, fontWeight: '600' }}>{label}</Text>
                      <Text
                        numberOfLines={1}
                        adjustsFontSizeToFit
                        style={{ color: saldo >= 0 ? colors.success : colors.danger, fontSize: 18, fontWeight: '800', fontVariant: ['tabular-nums'] }}
                      >
                        {formatCurrency(saldo)}
                      </Text>
                    </View>
                    <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
                      <View style={{ flex: 1, borderRadius: 12, backgroundColor: colors.successBg, paddingHorizontal: 10, paddingVertical: 8 }}>
                        <Text style={{ color: colors.success, fontSize: 10, fontWeight: '700' }}>Entradas</Text>
                        <Text numberOfLines={1} adjustsFontSizeToFit style={{ color: colors.success, fontSize: 13, fontWeight: '700', marginTop: 2, fontVariant: ['tabular-nums'] }}>
                          {formatCurrency(item.entradas)}
                        </Text>
                      </View>
                      <View style={{ flex: 1, borderRadius: 12, backgroundColor: colors.dangerBg, paddingHorizontal: 10, paddingVertical: 8 }}>
                        <Text style={{ color: colors.danger, fontSize: 10, fontWeight: '700' }}>Saídas</Text>
                        <Text numberOfLines={1} adjustsFontSizeToFit style={{ color: colors.danger, fontSize: 13, fontWeight: '700', marginTop: 2, fontVariant: ['tabular-nums'] }}>
                          {formatCurrency(item.saidas)}
                        </Text>
                      </View>
                    </View>
                  </View>
                ))}
              </View>

              {variacaoPercentual !== null && (
                <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 12 }}>
                  Saldo {variacaoSaldo >= 0 ? 'subiu' : 'caiu'} {formatPercent(Math.abs(variacaoPercentual), 1)} versus o mês anterior.
                </Text>
              )}
            </Card>
          )}

          {data.gastosPorCategoria.length > 0 && (
            <Card radius={20}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700', marginBottom: 12 }}>Gastos por categoria</Text>
              <View style={{ gap: 12 }}>
                {data.gastosPorCategoria.map((c, i) => (
                  <View key={c.categoriaId ?? `${c.nome}-${i}`} style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                    <IconTile size={36}>{c.icone || '🏷️'}</IconTile>
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 8 }}>
                        <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '600', flex: 1 }} numberOfLines={1}>{c.nome}</Text>
                        <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
                          {formatCurrency(c.valorTotal)}
                        </Text>
                      </View>
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 5 }}>
                        <View style={{ flex: 1, height: 5, borderRadius: 3, backgroundColor: colors.border, overflow: 'hidden' }}>
                          <View style={{ width: `${maiorGasto > 0 ? Math.max((c.valorTotal / maiorGasto) * 100, 2) : 0}%`, height: '100%', borderRadius: 3, backgroundColor: c.cor }} />
                        </View>
                        <Text style={{ color: colors.textSecondary, fontSize: 11, width: 36, textAlign: 'right', fontVariant: ['tabular-nums'] }}>
                          {Math.round(c.porcentagem)}%
                        </Text>
                      </View>
                    </View>
                  </View>
                ))}
              </View>
            </Card>
          )}

          {data.maioresDespesas.length > 0 && (
            <Card radius={20}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700', marginBottom: 12 }}>Maiores despesas</Text>
              <View style={{ gap: 12 }}>
                {data.maioresDespesas.map(d => (
                  <View key={d.id} style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '600' }} numberOfLines={1}>{d.descricao}</Text>
                      <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 2 }}>
                        {d.categoriaNome || 'Sem categoria'} · {formatDate(d.data.length === 10 ? `${d.data}T00:00:00` : d.data)}
                      </Text>
                    </View>
                    <Text style={{ color: colors.danger, fontSize: 13, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
                      −{formatCurrency(d.valor)}
                    </Text>
                  </View>
                ))}
              </View>
            </Card>
          )}
        </ScrollView>
      )}
    </View>
  );
}
