import React from 'react';
import { Text, TouchableOpacity, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, spacing, typography } from '../../theme';

interface Props {
  /** "Março de 2026" — o texto visível, que também é o rótulo do bloco. */
  rotulo: string;
  /** Linha de apoio sob o mês: contagem, total, o que a tela quiser. */
  apoio?: string;
  onAnterior: () => void;
  onProximo: () => void;
  /**
   * Falso trava a seta de avançar. O futuro não tem lançamento: avançar além do
   * mês corrente só mostraria uma tela vazia.
   */
  podeAvancar?: boolean;
}

const Seta = ({ direcao, onPress, desabilitada }: {
  direcao: 'anterior' | 'proximo';
  onPress: () => void;
  desabilitada: boolean;
}) => {
  const colors = useTheme();
  return (
    <TouchableOpacity
      onPress={onPress}
      disabled={desabilitada}
      accessibilityRole="button"
      accessibilityLabel={direcao === 'anterior' ? 'Mês anterior' : 'Próximo mês'}
      accessibilityState={{ disabled: desabilitada }}
      style={{
        minWidth: 44, minHeight: 44,
        alignItems: 'center', justifyContent: 'center',
        opacity: desabilitada ? 0.3 : 1,
      }}
    >
      <Ionicons
        name={direcao === 'anterior' ? 'chevron-back' : 'chevron-forward'}
        size={20}
        color={colors.brandFg}
      />
    </TouchableOpacity>
  );
};

/**
 * Navegação mês a mês. Cada tela desenhava a sua — uma com glifos `‹`/`›` de 20pt
 * e alvo de 44, outra com `padding: 8` e nenhum `accessibilityRole`, que deixava
 * as setas invisíveis para o leitor de tela e menores que o alvo mínimo.
 */
export default function NavegadorDeMes({ rotulo, apoio, onAnterior, onProximo, podeAvancar = true }: Props) {
  const colors = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <Seta direcao="anterior" onPress={onAnterior} desabilitada={false} />
      <View style={{ alignItems: 'center', flex: 1 }}>
        <Text style={{ ...typography.value, color: colors.textPrimary }}>{rotulo}</Text>
        {apoio != null && (
          <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>{apoio}</Text>
        )}
      </View>
      <Seta direcao="proximo" onPress={onProximo} desabilitada={!podeAvancar} />
    </View>
  );
}
