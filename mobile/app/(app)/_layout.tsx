import React from 'react';
import { Tabs } from 'expo-router';
import { useTheme } from '../../src/theme';
import { TouchableOpacity, View } from 'react-native';

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
      <Tabs.Screen name="transacoes" options={{ title: 'Transações' }} />
      <Tabs.Screen name="metas" options={{ title: 'Metas' }} />
      <Tabs.Screen name="index" options={{ title: '', tabBarButton: (props) => (
        <TouchableOpacity {...props} style={{ width: 56, height: 56, borderRadius: 28, backgroundColor: colors.brand, marginTop: -20, alignItems: 'center', justifyContent: 'center' }}>
          <View style={{ width: 26, height: 26, flexDirection: 'row', flexWrap: 'wrap' }}>
            <View style={{ width: 7, height: 7, borderRadius: 2, margin: 2, backgroundColor: colors.brandText }} />
            <View style={{ width: 7, height: 7, borderRadius: 2, margin: 2, backgroundColor: colors.brandText }} />
            <View style={{ width: 7, height: 7, borderRadius: 2, margin: 2, backgroundColor: colors.brandText }} />
            <View style={{ width: 7, height: 7, borderRadius: 2, margin: 2, backgroundColor: colors.brandText }} />
          </View>
        </TouchableOpacity>
      ) }} />
      <Tabs.Screen name="perfil" options={{ title: 'Perfil' }} />
      <Tabs.Screen name="more" options={{ title: 'Mais' }} />
    </Tabs>
  );
}
