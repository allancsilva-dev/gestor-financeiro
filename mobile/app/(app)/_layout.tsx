import React from 'react';
import { Tabs } from 'expo-router';
import { useTheme } from '../../src/theme';
import { TouchableOpacity, View } from 'react-native';

// Simple functional icons built with Views — avoids external icon dependency
const IconTransacoes = ({ color }: { color: string }) => (
  <View style={{ gap: 3, width: 20, alignItems: 'flex-start' }}>
    <View style={{ height: 2, width: 18, backgroundColor: color, borderRadius: 1 }} />
    <View style={{ height: 2, width: 14, backgroundColor: color, borderRadius: 1 }} />
    <View style={{ height: 2, width: 10, backgroundColor: color, borderRadius: 1 }} />
  </View>
);

const IconMetas = ({ color }: { color: string }) => (
  <View style={{
    width: 18, height: 18, borderRadius: 9,
    borderWidth: 2, borderColor: color,
    alignItems: 'center', justifyContent: 'center'
  }}>
    <View style={{ width: 2, height: 6, backgroundColor: color, borderRadius: 1, marginBottom: -2 }} />
    <View style={{ width: 4, height: 2, backgroundColor: color, borderRadius: 1 }} />
  </View>
);

const IconPerfil = ({ color }: { color: string }) => (
  <View style={{ alignItems: 'center', gap: 2 }}>
    <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: color }} />
    <View style={{
      width: 18, height: 6, borderTopLeftRadius: 9, borderTopRightRadius: 9,
      backgroundColor: color, opacity: 0.7
    }} />
  </View>
);

const IconMais = ({ color }: { color: string }) => (
  <View style={{ flexDirection: 'row', gap: 3, alignItems: 'center' }}>
    {[0, 1, 2].map(i => (
      <View key={i} style={{ width: 4, height: 4, borderRadius: 2, backgroundColor: color, marginHorizontal: 1 }} />
    ))}
  </View>
);

export default function AppLayout() {
  const colors = useTheme();

  return (
    <Tabs screenOptions={{
      headerShown: false,
      tabBarStyle: {
        backgroundColor: colors.navBg,
        borderTopColor: colors.navBorder,
        borderTopWidth: 1,
        height: 64,
        paddingBottom: 8,
        paddingTop: 4,
      },
      tabBarActiveTintColor: colors.brand,
      tabBarInactiveTintColor: colors.textSecondary,
      tabBarLabelStyle: { fontSize: 9 },
    }}>
      <Tabs.Screen name="transacoes" options={{ title: 'Transações', tabBarIcon: ({ color }) => <IconTransacoes color={color} /> }} />
      <Tabs.Screen name="metas" options={{ title: 'Metas', tabBarIcon: ({ color }) => <IconMetas color={color} /> }} />
      <Tabs.Screen name="index" options={{ title: '', tabBarButton: (props) => (
        <TouchableOpacity
          onPress={props.onPress}
          style={{ width: 56, height: 56, borderRadius: 28, backgroundColor: colors.brand, marginTop: -20, alignItems: 'center', justifyContent: 'center' }}
        >
          <View style={{ width: 26, height: 26, flexDirection: 'row', flexWrap: 'wrap' }}>
            <View style={{ width: 7, height: 7, borderRadius: 2, margin: 2, backgroundColor: colors.brandText }} />
            <View style={{ width: 7, height: 7, borderRadius: 2, margin: 2, backgroundColor: colors.brandText }} />
            <View style={{ width: 7, height: 7, borderRadius: 2, margin: 2, backgroundColor: colors.brandText }} />
            <View style={{ width: 7, height: 7, borderRadius: 2, margin: 2, backgroundColor: colors.brandText }} />
          </View>
        </TouchableOpacity>
      ) }} />
      <Tabs.Screen name="perfil" options={{ title: 'Perfil', tabBarIcon: ({ color }) => <IconPerfil color={color} /> }} />
      <Tabs.Screen name="more" options={{ title: 'Mais', tabBarIcon: ({ color }) => <IconMais color={color} /> }} />
    </Tabs>
  );
}
