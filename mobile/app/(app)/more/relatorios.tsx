import React, { useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, StyleSheet, TextInput } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../../src/theme';
import relatorioService from '../../src/services/relatorioService';
import { RelatorioResponse } from '../../src/types';
import { formatCurrency } from '../../src/utils/format';

export default function RelatorioScreen() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const now = new Date();
  const [inicio, setInicio] = useState(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`);
  const [fim, setFim] = useState(now.toISOString().split('T')[0]);
  const [data, setData] = useState<RelatorioResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const carregar = async () => {
    setLoading(true);
    try { const r = await relatorioService.gerar(inicio, fim); setData(r); } catch {} finally { setLoading(false); }
  };

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ paddingTop: insets.top + 8, paddingHorizontal: 16, paddingBottom: 40 }}>
      <Text style={{ color: colors.textPrimary, fontSize: 18, fontWeight: '700', marginBottom: 12 }}>Relatórios</Text>

      <View style={{ flexDirection: 'row', gap: 8, marginBottom: 16, alignItems: 'flex-end' }}>
        <View style={{ flex: 1 }}>
          <Text style={{ color: colors.textSecondary, fontSize: 10 }}>Início</Text>
          <TextInput style={[s.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} value={inicio} onChangeText={setInicio} placeholderTextColor={colors.textMuted} />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={{ color: colors.textSecondary, fontSize: 10 }}>Fim</Text>
          <TextInput style={[s.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} value={fim} onChangeText={setFim} placeholderTextColor={colors.textMuted} />
        </View>
        <TouchableOpacity onPress={carregar} disabled={loading} style={[s.btn, { backgroundColor: colors.brand }]}>
          {loading ? <ActivityIndicator color={colors.brandText} size="small" /> : <Text style={{ color: colors.brandText, fontWeight: '700', fontSize: 13 }}>Gerar</Text>}
        </TouchableOpacity>
      </View>

      {loading ? (
        <ActivityIndicator color={colors.brand} style={{ marginTop: 20 }} />
      ) : data ? (
        <View style={{ gap: 16 }}>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {[
              { l: 'Entradas', v: data.totalEntradas, c: colors.success },
              { l: 'Saídas', v: data.totalSaidas, c: colors.danger },
              { l: 'Saldo', v: data.saldo, c: data.saldo >= 0 ? colors.success : colors.danger },
            ].map((k, i) => (
              <View key={i} style={[s.kpi, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={{ color: colors.textSecondary, fontSize: 10 }}>{k.l}</Text>
                <Text style={{ color: k.c, fontSize: 13, fontWeight: '700' }}>{formatCurrency(k.v)}</Text>
              </View>
            ))}
          </View>

          {data.gastosPorCategoria.length > 0 && (
            <View>
              <Text style={{ color: colors.textPrimary, fontWeight: '600', marginBottom: 8 }}>Gastos por Categoria</Text>
              {data.gastosPorCategoria.map((c, i) => (
                <View key={i} style={{ flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 6 }}>
                  <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: c.cor }} />
                  <Text style={{ flex: 1, color: colors.textPrimary, fontSize: 13 }}>{c.nome}</Text>
                  <Text style={{ color: colors.danger, fontSize: 13 }}>{formatCurrency(c.valorTotal)}</Text>
                  <Text style={{ color: colors.textSecondary, fontSize: 11, width: 32, textAlign: 'right' }}>{c.porcentagem}%</Text>
                </View>
              ))}
            </View>
          )}

          {data.maioresDespesas.length > 0 && (
            <View>
              <Text style={{ color: colors.textPrimary, fontWeight: '600', marginBottom: 8 }}>Maiores Despesas</Text>
              {data.maioresDespesas.map((d, i) => (
                <View key={i} style={[s.row, { backgroundColor: colors.card, borderColor: colors.border }]}>
                  <View style={{ flex: 1 }}>
                    <Text style={{ color: colors.textPrimary, fontSize: 13 }}>{d.descricao}</Text>
                    <Text style={{ color: colors.textSecondary, fontSize: 11 }}>{d.categoriaNome || 'Sem categoria'} · {d.data}</Text>
                  </View>
                  <Text style={{ color: colors.danger, fontSize: 13, fontWeight: '600' }}>{formatCurrency(d.valor)}</Text>
                </View>
              ))}
            </View>
          )}
        </View>
      ) : (
        <View style={{ alignItems: 'center', padding: 40 }}>
          <Text style={{ color: colors.textSecondary }}>Selecione datas e gere o relatório</Text>
        </View>
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  input: { borderWidth: 1, borderRadius: 6, padding: 8, fontSize: 13, marginTop: 2 },
  btn: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: 6, alignItems: 'center', justifyContent: 'center', marginTop: 2 },
  kpi: { flex: 1, borderRadius: 8, borderWidth: 1, padding: 10, alignItems: 'center' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderRadius: 8, borderWidth: 1, marginBottom: 6 },
});
