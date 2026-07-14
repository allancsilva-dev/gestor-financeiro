import React from 'react';
import { RefreshControl, ScrollView, View, Text, TouchableOpacity } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import api from '../../src/services/api';
import insightsService from '../../src/services/insightsService';
import { DashboardResumo, Transacao, PagedResponse, ProjecaoResponse, InsightsResponse } from '../../src/types';
import { useTheme } from '../../src/theme';
import { useAuth } from '../../src/context/AuthContext';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import Card from '../../src/components/ui/Card';
import ListRow from '../../src/components/ui/ListRow';
import Entrance from '../../src/components/ui/Entrance';
import FloatEmoji from '../../src/components/ui/FloatEmoji';
import { formatCurrency, getInitials } from '../../src/utils/format';

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

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

  const insightsQuery = useQuery<InsightsResponse>({
    queryKey: ['insights'],
    queryFn: () => insightsService.buscar(),
  });

  const { usuario } = useAuth();
  const insets = useSafeAreaInsets();

  const primeiroNome = usuario?.nome?.split(' ')[0] ?? '';
  const mesAtual = capitalize(new Date().toLocaleDateString('pt-BR', { month: 'long' }));
  const saldo = formatCurrency(Number(resumoQuery.data?.saldoCarteiras ?? 0));
  const [saldoInt, saldoCents] = saldo.split(',');
  const refreshing = resumoQuery.isRefetching || transacoesQuery.isRefetching
    || projecaoQuery.isRefetching || insightsQuery.isRefetching;

  const handleRefresh = async () => {
    await Promise.all([
      resumoQuery.refetch(),
      transacoesQuery.refetch(),
      projecaoQuery.refetch(),
      insightsQuery.refetch(),
    ]);
  };

  const atalhos: Array<{ label: string; glyph: string; bg: string; fg: string; onPress: () => void }> = [
    { label: 'Contas\nfixas', glyph: '📅', bg: colors.brandBg, fg: colors.brandFg, onPress: () => router.push('/more/contas-fixas' as any) },
    { label: 'Cartão', glyph: '💳', bg: colors.dangerBg, fg: colors.danger, onPress: () => router.push('/more/faturas' as any) },
    { label: 'Metas', glyph: '◎', bg: colors.brandBg, fg: colors.brandFg, onPress: () => router.push('/(app)/metas') },
    { label: 'Relatórios', glyph: '📊', bg: colors.infoBg, fg: colors.info, onPress: () => router.push('/more/relatorios' as any) },
  ];

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingTop: insets.top + 12, paddingHorizontal: 16, paddingBottom: 32 }}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} tintColor={colors.brand} />}
    >
      {/* Header: Olá + avatar com anel gradiente (leva ao perfil) */}
      <Entrance delay={50} style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <View>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.5 }}>
              Olá, {primeiroNome}
            </Text>
            <FloatEmoji>👋</FloatEmoji>
          </View>
          <Text style={{ color: colors.textSecondary, fontSize: 14, marginTop: 2 }}>Bem-vindo de volta!</Text>
        </View>
        <TouchableOpacity
          onPress={() => router.push('/(app)/perfil')}
          accessibilityRole="button"
          accessibilityLabel="Abrir perfil"
        >
          <LinearGradient
            colors={[colors.brand, colors.brandDeep]}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={{
              width: 46, height: 46, borderRadius: 23, padding: 2,
              shadowColor: colors.brand, shadowOpacity: 0.4, shadowRadius: 10,
              shadowOffset: { width: 0, height: 6 }, elevation: 4,
            }}
          >
            <View style={{ flex: 1, borderRadius: 21, backgroundColor: colors.card, alignItems: 'center', justifyContent: 'center' }}>
              <Text style={{ color: colors.brandFg, fontWeight: '700', fontSize: 15 }}>{usuario?.nome ? getInitials(usuario.nome) : ''}</Text>
            </View>
          </LinearGradient>
        </TouchableOpacity>
      </Entrance>

      <Entrance delay={100}>
      <View
        style={{
          borderRadius: 16,
          paddingVertical: 18,
          marginBottom: 16,
          borderBottomWidth: 1,
          borderBottomColor: colors.border,
        }}
      >
        {resumoQuery.isLoading ? (
          <SkeletonBox width="100%" height={110} />
        ) : resumoQuery.isError ? (
          <View>
            <Text style={{ color: colors.danger }}>Erro ao carregar o saldo</Text>
            <TouchableOpacity accessibilityRole="button" onPress={() => resumoQuery.refetch()} style={{ marginTop: 8, minHeight: 44, justifyContent: 'center' }}>
              <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text>
            </TouchableOpacity>
          </View>
        ) : resumoQuery.data ? (
          <>
            <Text style={{ color: colors.textSecondary, fontSize: 14, fontWeight: '500' }}>
              Saldo total
            </Text>
            <View style={{ flexDirection: 'row', alignItems: 'baseline', marginTop: 6 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 36, fontWeight: '800', letterSpacing: -1, fontVariant: ['tabular-nums'] }}>
                {saldoInt}
              </Text>
              {saldoCents != null && (
                <Text style={{ color: colors.textPrimary, fontSize: 21, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
                  ,{saldoCents}
                </Text>
              )}
            </View>
            <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 12, gap: 10 }}>
              <View style={{ backgroundColor: colors.successBg, borderRadius: 999, paddingHorizontal: 11, paddingVertical: 5 }}>
                <Text style={{ color: colors.success, fontSize: 13, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
                  ↑ {formatCurrency(Number(resumoQuery.data.totalEntradas ?? 0))}
                </Text>
              </View>
              <View style={{ backgroundColor: colors.dangerBg, borderRadius: 999, paddingHorizontal: 11, paddingVertical: 5 }}>
                <Text style={{ color: colors.danger, fontSize: 13, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
                  ↓ {formatCurrency(Number(resumoQuery.data.totalSaidas ?? 0))}
                </Text>
              </View>
              <Text style={{ color: colors.textSecondary, fontSize: 12, fontWeight: '500' }}>
                em {mesAtual}
              </Text>
            </View>
          </>
        ) : null}
      </View>
      </Entrance>

      {/* Faixa KPI: Receitas · Despesas · Disponível */}
      {resumoQuery.data && (
        <Entrance delay={150}>
        <Card padded={false} radius={20} style={{ flexDirection: 'row', alignItems: 'stretch', paddingVertical: 16, paddingHorizontal: 6, marginBottom: 16 }}>
          {([
            { label: 'Receitas', valor: Number(resumoQuery.data.totalEntradas ?? 0), glyph: '↑', bg: colors.successBg, fg: colors.success },
            { label: 'Despesas', valor: Number(resumoQuery.data.totalSaidas ?? 0), glyph: '↓', bg: colors.dangerBg, fg: colors.danger },
            { label: 'Saldo do mês', valor: Number(resumoQuery.data.saldo ?? 0), glyph: '●', bg: colors.brandBg, fg: colors.brandFg },
          ]).map((kpi, i) => (
            <React.Fragment key={kpi.label}>
              {i > 0 && <View style={{ width: 1, backgroundColor: colors.border, marginVertical: 2 }} />}
              <View style={{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 8 }}>
                <View style={{ width: 34, height: 34, borderRadius: 17, backgroundColor: kpi.bg, alignItems: 'center', justifyContent: 'center' }}>
                  <Text style={{ color: kpi.fg, fontSize: kpi.glyph === '●' ? 10 : 15, fontWeight: '700' }}>{kpi.glyph}</Text>
                </View>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={{ color: colors.textSecondary, fontSize: 11 }}>{kpi.label}</Text>
                  <Text numberOfLines={1} style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
                    {formatCurrency(kpi.valor)}
                  </Text>
                </View>
              </View>
            </React.Fragment>
          ))}
        </Card>
        </Entrance>
      )}

      {insightsQuery.data && (
        <Entrance delay={175}>
          <Card padded radius={20} style={{ marginBottom: 16 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>Insights</Text>
                <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 3 }}>
                  {insightsQuery.data.resumo || 'Leitura automática do seu mês financeiro.'}
                </Text>
              </View>
              <View style={{ borderRadius: 999, backgroundColor: Number(insightsQuery.data.variacaoPercentual ?? 0) <= 0 ? colors.successBg : colors.warningBg, paddingHorizontal: 10, paddingVertical: 5 }}>
                <Text style={{ color: Number(insightsQuery.data.variacaoPercentual ?? 0) <= 0 ? colors.success : colors.warning, fontSize: 11, fontWeight: '700' }}>
                  {Number(insightsQuery.data.variacaoPercentual ?? 0) > 0 ? '+' : ''}{Number(insightsQuery.data.variacaoPercentual ?? 0).toFixed(1)}%
                </Text>
              </View>
            </View>

            <View style={{ flexDirection: 'row', gap: 8, marginTop: 14 }}>
              <View style={{ flex: 1, borderRadius: 12, backgroundColor: colors.dangerBg, padding: 10 }}>
                <Text style={{ color: colors.danger, fontSize: 10, fontWeight: '700' }}>Gasto mês</Text>
                <Text numberOfLines={1} adjustsFontSizeToFit style={{ color: colors.danger, fontSize: 13, fontWeight: '800', marginTop: 2, fontVariant: ['tabular-nums'] }}>
                  {formatCurrency(Number(insightsQuery.data.gastoMesAtual ?? 0))}
                </Text>
              </View>
              <View style={{ flex: 1, borderRadius: 12, backgroundColor: colors.infoBg, padding: 10 }}>
                <Text style={{ color: colors.info, fontSize: 10, fontWeight: '700' }}>Média</Text>
                <Text numberOfLines={1} adjustsFontSizeToFit style={{ color: colors.info, fontSize: 13, fontWeight: '800', marginTop: 2, fontVariant: ['tabular-nums'] }}>
                  {formatCurrency(Number(insightsQuery.data.gastoMedioMensal ?? 0))}
                </Text>
              </View>
            </View>

            {insightsQuery.data.categoriasAlerta?.length > 0 && (
              <View style={{ marginTop: 12, gap: 8 }}>
                {insightsQuery.data.categoriasAlerta.slice(0, 2).map((c, i) => (
                  <View key={`${c.categoriaNome}-${i}`} style={{ paddingTop: 10, borderTopWidth: 1, borderTopColor: colors.border }}>
                    <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '600' }} numberOfLines={1}>{c.categoriaNome}</Text>
                    <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 2 }}>
                      {formatCurrency(Number(c.gastoAtual ?? 0))} no mês · {Number(c.variacaoPercentual ?? 0).toFixed(1)}% vs média
                    </Text>
                  </View>
                ))}
              </View>
            )}

            {insightsQuery.data.recomendacoes?.[0] && (
              <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 12 }}>
                {insightsQuery.data.recomendacoes[0]}
              </Text>
            )}
          </Card>
        </Entrance>
      )}

      {/* Atalhos rápidos */}
      <Entrance delay={200} style={{ flexDirection: 'row', gap: 10, marginBottom: 16 }}>
        {atalhos.map(a => (
          <TouchableOpacity
            key={a.label}
            onPress={a.onPress}
            activeOpacity={0.7}
            accessibilityRole="button"
            accessibilityLabel={a.label.replace('\n', ' ')}
            style={{ flex: 1 }}
          >
            <Card padded={false} radius={16} style={{ alignItems: 'center', gap: 9, paddingVertical: 16, paddingHorizontal: 4 }}>
              <View style={{ width: 42, height: 42, borderRadius: 21, backgroundColor: a.bg, alignItems: 'center', justifyContent: 'center' }}>
                <Text style={{ color: a.fg, fontSize: 20, fontWeight: '500' }}>{a.glyph}</Text>
              </View>
              <Text style={{ color: colors.textSecondary, fontSize: 11.5, fontWeight: '500', textAlign: 'center', lineHeight: 14 }}>
                {a.label}
              </Text>
            </Card>
          </TouchableOpacity>
        ))}
      </Entrance>

      <Entrance delay={250}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>Últimas transações</Text>
        <TouchableOpacity onPress={() => router.push('/(app)/transacoes')} accessibilityRole="button">
          <Text style={{ color: colors.brandFg, fontSize: 13, fontWeight: '600' }}>Ver todas</Text>
        </TouchableOpacity>
      </View>

      <Card padded radius={20}>
        {transacoesQuery.isLoading ? (
          <View style={{ gap: 8 }}>
            {[1, 2, 3, 4].map(i => <SkeletonBox key={i} width="100%" height={52} />)}
          </View>
        ) : transacoesQuery.isError ? (
          <View style={{ alignItems: 'center', paddingVertical: 12 }}>
            <Text style={{ color: colors.textSecondary }}>Erro ao carregar transações</Text>
            <TouchableOpacity onPress={() => transacoesQuery.refetch()} style={{ marginTop: 8 }}>
              <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text>
            </TouchableOpacity>
          </View>
        ) : transacoesQuery.data && transacoesQuery.data.content.length === 0 ? (
          <View style={{ alignItems: 'center', paddingVertical: 16 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Nenhuma transação ainda</Text>
            <Text style={{ color: colors.textSecondary, marginTop: 4, fontSize: 13 }}>Toque no + para lançar a primeira</Text>
          </View>
        ) : (
          transacoesQuery.data?.content.map((t, i, arr) => (
            <ListRow
              key={t.id}
              height={56}
              divider={i < arr.length - 1}
              icon={t.categoria?.icone || (t.tipo === 'ENTRADA' ? '↑' : '↓')}
              iconTone={t.tipo === 'ENTRADA' ? 'success' : 'danger'}
              title={t.descricao}
              subtitle={t.categoria?.nome ?? undefined}
              value={`${t.tipo === 'ENTRADA' ? '+' : '−'} ${formatCurrency(Number(t.valorTotal ?? 0))}`}
              valueTone={t.tipo === 'ENTRADA' ? 'success' : 'danger'}
            />
          ))
        )}
      </Card>
      </Entrance>

      {projecaoQuery.data && projecaoQuery.data.meses && projecaoQuery.data.meses.length > 0 && (
        <View style={{ marginTop: 24 }}>
          <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700', marginBottom: 10 }}>Projeção de caixa</Text>
          <Card padded radius={20}>
            {projecaoQuery.data.meses.map((m, i, arr) => (
              <View
                key={i}
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  paddingVertical: 10,
                  borderBottomWidth: i < arr.length - 1 ? 1 : 0,
                  borderBottomColor: colors.border,
                }}
              >
                <Text style={{ color: i === 0 ? colors.brandFg : colors.textSecondary, fontSize: 13, fontWeight: '600', width: 44 }}>
                  {m.periodo}
                </Text>
                <View style={{ flex: 1 }}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Saídas</Text>
                    <Text style={{ color: colors.danger, fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] }}>
                      {formatCurrency(m.totalSaidas)}
                    </Text>
                  </View>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 2 }}>
                    <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Saldo final</Text>
                    <Text
                      style={{
                        color: m.saldoFinal >= 0 ? colors.success : colors.danger,
                        fontSize: 12,
                        fontWeight: '700',
                        fontVariant: ['tabular-nums'],
                      }}
                    >
                      {formatCurrency(m.saldoFinal)}
                    </Text>
                  </View>
                </View>
              </View>
            ))}
          </Card>
        </View>
      )}

    </ScrollView>
  );
}
