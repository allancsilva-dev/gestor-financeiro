import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../theme';
import { BadgeStatus } from '../../types';

interface Props {
  status: BadgeStatus;
  label: string;
}

export const Badge: React.FC<Props> = ({ status, label }) => {
  const colors = useTheme();
  const map = {
    ativo: { bg: colors.successBg, color: colors.success },
    pendente: { bg: colors.warningBg, color: colors.warning },
    inativo: { bg: colors.dangerBg, color: colors.danger },
    cancelado: { bg: colors.dangerBg, color: colors.danger },
  } as const;

  const styles = StyleSheet.create({
    container: {
      paddingHorizontal: 8,
      paddingVertical: 2,
      borderRadius: 20,
      backgroundColor: map[status].bg,
    },
    text: {
      fontSize: 10,
      fontWeight: '700' as any,
      color: map[status].color,
    },
  });

  return (
    <View style={styles.container}>
      <Text style={styles.text}>{label}</Text>
    </View>
  );
};

export default Badge;
