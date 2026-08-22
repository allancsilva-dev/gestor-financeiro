import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, radius, spacing, typography } from '../../theme';
import BackButton from './BackButton';

interface AcaoCircular {
  icone: React.ComponentProps<typeof Ionicons>['name'];
  onPress: () => void;
  accessibilityLabel: string;
}

interface Props {
  titulo: string;
  /** Linha abaixo do título: período, status, contagem. */
  apoio?: React.ReactNode;
  /** Botão circular à direita — mesmo contrato de `ui/CabecalhoDeTela`. */
  acao?: AcaoCircular;
}

/**
 * Header das sub-telas de `more/`: voltar, título grande e apoio opcional.
 *
 * É o irmão de `ui/CabecalhoDeTela` para telas que não são aba. Cada sub-tela
 * repetia `insets.top + spacing.*` na mão e escolhia o próprio corpo de título —
 * havia 23, 26 e 22 convivendo. O título é `typography.screenTitle` em todas.
 */
export default function CabecalhoSubTela({ titulo, apoio, acao }: Props) {
  const colors = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <View
      style={{
        paddingTop: insets.top + spacing.md,
        paddingHorizontal: spacing.lg,
        paddingBottom: spacing.md,
      }}
    >
      <BackButton />
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
        <Text
          accessibilityRole="header"
          numberOfLines={1}
          style={{ ...typography.screenTitle, color: colors.textPrimary, flex: 1 }}
        >
          {titulo}
        </Text>
        {!!acao && (
          <TouchableOpacity
            onPress={acao.onPress}
            activeOpacity={0.7}
            accessibilityRole="button"
            accessibilityLabel={acao.accessibilityLabel}
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
            style={{
              width: 36, height: 36, borderRadius: radius.pill,
              backgroundColor: colors.overlay,
              borderWidth: 1, borderColor: colors.border,
              alignItems: 'center', justifyContent: 'center',
            }}
          >
            <Ionicons name={acao.icone} size={20} color={colors.brandFg} />
          </TouchableOpacity>
        )}
      </View>
      {!!apoio && (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginTop: spacing.xs }}>
          {apoio}
        </View>
      )}
    </View>
  );
}
