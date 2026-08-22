import React from 'react';
import { Text, View } from 'react-native';
import { useTheme, radius, spacing, typography } from '../../theme';

interface Props {
  valor?: number;
  /** Acima disto o número vira "N+" — o círculo não cresce sem limite. */
  maximo?: number;
}

/**
 * Bolha de contagem não lida — o sino da home e a linha de notificações em
 * Ajustes desenhavam a sua, com diâmetros e corpos diferentes.
 *
 * Não recebe `accessibilityLabel`: quem anuncia a contagem é o controle que a
 * contém ("Notificações, 3 não lidas"), senão o número é lido duas vezes.
 */
export default function Contador({ valor, maximo = 9 }: Props) {
  const colors = useTheme();
  if (!valor) return null;

  return (
    <View
      style={{
        minWidth: 20,
        height: 20,
        borderRadius: radius.pill,
        paddingHorizontal: spacing.xs + 1,
        backgroundColor: colors.brand,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Text style={{ ...typography.meta, fontWeight: '700', color: colors.brandText }}>
        {valor > maximo ? `${maximo}+` : valor}
      </Text>
    </View>
  );
}
