import React from 'react';
import { ActivityIndicator, Text, TouchableOpacity, ViewStyle } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, radius, spacing, typography } from '../../theme';

type Variante = 'primario' | 'secundario' | 'perigo' | 'texto' | 'invertido';
/**
 * `padrao` é o botão de formulário e rodapé de fluxo (altura 52).
 * `pill` é a ação dentro de um card — mais curta e totalmente arredondada,
 * como o CTA "Depositar" do card de meta. Nunca abaixo de 44: é alvo de toque.
 */
type Tamanho = 'padrao' | 'pill';

interface BotaoProps {
  titulo: string;
  onPress: () => void;
  variante?: Variante;
  tamanho?: Tamanho;
  carregando?: boolean;
  desabilitado?: boolean;
  icone?: React.ComponentProps<typeof Ionicons>['name'];
  testID?: string;
  accessibilityLabel?: string;
  style?: ViewStyle;
}

/**
 * Botão do app. Antes cada tela repetia um `TouchableOpacity` com altura,
 * raio e peso próprios — duas assinaturas diferentes conviviam no mesmo fluxo.
 * Aqui ficam os estados que todo botão precisa ter: normal, pressionado,
 * desabilitado e carregando.
 */
export default function Botao({
  titulo,
  onPress,
  variante = 'primario',
  tamanho = 'padrao',
  carregando = false,
  desabilitado = false,
  icone,
  testID,
  accessibilityLabel,
  style,
}: BotaoProps) {
  const colors = useTheme();
  const inativo = desabilitado || carregando;

  const fundo = {
    primario: colors.brand,
    secundario: 'transparent',
    perigo: colors.danger,
    texto: 'transparent',
    // Inverte o tema: o único elemento claro-sobre-escuro do card. É assim que o
    // CTA lê como CTA sem gastar a cor da marca numa superfície que já é colorida.
    invertido: colors.textPrimary,
  }[variante];

  const tinta = {
    primario: colors.brandText,
    secundario: colors.textPrimary,
    perigo: colors.brandText,
    texto: colors.brandFg,
    invertido: colors.bg,
  }[variante];

  const alturaMinima = tamanho === 'pill' ? 44 : variante === 'texto' ? 44 : 52;

  return (
    <TouchableOpacity
      testID={testID}
      onPress={onPress}
      disabled={inativo}
      activeOpacity={0.85}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? titulo}
      accessibilityState={{ disabled: inativo, busy: carregando }}
      style={[
        {
          minHeight: alturaMinima,
          borderRadius: tamanho === 'pill' ? radius.pill : radius.md,
          paddingHorizontal: spacing.lg,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          gap: spacing.sm,
          backgroundColor: fundo,
          borderWidth: variante === 'secundario' ? 1 : 0,
          borderColor: colors.border,
          opacity: inativo ? 0.6 : 1,
        },
        style,
      ]}
    >
      {carregando ? (
        <ActivityIndicator color={tinta} />
      ) : (
        <>
          {icone ? <Ionicons name={icone} size={18} color={tinta} /> : null}
          <Text style={{ ...typography.button, color: tinta }}>{titulo}</Text>
        </>
      )}
    </TouchableOpacity>
  );
}
