import React from 'react';
import { View, Text, TextInput, TextInputProps } from 'react-native';
import { useTheme } from '../../theme';

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

// Campo com label-eyebrow uppercase (assinatura visual "E-MAIL" / "VALOR") + erro
export default function Field({ label, error, style, ref, ...rest }: FieldProps) {
  const colors = useTheme();
  return (
    <View style={{ marginBottom: 16 }}>
      <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>
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
            borderRadius: 12,
            padding: 12,
            color: colors.textPrimary,
            fontSize: 15,
          },
          style,
        ]}
        {...rest}
      />
      {error ? (
        <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={{ color: colors.danger, fontSize: 12, marginTop: 6 }}>
          {error}
        </Text>
      ) : null}
    </View>
  );
}
