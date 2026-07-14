import React from 'react';
import { Stack } from 'expo-router';
import { useTheme } from '../../src/theme';
import { useReducedMotion } from 'react-native-reanimated';

export default function AuthLayout() {
  const colors = useTheme();
  const reduceMotion = useReducedMotion();
  return (
    <Stack
      screenOptions={{
        headerShown: false,
        animation: reduceMotion ? 'none' : 'fade',
        animationDuration: reduceMotion ? 0 : 180,
        contentStyle: { backgroundColor: colors.bg },
      }}
    />
  );
}
