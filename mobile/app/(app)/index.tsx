import React from 'react';
import { RefreshControl, ScrollView, View, Text, TouchableOpacity } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import api from '../../src/services/api';
import insightsService from '../../src/services/insightsService';
import metricasService from '../../src/services/metricasService';
import compromissosService from '../../src/services/compromissosService';
import { Transacao, PagedResponse, InsightsResponse, CompromissoItem } from '../../src/types';
import { useTheme } from '../../src/theme';
import { useAuth } from '../../src/context/AuthContext';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import Card from '../../src/components/ui/Card';
import ListRow from '../../src/components/ui/ListRow';
import NovaTransacaoModal, { LancamentoInicial } from '../../src/components/NovaTransacaoModal';
import Entrance from '../../src/components/ui/Entrance';
import FloatEmoji from '../../src/components/ui/FloatEmoji';
import { formatCurrency, formatDateOnlyBR, getInitials } from '../../src/utils/format';

// Home reduzida (PR-F3-07) — ordem fixa: Disponível para gastar; Compromissos
// próximos; lançar; cinco movimentações; uma recomendação textual; link para
// a Visão financeira. Máximo de 4 requests: métricas, compromissos,
// movimentações e insights.
export default function Dashboard() {
  const colors = useTheme();
  const router = useRouter();

  const [lancarAberto, setLancarAberto] = React.useState(false);
  // Repetir lançamento (PR-F3-05): pré-preenche e exige confirmação no Salvar
  const [repetirLancamento, setRepetirLancamento] = React.useState<LancamentoInicial | null>(null);

  const metricasQuery = useQuery({ queryKey: ['metricas'], queryFn: () => metricasService.obter() });
  const compromissosQuery = useQuery({
    queryKey: ['compromissos'],
    queryFn: () => compromissosService.listar(),
  });
  const transacoesQuery = useQuery<PagedResponse<Transacao>>({
    queryKey: ['transacoes-recentes'],
    queryFn: () => api.get<PagedResponse<Transacao>>('/v1/transacoes/minhas?page=0&size=5&sort=data,desc').then(r => r.data),
  });
  const insightsQuery = useQuery<InsightsResponse>({
    queryKey: ['insights'],
    queryFn: () => insightsService.buscar(),
  });

  const { usuario } = useAuth();
  const insets = useSafeAreaInsets();

  const primeiroNome = usuario?.nome?.split(' ')[0] ?? '';
  const saldo = formatCurrency(Number(metricasQuery.data?.disponivelParaGastar ?? 0));
  const [saldoInt, saldoCents] = saldo.split(',');
  const refreshing = metricasQuery.isRefetching || compromissosQuery.isRefetching
    || transacoesQuery.isRefetching || insightsQuery.isRefetching;

  const handleRefresh = async () => {
    await Promise.all([
      metricasQuery.refetch(),
      compromissosQuery.refetch(),
      transacoesQuery.refetch(),
      insightsQuery.refetch(),
    ]);
  };

  const comprometidos = compromissosQuery.data?.itens.filter(i => i.grupo === 'COMPROMETIDO') ?? [];
  const previstos = compromissosQuery.data?.itens.filter(i => i.grupo === 'PREVISTO') ?? [];
  // Recomendação: somente texto — números de insights nunca aparecem na home
  const recomendacao = insightsQuery.data?.recomendacoes?.[0] || insightsQuery.data?.resumo || null;

  const destinoCompromisso = (item: CompromissoItem) => {
    if (item.tipo === 'FATURA') return '/more/faturas';
    if (item.tipo === 'CONTA_FIXA') return '/more/contas-fixas';
    return '/(app)/transacoes';
  };

  const linhaCompromisso = (item: CompromissoItem, i: number, arr: CompromissoItem[]) => (
    <ListRow
      key={`${item.tipo}-${item.id}`}
      height={52}
      divider={i < arr.length - 1}
      icon={item.alerta === 'FALHA_SALDO' ? '⚠️' : item.tipo === 'FATURA' ? '💳' : item.tipo === 'PARCELA' ? '🧾' : '📅'}
      iconTone={item.alerta === 'FALHA_SALDO' ? 'danger' : item.grupo === 'COMPROMETIDO' ? 'danger' : 'neutral'}
      title={item.descricao}
      subtitle={item.alerta === 'FALHA_SALDO'
        ? 'Aguardando saldo — abra para resolver'
        : `Vence ${formatDateOnlyBR(item.vencimento)}`}
      value={formatCurrency(Number(item.valor))}
      valueTone={item.grupo === 'COMPROMETIDO' ? 'danger' : undefined}
      onPress={() => router.push(destinoCompromisso(item) as any)}
      accessibilityLabel={`${item.descricao}, ${formatCurrency(Number(item.valor))}, vence ${formatDateOnlyBR(item.vencimento)}${item.alerta === 'FALHA_SALDO' ? ', aguardando saldo' : ''}`}
    />
  );

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

      {/* 1. Disponível para gastar */}
      <Entrance kind="pop" delay={100}>
      <View
        style={{
          marginBottom: 16,
          borderRadius: 22,
          shadowColor: colors.brandDeep,
          shadowOpacity: 0.32,
          shadowRadius: 12,
          shadowOffset: { width: 0, height: 8 },
          elevation: 6,
        }}
      >
      <LinearGradient
        colors={[colors.brand, colors.brandDeep]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={{ borderRadius: 22, padding: 22, minHeight: 132, overflow: 'hidden' }}
      >
        <View
          pointerEvents="none"
          style={{
            position: 'absolute', inset: 0,
            backgroundColor: 'rgba(15,8,35,0.24)',
          }}
        />
        <View
          pointerEvents="none"
          style={{
            position: 'absolute', top: -54, right: -36, width: 156, height: 156,
            borderRadius: 78, backgroundColor: 'rgba(255,255,255,0.08)',
          }}
        />
        <View
          pointerEvents="none"
          style={{
            position: 'absolute', bottom: -74, left: -48, width: 176, height: 176,
            borderRadius: 88, backgroundColor: 'rgba(255,255,255,0.05)',
          }}
        />
        {metricasQuery.isLoading ? (
          <View style={{ gap: 12 }} accessibilityLabel="Carregando saldo disponível">
            <SkeletonBox width={88} height={16} borderRadius={8} tone="inverse" />
            <SkeletonBox width="72%" height={43} borderRadius={10} tone="inverse" />
          </View>
        ) : metricasQuery.isError ? (
          <View style={{ minHeight: 90, justifyContent: 'center', alignItems: 'flex-start' }}>
            <Text style={{ color: '#ffffff', fontSize: 15, fontWeight: '700' }}>Saldo indisponível</Text>
            <Text style={{ color: '#ffffff', fontSize: 13, marginTop: 4 }}>Não foi possível atualizar seus valores.</Text>
            <TouchableOpacity
              accessibilityRole="button"
              accessibilityLabel="Tentar carregar saldo novamente"
              activeOpacity={0.78}
              onPress={() => metricasQuery.refetch()}
              style={{ marginTop: 12, minHeight: 44, paddingHorizontal: 14, borderRadius: 999, backgroundColor: 'rgba(255,255,255,0.18)', justifyContent: 'center' }}
            >
              <Text style={{ color: '#ffffff', fontWeight: '700' }}>Tentar novamente</Text>
            </TouchableOpacity>
          </View>
        ) : metricasQuery.data ? (
          <>
            <Text style={{ color: '#ffffff', fontSize: 14, fontWeight: '600' }}>
              Disponível para gastar
            </Text>
            <Text
              numberOfLines={1}
              adjustsFontSizeToFit
              minimumFontScale={0.68}
              style={{ color: '#ffffff', fontSize: 40, lineHeight: 48, fontWeight: '800', letterSpacing: -1, marginTop: 2, fontVariant: ['tabular-nums'] }}
            >
              {saldoInt}
              {saldoCents != null && (
                <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: 23, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
                  ,{saldoCents}
                </Text>
              )}
            </Text>
          </>
        ) : null}
      </LinearGradient>
      </View>
      </Entrance>

      {/* 2. Compromissos próximos: comprometidos compõem o total; previstos ficam fora */}
      <Entrance delay={150}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>Compromissos próximos</Text>
        {compromissosQuery.data && (
          <Text
            accessibilityLabel={`Total comprometido: ${formatCurrency(Number(compromissosQuery.data.totalComprometido))}`}
            style={{ color: colors.danger, fontSize: 14, fontWeight: '800', fontVariant: ['tabular-nums'] }}
          >
            {formatCurrency(Number(compromissosQuery.data.totalComprometido))}
          </Text>
        )}
      </View>
      <Card padded radius={20} style={{ marginBottom: 16 }}>
        {compromissosQuery.isLoading ? (
          <View style={{ gap: 8 }}>
            {[1, 2].map(i => <SkeletonBox key={i} width="100%" height={48} />)}
          </View>
        ) : compromissosQuery.isError ? (
          <View style={{ alignItems: 'center', paddingVertical: 12 }}>
            <Text style={{ color: colors.textSecondary }}>Erro ao carregar compromissos</Text>
            <TouchableOpacity onPress={() => compromissosQuery.refetch()} style={{ marginTop: 8 }} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text>
            </TouchableOpacity>
          </View>
        ) : comprometidos.length === 0 && previstos.length === 0 ? (
          <Text style={{ color: colors.textSecondary, textAlign: 'center', paddingVertical: 12 }}>
            Nenhum compromisso até o fim do mês.
          </Text>
        ) : (
          <>
            {comprometidos.map((item, i, arr) => linhaCompromisso(item, i, arr))}
            {previstos.length > 0 && (
              <>
                <Text style={{ color: colors.textSecondary, fontSize: 11, letterSpacing: 0.8, textTransform: 'uppercase', marginTop: comprometidos.length > 0 ? 14 : 0, marginBottom: 4 }}>
                  Previstos · fora do total
                </Text>
                {previstos.map((item, i, arr) => linhaCompromisso(item, i, arr))}
              </>
            )}
          </>
        )}
      </Card>
      </Entrance>

      {/* 3. Lançar */}
      <Entrance delay={200}>
      <TouchableOpacity
        onPress={() => setLancarAberto(true)}
        activeOpacity={0.85}
        accessibilityRole="button"
        accessibilityLabel="Lançar transação"
        style={{ marginBottom: 16 }}
      >
        <LinearGradient
          colors={[colors.brand, colors.brandDeep]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={{ borderRadius: 16, minHeight: 52, alignItems: 'center', justifyContent: 'center' }}
        >
          <Text style={{ color: '#ffffff', fontSize: 16, fontWeight: '700' }}>＋ Lançar</Text>
        </LinearGradient>
      </TouchableOpacity>
      </Entrance>

      {/* 4. Cinco movimentações */}
      <Entrance delay={250}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>Últimas movimentações</Text>
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
            <Text style={{ color: colors.textSecondary, marginTop: 4, fontSize: 13 }}>Toque em Lançar para registrar a primeira</Text>
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
              trailing={
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  <Text style={{ color: t.tipo === 'ENTRADA' ? colors.success : colors.danger, fontSize: 14, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
                    {`${t.tipo === 'ENTRADA' ? '+' : '−'} ${formatCurrency(Number(t.valorTotal ?? 0))}`}
                  </Text>
                  <TouchableOpacity
                    onPress={() => setRepetirLancamento({
                      descricao: t.descricao,
                      valor: Number(t.valorTotal ?? 0),
                      tipo: t.tipo,
                      categoriaId: t.categoria?.id,
                      cartaoId: t.cartao?.id,
                    })}
                    accessibilityRole="button"
                    accessibilityLabel={`Repetir lançamento ${t.descricao}`}
                    hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                    style={{ paddingLeft: 10 }}
                  >
                    <Text style={{ color: colors.brandFg, fontSize: 16 }}>⟳</Text>
                  </TouchableOpacity>
                </View>
              }
            />
          ))
        )}
      </Card>
      </Entrance>

      {/* 5. Uma recomendação textual (nunca números de insights) */}
      {recomendacao && (
        <Entrance delay={300}>
          <Card padded radius={20} style={{ marginTop: 16 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '700' }}>Recomendação</Text>
            <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>{recomendacao}</Text>
          </Card>
        </Entrance>
      )}

      {/* 6. Link para a Visão financeira */}
      <Entrance delay={350}>
        <TouchableOpacity
          onPress={() => router.push('/more/visao-financeira' as any)}
          accessibilityRole="button"
          accessibilityLabel="Abrir visão financeira completa"
          style={{ marginTop: 16, minHeight: 48, borderRadius: 16, borderWidth: 1, borderColor: colors.border, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.card }}
        >
          <Text style={{ color: colors.brandFg, fontSize: 14, fontWeight: '700' }}>Ver visão financeira completa ›</Text>
        </TouchableOpacity>
      </Entrance>

      <NovaTransacaoModal
        visible={lancarAberto || repetirLancamento != null}
        initialData={repetirLancamento}
        onClose={() => { setLancarAberto(false); setRepetirLancamento(null); }}
        onSaved={() => { setLancarAberto(false); setRepetirLancamento(null); }}
      />
    </ScrollView>
  );
}
