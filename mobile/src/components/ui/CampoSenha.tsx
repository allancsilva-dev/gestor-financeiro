import React, { useState } from 'react';
import { Text, TextInputProps, TouchableOpacity, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, radius, spacing, typography } from '../../theme';
import Field from './Field';
import { forcaSenha } from '../../utils/forcaSenha';

interface Props extends Omit<TextInputProps, 'secureTextEntry'> {
  label: string;
  value: string;
  error?: string | null;
  /** Mostra a régua de força — só faz sentido ao criar/trocar senha. */
  medidor?: boolean;
  /** Fluxo local-e2e: o AutoFill do iOS trunca campo protegido (ver register). */
  desprotegido?: boolean;
}

/**
 * Campo de senha com olho e medidor. Digitar senha às cegas e descobrir o erro
 * só depois do envio era a maior fonte de tentativa e erro no cadastro.
 */
export default function CampoSenha({
  label,
  value,
  error,
  medidor = false,
  desprotegido = false,
  ...rest
}: Props) {
  const colors = useTheme();
  const [visivel, setVisivel] = useState(false);
  const forca = forcaSenha(value);

  const corDoNivel = forca.nivel >= 3 ? colors.success : forca.nivel >= 2 ? colors.warning : colors.danger;

  return (
    <View>
      <View style={{ justifyContent: 'center' }}>
        <Field
          label={label}
          value={value}
          error={error}
          secureTextEntry={!desprotegido && !visivel}
          style={{ paddingRight: 48 }}
          {...rest}
        />
        <TouchableOpacity
          onPress={() => setVisivel((v) => !v)}
          activeOpacity={0.7}
          accessibilityRole="button"
          accessibilityLabel={visivel ? 'Ocultar senha' : 'Mostrar senha'}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          style={{
            position: 'absolute',
            right: 0,
            top: 22,
            width: 44,
            height: 44,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Ionicons name={visivel ? 'eye-off-outline' : 'eye-outline'} size={20} color={colors.textSecondary} />
        </TouchableOpacity>
      </View>

      {medidor && value.length > 0 ? (
        <View style={{ marginTop: -spacing.sm, marginBottom: spacing.lg }}>
          <View style={{ flexDirection: 'row', gap: spacing.xs }}>
            {[1, 2, 3, 4].map((n) => (
              <View
                key={n}
                style={{
                  flex: 1,
                  height: 4,
                  borderRadius: radius.pill,
                  backgroundColor: n <= forca.nivel ? corDoNivel : colors.trilha,
                }}
              />
            ))}
          </View>
          <Text
            accessibilityLiveRegion="polite"
            style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xs }}
          >
            {forca.pendencias.length > 0
              ? `Falta: ${forca.pendencias.join(', ')}`
              : `Senha ${forca.rotulo.toLowerCase()}`}
          </Text>
        </View>
      ) : null}
    </View>
  );
}
