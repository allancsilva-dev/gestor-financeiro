import React from 'react';
import { View, Text, TextInput, TextInputProps } from 'react-native';
import { useTheme, radius, spacing, typography } from '../../theme';

interface FieldProps extends TextInputProps {
  label: string;
  error?: string | null;
  /**
   * Ref do TextInput, para encadear foco entre campos
   * (`onSubmitEditing={() => proximo.current?.focus()}`).
   * React 19 passa ref como prop comum — não precisa de forwardRef.
   */
  ref?: React.Ref<TextInput>;
}

/**
 * Campo de formulário: rótulo, entrada e erro.
 *
 * O rótulo era um eyebrow em CAIXA ALTA de 10pt com `letterSpacing` — abaixo do
 * piso de 12 da escala e com a mesma assinatura que `DESIGN.md` já rejeita nos
 * botões. Agora é `typography.meta` em caixa normal: o campo mais repetido do
 * app não é o lugar de gritar.
 */
export default function Field({ label, error, style, ref, ...rest }: FieldProps) {
  const colors = useTheme();
  return (
    <View style={{ marginBottom: spacing.lg }}>
      <Text style={{ ...typography.meta, color: colors.textSecondary, marginBottom: spacing.xs + 2 }}>
        {label}
      </Text>
      <TextInput
        ref={ref}
        placeholderTextColor={colors.textMuted}
        accessibilityLabel={label}
        style={[
          {
            // fieldBg é o token do papel "campo"; card é superfície de cartão
            backgroundColor: colors.fieldBg,
            borderWidth: 1,
            borderColor: error ? colors.danger : colors.border,
            borderRadius: radius.md,
            padding: spacing.md,
            color: colors.textPrimary,
            ...typography.input,
          },
          style,
        ]}
        {...rest}
      />
      {error ? (
        <Text
          accessibilityRole="alert"
          accessibilityLiveRegion="polite"
          style={{ ...typography.meta, color: colors.danger, marginTop: spacing.xs + 2 }}
        >
          {error}
        </Text>
      ) : null}
    </View>
  );
}
