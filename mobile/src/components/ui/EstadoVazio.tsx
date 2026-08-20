import React from 'react';
import { Text, TouchableOpacity, View } from 'react-native';
import { useTheme, spacing, typography, radius } from '../../theme';

interface Props {
  emoji: string;
  titulo: string;
  texto?: string;
  acao?: { rotulo: string; onPress: () => void };
}

/**
 * Estado vazio/erro com voz própria. Existe para que "sem dado", "deu erro" e
 * "params inválidos" não colapsem todos na mesma tela zerada — que era
 * exatamente o problema da tela de fatura.
 */
export default function EstadoVazio({ emoji, titulo, texto, acao }: Props) {
  const colors = useTheme();
  return (
    <View style={{ alignItems: 'center', paddingVertical: 56, paddingHorizontal: spacing.xxl }}>
      <Text style={{ fontSize: 36 }}>{emoji}</Text>
      <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginTop: spacing.md, textAlign: 'center' }}>
        {titulo}
      </Text>
      {!!texto && (
        <Text style={{ ...typography.body, color: colors.textSecondary, marginTop: spacing.xs, textAlign: 'center' }}>
          {texto}
        </Text>
      )}
      {!!acao && (
        <TouchableOpacity
          onPress={acao.onPress}
          accessibilityRole="button"
          accessibilityLabel={acao.rotulo}
          style={{
            marginTop: spacing.lg, paddingHorizontal: spacing.xl, height: 44,
            borderRadius: radius.md, alignItems: 'center', justifyContent: 'center',
            backgroundColor: colors.brand,
          }}
        >
          <Text style={{ ...typography.button, color: colors.brandText }}>{acao.rotulo}</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}
