import React from 'react';
import { ScrollView, Text, TouchableOpacity, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, radius, spacing, typography, numeric } from '../../theme';
import { ParcelaAgendada } from '../../types';
import { formatCurrency, formatDateOnlyBR } from '../../utils/format';
import SkeletonBox from '../ui/SkeletonBox';

interface Props {
  itens: ParcelaAgendada[];
  carregando?: boolean;
  onVerTodas: () => void;
  onAbrir: (item: ParcelaAgendada) => void;
}

const CARTAO_LARGURA = 300;

const BANDEIRA_LABEL: Record<string, string> = {
  VISA: 'Visa',
  MASTERCARD: 'Mastercard',
  ELO: 'Elo',
  AMEX: 'Amex',
  HIPERCARD: 'Hipercard',
  OUTRA: 'Cartão',
};

/** Data curta do card: "9 de set." */
const dataCurta = (iso: string) => {
  const [ano, mes, dia] = iso.split('-').map(Number);
  const meses = ['jan.', 'fev.', 'mar.', 'abr.', 'mai.', 'jun.', 'jul.', 'ago.', 'set.', 'out.', 'nov.', 'dez.'];
  return `${dia} de ${meses[mes - 1] ?? ''}`;
};

export default function ParcelasCarrossel({ itens, carregando, onVerTodas, onAbrir }: Props) {
  const colors = useTheme();

  if (!carregando && itens.length === 0) return null;

  return (
    <View>
      <View style={{
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        marginBottom: spacing.md,
      }}>
        <Text style={{ ...typography.section, color: colors.textPrimary }}>Parcelas Agendadas</Text>
        <TouchableOpacity
          onPress={onVerTodas}
          accessibilityRole="button"
          accessibilityLabel="Ver todas as parcelas"
          style={{ flexDirection: 'row', alignItems: 'center', gap: 2, minHeight: 44, justifyContent: 'center' }}
        >
          <Text style={{ ...typography.label, color: colors.brandFg }}>Ver todas</Text>
          <Ionicons name="chevron-forward" size={15} color={colors.brandFg} />
        </TouchableOpacity>
      </View>

      {carregando ? (
        <View style={{ flexDirection: 'row', gap: spacing.md }}>
          <SkeletonBox width={CARTAO_LARGURA} height={96} borderRadius={radius.lg} />
          <SkeletonBox width={CARTAO_LARGURA} height={96} borderRadius={radius.lg} />
        </View>
      ) : (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ gap: spacing.md, paddingRight: spacing.lg }}
        >
          {itens.map(item => {
            const bandeira = item.cartao?.bandeira ? BANDEIRA_LABEL[item.cartao.bandeira] ?? 'Cartão' : null;
            const progresso = item.numeroParcela && item.totalParcelas
              ? `${item.numeroParcela}/${item.totalParcelas}`
              : null;
            return (
              <TouchableOpacity
                key={item.id}
                onPress={() => onAbrir(item)}
                activeOpacity={0.85}
                accessibilityRole="button"
                accessibilityLabel={`${item.descricao}${progresso ? `, parcela ${progresso}` : ''}, ${formatCurrency(item.valor)}, vence ${formatDateOnlyBR(item.vencimento)}`}
                style={{
                  width: CARTAO_LARGURA,
                  borderRadius: radius.lg,
                  borderWidth: 1,
                  borderColor: colors.chipBorder,
                  backgroundColor: colors.card,
                  padding: spacing.md + 2,
                }}
              >
                {/* selo de agendamento no canto, como na referência */}
                <View style={{
                  position: 'absolute', top: spacing.md, right: spacing.md,
                  width: 22, height: 22, borderRadius: 11,
                  backgroundColor: colors.warningBg,
                  alignItems: 'center', justifyContent: 'center',
                }}>
                  <Ionicons
                    name={item.atrasada ? 'alert' : 'time-outline'}
                    size={13}
                    color={colors.warning}
                  />
                </View>

                <View style={{ flexDirection: 'row', gap: spacing.md }}>
                  <View style={{
                    width: 38, height: 38, borderRadius: radius.md,
                    backgroundColor: colors.brandBg,
                    alignItems: 'center', justifyContent: 'center',
                  }}>
                    <Text style={{ fontSize: 18 }}>{item.categoria?.icone ?? '🧾'}</Text>
                  </View>

                  <View style={{ flex: 1 }}>
                    <Text numberOfLines={1} style={{ ...typography.cardTitle, lineHeight: 18, color: colors.textPrimary }}>
                      {item.descricao}{progresso ? ` (${progresso})` : ''}
                    </Text>
                    <Text style={{ ...typography.meta, color: colors.textSecondary, lineHeight: 15, marginTop: 1 }}>
                      {dataCurta(item.vencimento)}
                    </Text>
                    <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: spacing.sm }}>
                      {progresso && (
                        <View style={{
                          paddingHorizontal: spacing.sm, paddingVertical: 2,
                          borderRadius: radius.sm, backgroundColor: colors.brandBg,
                        }}>
                          <Text style={{ ...typography.meta, ...numeric, color: colors.brandFg }}>{progresso}</Text>
                        </View>
                      )}
                      <Text style={{
                        ...typography.value, ...numeric, color: colors.textPrimary,
                        marginLeft: 'auto',
                      }}>
                        -{formatCurrency(item.valor)}
                      </Text>
                    </View>
                  </View>
                </View>

                {item.cartao?.ultimosDigitos && (
                  <View style={{
                    flexDirection: 'row', alignItems: 'center', gap: 6,
                    marginTop: spacing.sm + 2, paddingTop: spacing.sm + 2,
                    borderTopWidth: 1, borderTopColor: colors.border,
                  }}>
                    <Ionicons name="card" size={14} color={colors.warning} />
                    <Text style={{ ...typography.meta, ...numeric, lineHeight: 14, color: colors.textSecondary }}>
                      {bandeira ? `${bandeira} ` : ''}•••• {item.cartao.ultimosDigitos}
                    </Text>
                  </View>
                )}
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      )}
    </View>
  );
}
