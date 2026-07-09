import React from 'react';
import { View, Text, StyleProp, ViewStyle } from 'react-native';
import { useTheme } from '../../theme';

export type TileTone = 'brand' | 'success' | 'danger' | 'warning' | 'info' | 'neutral';

interface IconTileProps {
  children: React.ReactNode;
  tone?: TileTone;
  size?: number;
  style?: StyleProp<ViewStyle>;
}

// Quadradinho pastel + emoji/glifo — o sistema de ícones de categoria do app
export default function IconTile({ children, tone = 'brand', size = 40, style }: IconTileProps) {
  const colors = useTheme();
  const tones: Record<TileTone, { bg: string; fg: string }> = {
    brand: { bg: colors.brandBg, fg: colors.brandFg },
    success: { bg: colors.successBg, fg: colors.success },
    danger: { bg: colors.dangerBg, fg: colors.danger },
    warning: { bg: colors.warningBg, fg: colors.warning },
    info: { bg: colors.infoBg, fg: colors.info },
    neutral: { bg: colors.card, fg: colors.textSecondary },
  };
  const t = tones[tone];

  return (
    <View
      style={[
        {
          width: size,
          height: size,
          borderRadius: 12,
          backgroundColor: t.bg,
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        },
        style,
      ]}
    >
      {typeof children === 'string' ? (
        <Text style={{ color: t.fg, fontSize: Math.round(size * 0.45), lineHeight: Math.round(size * 0.55) }}>
          {children}
        </Text>
      ) : (
        children
      )}
    </View>
  );
}
