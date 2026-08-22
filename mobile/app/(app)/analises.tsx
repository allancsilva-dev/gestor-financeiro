import React, { useState } from 'react';
import { View, Text, ScrollView } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import {
  useTheme, useTabBarSpace, cardRadius, numeric, radius, screenPadding, spacing, typography,
} from '../../src/theme';
import Badge from '../../src/components/ui/Badge';
import CabecalhoDeTela from '../../src/components/ui/CabecalhoDeTela';
import Card from '../../src/components/ui/Card';
import Chip from '../../src/components/ui/Chip';
import Entrance from '../../src/components/ui/Entrance';
import EstadoVazio from '../../src/components/ui/EstadoVazio';
import IconTile from '../../src/components/ui/IconTile';
import ListRow from '../../src/components/ui/ListRow';
import ProgressBar from '../../src/components/ui/ProgressBar';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import relatorioService from '../../src/services/relatorioService';
import { PERIODOS, Periodo, intervaloDoPeriodo } from '../../src/domain/periodo';
import { formatCurrency, formatDate, formatPercent } from '../../src/utils/format';

/** Altura útil das colunas do gráfico de evolução — geometria, não escala. */
const ALTURA_BARRA = 96;
/** Largura de cada coluna do par entradas/saídas. */
const LARGURA_BARRA = 9;

