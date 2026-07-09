import React, { useState } from 'react';
import { Tabs, useRouter } from 'expo-router';
import { useTheme } from '../../src/theme';
import { Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import NovaTransacaoModal from '../../src/components/NovaTransacaoModal';
import ScreenTransition from '../../src/components/ui/ScreenTransition';

// Ícones funcionais em Views — sem dependência externa de ícones
const IconInicio = ({ color }: { color: string }) => (
  <View style={{ width: 20, height: 18, alignItems: 'center' }}>
    <View style={{
      width: 0, height: 0,
      borderLeftWidth: 9, borderRightWidth: 9, borderBottomWidth: 7,
      borderLeftColor: 'transparent', borderRightColor: 'transparent', borderBottomColor: color,
    }} />
    <View style={{
      width: 14, height: 10, borderWidth: 2, borderTopWidth: 0, borderColor: color,
      borderBottomLeftRadius: 2, borderBottomRightRadius: 2, marginTop: -1,
    }} />
  </View>
);

const IconTransacoes = ({ color }: { color: string }) => (
  <View style={{ flexDirection: 'row', gap: 5, height: 18, alignItems: 'center' }}>
    <View style={{ alignItems: 'center' }}>
      <View style={{
        width: 0, height: 0,
        borderLeftWidth: 4, borderRightWidth: 4, borderBottomWidth: 5,
        borderLeftColor: 'transparent', borderRightColor: 'transparent', borderBottomColor: color,
      }} />
      <View style={{ width: 2, height: 10, backgroundColor: color, borderRadius: 1 }} />
    </View>
    <View style={{ alignItems: 'center' }}>
      <View style={{ width: 2, height: 10, backgroundColor: color, borderRadius: 1 }} />
      <View style={{
        width: 0, height: 0,
        borderLeftWidth: 4, borderRightWidth: 4, borderTopWidth: 5,
        borderLeftColor: 'transparent', borderRightColor: 'transparent', borderTopColor: color,
      }} />
    </View>
  </View>
);

const IconPlanejamento = ({ color }: { color: string }) => (
  <View style={{
    width: 18, height: 18, borderRadius: 9,
    borderWidth: 2, borderColor: color,
  }}>
    <View style={{
      position: 'absolute', top: -2, right: -2,
      width: 9, height: 9, borderTopRightRadius: 9, backgroundColor: color,
    }} />
  </View>
);

const IconMais = ({ color }: { color: string }) => (
  <View style={{ width: 16, flexDirection: 'row', flexWrap: 'wrap', gap: 4, justifyContent: 'center' }}>
    {[0, 1, 2, 3].map(i => (
      <View key={i} style={{ width: 5, height: 5, borderRadius: 2.5, backgroundColor: color }} />
    ))}
  </View>
);

export default function AppLayout() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const [novaTransacaoVisible, setNovaTransacaoVisible] = useState(false);

  return (
    <>
      <Tabs screenLayout={({ children }) => <ScreenTransition>{children}</ScreenTransition>} screenOptions={{
        headerShown: false,
        tabBarStyle: {
          backgroundColor: colors.navBg,
          borderTopColor: colors.navBorder,
          borderTopWidth: 1,
          height: 64 + insets.bottom,
          paddingBottom: insets.bottom + 8,
          paddingTop: 6,
        },
        tabBarActiveTintColor: colors.brandFg,
        tabBarInactiveTintColor: colors.textSecondary,
        tabBarLabelStyle: { fontSize: 10, fontWeight: '500' },
      }}>
        <Tabs.Screen name="index" options={{ title: 'Início', tabBarIcon: ({ color }) => <IconInicio color={color} /> }} />
        <Tabs.Screen name="transacoes" options={{ title: 'Transações', tabBarIcon: ({ color }) => <IconTransacoes color={color} /> }} />
        <Tabs.Screen name="nova" options={{ title: '', tabBarButton: () => (
          <TouchableOpacity
            onPress={() => setNovaTransacaoVisible(true)}
            activeOpacity={0.8}
            accessibilityRole="button"
            accessibilityLabel="Nova transação"
            style={{ flex: 1, alignItems: 'center' }}
          >
            <LinearGradient
              colors={[colors.brand, colors.brandDeep]}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={{
                width: 54, height: 54, borderRadius: 27,
                marginTop: -22, alignItems: 'center', justifyContent: 'center',
                shadowColor: colors.brand, shadowOpacity: 0.55, shadowRadius: 14,
                shadowOffset: { width: 0, height: 8 }, elevation: 8,
              }}
            >
              <Text style={{ color: '#ffffff', fontSize: 30, lineHeight: 34, fontWeight: '300', marginTop: -2 }}>+</Text>
            </LinearGradient>
          </TouchableOpacity>
        ) }} />
        <Tabs.Screen name="metas" options={{ title: 'Planejamento', tabBarIcon: ({ color }) => <IconPlanejamento color={color} /> }} />
        <Tabs.Screen name="more" options={{ title: 'Mais', tabBarIcon: ({ color }) => <IconMais color={color} /> }} />
        <Tabs.Screen name="perfil" options={{ href: null }} />
      </Tabs>

      <NovaTransacaoModal
        visible={novaTransacaoVisible}
        onClose={() => setNovaTransacaoVisible(false)}
        onSaved={() => router.push('/(app)/transacoes')}
      />
    </>
  );
}
