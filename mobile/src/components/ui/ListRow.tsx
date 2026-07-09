import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { useTheme } from '../../theme';
import IconTile, { TileTone } from './IconTile';

interface ListRowProps {
  icon?: React.ReactNode;
  iconTone?: TileTone;
  title: string;
  subtitle?: string;
  value?: string;
  valueTone?: 'success' | 'danger';
  trailing?: React.ReactNode;
  height?: number;
  divider?: boolean;
  onPress?: () => void;
  accessibilityLabel?: string;
}

// Linha padrão de lista financeira: tile + título/metadado + valor colorido
export default function ListRow({
  icon,
  iconTone = 'brand',
  title,
  subtitle,
  value,
  valueTone,
  trailing,
  height = 64,
  divider = true,
  onPress,
  accessibilityLabel,
}: ListRowProps) {
  const colors = useTheme();
  const valueColor =
    valueTone === 'success' ? colors.success : valueTone === 'danger' ? colors.danger : colors.textPrimary;

  const content = (
    <View
      style={{
        minHeight: height,
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
        borderBottomWidth: divider ? 1 : 0,
        borderBottomColor: colors.border,
      }}
    >
      {icon != null && <IconTile tone={iconTone}>{icon}</IconTile>}
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text numberOfLines={1} style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>
          {title}
        </Text>
        {subtitle != null && (
          <Text numberOfLines={1} style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }}>
            {subtitle}
          </Text>
        )}
      </View>
      {trailing != null
        ? trailing
        : value != null && (
            <Text style={{ color: valueColor, fontSize: 14, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
              {value}
            </Text>
          )}
    </View>
  );

  if (onPress) {
    return (
      <TouchableOpacity onPress={onPress} activeOpacity={0.7} accessibilityRole="button" accessibilityLabel={accessibilityLabel ?? title}>
        {content}
      </TouchableOpacity>
    );
  }
  return content;
}
