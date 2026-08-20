import React from 'react';
import { RefreshControl, ScrollView, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import Ionicons from '@expo/vector-icons/Ionicons';
import notificacaoService from '../../src/services/notificacaoService';
import { Notificacao, TipoNotificacao } from '../../src/types';
import { useTheme, radius, spacing, typography, screenPadding, useTabBarSpace } from '../../src/theme';
import BackButton from '../../src/components/ui/BackButton';
import Card from '../../src/components/ui/Card';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import { formatDateOnlyBR } from '../../src/utils/format';

// Caixa de notificações in-app (V42). O destino de cada aviso vem do backend
// no padrão do PR-F3-04 — o cliente nunca inventa link aproximado.
const ICONE: Record<TipoNotificacao, React.ComponentProps<typeof Ionicons>['name']> = {
  FATURA_VENCENDO: 'card',
  PARCELA_AGENDADA: 'time',
  FALHA_SALDO: 'alert-circle',
  ORCAMENTO_ESTOURADO: 'trending-up',
  META_ATINGIDA: 'trophy',
};

const ROTA_POR_DESTINO: Record<string, string> = {
  FATURA: '/more/faturas',
  TRANSACAO: '/transacoes',
  CONTA_FIXA: '/more/contas-fixas',
  ORCAMENTO: '/more/orcamentos',
  META: '/(app)/metas',
};

export default function Notificacoes() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['notificacoes'],
    queryFn: () => notificacaoService.listar(0, 50),
  });

  const invalidar = () => {
    queryClient.invalidateQueries({ queryKey: ['notificacoes'] });
    queryClient.invalidateQueries({ queryKey: ['home'] });
  };

  const marcarLida = useMutation({
    mutationFn: (id: number) => notificacaoService.marcarComoLida(id),
    onSuccess: invalidar,
  });
  const marcarTodas = useMutation({
    mutationFn: () => notificacaoService.marcarTodasComoLidas(),
    onSuccess: invalidar,
  });

  const itens = query.data?.content ?? [];
  const naoLidas = itens.filter(n => !n.lida).length;

  const abrir = (n: Notificacao) => {
    if (!n.lida) marcarLida.mutate(n.id);
    const rota = n.destino ? ROTA_POR_DESTINO[n.destino] : undefined;
    if (rota) router.push(rota as never);
  };

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{
        paddingTop: insets.top + spacing.md,
        paddingHorizontal: screenPadding,
        paddingBottom: tabBarSpace,
      }}
      refreshControl={
        <RefreshControl
          refreshing={query.isRefetching}
          onRefresh={() => query.refetch()}
          tintColor={colors.brand}
        />
      }
    >
      <BackButton />

      <View style={{
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        marginTop: spacing.md, marginBottom: spacing.lg,
      }}>
        <Text style={{ ...typography.section, color: colors.textPrimary }}>Notificações</Text>
        {naoLidas > 0 && (
          <TouchableOpacity
            onPress={() => marcarTodas.mutate()}
            accessibilityRole="button"
            accessibilityLabel="Marcar todas como lidas"
            style={{ minHeight: 44, justifyContent: 'center' }}
          >
            <Text style={{ ...typography.label, color: colors.brandFg }}>Marcar todas</Text>
          </TouchableOpacity>
        )}
      </View>

      {query.isLoading ? (
        <View style={{ gap: spacing.md }}>
          {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={76} borderRadius={radius.lg} />)}
        </View>
      ) : query.isError ? (
        <Card padded radius={radius.xl}>
          <Text style={{ color: colors.textSecondary, textAlign: 'center' }}>
            Erro ao carregar notificações
          </Text>
          <TouchableOpacity
            onPress={() => query.refetch()}
            accessibilityRole="button"
            style={{ marginTop: spacing.sm, minHeight: 44, justifyContent: 'center' }}
          >
            <Text style={{ ...typography.button, color: colors.brandFg, textAlign: 'center' }}>
              Tentar novamente
            </Text>
          </TouchableOpacity>
        </Card>
      ) : itens.length === 0 ? (
        <Card padded radius={radius.xl}>
          <View style={{ alignItems: 'center', paddingVertical: spacing.lg }}>
            <Ionicons name="notifications-off-outline" size={30} color={colors.textMuted} />
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginTop: spacing.md }}>
              Nada por aqui
            </Text>
            <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: 4, textAlign: 'center' }}>
              Avisamos quando uma fatura vencer, uma parcela chegar ou um orçamento estourar.
            </Text>
          </View>
        </Card>
      ) : (
        <View style={{ gap: spacing.md }}>
          {itens.map(n => (
            <TouchableOpacity
              key={n.id}
              onPress={() => abrir(n)}
              activeOpacity={0.85}
              accessibilityRole="button"
              accessibilityLabel={`${n.titulo}. ${n.mensagem}${n.lida ? '' : '. Não lida'}`}
              style={{
                flexDirection: 'row', gap: spacing.md,
                padding: spacing.lg,
                borderRadius: radius.lg,
                backgroundColor: colors.card,
                borderWidth: 1,
                borderColor: n.lida ? colors.border : colors.brand,
              }}
            >
              <View style={{
                width: 40, height: 40, borderRadius: radius.md,
                backgroundColor: colors.brandBg,
                alignItems: 'center', justifyContent: 'center',
              }}>
                <Ionicons name={ICONE[n.tipo] ?? 'notifications'} size={19} color={colors.brandFg} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>{n.titulo}</Text>
                <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: 2 }}>
                  {n.mensagem}
                </Text>
                <Text style={{ ...typography.meta, color: colors.textMuted, marginTop: 6 }}>
                  {formatDateOnlyBR(n.criadaEm.slice(0, 10))}
                </Text>
              </View>
              {!n.lida && (
                <View style={{
                  width: 8, height: 8, borderRadius: 4,
                  backgroundColor: colors.brand, marginTop: 6,
                }} />
              )}
            </TouchableOpacity>
          ))}
        </View>
      )}
    </ScrollView>
  );
}
