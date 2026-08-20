import React from 'react';
import { ScrollView, Text, TextInput, TouchableOpacity, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, radius, spacing, typography, numeric } from '../../theme';
import { CategoriaResumo } from '../../types';
import {
  PERIODO_LABEL, PeriodoPreset, TipoFiltro,
} from '../../hooks/useOperacoesFiltro';

interface Props {
  categorias: CategoriaResumo[];
  categoriaId: number | null;
  onCategoria: (id: number | null) => void;
  periodo: PeriodoPreset;
  onPeriodo: (p: PeriodoPreset) => void;
  tipo: TipoFiltro;
  onTipo: (t: TipoFiltro) => void;
  busca: string;
  onBusca: (v: string) => void;
  total: number;
}

/** Pill grande da primeira linha (categorias), com emoji. */
const ChipCategoria = ({
  label, icone, ativo, onPress,
}: { label: string; icone?: string; ativo: boolean; onPress: () => void }) => {
  const colors = useTheme();
  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.85}
      accessibilityRole="button"
      accessibilityState={{ selected: ativo }}
      accessibilityLabel={`Filtrar por ${label}`}
      style={{
        minHeight: 44,
        flexDirection: 'row', alignItems: 'center', gap: 7,
        paddingHorizontal: spacing.lg,
        borderRadius: radius.pill,
        backgroundColor: ativo ? colors.brand : colors.chipBg,
        borderWidth: ativo ? 0 : 1,
        borderColor: colors.chipBorder,
      }}
    >
      <Text style={{ fontSize: 15 }}>{icone ?? '🏷️'}</Text>
      <Text style={{ ...typography.chip, color: ativo ? colors.brandText : colors.textSecondary }}>
        {label}
      </Text>
    </TouchableOpacity>
  );
};

/** Pill menor da segunda linha (período e tipo). */
const ChipPeriodo = ({
  label, ativo, onPress,
}: { label: string; ativo: boolean; onPress: () => void }) => {
  const colors = useTheme();
  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.85}
      accessibilityRole="button"
      accessibilityState={{ selected: ativo }}
      style={{
        minHeight: 36,
        paddingHorizontal: spacing.md,
        borderRadius: radius.pill,
        borderWidth: 1,
        borderColor: ativo ? colors.brand : colors.chipBorder,
        backgroundColor: ativo ? colors.brandBg : 'transparent',
        justifyContent: 'center',
      }}
    >
      <Text style={{ ...typography.meta, color: ativo ? colors.brandFg : colors.textSecondary }}>
        {label}
      </Text>
    </TouchableOpacity>
  );
};

export default function OperacoesFiltro({
  categorias, categoriaId, onCategoria,
  periodo, onPeriodo, tipo, onTipo,
  busca, onBusca, total,
}: Props) {
  const colors = useTheme();

  return (
    <View>
      <Text style={{ ...typography.section, color: colors.textPrimary, marginBottom: spacing.md }}>
        Operações
      </Text>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ gap: spacing.md, paddingRight: spacing.lg }}
      >
        <ChipCategoria label="Tudo" ativo={categoriaId == null} onPress={() => onCategoria(null)} />
        {categorias.map(c => (
          <ChipCategoria
            key={c.id}
            label={c.nome}
            icone={c.icone ?? undefined}
            ativo={categoriaId === c.id}
            onPress={() => onCategoria(categoriaId === c.id ? null : c.id)}
          />
        ))}
      </ScrollView>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ gap: spacing.sm, paddingRight: spacing.lg }}
        style={{ marginTop: spacing.md }}
      >
        {(Object.keys(PERIODO_LABEL) as PeriodoPreset[]).map(p => (
          <ChipPeriodo key={p} label={PERIODO_LABEL[p]} ativo={periodo === p} onPress={() => onPeriodo(p)} />
        ))}
        <ChipPeriodo
          label="Entradas"
          ativo={tipo === 'ENTRADA'}
          onPress={() => onTipo(tipo === 'ENTRADA' ? 'TODOS' : 'ENTRADA')}
        />
        <ChipPeriodo
          label="Saídas"
          ativo={tipo === 'SAIDA'}
          onPress={() => onTipo(tipo === 'SAIDA' ? 'TODOS' : 'SAIDA')}
        />
      </ScrollView>

      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginTop: spacing.lg }}>
        <View style={{
          flex: 1, flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
          minHeight: 48, paddingHorizontal: spacing.lg,
          borderRadius: radius.lg, backgroundColor: colors.fieldBg,
        }}>
          <Ionicons name="search" size={18} color={colors.textMuted} />
          <TextInput
            value={busca}
            onChangeText={onBusca}
            placeholder="Buscar transações"
            placeholderTextColor={colors.textMuted}
            returnKeyType="search"
            accessibilityLabel="Buscar transações"
            style={{ flex: 1, ...typography.body, color: colors.textPrimary, paddingVertical: 0 }}
          />
        </View>
        <View style={{
          minHeight: 36, paddingHorizontal: spacing.md, justifyContent: 'center',
          borderRadius: radius.pill, borderWidth: 1, borderColor: colors.brand,
        }}>
          <Text style={{ ...typography.meta, ...numeric, color: colors.brandFg }}>
            {total} {total === 1 ? 'item' : 'itens'}
          </Text>
        </View>
      </View>
    </View>
  );
}
