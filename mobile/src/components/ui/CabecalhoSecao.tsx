import React from 'react';
import { View, Text } from 'react-native';
import { useTheme, spacing, typography } from '../../theme';
import { e } from '../../theme/escala';

interface Props {
  eyebrow: string;
  titulo: string;
  texto: string;
  /**
   * Converte as medidas pela largura da tela (`theme/escala`). Só para telas com
   * mock medido em `.design/` — a de metas nasceu assim. Sem referência, escalar
   * é inventar proporção, então o padrão é token cru.
   */
  escalar?: boolean;
}

/**
 * Abertura editorial de uma seção: rótulo curto, título grande e uma linha de
 * orientação. Medidas em `.design/MEDICOES-metas.md`.
 */
export default function CabecalhoSecao({ eyebrow, titulo, texto, escalar = false }: Props) {
  const colors = useTheme();
  const m = escalar ? e : (v: number) => v;

  return (
    <View style={{
      paddingHorizontal: escalar ? m(16) : spacing.lg,
      paddingTop: m(22),
      paddingBottom: escalar ? m(25) : spacing.lg,
    }}>
      <Text
        accessibilityRole="header"
        style={{ color: colors.textMuted, fontSize: m(10), fontWeight: '700', letterSpacing: m(1.2) }}
      >
        {eyebrow}
      </Text>
      <Text style={{
        color: colors.textPrimary,
        fontSize: escalar ? m(25) : 22,
        lineHeight: escalar ? undefined : 28,
        fontWeight: '500',
        letterSpacing: -0.4,
        marginTop: escalar ? m(10) : spacing.sm,
      }}>
        {titulo}
      </Text>
      <Text style={{
        color: colors.textSecondary,
        fontSize: escalar ? m(14) : typography.body.fontSize,
        lineHeight: escalar ? m(19) : typography.body.lineHeight,
        marginTop: escalar ? m(12) : spacing.sm,
      }}>
        {texto}
      </Text>
    </View>
  );
}