export default function RelatorioScreen() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const [periodo, setPeriodo] = useState<Periodo>('mes');
  const { inicio, fim } = intervaloDoPeriodo(periodo);

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

  // A barra de cada categoria é relativa à maior do período. Antes isto lia
  // `gastosPorCategoria[0]`, assumindo que o backend já mandava ordenado — se um
  // dia mandasse por nome, a régua de 100% viria de uma categoria qualquer.
  const maiorGasto = Math.max(...(data?.gastosPorCategoria ?? []).map(c => c.valorTotal), 0);

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
      <CabecalhoDeTela titulo="Relatórios" />

      {/* Os filtros ficam fora do scroll: trocar de período é o que mais se faz
          aqui, e a régua de datas pertence ao filtro, não ao título. */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ gap: spacing.sm, paddingHorizontal: screenPadding }}
      >
        {PERIODOS.map(p => (
          <Chip key={p.key} label={p.label} selected={periodo === p.key} onPress={() => setPeriodo(p.key)} />
        ))}
      </ScrollView>
      <Text style={{
        ...typography.meta, color: colors.textSecondary,
        paddingHorizontal: screenPadding, marginTop: spacing.sm, marginBottom: spacing.lg,
      }}>
        {formatDate(inicio)} até {formatDate(fim)}
      </Text>

      {isLoading ? (
        <View style={{ paddingHorizontal: screenPadding, gap: spacing.md }}>
          <SkeletonBox width="100%" height={84} borderRadius={cardRadius} />
          <SkeletonBox width="100%" height={200} borderRadius={cardRadius} />
          <SkeletonBox width="100%" height={160} borderRadius={cardRadius} />
        </View>
      ) : isError ? (
        <EstadoVazio
          emoji="📶"
          titulo="Não deu para gerar o relatório"
          texto="Verifique sua conexão e tente de novo."
          acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
        />
      ) : !data || data.totalTransacoes === 0 ? (
        <EstadoVazio
          emoji="📊"
          titulo="Nada por aqui neste período"
          texto="Lance transações ou escolha outro período para ver o resumo."
        />
      ) : (
        <ScrollView contentContainerStyle={{
          paddingHorizontal: screenPadding, paddingBottom: tabBarSpace, gap: spacing.lg,
        }}>
          <Entrance delay={50}>
            <View style={{ flexDirection: 'row', gap: spacing.sm }}>
              {[
                { l: 'Entradas', v: data.totalEntradas, c: colors.success },
                { l: 'Saídas', v: data.totalSaidas, c: colors.danger },
                { l: 'Saldo', v: data.saldo, c: data.saldo >= 0 ? colors.success : colors.danger },
              ].map(k => (
                <Card
                  key={k.l}
                  radius={radius.lg}
                  style={{ flex: 1, paddingHorizontal: spacing.md, paddingVertical: spacing.md }}
                >
                  <Text style={{ ...typography.meta, color: colors.textSecondary }}>{k.l}</Text>
                  <Text
                    numberOfLines={1}
                    adjustsFontSizeToFit
                    minimumFontScale={0.7}
                    style={{ ...typography.value, ...numeric, color: k.c, marginTop: spacing.xs }}
                  >
                    {formatCurrency(k.v)}
                  </Text>
                </Card>
              ))}
            </View>
          </Entrance>

          {evolucaoQuery.data && evolucaoQuery.data.some(m => m.entradas > 0 || m.saidas > 0) && (
            <Entrance delay={100}>
              <Card radius={radius.xl}>
                <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Evolução mensal</Text>
                <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs, marginBottom: spacing.lg }}>
                  Entradas e saídas · últimos 6 meses
                </Text>
                {(() => {
                  const meses = evolucaoQuery.data;
                  const maior = Math.max(...meses.map(m => Math.max(m.entradas, m.saidas)), 1);
                  return (
                    <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: spacing.sm }}>
                      {meses.map((m, i) => {
                        const rotulo = m.mes.replace('.', '');
                        return (
                          <View
                            key={`${m.mes}-${i}`}
                            accessible
                            // As colunas não têm texto: sem rótulo curado o leitor
                            // de tela anuncia só o mês. Começa pelo texto visível.
                            accessibilityLabel={`${rotulo}: entradas ${formatCurrency(m.entradas)}, saídas ${formatCurrency(m.saidas)}`}
                            style={{ flex: 1, alignItems: 'center' }}
                          >
                            <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: spacing.xxs + 1, height: ALTURA_BARRA }}>
                              <Coluna valor={m.entradas} maior={maior} cor={colors.success} />
                              <Coluna valor={m.saidas} maior={maior} cor={colors.danger} />
                            </View>
                            <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.sm }}>
                              {rotulo}
                            </Text>
                          </View>
                        );
                      })}
                    </View>
                  );
                })()}
                <View style={{ flexDirection: 'row', gap: spacing.lg, marginTop: spacing.md }}>
                  <Legenda cor={colors.success} rotulo="Entradas" />
                  <Legenda cor={colors.danger} rotulo="Saídas" />
                </View>
              </Card>
            </Entrance>
          )}

          {mesAnterior && mesAtual && comparacao?.some(m => m.entradas > 0 || m.saidas > 0) && (
            <Entrance delay={150}>
              <Card radius={radius.xl}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: spacing.md, alignItems: 'flex-start' }}>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Comparação mensal</Text>
                    <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>
                      Mês atual contra mês anterior
                    </Text>
                  </View>
                  <Badge tone={variacaoSaldo >= 0 ? 'success' : 'danger'}>
                    {`${variacaoSaldo >= 0 ? '+' : '−'}${formatCurrency(Math.abs(variacaoSaldo))}`}
                  </Badge>
                </View>

                <View style={{ marginTop: spacing.lg, gap: spacing.md }}>
                  {[
                    { item: mesAtual, label: 'Mês atual', saldo: saldoAtual },
                    { item: mesAnterior, label: 'Mês anterior', saldo: saldoAnterior },
                  ].map(({ item, label, saldo }) => (
                    <View key={label} style={{ paddingTop: spacing.md, borderTopWidth: 1, borderTopColor: colors.border }}>
                      <View style={{ flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', gap: spacing.md }}>
                        <Text style={{ ...typography.meta, fontWeight: '600', color: colors.textSecondary }}>{label}</Text>
                        <Text
                          numberOfLines={1}
                          adjustsFontSizeToFit
                          minimumFontScale={0.7}
                          style={{
                            ...typography.section, ...numeric, fontWeight: '800',
                            color: saldo >= 0 ? colors.success : colors.danger,
                          }}
                        >
                          {formatCurrency(saldo)}
                        </Text>
                      </View>
                      <View style={{ flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm }}>
                        <Metade rotulo="Entradas" valor={item.entradas} cor={colors.success} fundo={colors.successBg} />
                        <Metade rotulo="Saídas" valor={item.saidas} cor={colors.danger} fundo={colors.dangerBg} />
                      </View>
                    </View>
                  ))}
                </View>

                {variacaoPercentual !== null && (
                  <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.md }}>
                    Saldo {variacaoSaldo >= 0 ? 'subiu' : 'caiu'} {formatPercent(Math.abs(variacaoPercentual), 1)} versus o mês anterior.
                  </Text>
                )}
              </Card>
            </Entrance>
          )}

          {data.gastosPorCategoria.length > 0 && (
            <Entrance delay={200}>
              <Card radius={radius.xl}>
                <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginBottom: spacing.md }}>
                  Gastos por categoria
                </Text>
                <View style={{ gap: spacing.md }}>
                  {data.gastosPorCategoria.map((c, i) => (
                    <View key={c.categoriaId ?? `${c.nome}-${i}`} style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                      <IconTile size={36} cor={c.cor}>{c.icone || '🏷️'}</IconTile>
                      <View style={{ flex: 1, minWidth: 0 }}>
                        <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm }}>
                          <Text numberOfLines={1} style={{ ...typography.rowTitle, color: colors.textPrimary, flex: 1 }}>
                            {c.nome}
                          </Text>
                          <Text style={{ ...typography.value, ...numeric, color: colors.textPrimary }}>
                            {formatCurrency(c.valorTotal)}
                          </Text>
                        </View>
                        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginTop: spacing.xs }}>
                          <View style={{ flex: 1 }}>
                            {/* Relativa à maior categoria do período, não ao total:
                                é o que deixa a segunda maior legível. */}
                            <ProgressBar
                              value={maiorGasto > 0 ? Math.max((c.valorTotal / maiorGasto) * 100, 2) : 0}
                              height={5}
                              paleta={{ trilha: colors.trilha, fillDe: c.cor }}
                              accessibilityLabel={`${c.nome}, ${Math.round(c.porcentagem)} por cento dos gastos`}
                            />
                          </View>
                          <Text style={{ ...typography.meta, ...numeric, color: colors.textSecondary, textAlign: 'right' }}>
                            {Math.round(c.porcentagem)}%
                          </Text>
                        </View>
                      </View>
                    </View>
                  ))}
                </View>
              </Card>
            </Entrance>
          )}

          {data.maioresDespesas.length > 0 && (
            <Entrance delay={250}>
              <Card radius={radius.xl}>
                <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginBottom: spacing.sm }}>
                  Maiores despesas
                </Text>
                {data.maioresDespesas.map((d, i, arr) => (
                  <ListRow
                    key={d.id}
                    height={56}
                    divider={i < arr.length - 1}
                    title={d.descricao}
                    subtitle={`${d.categoriaNome || 'Sem categoria'} · ${formatDate(d.data)}`}
                    value={`−${formatCurrency(d.valor)}`}
                    valueTone="danger"
                  />
                ))}
              </Card>
            </Entrance>
          )}
        </ScrollView>
      )}
    </View>
  );
}

