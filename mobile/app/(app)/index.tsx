import React from 'react';
import { ScrollView, View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import api from '../../src/services/api';
import { DashboardResumo, Transacao, PagedResponse, ProjecaoResponse } from '../../src/types';
import { useTheme } from '../../src/theme';
import { useAuth } from '../../src/context/AuthContext';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import { formatCurrency, getGreeting, getInitials } from '../../src/utils/format';

export default function Dashboard() {
  const colors = useTheme();
  const router = useRouter();

  const resumoQuery = useQuery<DashboardResumo>({
    queryKey: ['dashboard-resumo'],
    queryFn: () => api.get<DashboardResumo>('/v1/dashboard/resumo').then(r => r.data),
  });

  const transacoesQuery = useQuery<PagedResponse<Transacao>>({
    queryKey: ['transacoes-recentes'],
    queryFn: () => api.get<PagedResponse<Transacao>>('/v1/transacoes/minhas?page=0&size=5&sort=data,desc').then(r => r.data),
  });

  const projecaoQuery = useQuery<ProjecaoResponse>({
    queryKey: ['dashboard-projecao'],
    queryFn: () => api.get<ProjecaoResponse>('/v1/dashboard/projecao?meses=6').then(r => r.data),
  });
  
  const { usuario } = useAuth();
  const insets = useSafeAreaInsets();
  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.bg }]}
      contentContainerStyle={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 32 }}
    >
      <View style={styles.header}>
        <View>
          <Text style={{ color: colors.textSecondary, fontSize: 11 }}>{getGreeting()}</Text>
          <Text style={{ color: colors.textPrimary, fontSize: 18, fontWeight: '700' }}>{usuario?.nome ?? ''}</Text>
        </View>
        <View style={{ width: 38, height: 38, borderRadius: 19, backgroundColor: `${colors.brand}26`, alignItems: 'center', justifyContent: 'center' }}>
          <Text style={{ color: colors.brand, fontWeight: '700' }}>{usuario?.nome ? getInitials(usuario.nome) : ''}</Text>
        </View>
      </View>

      <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }] }>
        {resumoQuery.isLoading ? (
          <SkeletonBox width="100%" height={110} />
        ) : resumoQuery.isError ? (
          <Text style={{ color: colors.danger }}>Erro ao carregar dados</Text>
        ) : resumoQuery.data ? (
          <>
            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8 }}>SALDO TOTAL</Text>
            <Text style={{ color: colors.textPrimary, fontSize: 28, fontWeight: '800' }}>{formatCurrency(Number(resumoQuery.data.saldoCarteiras ?? 0))}</Text>
            <View style={{ flexDirection: 'row', marginTop: 12, gap: 12 }}>
              <View>
                <Text style={{ color: colors.textSecondary, fontSize: 8 }}>ENTRADAS</Text>
                <Text style={{ color: colors.success, fontSize: 13, fontWeight: '600' }}>{formatCurrency(Number(resumoQuery.data.totalEntradas ?? 0))}</Text>
              </View>
              <View>
                <Text style={{ color: colors.textSecondary, fontSize: 8 }}>SAÍDAS</Text>
                <Text style={{ color: colors.danger, fontSize: 13, fontWeight: '600' }}>{formatCurrency(Number(resumoQuery.data.totalSaidas ?? 0))}</Text>
              </View>
            </View>
          </>
        ) : null}
      </View>

      <View style={{ marginTop: 8 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 }}>
          <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8 }}>ÚLTIMAS TRANSAÇÕES</Text>
          <TouchableOpacity onPress={() => router.push('/(app)/transacoes')}>
            <Text style={{ color: colors.brand, fontSize: 11 }}>Ver todas</Text>
          </TouchableOpacity>
        </View>

        {transacoesQuery.isLoading ? (
          <>
            <SkeletonBox width="100%" height={52} borderRadius={8} />
            <View style={{ height: 8 }} />
            <SkeletonBox width="100%" height={52} borderRadius={8} />
            <View style={{ height: 8 }} />
            <SkeletonBox width="100%" height={52} borderRadius={8} />
            <View style={{ height: 8 }} />
            <SkeletonBox width="100%" height={52} borderRadius={8} />
          </>
        ) : transacoesQuery.isError ? (
          <Text style={{ color: colors.danger }}>Erro ao carregar dados</Text>
        ) : transacoesQuery.data && transacoesQuery.data.content.length === 0 ? (
          <View style={{ alignItems: 'center', padding: 24 }}>
            <Text style={{ color: colors.textSecondary }}>Nenhuma transação encontrada</Text>
            <Text style={{ color: colors.textMuted, marginTop: 8 }}>Adicione sua primeira transação</Text>
          </View>
        ) : (
          transacoesQuery.data?.content.map((t) => (
            <View key={t.id} style={{ height: 56, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: 1, borderBottomColor: colors.border }}>
              <View style={{ width: 36, height: 36, borderRadius: 8, backgroundColor: t.tipo === 'ENTRADA' ? colors.successBg : colors.dangerBg, alignItems: 'center', justifyContent: 'center' }}>
                <Text style={{ color: t.tipo === 'ENTRADA' ? colors.success : colors.danger }}>{t.tipo === 'ENTRADA' ? '↑' : '↓'}</Text>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '500' }}>{t.descricao}</Text>
                {t.categoria?.nome ? <Text style={{ color: colors.textSecondary, fontSize: 11 }}>{t.categoria.nome}</Text> : null}
              </View>
              <Text style={{ color: t.tipo === 'ENTRADA' ? colors.success : colors.danger, fontSize: 13, fontWeight: '600' }}>{formatCurrency(Number(t.valorTotal ?? 0))}</Text>
            </View>
          ))
        )}

        {projecaoQuery.data && projecaoQuery.data.meses && projecaoQuery.data.meses.length > 0 && (
          <View style={{ marginTop: 16 }}>
            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 10 }}>PROJEÇÃO DE CAIXA</Text>
            {projecaoQuery.data.meses.map((m, i) => (
              <View key={i} style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: colors.border, backgroundColor: i === 0 ? colors.card : 'transparent', paddingHorizontal: 8, borderRadius: 6 }}>
                <Text style={{ color: i === 0 ? colors.brand : colors.textSecondary, fontSize: 13, fontWeight: '600', width: 42 }}>{m.periodo}</Text>
                <View style={{ flex: 1 }}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    <Text style={{ color: colors.textSecondary, fontSize: 10 }}>Saídas</Text>
                    <Text style={{ color: colors.danger, fontSize: 11, fontWeight: '600' }}>{formatCurrency(m.totalSaidas)}</Text>
                  </View>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 2 }}>
                    <Text style={{ color: colors.textSecondary, fontSize: 10 }}>Saldo final</Text>
                    <Text style={{ color: m.saldoFinal >= 0 ? colors.success : colors.danger, fontSize: 11, fontWeight: '700' }}>
                      {formatCurrency(m.saldoFinal)}
                    </Text>
                  </View>
                </View>
              </View>
            ))}
          </View>
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  card: { borderRadius: 12, borderWidth: 1, padding: 16, marginBottom: 24 },
});
