import React from 'react';
import { RefreshControl, ScrollView, Text, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import api from '../../../src/services/api';
import metricasService from '../../../src/services/metricasService';
import { MetricaId, ProjecaoResponse } from '../../../src/types';
import {
  useTheme, useTabBarSpace, numeric, radius, screenPadding, spacing, typography,
} from '../../../src/theme';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import Card from '../../../src/components/ui/Card';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import ListRow from '../../../src/components/ui/ListRow';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';
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

const dataCurta = (iso: string) => new Date(`${iso}T12:00:00`).toLocaleDateString('pt-BR');

// Visão financeira (PR-F3-06): as 9 métricas oficiais vindas SOMENTE de
// /v1/metricas, cada uma com drill de composição; projeção mantém endpoint
// próprio.
export default function VisaoFinanceira() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
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

  const projecao = projecaoQuery.data?.meses ?? [];

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingBottom: tabBarSpace }}
      refreshControl={
        <RefreshControl
          refreshing={metricasQuery.isRefetching || projecaoQuery.isRefetching}
          onRefresh={() => { metricasQuery.refetch(); projecaoQuery.refetch(); }}
          tintColor={colors.brand}
        />
      }
    >
      <CabecalhoSubTela
        titulo="Visão financeira"
        apoio={
          <Text style={{ ...typography.body, color: colors.textSecondary }}>
            Métricas oficiais — toque para ver a composição
          </Text>
        }
      />

      <View style={{ paddingHorizontal: screenPadding }}>
        {metricasQuery.isLoading ? (
          <View style={{ gap: spacing.sm }}>
            {[1, 2, 3, 4, 5].map(i => <SkeletonBox key={i} width="100%" height={64} borderRadius={radius.lg} />)}
          </View>
        ) : metricasQuery.isError ? (
          <EstadoVazio
            emoji="📶"
            titulo="Não deu para carregar as métricas"
            texto="Verifique sua conexão e tente de novo."
            acao={{ rotulo: 'Tentar de novo', onPress: () => metricasQuery.refetch() }}
          />
        ) : (
          <>
            {/* Linhas num card único, não nove cards soltos: o rótulo curado que
                havia aqui apagava a descrição da métrica do leitor de tela. */}
            <Card padded radius={radius.xl}>
              {metricas.map(([id, label, valor], i, arr) => (
                <ListRow
                  key={id}
                  title={label}
                  subtitle={DESCRICOES[id]}
                  value={formatCurrency(Number(valor))}
                  valueTone={Number(valor) < 0 ? 'danger' : undefined}
                  divider={i < arr.length - 1}
                  onPress={() => setMetricaSelecionada(id)}
                  dica="Abre a composição da métrica"
                />
              ))}
            </Card>

            {!!m && (
              <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.md }}>
                Referência {dataCurta(m.dataReferencia)} · Comprometido até {dataCurta(m.horizonteComprometido)}
              </Text>
            )}
          </>
        )}

        <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginTop: spacing.xxl, marginBottom: spacing.md }}>
          Projeção de caixa
        </Text>
        {projecaoQuery.isLoading ? (
          <SkeletonBox width="100%" height={180} borderRadius={radius.xl} />
        ) : projecaoQuery.isError ? (
          // Antes a seção simplesmente sumia quando a projeção falhava: o usuário
          // não distinguia "não há projeção" de "não carregou".
          <EstadoVazio
            compacto
            emoji="📶"
            titulo="Não deu para carregar a projeção"
            texto="Verifique sua conexão e tente de novo."
            acao={{ rotulo: 'Tentar de novo', onPress: () => projecaoQuery.refetch() }}
          />
        ) : projecao.length === 0 ? (
          <EstadoVazio
            compacto
            emoji="🔮"
            titulo="Sem projeção para os próximos meses"
            texto="Cadastre recorrências e parcelas para o app projetar o caixa."
          />
        ) : (
          <Card padded radius={radius.xl}>
            {projecao.map((mes, i, arr) => (
              <View
                key={mes.periodo}
                style={{
                  flexDirection: 'row', alignItems: 'center',
                  paddingVertical: spacing.md,
                  borderBottomWidth: i < arr.length - 1 ? 1 : 0,
                  borderBottomColor: colors.border,
                }}
              >
                {/* O primeiro mês é o corrente: fica em cor de marca. */}
                <Text style={{
                  ...typography.meta, fontWeight: '600', width: 44,
                  color: i === 0 ? colors.brandFg : colors.textSecondary,
                }}>
                  {mes.periodo}
                </Text>
                <View style={{ flex: 1 }}>
                  <LinhaDaProjecao rotulo="Entradas" valor={mes.totalEntradas ?? 0} cor={colors.success} />
                  <LinhaDaProjecao rotulo="Saídas" valor={mes.totalSaidas} cor={colors.danger} />
                  <LinhaDaProjecao
                    rotulo="Saldo final"
                    valor={mes.saldoFinal}
                    cor={mes.saldoFinal >= 0 ? colors.success : colors.danger}
                    forte
                  />
                </View>
              </View>
            ))}
          </Card>
        )}
      </View>

      <ComposicaoMetricaModal metrica={metricaSelecionada} onClose={() => setMetricaSelecionada(null)} />
    </ScrollView>
  );
}

const LinhaDaProjecao = ({ rotulo, valor, cor, forte = false }: {
  rotulo: string;
  valor: number;
  cor: string;
  forte?: boolean;
}) => {
  const colors = useTheme();
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm, marginTop: spacing.xxs }}>
      <Text style={{ ...typography.meta, color: colors.textSecondary }}>{rotulo}</Text>
      <Text style={{ ...typography.meta, ...numeric, fontWeight: forte ? '700' : '600', color: cor }}>
        {formatCurrency(valor)}
      </Text>
    </View>
  );
};
