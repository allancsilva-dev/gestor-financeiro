import React from 'react';
import { Text } from 'react-native';
import { useTheme, spacing, typography } from '../../theme';

interface Props {
  children: string;
  /** Primeiro rótulo de um bloco não precisa do respiro de cima. */
  primeiro?: boolean;
}

/**
 * Rótulo de um grupo de controles que não é um `ui/Field` — a faixa de chips de
 * conta, a lista de modalidades, o seletor de bandeira.
 *
 * Existe porque `ui/Field` traz o próprio rótulo e essas fileiras não têm campo
 * de texto. Cada tela escrevia o seu, em CAIXA ALTA de 9 ou 10pt com
 * `letterSpacing` — a mesma assinatura que o `Field` já abandonou.
 */
export default function RotuloDeGrupo({ children, primeiro = false }: Props) {
  const colors = useTheme();
  return (
    <Text
      style={{
        ...typography.meta,
        color: colors.textSecondary,
        marginTop: primeiro ? 0 : spacing.sm,
        marginBottom: spacing.xs + 2,
      }}
    >
      {children}
    </Text>
  );
}
