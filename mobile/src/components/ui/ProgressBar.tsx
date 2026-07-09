import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../../theme';

interface ProgressBarProps {
  value: number; // 0–100
  height?: number;
}

// Barra de progresso de metas/orçamentos: trilha border, preenchimento brand (success em 100%)
export default function ProgressBar({ value, height = 6 }: ProgressBarProps) {
  const colors = useTheme();
  const pct = Math.max(0, Math.min(100, value));
  return (
    <View
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: Math.round(pct) }}
      style={{ height, borderRadius: height / 2, backgroundColor: colors.border, overflow: 'hidden' }}
    >
      <View
        style={{
          width: `${pct}%`,
          height: '100%',
          borderRadius: height / 2,
          backgroundColor: pct >= 100 ? colors.success : colors.brand,
        }}
      />
    </View>
  );
}
