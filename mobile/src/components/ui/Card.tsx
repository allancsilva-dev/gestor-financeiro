import React from 'react';
import { View, ViewProps } from 'react-native';
import { useEsquema, useTheme, cardRadius, shadow, spacing } from '../../theme';

interface CardProps extends ViewProps {
  radius?: number;
  padded?: boolean;
}

// Card padrão: branco com sombra suave no claro, borda sutil no escuro (DESIGN.md).
// A sombra é `shadow.card` (geometria) + `colors.sombra` (cor do tema) — antes o
// componente carregava um `shadowColor` próprio e o token de sombra ficava morto.
export default function Card({ radius = cardRadius, padded = true, style, children, ...rest }: CardProps) {
  const colors = useTheme();
  const dark = useEsquema() === 'dark';

  return (
    <View
      style={[
        {
          backgroundColor: colors.card,
          borderRadius: radius,
          padding: padded ? spacing.lg : 0,
          ...(dark
            ? { borderWidth: 1, borderColor: colors.border }
            : { shadowColor: colors.sombra, ...shadow.card }),
        },
        style,
      ]}
      {...rest}
    >
      {children}
    </View>
  );
}
