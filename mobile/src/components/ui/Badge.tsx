import React from 'react';
import { View, Text } from 'react-native';
import { useTheme } from '../../theme';
import { StatusPagamento } from '../../types';
import { STATUS_LABEL } from '../../utils/format';

export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'brand';

interface BadgeProps {
  status?: StatusPagamento;
  tone?: BadgeTone;
  children?: React.ReactNode;
}

// Pill de status: via `status` de pagamento (mapeia tom/label) ou `tone` + children
export default function Badge({ status, tone = 'info', children }: BadgeProps) {
  const colors = useTheme();

  const tones: Record<BadgeTone, { bg: string; text: string }> = {
    success: { bg: colors.successBg, text: colors.success },
    warning: { bg: colors.warningBg, text: colors.warning },
    danger: { bg: colors.dangerBg, text: colors.danger },
    info: { bg: colors.infoBg, text: colors.info },
    brand: { bg: colors.brandBg, text: colors.brandFg },
  };

  const statusTone: Record<StatusPagamento, BadgeTone> = {
    PAGO: 'success',
    PENDENTE: 'warning',
    ATRASADO: 'danger',
    CANCELADO: 'danger',
  } as Record<StatusPagamento, BadgeTone>;

  const resolved = status ? tones[statusTone[status] ?? 'warning'] : tones[tone];
  const label = children ?? (status ? STATUS_LABEL[status] ?? status : null);

  return (
    <View style={{ backgroundColor: resolved.bg, paddingHorizontal: 8, paddingVertical: 2, borderRadius: 999, alignSelf: 'flex-start' }}>
      <Text style={{ color: resolved.text, fontSize: 10, fontWeight: '700' }}>{label}</Text>
    </View>
  );
}
