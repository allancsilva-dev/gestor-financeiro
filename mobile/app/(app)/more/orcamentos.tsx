import React, { useState, useEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, TextInput, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTheme } from '../../../src/theme';
import BackButton from '../../../src/components/ui/BackButton';
import { orcamentoService } from '../../../src/services/orcamentoService';
import { categoriaService } from '../../../src/services/categoriaService';
import { ApiErrorWithMessage, OrcamentoResponse, OrcamentoCategoriaItem } from '../../../src/types';
import { formatCurrency, formatNumber, parseCurrencyBR, maskCurrencyInput } from '../../../src/utils/format';

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];

function getProgressColor(percentual: number, colors: any): string {
  if (percentual >= 100) return colors.danger;
  if (percentual >= 75) return colors.warning;
  return colors.success;
}

export default function OrcamentoScreen() {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const insets = useSafeAreaInsets();
  const now = new Date();
  const [mes, setMes] = useState(now.getMonth() + 1);
  const [ano, setAno] = useState(now.getFullYear());
  const [editando, setEditando] = useState(false);
  const [limites, setLimites] = useState<Map<number, string>>(new Map());
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const { data, isLoading } = useQuery<OrcamentoResponse | null>({
    queryKey: ['orcamento', mes, ano],
    queryFn: () => {
      if (mes === now.getMonth() + 1 && ano === now.getFullYear()) return orcamentoService.buscarAtual();
      return orcamentoService.buscarPorMes(mes, ano).catch(() => null);
    },
  });

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias-orcamento'],
    queryFn: () => categoriaService.listar(),
  });

  const iniciarEdicao = () => {
    const map = new Map<number, string>();
    data?.categorias?.forEach((c) => map.set(c.categoriaId, formatNumber(Number(c.valorLimite ?? 0))));
    categorias.forEach((c) => {
      if (c.id && !map.has(c.id)) map.set(c.id, '');
    });
    setLimites(map);
    setEditando(true);
  };

  const salvar = async () => {
    const cats = Array.from(limites.entries())
      .filter(([_, v]) => parseCurrencyBR(v) > 0)
      .map(([categoriaId, valorLimite]) => ({ categoriaId, valorLimite: parseCurrencyBR(valorLimite) }));

    if (cats.length === 0) return;
    setSaving(true);
    setSaveError(null);
    try {
      await orcamentoService.criarOuAtualizar({ mes, ano, categorias: cats });
      queryClient.invalidateQueries({ queryKey: ['orcamento'] });
      setEditando(false);
    } catch (err) {
      setSaveError((err as ApiErrorWithMessage).userMessage ?? 'Não foi possível salvar o orçamento. Tente novamente.');
    } finally { setSaving(false); }
  };

  const mesAnterior = () => { if (mes === 1) { setMes(12); setAno(ano - 1); } else setMes(mes - 1); };
  const mesProximo = () => { if (mes === 12) { setMes(1); setAno(ano + 1); } else setMes(mes + 1); };

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 40 }}>
      <View style={{ paddingBottom: 12 }}>
        <BackButton />
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Orçamentos</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Limites de gasto por categoria</Text>
      </View>
      <View style={styles.nav}>
        <TouchableOpacity onPress={mesAnterior} style={{ padding: 8 }}><Text style={{ color: colors.textSecondary, fontSize: 18 }}>‹</Text></TouchableOpacity>
        <Text style={[styles.mesAno, { color: colors.textPrimary }]}>{MESES[mes - 1]} {ano}</Text>
        <TouchableOpacity onPress={mesProximo} style={{ padding: 8 }}><Text style={{ color: colors.textSecondary, fontSize: 18 }}>›</Text></TouchableOpacity>
      </View>

      {isLoading ? (
        <ActivityIndicator color={colors.brand} style={{ marginTop: 40 }} />
      ) : editando ? (
        <View style={{ gap: 12, marginTop: 16 }}>
          <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Defina limites por categoria:</Text>
          {categorias.map((cat) => (
            <View key={cat.id} style={[styles.editRow, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Text style={{ fontSize: 16, width: 28 }}>{cat.icone || '📌'}</Text>
              <Text style={{ flex: 1, color: colors.textPrimary, fontSize: 14 }}>{cat.nome}</Text>
              <TextInput
                accessibilityLabel={`Limite para ${cat.nome}`}
                style={[styles.editInput, { backgroundColor: colors.bg, borderColor: colors.border, color: colors.textPrimary }]}
                value={limites.get(cat.id!) || ''}
                onChangeText={(t) => { const n = new Map(limites); n.set(cat.id!, maskCurrencyInput(t)); setLimites(n); }}
                keyboardType="number-pad"
                placeholder="0,00"
                placeholderTextColor={colors.textMuted}
              />
            </View>
          ))}
          {saveError ? <Text style={{ color: colors.danger, fontSize: 13 }}>{saveError}</Text> : null}
          <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
            <TouchableOpacity onPress={() => { setEditando(false); setSaveError(null); }} style={[styles.btn, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Text style={{ color: colors.textSecondary }}>Cancelar</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={salvar} disabled={saving} style={[styles.btn, { backgroundColor: colors.brand, flex: 1 }]}>
              {saving ? <ActivityIndicator color={colors.brandText} /> : <Text style={{ color: colors.brandText, fontWeight: '700' }}>Salvar</Text>}
            </TouchableOpacity>
          </View>
        </View>
      ) : !data?.categorias?.length ? (
        <View style={{ alignItems: 'center', marginTop: 60, gap: 12 }}>
          <Text style={{ fontSize: 40 }}>📊</Text>
          <Text style={{ color: colors.textSecondary, textAlign: 'center' }}>Nenhum orçamento para {MESES[mes - 1]}</Text>
          <TouchableOpacity onPress={iniciarEdicao} style={[styles.btn, { backgroundColor: colors.brand }]}>
            <Text style={{ color: colors.brandText, fontWeight: '700' }}>Criar Orçamento</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <View style={{ marginTop: 16, gap: 16 }}>
          {/* KPIs */}
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {[
              { label: 'Planejado', value: data.valorTotalPlanejado, color: colors.textPrimary },
              { label: 'Gasto', value: data.valorTotalGasto, color: colors.danger },
              { label: 'Disponível', value: data.valorTotalPlanejado - data.valorTotalGasto, color: data.valorTotalPlanejado - data.valorTotalGasto >= 0 ? colors.success : colors.danger },
            ].map((kpi, i) => (
              <View key={i} style={[styles.kpi, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={{ color: colors.textSecondary, fontSize: 10 }}>{kpi.label}</Text>
                <Text style={{ color: kpi.color, fontSize: 14, fontWeight: '700' }}>{formatCurrency(kpi.value)}</Text>
              </View>
            ))}
          </View>

          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text style={{ color: colors.textPrimary, fontWeight: '600' }}>Categorias</Text>
            <TouchableOpacity onPress={iniciarEdicao}>
              <Text style={{ color: colors.brand, fontSize: 13 }}>Editar</Text>
            </TouchableOpacity>
          </View>

          {data.categorias.map((cat: OrcamentoCategoriaItem) => (
            <View key={cat.id} style={{ gap: 6 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <View style={{ flexDirection: 'row', gap: 6, alignItems: 'center' }}>
                  <Text style={{ fontSize: 16 }}>{cat.categoriaIcone || '📌'}</Text>
                  <Text style={{ color: colors.textPrimary, fontSize: 13 }}>{cat.categoriaNome}</Text>
                </View>
                <Text style={{ color: getProgressColor(cat.percentualGasto, colors), fontSize: 12, fontWeight: '600' }}>
                  {formatCurrency(cat.valorGasto)} / {formatCurrency(cat.valorLimite)}
                </Text>
              </View>
              <View style={{ height: 6, backgroundColor: colors.border, borderRadius: 3, overflow: 'hidden' }}>
                <View style={{
                  height: 6,
                  width: `${Math.min(cat.percentualGasto, 100)}%`,
                  backgroundColor: getProgressColor(cat.percentualGasto, colors),
                  borderRadius: 3,
                }} />
              </View>
            </View>
          ))}
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  nav: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 },
  mesAno: { fontSize: 16, fontWeight: '700' },
  kpi: { flex: 1, borderRadius: 10, borderWidth: 1, padding: 12, alignItems: 'center' },
  btn: { paddingHorizontal: 16, paddingVertical: 12, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  editRow: { flexDirection: 'row', alignItems: 'center', gap: 8, borderRadius: 8, borderWidth: 1, padding: 8 },
  editInput: { width: 100, borderWidth: 1, borderRadius: 6, padding: 8, textAlign: 'right' },
});
