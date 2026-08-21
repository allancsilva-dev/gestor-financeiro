import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, spacing, typography, radius } from '../../theme';

interface AcaoCircular {
  icone: React.ComponentProps<typeof Ionicons>['name'];
  onPress: () => void;
  accessibilityLabel: string;
}

interface Props {
  titulo: string;
  /** Botão circular à direita — o mesmo da tela de metas. */
  acao?: AcaoCircular;
  /** Ação livre à direita (link textual, badge). Ignorada quando `acao` é passada. */
  children?: React.ReactNode;
}

/**
 * Header inline das telas de topo: título grande à esquerda, ação à direita.
 * Não existe header nativo no app (`headerShown: false` em todos os layouts), e
 * a safe area entra aqui — cada tela repetia `insets.top + spacing.*` na mão.
 */
export default function CabecalhoDeTela({ titulo, acao, children }: Props) {
  const colors = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <View
      style={{
        paddingTop: insets.top + spacing.md,
        paddingHorizontal: spacing.lg,
        paddingBottom: spacing.sm,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: spacing.md,
      }}
    >
      <Text
        accessibilityRole="header"
        numberOfLines={1}
        style={{ ...typography.screenTitle, color: colors.textPrimary, flexShrink: 1 }}
      >
        {titulo}
      </Text>

      {acao ? (
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
      ) : (
        children
      )}
    </View>
  );
}
