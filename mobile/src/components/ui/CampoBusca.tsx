import React from 'react';
import { TextInput, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, radius, spacing, typography } from '../../theme';
import { iconeDecorativo } from '../../utils/acessibilidade';

interface Props {
  valor: string;
  onChange: (texto: string) => void;
  /** Vira o placeholder e o rótulo do campo — não deixe divergirem. */
  placeholder: string;
}

/**
 * Caixa de busca do app: lupa dentro do campo, fundo `fieldBg`, sem rótulo em
 * cima. Não é `ui/Field` — busca não tem label, e o campo é a própria pergunta.
 *
 * Home e Transações desenhavam a sua, com raios e alturas diferentes.
 */
export default function CampoBusca({ valor, onChange, placeholder }: Props) {
  const colors = useTheme();
  return (
    <View
      style={{
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.sm,
        minHeight: 48,
        paddingHorizontal: spacing.lg,
        borderRadius: radius.lg,
        backgroundColor: colors.fieldBg,
      }}
    >
      <Ionicons name="search" size={18} color={colors.textMuted} {...iconeDecorativo} />
      <TextInput
        value={valor}
        onChangeText={onChange}
        placeholder={placeholder}
        placeholderTextColor={colors.textMuted}
        returnKeyType="search"
        accessibilityLabel={placeholder}
        style={{ flex: 1, ...typography.body, color: colors.textPrimary, paddingVertical: 0 }}
      />
    </View>
  );
}
