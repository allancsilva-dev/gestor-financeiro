import React from 'react';
import { View, Text } from 'react-native';
import { useTheme } from '../src/theme';

export default function MaisScreen() {
  const colors = useTheme();
  return (
    <View style={{ flex: 1, backgroundColor: colors.bg, justifyContent: 'center', alignItems: 'center' }}>
      <Text style={{ color: colors.textPrimary, fontSize: 18, fontWeight: '600' }}>Mais</Text>
      <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 8 }}>Em breve</Text>
    </View>
  );
}