/** Uma coluna do par entradas/saídas. Zero vira um traço fantasma, não some. */
const Coluna = ({ valor, maior, cor }: { valor: number; maior: number; cor: string }) => (
  <View
    style={{
      width: LARGURA_BARRA,
      borderRadius: radius.pill,
      backgroundColor: cor,
      height: Math.max((valor / maior) * ALTURA_BARRA, valor > 0 ? 4 : 2),
      opacity: valor > 0 ? 1 : 0.2,
    }}
  />
);

const Legenda = ({ cor, rotulo }: { cor: string; rotulo: string }) => {
  const colors = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.xs + 1 }}>
      <View style={{ width: 8, height: 8, borderRadius: radius.pill, backgroundColor: cor }} />
      <Text style={{ ...typography.meta, color: colors.textSecondary }}>{rotulo}</Text>
    </View>
  );
};

/** Metade entradas ou saídas dentro do bloco de um mês. */
const Metade = ({ rotulo, valor, cor, fundo }: { rotulo: string; valor: number; cor: string; fundo: string }) => (
  <View style={{ flex: 1, borderRadius: radius.md, backgroundColor: fundo, paddingHorizontal: spacing.md, paddingVertical: spacing.sm }}>
    <Text style={{ ...typography.meta, fontWeight: '700', color: cor }}>{rotulo}</Text>
    <Text
      numberOfLines={1}
      adjustsFontSizeToFit
      minimumFontScale={0.7}
      style={{ ...typography.body, ...numeric, fontWeight: '700', color: cor, marginTop: spacing.xxs }}
    >
      {formatCurrency(valor)}
    </Text>
  </View>
);
