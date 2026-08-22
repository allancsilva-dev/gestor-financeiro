import React from 'react';
import { Text, TouchableOpacity, StyleProp, ViewStyle } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useTheme, useTabBarSpace, fabSize, radius, shadow, spacing } from '../../theme';

interface FabProps {
  onPress: () => void;
  accessibilityLabel: string;
  style?: StyleProp<ViewStyle>;
}

// FAB flutuante das sub-telas (Categorias, Recorrências). Fica ACIMA da tab
// bar flutuante: com `bottom` fixo ele nascia atrás do painel de navegação e o
// toque não chegava nele. Cores do tema (fabFrom/fabTo), não o violeta cru do
// protótipo antigo.
export default function Fab({ onPress, accessibilityLabel, style }: FabProps) {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.8}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      style={[
        {
          position: 'absolute',
          right: spacing.lg,
          bottom: tabBarSpace,
          shadowColor: colors.fabGlow,
          ...shadow.glow,
        },
        style,
      ]}
    >
      <LinearGradient
        colors={[colors.fabFrom, colors.fabTo]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={{
          width: fabSize,
          height: fabSize,
          borderRadius: radius.pill,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {/* O "+" é geometria do botão, não um papel da escala tipográfica */}
        <Text style={{ color: colors.brandText, fontSize: 28, lineHeight: 32, fontWeight: '400' }}>+</Text>
      </LinearGradient>
    </TouchableOpacity>
  );
}
