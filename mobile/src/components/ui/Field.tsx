import React from 'react';
import { View, Text, TextInput, TextInputProps } from 'react-native';
import { useTheme } from '../../theme';

interface FieldProps extends TextInputProps {
  label: string;
  error?: string | null;
}

// Campo com label-eyebrow uppercase (assinatura visual "E-MAIL" / "VALOR") + erro
export default function Field({ label, error, style, ...rest }: FieldProps) {
  const colors = useTheme();
  return (
    <View style={{ marginBottom: 16 }}>
      <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>
        {label}
      </Text>
      <TextInput
        placeholderTextColor={colors.textMuted}
        accessibilityLabel={label}
        style={[
          {
            backgroundColor: colors.card,
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
