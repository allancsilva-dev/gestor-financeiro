import React from 'react';
import { Text, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import { useTheme } from '../../theme';

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
      style={{ flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start', marginBottom: 8, minHeight: 32 }}
    >
      <Text style={{ color: colors.textSecondary, fontSize: 22, lineHeight: 22, marginRight: 4 }}>‹</Text>
      <Text style={{ color: colors.textSecondary, fontSize: 15, fontWeight: '600' }}>Voltar</Text>
    </TouchableOpacity>
  );
}
