import React from 'react';
import { Stack } from 'expo-router';
import ScreenTransition from '../../src/components/ui/ScreenTransition';

export default function AuthLayout() {
  return (
    <Stack
      screenLayout={({ children }) => <ScreenTransition>{children}</ScreenTransition>}
      screenOptions={{
        headerShown: false,
        animation: 'none',
      }}
    />
  );
}
