import React from 'react';
import { RefreshControl, ScrollView, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import Ionicons from '@expo/vector-icons/Ionicons';
import homeService from '../../../src/services/homeService';
import { Transacao } from '../../../src/types';
import { useTheme, radius, spacing, typography, numeric, screenPadding, useTabBarSpace } from '../../../src/theme';
import { useAuth } from '../../../src/context/AuthContext';
import { useOperacoesFiltro } from '../../../src/hooks/useOperacoesFiltro';
import SaldoCard from '../../../src/components/home/SaldoCard';
import ParcelasCarrossel from '../../../src/components/home/ParcelasCarrossel';
import OperacoesFiltro from '../../../src/components/home/OperacoesFiltro';
import Card from '../../../src/components/ui/Card';
import ListRow from '../../../src/components/ui/ListRow';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';
import Entrance from '../../../src/components/ui/Entrance';
import NovaTransacaoModal, { LancamentoInicial } from '../../../src/components/NovaTransacaoModal';
import { formatCurrency, formatDateLongBR, getInitials } from '../../../src/utils/format';
import { isSaldoOculto, setSaldoOculto } from '../../../src/store/saldoVisivel';
import { dismissHomeChecklist, isHomeChecklistDismissed } from '../../../src/store/homeChecklist';

// Home da referência (mobile/.design/referencia-home.png): cabeçalho com
// saudação e sino, card de saldo com breakdown e três colunas, carrossel de
// parcelas agendadas e a seção Operações com filtros, busca e lista.
//
// Dois requests: /v1/home (agregado) e /v1/transacoes/periodo (paginado).
export default function Home() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { usuario } = useAuth();

  const [lancarAberto, setLancarAberto] = React.useState(false);
  const [repetirLancamento, setRepetirLancamento] = React.useState<LancamentoInicial | null>(null);
  const [oculto, setOculto] = React.useState(false);

  React.useEffect(() => { isSaldoOculto().then(setOculto); }, []);

  const alternarOculto = () => {
    setOculto(atual => {
      const proximo = !atual;
      setSaldoOculto(proximo).catch(() => {});
      return proximo;
    });
  };

  const homeQuery = useQuery({ queryKey: ['home'], queryFn: () => homeService.obter() });
  const operacoes = useOperacoesFiltro();

  const home = homeQuery.data;
  const primeiroNome = usuario?.nome?.split(' ')[0] ?? '';
  const refreshing = homeQuery.isRefetching || operacoes.query.isRefetching;

  const atualizar = async () => {
    await Promise.all([homeQuery.refetch(), operacoes.query.refetch()]);
  };

  // Checklist de setup (PR-F3-10): o que o usuário pulou no onboarding continua
  // convidando aqui. Derivado só das queries que a home já faz — nenhum request
  // extra — e dispensável de vez. Sumiu no redesign de navegação (9a3b205) e
  // voltou junto com o onboarding em etapas.
  const [checklistOculto, setChecklistOculto] = React.useState(true);
  React.useEffect(() => { isHomeChecklistDismissed().then(setChecklistOculto); }, []);
  const ocultarChecklist = () => {
    setChecklistOculto(true);
    dismissHomeChecklist().catch(() => {});
  };

  const checklistItens: Array<{ label: string; onPress: () => void }> = [];
  if (!checklistOculto && home && !operacoes.query.isLoading) {
    if (operacoes.total === 0 && !operacoes.filtrando) {
      checklistItens.push({ label: 'Lance sua primeira movimentação', onPress: () => setLancarAberto(true) });
    }
    if (home.categorias.length === 0) {
      checklistItens.push({ label: 'Escolha suas categorias', onPress: () => router.push('/more/categorias') });
    }
    if (Number(home.metricas.reservado) === 0) {
      checklistItens.push({ label: 'Crie sua primeira meta', onPress: () => router.push('/metas') });
    }
    if (Number(home.saldoEmCartoes) === 0 && Number(home.totalFaturas) === 0) {
      checklistItens.push({ label: 'Cadastre seu cartão', onPress: () => router.push('/more/faturas') });
    }
  }

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{
        paddingTop: insets.top + spacing.md,
        paddingHorizontal: screenPadding,
        paddingBottom: tabBarSpace,
      }}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={atualizar} tintColor={colors.brand} />
      }
      onMomentumScrollEnd={({ nativeEvent: e }) => {
        const fim = e.layoutMeasurement.height + e.contentOffset.y >= e.contentSize.height - 240;
        if (fim && operacoes.query.hasNextPage && !operacoes.query.isFetchingNextPage) {
          operacoes.query.fetchNextPage();
        }
      }}
      scrollEventThrottle={16}
    >
      {/* 1. Cabeçalho */}
      <Entrance delay={50}>
        <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: spacing.lg }}>
          <TouchableOpacity
            onPress={() => router.push('/(app)/perfil')}
            accessibilityRole="button"
            accessibilityLabel="Abrir perfil"
            style={{
              width: 44, height: 44, borderRadius: 22,
              backgroundColor: colors.brandBg,
              alignItems: 'center', justifyContent: 'center',
            }}
          >
            <Text style={{ ...typography.cardTitle, color: colors.brandFg }}>
              {usuario?.nome ? getInitials(usuario.nome) : ''}
            </Text>
          </TouchableOpacity>

          <View style={{ flex: 1, marginLeft: spacing.md }}>
            <Text style={{ ...typography.greeting, color: colors.accent }}>
              Olá, {primeiroNome}!
            </Text>
            <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: 2 }}>
              {formatDateLongBR()}
            </Text>
          </View>

          <TouchableOpacity
            onPress={() => router.push('/(app)/notificacoes' as never)}
            accessibilityRole="button"
            accessibilityLabel={
              home?.naoLidas
                ? `Notificações, ${home.naoLidas} não lidas`
                : 'Notificações'
            }
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
            style={{ width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }}
          >
            <Ionicons name="notifications-outline" size={25} color={colors.textPrimary} />
            {!!home?.naoLidas && (
              <View style={{
                position: 'absolute', top: 2, right: 2,
                minWidth: 19, height: 19, borderRadius: 10,
                paddingHorizontal: 5,
                backgroundColor: colors.brand,
                alignItems: 'center', justifyContent: 'center',
              }}>
                <Text style={{ fontSize: 11, fontWeight: '700', color: colors.brandText }}>
                  {home.naoLidas > 9 ? '9+' : home.naoLidas}
                </Text>
              </View>
            )}
          </TouchableOpacity>
        </View>
      </Entrance>

      {/* 2. Saldo */}
      <Entrance kind="pop" delay={100}>
        <SaldoCard
          saldo={Number(home?.metricas.disponivelParaGastar ?? 0)}
          emConta={Number(home?.saldoEmConta ?? 0)}
          emCartoes={Number(home?.saldoEmCartoes ?? 0)}
          entradas={Number(home?.totalEntradas ?? 0)}
          saidas={Number(home?.totalSaidas ?? 0)}
          faturas={Number(home?.totalFaturas ?? 0)}
          oculto={oculto}
          onToggleOculto={alternarOculto}
          onNovaTransacao={() => setLancarAberto(true)}
          onCarteira={() => router.push('/more/carteiras' as never)}
          carregando={homeQuery.isLoading}
          erro={homeQuery.isError}
          onTentarNovamente={() => homeQuery.refetch()}
        />
      </Entrance>

      {/* 3. Parcelas agendadas */}
      <Entrance delay={150}>
        <View style={{ marginTop: spacing.md }}>
          <ParcelasCarrossel
            itens={home?.parcelasAgendadas ?? []}
            carregando={homeQuery.isLoading}
            onVerTodas={() => router.push('/more/faturas' as never)}
            onAbrir={item => router.push(
              item.transacaoId
                ? `/transacoes?transacaoId=${item.transacaoId}` as never
                : '/more/faturas' as never,
            )}
          />
        </View>
      </Entrance>

      {/* 4. Operações */}
      <Entrance delay={200}>
        <View style={{ marginTop: spacing.md }}>
          <OperacoesFiltro
            categorias={home?.categorias ?? []}
            categoriaId={operacoes.categoriaId}
            onCategoria={operacoes.setCategoriaId}
            periodo={operacoes.periodo}
            onPeriodo={operacoes.setPeriodo}
            tipo={operacoes.tipo}
            onTipo={operacoes.setTipo}
            busca={operacoes.busca}
            onBusca={operacoes.setBusca}
            total={operacoes.total}
          />
        </View>
      </Entrance>

      {/* 5. Lista de operações */}
      <Entrance delay={250}>
        <Card padded radius={radius.xl} style={{ marginTop: spacing.lg }}>
          {operacoes.query.isLoading ? (
            <View style={{ gap: spacing.sm }}>
              {[1, 2, 3, 4, 5].map(i => <SkeletonBox key={i} width="100%" height={56} />)}
            </View>
          ) : operacoes.query.isError ? (
            <View style={{ alignItems: 'center', paddingVertical: spacing.md }}>
              <Text style={{ color: colors.textSecondary }}>Erro ao carregar operações</Text>
              <TouchableOpacity
                onPress={() => operacoes.query.refetch()}
                accessibilityRole="button"
                style={{ marginTop: spacing.sm, minHeight: 44, justifyContent: 'center' }}
              >
                <Text style={{ ...typography.button, color: colors.brandFg }}>Tentar novamente</Text>
              </TouchableOpacity>
            </View>
          ) : operacoes.itens.length === 0 ? (
            <View style={{ alignItems: 'center', paddingVertical: spacing.lg }}>
              <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>
                {operacoes.filtrando ? 'Nada com esse filtro' : 'Nenhuma operação ainda'}
              </Text>
              <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: 4 }}>
                {operacoes.filtrando
                  ? 'Ajuste os filtros ou a busca para ver outras.'
                  : 'Toque em Nova Transação para registrar a primeira.'}
              </Text>
            </View>
          ) : (
            operacoes.itens.map((t: Transacao, i, arr) => (
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
                    <Text style={{
                      ...typography.value, ...numeric,
                      color: t.tipo === 'ENTRADA' ? colors.success : colors.danger,
                    }}>
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
                      style={{ paddingLeft: spacing.sm }}
                    >
                      <Ionicons name="repeat" size={16} color={colors.brandFg} />
                    </TouchableOpacity>
                  </View>
                }
              />
            ))
          )}
          {operacoes.query.isFetchingNextPage && (
            <SkeletonBox width="100%" height={56} />
          )}
        </Card>
      </Entrance>

      {/* 6. Checklist de setup: discreto, opcional, nunca bloqueia */}
      {checklistItens.length > 0 && (
        <Entrance delay={300}>
          <Card padded radius={radius.xl} style={{ marginTop: spacing.lg }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text style={{ ...typography.meta, color: colors.textSecondary, fontWeight: '700', letterSpacing: 0.4, textTransform: 'uppercase' }}>
                Complete seu setup
              </Text>
              <TouchableOpacity
                onPress={ocultarChecklist}
                accessibilityRole="button"
                accessibilityLabel="Ocultar checklist de setup"
                hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              >
                <Text style={{ ...typography.meta, color: colors.textMuted }}>Ocultar</Text>
              </TouchableOpacity>
            </View>
            {checklistItens.map((item, i) => (
              <ListRow
                key={item.label}
                height={48}
                divider={i < checklistItens.length - 1}
                icon="›"
                iconTone="brand"
                title={item.label}
                onPress={item.onPress}
                accessibilityLabel={item.label}
              />
            ))}
          </Card>
        </Entrance>
      )}

      <NovaTransacaoModal
        visible={lancarAberto || repetirLancamento != null}
        initialData={repetirLancamento}
        onClose={() => { setLancarAberto(false); setRepetirLancamento(null); }}
        onSaved={() => {
          setLancarAberto(false);
          setRepetirLancamento(null);
          atualizar();
        }}
      />
    </ScrollView>
  );
}
