import React, { useEffect, useMemo, useRef, useState } from 'react';
import { View, Text, FlatList, RefreshControl } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { transacaoService } from '../../../src/services/transacaoService';
import relatorioService from '../../../src/services/relatorioService';
import {
  useTheme, useTabBarSpace, numeric, radius, screenPadding, spacing, typography,
} from '../../../src/theme';
import { formatCurrency, formatDate } from '../../../src/utils/format';
import { competenciaDe, ehCompetenciaCorrente, intervaloDoMes, somarMeses } from '../../../src/domain/periodo';
import { TipoTransacao, Transacao } from '../../../src/types';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import CampoBusca from '../../../src/components/ui/CampoBusca';
import NavegadorDeMes from '../../../src/components/ui/NavegadorDeMes';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';
import ListRow from '../../../src/components/ui/ListRow';
import Chip from '../../../src/components/ui/Chip';
import Card from '../../../src/components/ui/Card';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import EditarTransacaoModal from '../../../src/components/EditarTransacaoModal';
import { emojiDaCategoria } from '../../../src/domain/iconeCategoria';

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

export default function Transacoes() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  // Drill-down (PR-F3-08): filtros/transação chegam por rota (navegação do backend)
  const params = useLocalSearchParams<{ inicio?: string; tipo?: string; transacaoId?: string }>();

  const [mesRef, setMesRef] = useState(() => {
    if (params.inicio && /^\d{4}-\d{2}-\d{2}$/.test(params.inicio)) {
      const [ano, mes] = params.inicio.split('-').map(Number);
      return new Date(ano, mes - 1, 1);
    }
    const hoje = new Date();
    return new Date(hoje.getFullYear(), hoje.getMonth(), 1);
  });
  const [filtro, setFiltro] = useState<'TODOS' | TipoTransacao>(
    params.tipo === 'ENTRADA' || params.tipo === 'SAIDA' ? params.tipo : 'TODOS');
  const [busca, setBusca] = useState('');
  const [buscaAtiva, setBuscaAtiva] = useState('');
  const [selecionada, setSelecionada] = useState<Transacao | null>(null);
  const transacaoIdAberta = useRef<string | null>(null);

  // Parcela → transação: abre o detalhe da transação chegada por rota
  useEffect(() => {
    const id = params.transacaoId;
    if (!id || transacaoIdAberta.current === id) return;
    transacaoIdAberta.current = id;
    transacaoService.buscarPorId(Number(id))
      .then(t => setSelecionada(t))
      .catch(() => {}); // transação inacessível: permanece na lista, sem detalhe
  }, [params.transacaoId]);

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
  const { mes, ano } = competenciaDe(mesRef);
  const ehMesAtual = ehCompetenciaCorrente(mes, ano);

  const mudarMes = (delta: number) => setMesRef(m => somarMeses(m, delta));

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <CabecalhoSubTela titulo="Transações" />

      <View style={{ paddingHorizontal: screenPadding }}>
        <NavegadorDeMes
          rotulo={mesLabel}
          apoio={isLoading ? ' ' : `${total} ${total === 1 ? 'transação' : 'transações'}`}
          onAnterior={() => mudarMes(-1)}
          onProximo={() => mudarMes(1)}
          podeAvancar={!ehMesAtual}
        />
      </View>

      {/* Resumo do mês: totais do backend (todas as páginas, não só as carregadas) */}
      <View style={{
        flexDirection: 'row', gap: spacing.md,
        paddingHorizontal: screenPadding, paddingVertical: spacing.md,
      }}>
        <TotalDoMes
          rotulo="Entradas no mês"
          valor={resumoQuery.data?.totalEntradas ?? 0}
          sinal="+"
          cor={colors.success}
          carregando={resumoQuery.isLoading}
        />
        <TotalDoMes
          rotulo="Saídas no mês"
          valor={resumoQuery.data?.totalSaidas ?? 0}
          sinal="−"
          cor={colors.danger}
          carregando={resumoQuery.isLoading}
        />
      </View>

      <View style={{ flexDirection: 'row', paddingHorizontal: screenPadding }}>
        <CampoBusca valor={busca} onChange={setBusca} placeholder="Buscar por descrição" />
      </View>

      <View style={{ flexDirection: 'row', gap: spacing.sm, paddingHorizontal: screenPadding, paddingVertical: spacing.md }}>
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
        contentContainerStyle={{ paddingHorizontal: screenPadding, paddingBottom: tabBarSpace }}
        refreshControl={<RefreshControl refreshing={isRefetching && !isFetchingNextPage} onRefresh={refetch} tintColor={colors.brand} />}
        onEndReached={() => { if (hasNextPage && !isFetchingNextPage) fetchNextPage(); }}
        onEndReachedThreshold={0.4}
        ListFooterComponent={isFetchingNextPage ? (
          <View style={{ paddingVertical: spacing.md }}>
            <SkeletonBox width="100%" height={64} />
          </View>
        ) : null}
        ListEmptyComponent={isLoading ? (
          <View style={{ gap: spacing.sm }}>
            {[1, 2, 3, 4, 5].map(i => <SkeletonBox key={i} width="100%" height={64} />)}
          </View>
        ) : isError ? (
          <EstadoVazio
            emoji="📶"
            titulo="Não deu para carregar suas transações"
            texto="Verifique sua conexão e tente de novo."
            acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
          />
        ) : buscaAtiva || filtro !== 'TODOS' ? (
          <EstadoVazio
            emoji="🔍"
            titulo="Nada encontrado"
            texto="Ajuste a busca ou os filtros para ver outras transações."
          />
        ) : (
          <EstadoVazio
            emoji="📄"
            titulo={`Nenhuma transação em ${mesLabel}`}
            texto="Toque no + para lançar a primeira"
          />
        )}
        renderItem={({ item: t }) => (
          <ListRow
            icon={emojiDaCategoria(t.categoria, t.tipo === 'ENTRADA' ? '↑' : '↓')}
            iconTone={t.tipo === 'ENTRADA' ? 'success' : 'danger'}
            iconCor={t.categoria?.cor}
            title={t.descricao}
            subtitle={`${formatDate(t.data)} · ${t.categoria?.nome ?? 'Sem categoria'}${t.parcelado && t.totalParcelas ? ` · ${t.totalParcelas}x` : ''}`}
            value={`${t.tipo === 'ENTRADA' ? '+' : '−'} ${formatCurrency(Number(t.valorTotal ?? 0))}`}
            valueTone={t.tipo === 'ENTRADA' ? 'success' : 'danger'}
            onPress={() => setSelecionada(t)}
            dica="Abre a edição da transação"
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


/** Entradas ou saídas do mês inteiro — vem do backend, não da página carregada. */
const TotalDoMes = ({ rotulo, valor, sinal, cor, carregando }: {
  rotulo: string;
  valor: number;
  sinal: string;
  cor: string;
  carregando: boolean;
}) => {
  const colors = useTheme();
  return (
    <Card radius={radius.lg} style={{ flex: 1, padding: spacing.md }}>
      <Text style={{ ...typography.meta, color: colors.textSecondary }}>{rotulo}</Text>
      {carregando ? (
        <View style={{ marginTop: spacing.xs }}><SkeletonBox width={90} height={18} /></View>
      ) : (
        <Text
          numberOfLines={1}
          adjustsFontSizeToFit
          minimumFontScale={0.7}
          style={{ ...typography.value, ...numeric, fontWeight: '800', color: cor, marginTop: spacing.xxs }}
        >
          {sinal} {formatCurrency(valor)}
        </Text>
      )}
    </Card>
  );
};
