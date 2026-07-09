import React from 'react';
import { Text, TouchableOpacity, StyleProp, ViewStyle } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useTheme } from '../../theme';

interface FabProps {
  onPress: () => void;
  accessibilityLabel: string;
  style?: StyleProp<ViewStyle>;
}

// FAB violeta com gradiente e glow (protótipo: 140deg #7c5cfc→#8b2fff)
export default function Fab({ onPress, accessibilityLabel, style }: FabProps) {
  const colors = useTheme();
  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.8}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      style={[
        {
          position: 'absolute',
          right: 16,
          bottom: 24,
          shadowColor: colors.brand,
          shadowOpacity: 0.5,
          shadowRadius: 12,
          shadowOffset: { width: 0, height: 6 },
          elevation: 8,
        },
        style,
      ]}
    >
      <LinearGradient
        colors={[colors.brand, '#8b2fff']}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={{ width: 56, height: 56, borderRadius: 28, alignItems: 'center', justifyContent: 'center' }}
      >
        <Text style={{ color: '#ffffff', fontSize: 28, lineHeight: 32, fontWeight: '400' }}>+</Text>
      </LinearGradient>
    </TouchableOpacity>
  );
}
