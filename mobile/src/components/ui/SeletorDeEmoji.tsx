import React from 'react';
import { ScrollView, Text, TouchableOpacity, View } from 'react-native';
import { useTheme, radius, spacing } from '../../theme';
import { EMOJIS_CATEGORIA } from '../../domain/iconeCategoria';

interface Props {
  valor: string | null;
  onChange: (emoji: string) => void;
  /** Rótulo do grupo para o leitor de tela ("Ícone da categoria"). */
  rotulo: string;
  testID?: string;
}

/**
 * Grade de emoji para o campo `icone` de categoria e meta.
 *
 * O emoji É o sistema de ícones do app (DESIGN.md:76-77): o valor escolhido
 * aqui vai para o banco e é desenhado como texto no tile das listas. Sem este
 * seletor, categoria criada no app nascia sem ícone e a lista caía em '↑'/'↓'.
 *
 * A grade vive em `domain/iconeCategoria` porque o backend limita o campo a 10
 * unidades UTF-16 e a lista precisa de teste — não é decoração de tela.
 */
export default function SeletorDeEmoji({ valor, onChange, rotulo, testID }: Props) {
  const colors = useTheme();

  return (
    <ScrollView
      style={{ maxHeight: 168 }}
      contentContainerStyle={{
        flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm,
        paddingVertical: spacing.xs,
      }}
      accessibilityRole="radiogroup"
      accessibilityLabel={rotulo}
      keyboardShouldPersistTaps="handled"
      testID={testID}
    >
      {EMOJIS_CATEGORIA.map(emoji => (
        <TouchableOpacity
          key={emoji}
          onPress={() => onChange(emoji)}
          accessibilityRole="radio"
          accessibilityState={{ selected: valor === emoji }}
          accessibilityLabel={`Ícone ${emoji}`}
          style={{
            width: 44, height: 44, borderRadius: radius.md,
            alignItems: 'center', justifyContent: 'center',
            backgroundColor: valor === emoji ? colors.brandBg : colors.card,
            borderWidth: valor === emoji ? 2 : 1,
            borderColor: valor === emoji ? colors.brand : colors.border,
          }}
        >
          <Text style={{ fontSize: 22, lineHeight: 26 }}>{emoji}</Text>
        </TouchableOpacity>
      ))}
    </ScrollView>
  );
}
