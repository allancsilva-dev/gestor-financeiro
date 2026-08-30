import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { useTheme, numeric, spacing, typography } from '../../theme';
import { iconeDecorativo } from '../../utils/acessibilidade';
import IconTile, { TileTone } from './IconTile';

interface ListRowProps {
  icon?: React.ReactNode;
  iconTone?: TileTone;
  /**
   * Cor da entidade dona da linha (categoria, meta). Vence o `iconTone` —
   * DESIGN.md:76-77 manda a entidade com cor própria passar `cor` ao tile, e
   * a linha era o único lugar que engolia esse dado.
   */
  iconCor?: string | null;
  title: string;
  subtitle?: string;
  value?: string;
  valueTone?: 'success' | 'danger';
  trailing?: React.ReactNode;
  height?: number;
  divider?: boolean;
  onPress?: () => void;
  /** O que a linha faz ao ser tocada ("abre o extrato"). Nunca o rótulo. */
  dica?: string;
}

/**
 * Linha padrão de lista financeira: tile + título/metadado + valor colorido.
 *
 * A linha **não** recebe `accessibilityLabel`. Ela é um nó composto, e um rótulo
 * curado colapsaria os filhos: o leitor de tela anunciava só o título e a busca
 * por texto perdia o subtítulo e o valor — a mesma classe de bug do BACKLOG-0096.
 * O texto visível é o rótulo (DESIGN.md:148-172); o contexto vai em
 * `accessibilityHint`, e o tile fica escondido por ser decorativo.
 */
export default function ListRow({
  icon,
  iconTone = 'brand',
  iconCor,
  title,
  subtitle,
  value,
  valueTone,
  trailing,
  height = 64,
  divider = true,
  onPress,
  dica,
}: ListRowProps) {
  const colors = useTheme();
  const valueColor =
    valueTone === 'success' ? colors.success : valueTone === 'danger' ? colors.danger : colors.textPrimary;

  const content = (
    <View
      style={{
        minHeight: height,
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.md,
        borderBottomWidth: divider ? 1 : 0,
        borderBottomColor: colors.border,
      }}
    >
      {icon != null && (
        <View {...iconeDecorativo}>
          <IconTile tone={iconTone} cor={iconCor}>{icon}</IconTile>
        </View>
      )}
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text numberOfLines={1} style={{ ...typography.rowTitle, color: colors.textPrimary }}>
          {title}
        </Text>
        {subtitle != null && (
          <Text numberOfLines={1} style={{ ...typography.meta, color: colors.textSecondary, marginTop: 2 }}>
            {subtitle}
          </Text>
        )}
      </View>
      {trailing != null
        ? trailing
        : value != null && (
            <Text style={{ ...typography.value, ...numeric, color: valueColor }}>{value}</Text>
          )}
    </View>
  );

  if (onPress) {
    return (
      <TouchableOpacity
        onPress={onPress}
        activeOpacity={0.7}
        accessibilityRole="button"
        accessibilityHint={dica}
      >
        {content}
      </TouchableOpacity>
    );
  }
  return content;
}
