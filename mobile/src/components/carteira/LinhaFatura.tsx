import React from 'react';
import { Text, TouchableOpacity, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { iconeDecorativo } from '../../utils/acessibilidade';
import { useTheme, spacing, typography, numeric, radius } from '../../theme';
import { FaturaResumo } from '../../types';
import { formatCurrency, formatDateOnlyBR } from '../../utils/format';
import { competenciaCurta, PosicaoFatura, ROTULO_FATURA } from '../../domain/carteiraFormat';

interface Props {
  fatura: FaturaResumo;
  posicao: PosicaoFatura;
  /** Cor do emissor: destaca a fatura atual sem usar a marca do app. */
  corDestaque: string;
  onPress: () => void;
}

export default function LinhaFatura({ fatura, posicao, corDestaque, onPress }: Props) {
  const colors = useTheme();
  const destacada = posicao === 'atual';
  const rotulo = `${ROTULO_FATURA[posicao]} · ${competenciaCurta(fatura.mes, fatura.ano)}`;

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.85}
      accessibilityRole="button"
      accessibilityHint="Abre os detalhes da fatura"
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.md,
        minHeight: 64,
        paddingHorizontal: spacing.lg - 2,
        paddingVertical: spacing.md,
        borderRadius: radius.lg - 2,
        backgroundColor: colors.card,
        borderWidth: 1,
        borderColor: destacada ? corDestaque + '55' : colors.border,
      }}
    >
      {/* Tinta do emissor a 10% marca a fatura corrente — mesma leitura da
          referência, onde a linha "Fatura atual" puxa para o roxo do cartão. */}
      {destacada && (
        <View
          style={{
            position: 'absolute', left: 0, right: 0, top: 0, bottom: 0,
            borderRadius: radius.lg - 2, backgroundColor: corDestaque, opacity: 0.1,
          }}
        />
      )}

      <View style={{ flex: 1 }}>
        <Text
          numberOfLines={1}
          style={{
            ...typography.cardTitle,
            color: destacada ? colors.textPrimary : colors.textSecondary,
            fontWeight: destacada ? '800' : '700',
          }}
        >
          {rotulo}
        </Text>
        <Text style={{ ...typography.meta, color: colors.textMuted, marginTop: 2 }}>
          Vence {formatDateOnlyBR(fatura.dataVencimento)}
        </Text>
      </View>

      <Text style={{ ...numeric, color: colors.textPrimary, fontSize: 16, fontWeight: '800' }}>
        {formatCurrency(fatura.valorTotal)}
      </Text>
      <Ionicons name="chevron-forward" size={18} color={colors.textMuted} {...iconeDecorativo} />
    </TouchableOpacity>
  );
}
