import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../src/theme';

export default function Metas() {
  const colors = useTheme();
  return (
    <View style={[styles.container, { backgroundColor: colors.bg }]}> 
      <Text style={{ color: colors.textPrimary, fontSize: 18, fontWeight: '600' }}>Metas</Text>
      <Text style={{ color: colors.textSecondary, marginTop: 8 }}>Em breve</Text>
    </View>
  );
}

const styles = StyleSheet.create({ container: { flex: 1, alignItems: 'center', justifyContent: 'center' } });
