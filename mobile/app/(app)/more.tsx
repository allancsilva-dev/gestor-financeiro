import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import { useTheme } from '../../src/theme';

const itens = [
  { label: 'Carteiras',    rota: '/more/carteiras',    icone: '₿' },
  { label: 'Contas Fixas', rota: '/more/contas-fixas', icone: '📅' },
  { label: 'Categorias',   rota: '/more/categorias',   icone: '🏷' },
  { label: 'Contas',       rota: '/more/contas',       icone: '🏦' },
  { label: 'Entrada por IA (em breve)', rota: null, icone: '🤖', desabilitado: true },
];

export default function MoreScreen() {
  const colors = useTheme();
  const router = useRouter();

  const navegar = (rota: string | null) => {
    if (!rota) return;
    router.push(rota as any);
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      {itens.map((it, idx) => (
        <TouchableOpacity
          key={idx}
          onPress={() => navegar(it.rota)}
          activeOpacity={it.desabilitado ? 1 : 0.7}
          style={{ height: 60, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, borderBottomWidth: 1, borderBottomColor: colors.border, opacity: it.desabilitado ? 0.4 : 1 }}
        >
          <View style={{ width: 40, height: 40, borderRadius: 8, backgroundColor: colors.brand + '1a', alignItems: 'center', justifyContent: 'center' }}>
            <Text style={{ color: colors.brand }}>{it.icone}</Text>
          </View>
          <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '500', flex: 1, marginLeft: 12 }}>{it.label}</Text>
          {!it.desabilitado && <Text style={{ color: colors.textMuted, fontSize: 18 }}>›</Text>}
          {it.desabilitado && <View style={{ paddingHorizontal: 8, paddingVertical: 4, borderRadius: 8, marginLeft: 8, backgroundColor: colors.border }}><Text style={{ color: colors.textMuted, fontSize: 11 }}>Em breve</Text></View>}
        </TouchableOpacity>
      ))}
    </View>
  );
}
