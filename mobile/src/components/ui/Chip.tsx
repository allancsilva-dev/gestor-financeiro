import React from 'react';
import { Text, TouchableOpacity } from 'react-native';
import { useTheme } from '../../theme';

interface ChipProps {
  label: string;
  selected?: boolean;
  onPress?: () => void;
}

// Pill de filtro/seleção (Todos · Entradas · Saídas, categorias)
export default function Chip({ label, selected = false, onPress }: ChipProps) {
  const colors = useTheme();
  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.7}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={{
        minHeight: 44,
        paddingHorizontal: 14,
        paddingVertical: 6,
        borderRadius: 999,
        borderWidth: 1,
        borderColor: selected ? colors.brand : colors.border,
        backgroundColor: selected ? colors.brandBg : colors.card,
        justifyContent: 'center',
      }}
    >
      <Text style={{ color: selected ? colors.brandFg : colors.textSecondary, fontSize: 13, fontWeight: selected ? '600' : '400' }}>
        {label}
      </Text>
    </TouchableOpacity>
  );
}
