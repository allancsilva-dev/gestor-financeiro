import React from 'react';
import { Text, TouchableOpacity, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import Ionicons from '@expo/vector-icons/Ionicons';
import { iconeDecorativo } from '../../utils/acessibilidade';
import { useTheme, radius, spacing, typography, numeric } from '../../theme';
import { formatCurrency } from '../../utils/format';
import SkeletonBox from '../ui/SkeletonBox';

interface Props {
  saldo: number;
  emConta: number;
  emCartoes: number;
  entradas: number;
  saidas: number;
  faturas: number;
  oculto: boolean;
  onToggleOculto: () => void;
  onNovaTransacao: () => void;
  onCarteira: () => void;
  carregando?: boolean;
  erro?: boolean;
  onTentarNovamente?: () => void;
}

const MASCARA = '•••••••';

/** Uma das três colunas de resumo (Entradas · Saídas · Faturas). */
const Coluna = ({
  icone, rotulo, valor, cor, oculto,
}: {
  icone: React.ComponentProps<typeof Ionicons>['name'];
  rotulo: string; valor: number; cor: string; oculto: boolean;
}) => (
  <View style={{ flex: 1, paddingHorizontal: spacing.md }}>
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
      <Ionicons name={icone} size={14} color={cor} />
      <Text style={{ ...typography.meta, color: cor }}>{rotulo}</Text>
    </View>
    <Text
      numberOfLines={1}
      adjustsFontSizeToFit
      minimumFontScale={0.75}
      style={{ ...typography.value, ...numeric, color: cor, marginTop: 2 }}
    >
      {oculto ? MASCARA : formatCurrency(valor)}
    </Text>
  </View>
);

/**
 * Card de saldo da home. Gradiente diagonal e as três colunas de resumo vêm
 * medidos de mobile/.design/referencia-home.png.
 */
export default function SaldoCard({
  saldo, emConta, emCartoes, entradas, saidas, faturas,
  oculto, onToggleOculto, onNovaTransacao, onCarteira,
  carregando, erro, onTentarNovamente,
}: Props) {
  const colors = useTheme();

  const corpo = () => {
    if (carregando) {
      return (
        <View style={{ gap: spacing.md }} accessibilityLabel="Carregando saldo disponível">
          <SkeletonBox width={120} height={16} borderRadius={8} />
          <SkeletonBox width="70%" height={40} borderRadius={10} />
          <SkeletonBox width="85%" height={14} borderRadius={7} />
        </View>
      );
    }
    if (erro) {
      return (
        <View style={{ minHeight: 96, justifyContent: 'center' }}>
          <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Saldo indisponível</Text>
          <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: 4 }}>
            Não foi possível atualizar seus valores.
          </Text>
          <TouchableOpacity
            accessibilityRole="button"
            accessibilityLabel="Tentar carregar saldo novamente"
            onPress={onTentarNovamente}
            style={{
              marginTop: spacing.md, minHeight: 44, paddingHorizontal: spacing.lg,
              borderRadius: radius.pill, backgroundColor: colors.brandBg,
              alignSelf: 'flex-start', justifyContent: 'center',
            }}
          >
            <Text style={{ ...typography.button, color: colors.brandFg }}>Tentar novamente</Text>
          </TouchableOpacity>
        </View>
      );
    }
    return (
      <>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <Text style={{ ...typography.label, color: colors.textSecondary }}>Saldo Disponível</Text>
          <TouchableOpacity
            onPress={onToggleOculto}
            accessibilityRole="button"
            accessibilityLabel={oculto ? 'Mostrar valores' : 'Ocultar valores'}
            accessibilityState={{ selected: oculto }}
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
            style={{
              width: 40, height: 40, borderRadius: 20,
              backgroundColor: colors.overlay,
              alignItems: 'center', justifyContent: 'center', marginTop: -6,
            }}
          >
            <Ionicons
              name={oculto ? 'eye-off-outline' : 'eye-outline'}
              size={19}
              color={colors.textPrimary}
            />
          </TouchableOpacity>
        </View>

        <Text
          numberOfLines={1}
          adjustsFontSizeToFit
          minimumFontScale={0.7}
          accessibilityLabel={`Saldo disponível ${oculto ? 'oculto' : formatCurrency(saldo)}`}
          style={{ ...typography.display, ...numeric, color: colors.textPrimary, marginTop: spacing.sm + 2 }}
        >
          {oculto ? MASCARA : formatCurrency(saldo)}
        </Text>

        <Text style={{ ...typography.meta, ...numeric, color: colors.textMuted, marginTop: spacing.sm }}>
          {oculto
            ? `${MASCARA} em conta · ${MASCARA} em cartões`
            : `${formatCurrency(emConta)} em conta · -${formatCurrency(emCartoes)} em cartões`}
        </Text>

        <View style={{ height: 1, backgroundColor: colors.border, marginVertical: spacing.md + 2 }} />

        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <Coluna icone="trending-up" rotulo="Entradas" valor={entradas} cor={colors.success} oculto={oculto} />
          <View style={{ width: 1, height: 32, backgroundColor: colors.border }} />
          <Coluna icone="trending-down" rotulo="Saídas" valor={saidas} cor={colors.danger} oculto={oculto} />
          <View style={{ width: 1, height: 32, backgroundColor: colors.border }} />
          <Coluna icone="card-outline" rotulo="Faturas" valor={faturas} cor={colors.warning} oculto={oculto} />
        </View>

        <View style={{ flexDirection: 'row', gap: spacing.md, marginTop: spacing.lg }}>
          <TouchableOpacity
            onPress={onNovaTransacao}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel="Nova Transação"
            style={{
              flex: 1, minHeight: 48, borderRadius: radius.pill, backgroundColor: colors.brand,
              flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6,
            }}
          >
            <Ionicons name="add" size={19} color={colors.brandText} {...iconeDecorativo} />
            <Text style={{ ...typography.button, color: colors.brandText }}>Nova Transação</Text>
          </TouchableOpacity>

          <TouchableOpacity
            onPress={onCarteira}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel="Carteira"
            accessibilityHint="Abre sua carteira"
            style={{
              flex: 1, minHeight: 48, borderRadius: radius.pill,
              borderWidth: 1, borderColor: colors.chipBorder,
              flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6,
            }}
          >
            <Ionicons name="wallet-outline" size={18} color={colors.textPrimary} {...iconeDecorativo} />
            <Text style={{ ...typography.button, color: colors.textPrimary }}>Carteira</Text>
          </TouchableOpacity>
        </View>
      </>
    );
  };

  return (
    <LinearGradient
      colors={[colors.heroFrom, colors.heroTo]}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={{
        borderRadius: radius.xl,
        borderWidth: 1,
        borderColor: colors.border,
        padding: spacing.lg + 2,
      }}
    >
      {corpo()}
    </LinearGradient>
  );
}
