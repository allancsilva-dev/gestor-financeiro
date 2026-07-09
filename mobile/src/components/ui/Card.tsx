import React from 'react';
import { View, ViewProps, useColorScheme } from 'react-native';
import { useTheme } from '../../theme';

interface CardProps extends ViewProps {
  radius?: number;
  padded?: boolean;
}

// Card padrão: branco com sombra suave no claro, borda sutil no escuro (DESIGN.md)
export default function Card({ radius = 18, padded = true, style, children, ...rest }: CardProps) {
  const colors = useTheme();
  const dark = useColorScheme() === 'dark';

  return (
    <View
      style={[
        {
          backgroundColor: colors.card,
          borderRadius: radius,
          padding: padded ? 16 : 0,
          ...(dark
            ? { borderWidth: 1, borderColor: colors.border }
            : {
                shadowColor: '#1e1a3c',
                shadowOpacity: 0.08,
                shadowRadius: 12,
                shadowOffset: { width: 0, height: 8 },
                elevation: 3,
              }),
        },
        style,
      ]}
      {...rest}
    >
      {children}
    </View>
  );
}
