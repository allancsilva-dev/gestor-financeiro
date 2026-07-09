import '../src/global.css';
import React from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { Stack } from 'expo-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../src/context/AuthContext';
import ScreenTransition from '../src/components/ui/ScreenTransition';

const queryClient = new QueryClient();

export default function RootLayout() {
  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <AuthProvider>
          <Stack
            screenLayout={({ children }) => <ScreenTransition>{children}</ScreenTransition>}
            screenOptions={{
              headerShown: false,
              animation: 'none',
            }}
          />
        </AuthProvider>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
