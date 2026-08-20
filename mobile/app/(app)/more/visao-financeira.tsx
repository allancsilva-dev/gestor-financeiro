import React from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import api from '../../../src/services/api';
import metricasService from '../../../src/services/metricasService';
import { MetricaId, ProjecaoResponse } from '../../../src/types';
import { useTheme, useTabBarSpace } from '../../../src/theme';
import Card from '../../../src/components/ui/Card';
import ComposicaoMetricaModal from '../../../src/components/ComposicaoMetricaModal';
import { formatCurrency } from '../../../src/utils/format';

// Descrições no vocabulário oficial do glossário (ADR-0013)
const DESCRICOES: Record<string, string> = {
  DISPONIVEL_AGORA: 'Contas com liquidez imediata',
  RESERVADO: 'Cofres reais + alocações virtuais de metas',
  COMPROMETIDO: 'Obrigações vencidas e a vencer até o horizonte',
  DISPONIVEL_PARA_GASTAR: 'Disponível menos reservado e comprometido',
  RESULTADO_MENSAL: 'Competência do mês; fora transferências e reservas',
  INVESTIDO: 'Posições pela última cotação válida',
  DIVIDAS: 'Passivos; crédito de cartão não vira dívida',
  PATRIMONIO_LIQUIDO: 'Contas + investimentos − passivos',
  VARIACAO_PATRIMONIAL: 'Diferença de patrimônio no mês, decomposta',
};

// Visão financeira (PR-F3-06): as 9 métricas oficiais vindas SOMENTE de
// /v1/metricas, cada uma com drill de composição; projeção mantém endpoint
// próprio.
export default function VisaoFinanceira() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [metricaSelecionada, setMetricaSelecionada] = React.useState<MetricaId | null>(null);

  const metricasQuery = useQuery({ queryKey: ['metricas'], queryFn: () => metricasService.obter() });
  const projecaoQuery = useQuery<ProjecaoResponse>({
    queryKey: ['dashboard-projecao'],
    queryFn: () => api.get<ProjecaoResponse>('/v1/dashboard/projecao?meses=6').then(r => r.data),
  });

  const m = metricasQuery.data;

  const metricas: Array<[MetricaId, string, number]> = m ? [
    ['DISPONIVEL_PARA_GASTAR', 'Disponível para gastar', m.disponivelParaGastar],
    ['DISPONIVEL_AGORA', 'Disponível agora', m.disponivelAgora],
    ['RESERVADO', 'Reservado', m.reservado],
    ['COMPROMETIDO', 'Comprometido', m.comprometido],
    ['INVESTIDO', 'Investido', m.investido],
    ['DIVIDAS', 'Dívidas', m.dividas],
    ['RESULTADO_MENSAL', 'Resultado mensal', m.resultadoMensal],
    ['PATRIMONIO_LIQUIDO', 'Patrimônio líquido', m.patrimonioLiquido],
    ['VARIACAO_PATRIMONIAL', 'Variação patrimonial', m.variacaoPatrimonial.total],
  ] : [];

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: tabBarSpace }}
      refreshControl={
        <RefreshControl
          refreshing={metricasQuery.isRefetching || projecaoQuery.isRefetching}
          onRefresh={() => { metricasQuery.refetch(); projecaoQuery.refetch(); }}
          tintColor={colors.brand}
        />
      }
    >
      <TouchableOpacity onPress={() => router.back()} accessibilityRole="button" accessibilityLabel="Voltar" style={{ marginBottom: 8, minHeight: 44, justifyContent: 'center' }}>
        <Text style={{ color: colors.brandFg, fontSize: 15 }}>‹ Voltar</Text>
      </TouchableOpacity>
      <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Visão financeira</Text>
      <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4, marginBottom: 16 }}>
        Métricas oficiais — toque para ver a composição
      </Text>

      {metricasQuery.isLoading ? (
        <ActivityIndicator color={colors.brand} style={{ marginTop: 48 }} />
      ) : metricasQuery.isError ? (
        <TouchableOpacity onPress={() => metricasQuery.refetch()} style={{ alignItems: 'center', padding: 48 }} accessibilityRole="button">
          <Text style={{ color: colors.brandFg, fontWeight: '700' }}>Tentar novamente</Text>
        </TouchableOpacity>
      ) : (
        <View style={{ gap: 8 }}>
          {metricas.map(([id, label, valor]) => (
            <TouchableOpacity
              key={id}
              onPress={() => setMetricaSelecionada(id)}
              accessibilityRole="button"
              accessibilityLabel={`${label}: ${formatCurrency(Number(valor))}. Ver composição`}
            >
              <Card padded radius={16} style={{ flexDirection: 'row', alignItems: 'center' }}>
                <View style={{ flex: 1, minWidth: 0, paddingRight: 8 }}>
                  <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>{label}</Text>
                  <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }}>{DESCRICOES[id]}</Text>
                </View>
                <Text numberOfLines={1} style={{ color: Number(valor) < 0 ? colors.danger : colors.textPrimary, fontSize: 15, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
                  {formatCurrency(Number(valor))}
                </Text>
              </Card>
            </TouchableOpacity>
          ))}

          {m && (
            <Card padded radius={16}>
              <Text style={{ color: colors.textSecondary, fontSize: 12 }}>
                Referência {new Date(`${m.dataReferencia}T12:00:00`).toLocaleDateString('pt-BR')} · Comprometido até {new Date(`${m.horizonteComprometido}T12:00:00`).toLocaleDateString('pt-BR')}
              </Text>
            </Card>
          )}
        </View>
      )}

      {projecaoQuery.data && projecaoQuery.data.meses && projecaoQuery.data.meses.length > 0 && (
        <View style={{ marginTop: 24 }}>
          <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700', marginBottom: 10 }}>Projeção de caixa</Text>
          <Card padded radius={20}>
            {projecaoQuery.data.meses.map((mes, i, arr) => (
              <View key={i} style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 10, borderBottomWidth: i < arr.length - 1 ? 1 : 0, borderBottomColor: colors.border }}>
                <Text style={{ color: i === 0 ? colors.brandFg : colors.textSecondary, fontSize: 13, fontWeight: '600', width: 44 }}>{mes.periodo}</Text>
                <View style={{ flex: 1 }}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Entradas</Text>
                    <Text style={{ color: colors.success, fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] }}>{formatCurrency(mes.totalEntradas ?? 0)}</Text>
                  </View>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 2 }}>
                    <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Saídas</Text>
                    <Text style={{ color: colors.danger, fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] }}>{formatCurrency(mes.totalSaidas)}</Text>
                  </View>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 2 }}>
                    <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Saldo final</Text>
                    <Text style={{ color: mes.saldoFinal >= 0 ? colors.success : colors.danger, fontSize: 12, fontWeight: '700', fontVariant: ['tabular-nums'] }}>{formatCurrency(mes.saldoFinal)}</Text>
                  </View>
                </View>
              </View>
            ))}
          </Card>
        </View>
      )}

      <ComposicaoMetricaModal metrica={metricaSelecionada} onClose={() => setMetricaSelecionada(null)} />
    </ScrollView>
  );
}
