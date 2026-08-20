import React from 'react';
import { View, Text } from 'react-native';
import { useTheme } from '../../theme';
import { e } from '../../theme/escala';

interface Props {
  eyebrow: string;
  titulo: string;
  texto: string;
}

/**
 * Abertura editorial da lista de metas: rótulo curto, título grande e uma linha de
 * orientação. Medidas em `.design/MEDICOES-metas.md`.
 */
export default function CabecalhoSecao({ eyebrow, titulo, texto }: Props) {
  const colors = useTheme();
  return (
    <View style={{ paddingHorizontal: e(16), paddingTop: e(22), paddingBottom: e(25) }}>
      <Text
        accessibilityRole="header"
        style={{ color: colors.textMuted, fontSize: e(10), fontWeight: '700', letterSpacing: e(1.2) }}
      >
        {eyebrow}
      </Text>
      <Text style={{ color: colors.textPrimary, fontSize: e(25), fontWeight: '500', letterSpacing: -0.4, marginTop: e(10) }}>
        {titulo}
      </Text>
      <Text style={{ color: colors.textSecondary, fontSize: e(14), lineHeight: e(19), marginTop: e(12) }}>
        {texto}
      </Text>
    </View>
  );
}
