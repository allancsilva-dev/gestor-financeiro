import React from 'react';
import { View, Text } from 'react-native';
import { useTheme } from '../../theme';
import { StatusPagamento } from '../../types';
import { STATUS_LABEL } from '../../utils/format';

interface BadgeProps {
  status: StatusPagamento;
}

export default function Badge({ status }: BadgeProps) {
  const colors = useTheme();

  const config = {
    PAGO:      { bg: colors.successBg, text: colors.success },
    PENDENTE:  { bg: colors.warningBg, text: colors.warning },
    ATRASADO:  { bg: colors.dangerBg,  text: colors.danger  },
    CANCELADO: { bg: colors.dangerBg,  text: colors.danger  },
  } as const;

  const { bg, text } = config[status] ?? config.PENDENTE;

  return (
    <View style={{ backgroundColor: bg, paddingHorizontal: 8, paddingVertical: 2, borderRadius: 20, alignSelf: 'flex-start' }}>
      <Text style={{ color: text, fontSize: 10, fontWeight: '700' }}>
        {STATUS_LABEL[status] ?? status}
      </Text>
    </View>
  );
}
