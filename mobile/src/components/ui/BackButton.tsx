import React from 'react';
import { Text, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, spacing, typography } from '../../theme';
import { iconeDecorativo } from '../../utils/acessibilidade';

// Botão de voltar para as sub-telas de "Mais" (more/), onde o header nativo
// fica oculto (headerShown:false). Colocar como primeiro filho do bloco de
// título da tela. Ver PROB-0018.
export default function BackButton() {
  const colors = useTheme();
  const router = useRouter();
  return (
    <TouchableOpacity
      onPress={() => router.back()}
      activeOpacity={0.7}
      accessibilityRole="button"
      accessibilityLabel="Voltar"
      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        alignSelf: 'flex-start',
        marginBottom: spacing.sm,
        minHeight: 44,
      }}
    >
      {/* O glifo entrava na composição do rótulo (DESIGN.md:157) — fica escondido */}
      <Ionicons name="chevron-back" size={20} color={colors.textSecondary} {...iconeDecorativo} />
      <Text style={{ ...typography.value, color: colors.textSecondary }}>Voltar</Text>
    </TouchableOpacity>
  );
}
