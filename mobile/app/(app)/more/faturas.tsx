import React, { useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTheme } from '../../../src/theme';
import faturaService from '../../../src/services/faturaService';
import contaService from '../../../src/services/contaService';
import { FaturaResponse, FaturaLancamento, Conta } from '../../../src/types';
import { formatCurrency, formatDate } from '../../../src/utils/format';

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];

export default function FaturasScreen() {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const insets = useSafeAreaInsets();
  const now = new Date();
  const [mes, setMes] = useState(now.getMonth() + 1);
  const [ano, setAno] = useState(now.getFullYear());
  const [contaIdx, setContaIdx] = useState(0);
  const [paying, setPaying] = useState(false);

  const { data: contasData } = useQuery({
    queryKey: ['contas-fatura'],
    queryFn: () => contaService.listar(),
  });
  const contasCredito = (contasData?.content ?? []).filter((c: Conta) => c.tipo === 'CREDITO');
  const contaSelecionada = contasCredito[contaIdx];

  const { data: fatura, isLoading, refetch } = useQuery<FaturaResponse | null>({
    queryKey: ['fatura', contaSelecionada?.id, mes, ano],
    queryFn: () => {
      if (!contaSelecionada?.id) return null;
      if (mes === now.getMonth() + 1 && ano === now.getFullYear())
        return faturaService.buscarAtual(contaSelecionada.id!);
      return faturaService.buscarPorMes(contaSelecionada.id!, mes, ano).catch(() => null);
    },
    enabled: !!contaSelecionada?.id,
  });

  const handlePagar = async () => {
    if (!fatura || fatura.valorTotal <= 0) return;
    setPaying(true);
    try {
      await faturaService.pagarFatura(fatura.id, fatura.valorTotal);
      refetch();
    } catch {} finally { setPaying(false); }
  };

  const mesAnterior = () => { if (mes === 1) { setMes(12); setAno(ano - 1); } else setMes(mes - 1); };
  const mesProximo = () => { if (mes === 12) { setMes(1); setAno(ano + 1); } else setMes(mes + 1); };

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ paddingTop: insets.top + 8, paddingHorizontal: 16, paddingBottom: 40 }}>
      <View style={styles.nav}>
        <TouchableOpacity onPress={mesAnterior} style={{ padding: 8 }}><Text style={{ color: colors.textSecondary, fontSize: 18 }}>‹</Text></TouchableOpacity>
        <Text style={[styles.mesAno, { color: colors.textPrimary }]}>{MESES[mes - 1]} {ano}</Text>
        <TouchableOpacity onPress={mesProximo} style={{ padding: 8 }}><Text style={{ color: colors.textSecondary, fontSize: 18 }}>›</Text></TouchableOpacity>
      </View>

      {contasCredito.length > 1 && (
        <View style={{ flexDirection: 'row', gap: 6, marginBottom: 16, flexWrap: 'wrap' }}>
          {contasCredito.map((c: Conta, i: number) => (
            <TouchableOpacity key={c.id} onPress={() => setContaIdx(i)} style={[styles.chip, { backgroundColor: i === contaIdx ? colors.brand : colors.card, borderColor: i === contaIdx ? colors.brand : colors.border }]}>
              <Text style={{ color: i === contaIdx ? colors.brandText : colors.textSecondary, fontSize: 13, fontWeight: '500' }}>{c.nome}</Text>
            </TouchableOpacity>
          ))}
        </View>
      )}

      {contasCredito.length === 0 ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textSecondary, fontSize: 15 }}>Nenhum cartão de crédito</Text>
          <Text style={{ color: colors.textMuted, fontSize: 12, marginTop: 4 }}>Cadastre uma conta tipo Crédito</Text>
        </View>
      ) : isLoading ? (
        <ActivityIndicator color={colors.brand} style={{ marginTop: 40 }} />
      ) : fatura ? (
        <View style={{ gap: 16, marginTop: 8 }}>
          <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <View>
                <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>{contaSelecionada?.nome}</Text>
                <Text style={{ color: colors.textSecondary, fontSize: 11 }}>
                  Fecha: {fatura.dataFechamento ? formatDate(fatura.dataFechamento) : '—'} • Vence: {fatura.dataVencimento ? formatDate(fatura.dataVencimento) : '—'}
                </Text>
              </View>
              <View style={{ alignItems: 'flex-end' }}>
                <Text style={{ color: colors.textPrimary, fontSize: 22, fontWeight: '800' }}>{formatCurrency(fatura.valorTotal)}</Text>
                <View style={[styles.badge, { backgroundColor: fatura.status === 'PAGA' ? colors.success + '20' : colors.danger + '20' }]}>
                  <Text style={{ color: fatura.status === 'PAGA' ? colors.success : colors.danger, fontSize: 10, fontWeight: '600' }}>
                    {fatura.status === 'PAGA' ? 'PAGA' : fatura.status === 'VENCIDA' ? 'VENCIDA' : 'ABERTA'}
                  </Text>
                </View>
              </View>
            </View>
          </View>

          {fatura.status !== 'PAGA' && fatura.valorTotal > 0 && (
            <TouchableOpacity onPress={handlePagar} disabled={paying} style={[styles.payBtn, { backgroundColor: '#22C55E' }]}>
              {paying ? <ActivityIndicator color="white" /> : <Text style={{ color: 'white', fontWeight: '700', fontSize: 15 }}>Pagar Fatura</Text>}
            </TouchableOpacity>
          )}

          <Text style={{ color: colors.textPrimary, fontWeight: '600', marginTop: 4 }}>Lançamentos</Text>
          {fatura.lancamentos.length === 0 ? (
            <Text style={{ color: colors.textSecondary, textAlign: 'center', fontSize: 13, padding: 24 }}>Nenhum lançamento</Text>
          ) : (
            fatura.lancamentos.map((l: FaturaLancamento, i: number) => (
              <View key={l.transacaoId || i} style={[styles.lancamento, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <View style={{ width: 32, height: 32, borderRadius: 8, backgroundColor: l.categoriaCor + '20', alignItems: 'center', justifyContent: 'center' }}>
                  <Text style={{ fontSize: 14 }}>{l.categoriaIcone || '💳'}</Text>
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.textPrimary, fontSize: 13 }}>{l.descricao}</Text>
                  <Text style={{ color: colors.textSecondary, fontSize: 11 }}>
                    {formatDate(l.data)}
                    {l.totalParcelas ? ` · ${l.parcelaAtual}/${l.totalParcelas}` : ''}
                  </Text>
                </View>
                <Text style={{ color: colors.danger, fontSize: 13, fontWeight: '600' }}>{formatCurrency(l.valor)}</Text>
              </View>
            ))
          )}
        </View>
      ) : (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textSecondary }}>Nenhuma fatura encontrada</Text>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  nav: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 },
  mesAno: { fontSize: 16, fontWeight: '700' },
  chip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, borderWidth: 1 },
  card: { borderRadius: 12, borderWidth: 1, padding: 16 },
  badge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 10, marginTop: 4 },
  payBtn: { height: 48, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  lancamento: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 10, borderRadius: 8, borderWidth: 1 },
});
