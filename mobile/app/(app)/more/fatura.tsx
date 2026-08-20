import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, ScrollView, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTheme, useTabBarSpace, spacing, typography, numeric, radius } from '../../../src/theme';
import BackButton from '../../../src/components/ui/BackButton';
import Badge from '../../../src/components/ui/Badge';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import faturaService from '../../../src/services/faturaService';
import contaFinanceiraService from '../../../src/services/contaFinanceiraService';
import { ContaFinanceira, FaturaLancamento, FaturaResponse } from '../../../src/types';
import { formatCurrency, formatDate, maskCurrencyInput, parseCurrencyBR } from '../../../src/utils/format';

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];

/**
 * Detalhe de uma fatura: lançamentos e pagamento. Saiu de more/faturas.tsx,
 * que virou a tela Carteira — nenhuma regra de pagamento mudou aqui, incluindo
 * a Idempotency-Key por tentativa.
 */
export default function FaturaDetalheScreen() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const tabBarSpace = useTabBarSpace();
  const queryClient = useQueryClient();
  const params = useLocalSearchParams<{ cartaoId?: string; mes?: string; ano?: string; nome?: string }>();

  const cartaoId = Number(params.cartaoId);
  const mes = Number(params.mes);
  const ano = Number(params.ano);

  const [paying, setPaying] = useState(false);
  const payingRef = useRef(false);
  const [payError, setPayError] = useState<string | null>(null);
  const [carteiraPagamentoId, setCarteiraPagamentoId] = useState<number | null>(null);
  const [valorPagamento, setValorPagamento] = useState('');

  const { data: carteirasData } = useQuery({
    queryKey: ['contas-financeiras-caixa'],
    queryFn: () => contaFinanceiraService.listarParaCaixa(),
  });
  const carteiras = carteirasData ?? [];

  useEffect(() => {
    if (carteiraPagamentoId == null && carteiras.length > 0) setCarteiraPagamentoId(carteiras[0].id);
  }, [carteiras, carteiraPagamentoId]);

  // Params inválidos são um estado próprio: sem isto, `enabled:false` faz o
  // react-query nunca sair de idle (não fica em isLoading), a tela pinta R$ 0,00
  // e uma falha de navegação vira "fatura zerada".
  const paramsValidos = Number.isFinite(cartaoId) && Number.isFinite(mes)
    && Number.isFinite(ano) && mes >= 1 && mes <= 12;

  const hoje = new Date();
  const ehCompetenciaCorrente = mes === hoje.getMonth() + 1 && ano === hoje.getFullYear();

  const { data: fatura, isLoading, isError, refetch } = useQuery<FaturaResponse>({
    queryKey: ['fatura', cartaoId, mes, ano],
    // Na competência corrente usa /atual: quem decide o mês passa a ser o relógio
    // do servidor (America/Sao_Paulo), não o do aparelho. Sem `.catch`: erro tem
    // que chegar como erro, não virar fatura vazia.
    queryFn: () => (ehCompetenciaCorrente
      ? faturaService.buscarAtual(cartaoId)
      : faturaService.buscarPorMes(cartaoId, mes, ano)),
    enabled: paramsValidos,
  });

  
  const saldoRestante = Math.max(Number(fatura?.valorTotal ?? 0) - Number(fatura?.valorPago ?? 0), 0);

  useEffect(() => {
    setValorPagamento(saldoRestante > 0
      ? maskCurrencyInput(Math.round(saldoRestante * 100).toString())
      : '');
  }, [fatura?.id, fatura?.valorTotal, fatura?.valorPago, saldoRestante]);

  const handlePagar = async () => {
    if (payingRef.current) return;
    if (!fatura || saldoRestante <= 0) return;
    if (!carteiraPagamentoId) { setPayError('Selecione a conta de pagamento.'); return; }
    const valor = parseCurrencyBR(valorPagamento);
    if (!Number.isFinite(valor) || valor <= 0) { setPayError('Informe um valor de pagamento válido.'); return; }
    if (valor > saldoRestante) { setPayError(`Valor máximo: ${formatCurrency(saldoRestante)}.`); return; }

    setPayError(null);
    payingRef.current = true;
    setPaying(true);
    try {
      const key = `${fatura.id}:${Date.now()}:${Math.random().toString(36).slice(2)}`;
      await faturaService.pagarFatura(fatura.id, valor, carteiraPagamentoId, key);
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-projecao'] });
      // prefixo ['cartoes'] cobre ['cartoes','carteira'] da tela Carteira
      queryClient.invalidateQueries({ queryKey: ['cartoes'] });
      refetch();
    } catch (err: any) {
      setPayError(err?.userMessage ?? 'Erro ao pagar fatura.');
    } finally {
      payingRef.current = false;
      setPaying(false);
    }
  };

  const statusTone = fatura?.status === 'PAGA' ? 'success'
    : fatura?.status === 'VENCIDA' ? 'danger'
    : fatura?.status === 'FECHADA' ? 'warning' : 'info';

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScrollView contentContainerStyle={{ paddingBottom: tabBarSpace }}>
        <View style={{ paddingTop: insets.top + spacing.lg, paddingHorizontal: spacing.lg, paddingBottom: spacing.md }}>
          <BackButton />
          <Text style={{ ...typography.greeting, color: colors.textPrimary }}>
            {params.nome ?? fatura?.cartaoNome ?? 'Fatura'}
          </Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginTop: spacing.xs }}>
            <Text style={{ ...typography.body, color: colors.textSecondary }}>
              {MESES[mes - 1]} de {ano}
            </Text>
            {!!fatura && <Badge tone={statusTone}>{fatura.status}</Badge>}
          </View>
        </View>

        {!paramsValidos ? (
          <EstadoVazio
            emoji="🧾"
            titulo="Não foi possível abrir esta fatura"
            texto="A competência não chegou corretamente. Volte para a Carteira e toque na fatura de novo."
          />
        ) : isLoading ? (
          <ActivityIndicator color={colors.brand} style={{ marginTop: 40 }} />
        ) : isError ? (
          <EstadoVazio
            emoji="📶"
            titulo="Não deu para carregar a fatura"
            texto="Verifique sua conexão e tente de novo."
            acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
          />
        ) : (
          <View style={{ paddingHorizontal: spacing.lg, gap: spacing.lg }}>
            <View style={{
              backgroundColor: colors.card, borderRadius: radius.lg, padding: spacing.lg,
              borderWidth: 1, borderColor: colors.border,
            }}>
              <Text style={{ ...typography.body, color: colors.textSecondary }}>Total da fatura</Text>
              <Text style={{ ...numeric, color: colors.textPrimary, fontSize: 28, fontWeight: '800', letterSpacing: -0.6 }}>
                {formatCurrency(fatura?.valorTotal ?? 0)}
              </Text>
              {!!fatura && (
                <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xs }}>
                  Fecha {formatDate(fatura.dataFechamento)} · Vence {formatDate(fatura.dataVencimento)}
                </Text>
              )}
              {!!fatura && fatura.valorPago > 0 && (
                <Text style={{ ...typography.meta, ...numeric, color: colors.success, marginTop: spacing.xs }}>
                  Pago: {formatCurrency(fatura.valorPago)} · Restante: {formatCurrency(saldoRestante)}
                </Text>
              )}
            </View>

            {fatura && fatura.status !== 'PAGA' && saldoRestante > 0 && (
              <View style={{ gap: spacing.md }}>
                <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Pagar com</Text>
                <View style={{ flexDirection: 'row', gap: spacing.sm, flexWrap: 'wrap' }}>
                  {carteiras.map((c: ContaFinanceira) => (
                    <TouchableOpacity
                      key={c.id}
                      onPress={() => setCarteiraPagamentoId(c.id)}
                      accessibilityRole="button"
                      accessibilityLabel={`Pagar com ${c.nome}`}
                      accessibilityState={{ selected: carteiraPagamentoId === c.id }}
                      style={{
                        paddingHorizontal: spacing.md, minHeight: 44, justifyContent: 'center',
                        borderRadius: radius.pill, borderWidth: 1,
                        backgroundColor: carteiraPagamentoId === c.id ? colors.brand : colors.card,
                        borderColor: carteiraPagamentoId === c.id ? colors.brand : colors.border,
                      }}
                    >
                      <Text style={{
                        ...typography.chip,
                        color: carteiraPagamentoId === c.id ? colors.brandText : colors.textSecondary,
                      }}>
                        {c.nome}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
                <TextInput
                  testID="invoice-payment-value"
                  accessibilityLabel="Valor do pagamento"
                  value={valorPagamento}
                  onChangeText={t => setValorPagamento(maskCurrencyInput(t))}
                  keyboardType="number-pad"
                  placeholder="0,00"
                  placeholderTextColor={colors.textMuted}
                  style={{
                    borderWidth: 1, borderRadius: radius.md, padding: spacing.md, fontSize: 15,
                    backgroundColor: colors.fieldBg, borderColor: colors.border, color: colors.textPrimary,
                  }}
                />
                {payError && <Text style={{ ...typography.meta, color: colors.danger }}>{payError}</Text>}
                <TouchableOpacity
                  onPress={handlePagar}
                  disabled={paying}
                  accessibilityRole="button"
                  accessibilityLabel="Pagar Fatura"
                  accessibilityState={{ disabled: paying }}
                  style={{
                    height: 48, borderRadius: radius.md, alignItems: 'center',
                    justifyContent: 'center', backgroundColor: colors.success,
                  }}
                >
                  {paying
                    ? <ActivityIndicator color={colors.brandText} />
                    : <Text style={{ ...typography.button, color: colors.brandText }}>Pagar Fatura</Text>}
                </TouchableOpacity>
              </View>
            )}

            <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Lançamentos</Text>
            {!fatura || fatura.lancamentos.length === 0 ? (
              <Text style={{ ...typography.body, color: colors.textSecondary, textAlign: 'center', padding: spacing.xxl }}>
                Nenhuma compra nesta fatura ainda
              </Text>
            ) : (
              fatura.lancamentos.map((l: FaturaLancamento, i: number) => {
                // Backend prefixa a descrição com "Estorno:"/"Ajuste:"; o badge assume esse papel
                const descricao = l.tipo !== 'COMPRA'
                  ? l.descricao.replace(/^(Estorno|Ajuste):\s*/, '')
                  : l.descricao;
                const badge = l.tipo === 'ESTORNO' ? { tone: 'success' as const, label: 'ESTORNO' }
                  : l.tipo === 'AJUSTE' ? { tone: 'warning' as const, label: 'AJUSTE' }
                  : l.tipo === 'CREDITO_ANTERIOR' ? { tone: 'success' as const, label: 'CRÉDITO ANTERIOR' }
                  : l.tipo === 'SALDO_DEVEDOR_ANTERIOR' ? { tone: 'warning' as const, label: 'SALDO DEVEDOR ANTERIOR' }
                  : null;
                // Saldo devedor rolado é aviso (dívida carregada), não erro:
                // nunca usar a cor de perigo aqui.
                const valorColor = l.tipo === 'SALDO_DEVEDOR_ANTERIOR' ? colors.warning
                  : l.valor < 0 ? colors.success : colors.danger;
                return (
                  <View
                    key={l.transacaoId || i}
                    style={{
                      flexDirection: 'row', alignItems: 'center', gap: spacing.md,
                      padding: spacing.md, borderRadius: radius.md,
                      backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border,
                    }}
                  >
                    <View style={{
                      width: 34, height: 34, borderRadius: radius.sm,
                      backgroundColor: (l.categoriaCor ?? colors.brand) + '20',
                      alignItems: 'center', justifyContent: 'center',
                    }}>
                      <Text style={{ fontSize: 15 }}>{l.categoriaIcone || '💳'}</Text>
                    </View>
                    <View style={{ flex: 1 }}>
                      {/* Descrição sozinha na primeira linha: badges longos como
                          SALDO DEVEDOR ANTERIOR comiam o texto e sobrava "Sal...". */}
                      <Text numberOfLines={1} style={{ ...typography.body, color: colors.textPrimary }}>
                        {descricao}
                      </Text>
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm - 2, marginTop: 2 }}>
                        <Text style={{ ...typography.meta, color: colors.textSecondary }}>
                          {formatDate(l.data)}
                          {l.totalParcelas ? ` · ${l.parcelaAtual}/${l.totalParcelas}` : ''}
                        </Text>
                        {badge && <Badge tone={badge.tone}>{badge.label}</Badge>}
                      </View>
                    </View>
                    <Text style={{ ...typography.value, ...numeric, color: valorColor }}>
                      {formatCurrency(l.valor)}
                    </Text>
                  </View>
                );
              })
            )}
          </View>
        )}
      </ScrollView>
    </View>
  );
}
