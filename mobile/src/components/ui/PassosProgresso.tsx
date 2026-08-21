import React from 'react';
import { View } from 'react-native';
import { useTheme, radius, spacing } from '../../theme';

interface Props {
  passo: number;
  total: number;
}

/**
 * Progresso do fluxo de entrada (cadastro → conta principal). Segmentos, não
 * barra contínua: o usuário precisa ver quantas etapas faltam, não uma
 * porcentagem.
 */
export default function PassosProgresso({ passo, total }: Props) {
  const colors = useTheme();

  return (
    <View
      accessibilityRole="progressbar"
      accessibilityLabel={`Passo ${passo} de ${total}`}
      accessibilityValue={{ min: 1, max: total, now: passo }}
      style={{ flexDirection: 'row', gap: spacing.xs, flex: 1 }}
    >
      {Array.from({ length: total }, (_, i) => (
        <View
          key={i}
          style={{
            flex: 1,
            height: 4,
            borderRadius: radius.pill,
            backgroundColor: i < passo ? colors.brand : colors.trilha,
          }}
        />
      ))}
    </View>
  );
}
